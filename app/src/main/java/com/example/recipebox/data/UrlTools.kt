package com.example.recipebox.data

import android.net.Uri

enum class Platform { TIKTOK, INSTAGRAM, YOUTUBE, FACEBOOK, OTHER }

object UrlTools {
    private val urlRegex = Regex("""https?://\S+""")

    private fun isTracking(name: String) =
        name.startsWith("utm_") || name.startsWith("share_") ||
                name in setOf("igsh", "igshid", "si", "fbclid", "feature",
            "is_from_webapp", "sender_device", "_r", "_t", "mibextid")

    /** Shared text often has extra words around the link, so pull the URL out. */
    fun extractUrl(text: String): String? =
        urlRegex.find(text)?.value?.trimEnd('.', ',', ')', ']', '!', '?')

    /** Same video shared twice should produce the same string. */
    fun normalize(raw: String): String {
        val uri = Uri.parse(raw)
        val host = uri.host?.lowercase()?.removePrefix("www.") ?: return raw
        val builder = Uri.Builder()
            .scheme("https")
            .authority(host)
            .path(uri.path?.trimEnd('/') ?: "")
        uri.queryParameterNames.sorted().filterNot(::isTracking).forEach { name ->
            uri.getQueryParameters(name).forEach { builder.appendQueryParameter(name, it) }
        }
        return builder.build().toString()
    }

    /** Match on the parsed hostname, never a substring of the whole URL. */
    fun identifyPlatform(url: String): Platform {
        val host = Uri.parse(url).host?.lowercase() ?: return Platform.OTHER
        fun isDomain(d: String) = host == d || host.endsWith(".$d")
        return when {
            isDomain("tiktok.com") -> Platform.TIKTOK
            isDomain("instagram.com") -> Platform.INSTAGRAM
            isDomain("youtube.com") || host == "youtu.be" -> Platform.YOUTUBE
            isDomain("facebook.com") || host == "fb.watch" -> Platform.FACEBOOK
            else -> Platform.OTHER
        }
    }

    /** "YouTube", "TikTok"... for known platforms, otherwise the site's domain. */
    fun sourceLabel(url: String): String = when (identifyPlatform(url)) {
        Platform.YOUTUBE -> "YouTube"
        Platform.TIKTOK -> "TikTok"
        Platform.INSTAGRAM -> "Instagram"
        Platform.FACEBOOK -> "Facebook"
        Platform.OTHER -> Uri.parse(url).host?.lowercase()
            ?.removePrefix("www.")?.removePrefix("m.") ?: url
    }
}