package com.example.newtube.account

/** Pure scraping helpers for the TV web page and its base JavaScript bundle. */
object YouTubeTvPage {
    private const val ORIGIN = "https://www.youtube.com"

    private val scriptWithIdThenSrc =
        Regex("""<script[^>]*\bid\s*=\s*["']base-js["'][^>]*\bsrc\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val scriptWithSrcThenId =
        Regex("""<script[^>]*\bsrc\s*=\s*["']([^"']+)["'][^>]*\bid\s*=\s*["']base-js["']""", RegexOption.IGNORE_CASE)
    private val assignedSrcWithBase =
        Regex("""\.src\s*=\s*['"]([^'"]*m=base[^'"]*)['"]""")
    private val assignedSrcWithId =
        Regex("""\.src\s*=\s*['"]([^'"]+)['"][\s\S]{0,200}?\.id\s*=\s*['"]base-js['"]""")
    private val clientCredentials =
        Regex("""clientId\s*:\s*["']([-\w]+\.apps\.googleusercontent\.com)["']\s*,\s*\w+\s*:\s*["']([\w-]+)["']""")

    fun findBaseJsUrl(html: String): String? {
        val raw =
            scriptWithIdThenSrc.find(html)?.groupValues?.get(1)
                ?: scriptWithSrcThenId.find(html)?.groupValues?.get(1)
                ?: assignedSrcWithBase.find(html)?.groupValues?.get(1)
                ?: assignedSrcWithId.find(html)?.groupValues?.get(1)
                ?: return null
        return resolve(raw)
    }

    fun findClientCredentials(js: String): ClientCredentials? {
        val match = clientCredentials.find(js) ?: return null
        return ClientCredentials(
            clientId = match.groupValues[1],
            clientSecret = match.groupValues[2],
        )
    }

    fun resolve(url: String): String =
        when {
            url.startsWith("https://") || url.startsWith("http://") -> url
            url.startsWith("//") -> "https:$url"
            url.startsWith("/") -> "$ORIGIN$url"
            else -> "$ORIGIN/$url"
        }
}
