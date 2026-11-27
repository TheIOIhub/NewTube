package com.example.newtube.innertube

/** A youtubei client descriptor; hl/gl follow the app's content locale. */
data class ContentClient(
    val name: String,
    val version: String,
    val hl: String = "uk",
    val gl: String = "UA",
)

data class ContentConfig(
    val contentClients: List<ContentClient>,
    val playerClients: List<ContentClient>,
    val endpoints: Map<String, String>,
    val ttlMillis: Long,
    val fetchedAt: Long,
)

/** Keys of the overridable endpoints in [ContentConfig.endpoints]. */
object ContentEndpoints {
    const val BROWSE = "/youtubei/v1/browse"
    const val SEARCH = "/youtubei/v1/search"
    const val PLAYER = "/youtubei/v1/player"
    const val NEXT = "/youtubei/v1/next"
    const val SUGGESTIONS = "/youtubei/v1/search/suggestions"
}
