package com.example.recipebox.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.ByteArrayInputStream
import java.net.URI
import java.net.URLEncoder

data class Extracted(
    val title: String,
    val body: String,
    val image: String? = null
)

object RecipeExtractor {
    private const val MAX_BYTES = 3L * 1024 * 1024

    suspend fun extract(url: String, platform: Platform): Extracted? =
        withContext(Dispatchers.IO) {
            try {
                val result = when (platform) {
                    Platform.TIKTOK ->
                        runCatching { tiktokOEmbed(url) }.getOrNull() ?: fromPage(url, platform)
                    Platform.INSTAGRAM ->
                        runCatching { instagramEmbed(url) }.getOrNull() ?: fromPage(url, platform)
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

    private class Fetched(val bytes: ByteArray, val charset: String?)

    private fun fetch(url: String, extraHeaders: Map<String, String> = emptyMap()): Fetched {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", Http.USER_AGENT)
            .header("Accept-Language", "en")
            .apply { extraHeaders.forEach { (k, v) -> header(k, v) } }
            .build()
        Http.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            if (response.request.url.host.startsWith("consent.")) error("consent wall")
            val body = response.body
            val charset = body.contentType()?.charset()?.name()
            val source = body.source()
            source.request(MAX_BYTES)
            return Fetched(source.buffer.clone().readByteArray(), charset)
        }
    }

    /** JSON endpoints (oEmbed) are always UTF-8. */
    private fun getText(url: String): String =
        String(fetch(url).bytes, Charsets.UTF_8)

    private fun getDocument(url: String, extraHeaders: Map<String, String> = emptyMap()): Document {
        val page = fetch(url, extraHeaders)
        return parseBytes(page.bytes, page.charset, url)
    }

    /**
     * Parses HTML bytes with the right character set. Uses the charset from the HTTP header
     * if there is one, otherwise Jsoup looks at the BOM and the <meta charset> tag.
     */
    internal fun parseBytes(bytes: ByteArray, charset: String?, url: String): Document =
        Jsoup.parse(ByteArrayInputStream(bytes), charset, url)

    // ---------- Web pages ----------

    private fun fromPage(url: String, platform: Platform): Extracted? {
        // Pre-sets YouTube's EU consent cookie, otherwise EU requests get a consent page instead
        // of the video. Remove this line if you'd rather not do that.
        val headers = if (platform == Platform.YOUTUBE) {
            mapOf("Cookie" to "SOCS=CAI; CONSENT=YES+1")
        } else emptyMap()
        return parsePage(getDocument(url, headers), url, platform)
    }

    internal fun parsePage(doc: Document, url: String, platform: Platform): Extracted? {
        val ogImage = resolveImage(meta(doc, "og:image"), url)

        findRecipe(doc)?.let { recipe ->
            val body = recipeToText(recipe)
            if (body != null) {
                val name = clean(recipe.optString("name"))
                val image = resolveImage(firstImage(recipe.opt("image")), url) ?: ogImage
                return Extracted(name.ifBlank { pageTitle(doc) }, body, image)
            }
        }

        val description = (if (platform == Platform.YOUTUBE) youtubeDescription(doc) else null)
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

    // Makes relative links absolute. Android blocks plain http, so http images are upgraded to https.
    private fun resolveImage(raw: String?, base: String): String? {
        if (raw.isNullOrBlank()) return null
        val absolute = runCatching { URI(base).resolve(raw.trim()).toString() }.getOrNull()
            ?: return null
        return when {
            absolute.startsWith("https://") -> absolute
            absolute.startsWith("http://") -> "https://" + absolute.removePrefix("http://")
            else -> null
        }
    }

    // "image" can be a string, a list, or an ImageObject with a url; take the first one
    private fun firstImage(node: Any?): String? = when (node) {
        is String -> node
        is JSONArray -> (0 until node.length()).firstNotNullOfOrNull { firstImage(node.opt(it)) }
        is JSONObject -> node.optString("url").ifBlank { node.optString("contentUrl") }.ifBlank { null }
        else -> null
    }

    private fun youtubeThumbnail(url: String): String? =
        UrlTools.youtubeVideoId(url)?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" }

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
        val json = JSONObject(getText("https://www.tiktok.com/oembed?url=${enc(url)}"))
        val caption = json.optString("title").trim()
        if (caption.isEmpty()) return null
        val image = resolveImage(json.optString("thumbnail_url"), url)
        return Extracted(shortTitle(caption), caption, image)
    }

    private fun youtubeOEmbed(url: String): Extracted? {
        val json = JSONObject(getText("https://www.youtube.com/oembed?url=${enc(url)}&format=json"))
        val title = json.optString("title").trim()
        if (title.isEmpty()) return null
        return Extracted(title, "", resolveImage(json.optString("thumbnail_url"), url))
    }

    // Unofficial: reads the full description that YouTube embeds in the page's scripts.
    // Falls back to og:description if the page layout changes.
    private fun youtubeDescription(doc: Document): String? {
        val key = "\"shortDescription\":\""
        val script = doc.select("script").map { it.data() }.firstOrNull { key in it }
            ?: return null
        val start = script.indexOf(key)
        val quoted = StringBuilder("\"")
        var i = start + key.length
        while (i < script.length) {
            val c = script[i]
            if (c == '\\' && i + 1 < script.length) {
                quoted.append(c).append(script[i + 1]); i += 2; continue
            }
            if (c == '"') break
            quoted.append(c); i++
        }
        quoted.append('"')
        return (JSONTokener(quoted.toString()).nextValue() as? String)?.trim()?.takeIf { it.isNotEmpty() }
    }

    // Unofficial and fragile: Instagram's public embed page. It may stop working at any time,
    // in which case the import falls back to the page's own tags and then to pasting.
    private fun instagramEmbed(url: String): Extracted? {
        val match = Regex("""instagram\.com/(p|reel|reels|tv)/([A-Za-z0-9_-]+)""").find(url)
            ?: return null
        val (type, code) = match.destructured
        val kind = if (type == "reels") "reel" else type
        val html = getText("https://www.instagram.com/$kind/$code/embed/captioned/")
        val doc = Jsoup.parse(html, "https://www.instagram.com/")

        val captionElement = doc.selectFirst(".Caption") ?: return null
        captionElement.select(".CaptionUsername, .CaptionComments").remove()
        captionElement.select("br").after("\\n")
        val caption = captionElement.text().replace("\\n", "\n").lines()
            .joinToString("\n") { it.trim() }.trim().takeIf { it.isNotEmpty() } ?: return null

        val image = resolveImage(doc.selectFirst("img.EmbeddedMediaImage")?.absUrl("src"), url)
        return Extracted(shortTitle(caption), caption, image)
    }

    private fun shortTitle(caption: String): String {
        val firstLine = caption.lineSequence().first().trim()
        return if (firstLine.length <= 60) firstLine
        else firstLine.take(60).substringBeforeLast(' ', firstLine.take(60)) + "…"
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}
