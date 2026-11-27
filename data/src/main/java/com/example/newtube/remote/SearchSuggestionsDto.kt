package com.example.newtube.remote

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SearchSuggestionsDto(
    val query: String = "",
    val suggestions: List<String> = emptyList(),
)
