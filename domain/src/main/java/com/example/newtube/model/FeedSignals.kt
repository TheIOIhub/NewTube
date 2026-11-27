package com.example.newtube.model

/** Local, privacy-friendly signals used to rank the "Для вас" feed. */
data class FeedSignals(
    val channelAffinity: Map<String, Float>,
    val subscribedChannelIds: Set<String>,
    val preferredLanguage: String,
)
