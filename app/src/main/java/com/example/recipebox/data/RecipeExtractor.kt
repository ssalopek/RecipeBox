package com.example.recipebox.data

import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class Extracted(
    val title: String,
    val body: String,
    val image: String? = null
)

object RecipeExtractor {
    private const val MAX_BYTES = 3L * 1024 * 1024
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun extract(url: String, platform: Platform): Extracted? =
        withContext(Dispatchers.IO) {
            try {
                val result = when (platform) {
                    Platform.TIKTOK ->
                        runCatching { tiktokOEmbed(url) }.getOrNull() ?: fromPage(url, platform)
                    Platform.INSTAGRAM ->
                        instagramCaption(url) ?: fromPage(url, platform)
                    Platform.YOUTUBE ->
                        runCatching { fromPage(url, platform) }.getOrNull() ?: youtubeOEmbed(url)
                    else -> fromPage(url, platform)
                }
                // YouTube thumbnails can be built from the video ID if the page gave no image
                if (platform == Platform.YOUTUBE && result != null && result.image == null) {
                    result.copy(image = youtubeThumbnail(url))
                } else result
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }

    // ---------- Networking ----------

    private fun get(url: String, extraHeaders: Map<String, String> = emptyMap()): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept-Language", "en")
            .apply { extraHeaders.forEach { (k, v) -> header(k, v) } }
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            if (response.request.url.host.startsWith("consent.")) error("consent wall")
            val source = response.body.source()
            source.request(MAX_BYTES)
            return source.buffer.clone().readUtf8()
        }
    }

    // ---------- Web pages ----------

    private fun fromPage(url: String, platform: Platform): Extracted? {
        val headers = if (platform == Platform.YOUTUBE) {
            mapOf("Cookie" to "SOCS=CAI; CONSENT=YES+1")
        } else emptyMap()
        val html = get(url, headers)
        val doc = Jsoup.parse(html, url)
        val ogImage = resolveImage(meta(doc, "og:image"), url)

        findRecipe(doc)?.let { recipe ->
            val body = recipeToText(recipe)
            if (body != null) {
                val name = clean(recipe.optString("name"))
                val image = resolveImage(firstImage(recipe.opt("image")), url) ?: ogImage
                return Extracted(name.ifBlank { pageTitle(doc) }, body, image)
            }
        }

        val description = (if (platform == Platform.YOUTUBE) youtubeDescription(html) else null)
            ?: meta(doc, "og:description")
            ?: meta(doc, "description")
        val title = pageTitle(doc)
        if (title.isBlank() && description.isNullOrBlank()) return null
        return Extracted(title, description.orEmpty(), ogImage)
    }

    private fun pageTitle(doc: Document): String =
        (meta(doc, "og:title") ?: doc.title()).trim()

    private fun meta(doc: Document, key: String): String? =
        doc.selectFirst("meta[property=$key], meta[name=$key]")
            ?.attr("content")?.trim()?.takeIf { it.isNotEmpty() }

    private fun clean(s: String): String = Jsoup.parse(s).text().trim()

    // ---------- Image ----------

    // Makes relative links absolute and accepts only http(s)
    private fun resolveImage(raw: String?, base: String): String? {
        if (raw.isNullOrBlank()) return null
        val absolute = runCatching { URI(base).resolve(raw.trim()).toString() }.getOrNull()
            ?: return null
        return absolute.takeIf { it.startsWith("http://") || it.startsWith("https://") }
    }

    // "image" can be a string, a list, or an ImageObject with a url; take the first one
    private fun firstImage(node: Any?): String? = when (node) {
        is String -> node
        is JSONArray -> (0 until node.length()).firstNotNullOfOrNull { firstImage(node.opt(it)) }
        is JSONObject -> node.optString("url").ifBlank { node.optString("contentUrl") }.ifBlank { null }
        else -> null
    }

    private fun youtubeThumbnail(url: String): String? {
        val uri = Uri.parse(url)
        val segments = uri.pathSegments
        val id = when {
            uri.host?.lowercase() == "youtu.be" -> segments.firstOrNull()
            uri.getQueryParameter("v") != null -> uri.getQueryParameter("v")
            segments.firstOrNull() in setOf("shorts", "embed", "live", "v") -> segments.getOrNull(1)
            else -> null
        } ?: return null
        if (!Regex("^[A-Za-z0-9_-]{11}$").matches(id)) return null
        return "https://i.ytimg.com/vi/$id/hqdefault.jpg"
    }

    // ---------- JSON-LD recipes ----------

    private fun findRecipe(doc: Document): JSONObject? {
        for (script in doc.select("script[type=application/ld+json]")) {
            val node = try {
                JSONTokener(script.data().trim()).nextValue()
            } catch (e: Exception) {
                continue
            }
            findRecipeNode(node)?.let { return it }
        }
        return null
    }

    private fun findRecipeNode(node: Any?): JSONObject? = when (node) {
        is JSONArray -> (0 until node.length()).firstNotNullOfOrNull { findRecipeNode(node.opt(it)) }
        is JSONObject -> if (isRecipe(node)) node else findRecipeNode(node.opt("@graph"))
        else -> null
    }

    private fun isRecipe(o: JSONObject): Boolean = when (val type = o.opt("@type")) {
        is String -> type == "Recipe"
        is JSONArray -> (0 until type.length()).any { type.optString(it) == "Recipe" }
        else -> false
    }

    private fun recipeToText(recipe: JSONObject): String? {
        val ingredients = arrayOf("recipeIngredient", "recipeIngredients")
            .firstNotNullOfOrNull { recipe.optJSONArray(it) }
            ?.let { arr -> (0 until arr.length()).map { clean(arr.optString(it)) } }
            .orEmpty()
            .filter { it.isNotEmpty() }

        val steps = mutableListOf<String>()
        flattenSteps(recipe.opt("recipeInstructions"), steps)

        if (ingredients.isEmpty() && steps.isEmpty()) return null
        return buildString {
            if (ingredients.isNotEmpty()) {
                appendLine("Ingredients")
                ingredients.forEach { appendLine("- $it") }
            }
            if (steps.isNotEmpty()) {
                if (isNotEmpty()) appendLine()
                appendLine("Steps")
                steps.forEachIndexed { i, s -> appendLine("${i + 1}. $s") }
            }
        }.trim()
    }

    private fun flattenSteps(node: Any?, out: MutableList<String>) {
        when (node) {
            is String -> node.split("\n").map { clean(it) }.filter { it.isNotEmpty() }.forEach(out::add)
            is JSONArray -> for (i in 0 until node.length()) flattenSteps(node.opt(i), out)
            is JSONObject ->
                if (node.has("itemListElement")) {
                    flattenSteps(node.opt("itemListElement"), out)
                } else {
                    val text = node.optString("text").ifBlank { node.optString("name") }
                    if (text.isNotBlank()) out.add(clean(text))
                }
        }
    }

    // ---------- Social platforms ----------

    private fun tiktokOEmbed(url: String): Extracted? {
        val json = JSONObject(get("https://www.tiktok.com/oembed?url=${enc(url)}"))
        val caption = json.optString("title").trim()
        if (caption.isEmpty()) return null
        val image = resolveImage(json.optString("thumbnail_url"), url)
        return Extracted(shortTitle(caption), caption, image)
    }

    private fun youtubeOEmbed(url: String): Extracted? {
        val json = JSONObject(get("https://www.youtube.com/oembed?url=${enc(url)}&format=json"))
        val title = json.optString("title").trim()
        if (title.isEmpty()) return null
        return Extracted(title, "", resolveImage(json.optString("thumbnail_url"), url))
    }

    // Unofficial: reads the full description embedded in the page. Falls back to og:description.
    private fun youtubeDescription(html: String): String? {
        val key = "\"shortDescription\":\""
        val start = html.indexOf(key).takeIf { it >= 0 } ?: return null
        val quoted = StringBuilder("\"")
        var i = start + key.length
        while (i < html.length) {
            val c = html[i]
            if (c == '\\' && i + 1 < html.length) {
                quoted.append(c).append(html[i + 1]); i += 2; continue
            }
            if (c == '"') break
            quoted.append(c); i++
        }
        quoted.append('"')
        return (JSONTokener(quoted.toString()).nextValue() as? String)?.trim()?.takeIf { it.isNotEmpty() }
    }

    // Unofficial and fragile: tries the public embed page first, then the preview tags
    // Instagram serves to link-preview crawlers. Both may stop working at any time.
    private fun instagramCaption(url: String): Extracted? =
        runCatching { instagramEmbed(url) }.getOrNull()
            ?: runCatching { instagramPreview(url) }.getOrNull()

    private fun instagramEmbed(url: String): Extracted? {
        val match = Regex("""instagram\.com/(p|reel|reels|tv)/([A-Za-z0-9_-]+)""").find(url)
            ?: return null
        val (type, code) = match.destructured
        val kind = if (type == "reels") "reel" else type
        val html = get("https://www.instagram.com/$kind/$code/embed/captioned/")
        val doc = Jsoup.parse(html, "https://www.instagram.com/")

        val captionElement = doc.selectFirst(".Caption") ?: return null
        captionElement.select(".CaptionUsername, .CaptionComments").remove()
        captionElement.select("br").after("\\n")
        val caption = captionElement.text().replace("\\n", "\n").lines()
            .joinToString("\n") { it.trim() }.trim().takeIf { it.isNotEmpty() } ?: return null

        val image = resolveImage(doc.selectFirst("img.EmbeddedMediaImage")?.absUrl("src"), url)
        return Extracted(shortTitle(caption), caption, image)
    }

    private fun instagramPreview(url: String): Extracted? {
        val html = get(
            url,
            mapOf("User-Agent" to "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)")
        )
        val doc = Jsoup.parse(html, url)
        val description = meta(doc, "og:description") ?: return null
        // Format is usually: 12 likes, 3 comments - user on date: "the caption"
        val caption = Regex("""^.*?:\s*["“](.*)["”]\.?$""", RegexOption.DOT_MATCHES_ALL)
            .find(description)?.groupValues?.get(1)?.trim()
            ?.takeIf { it.isNotEmpty() } ?: return null
        return Extracted(shortTitle(caption), caption, resolveImage(meta(doc, "og:image"), url))
    }

    private fun shortTitle(caption: String): String {
        val firstLine = caption.lineSequence().first().trim()
        return if (firstLine.length <= 60) firstLine
        else firstLine.take(60).substringBeforeLast(' ', firstLine.take(60)) + "…"
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}