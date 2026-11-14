package com.example.newtube.remote

import com.squareup.moshi.JsonClass

/**
 * Comment from `/api/v1/comments/{videoId}`.
 *
 * `replies` and `contentHtml` are ignored on purpose: the app shows a flat list
 * of top-level comments and renders the plain-text `content`.
 */
@JsonClass(generateAdapter = true)
data class CommentDto(
    val commentId: String? = null,
    val author: String = "",
    val authorId: String? = null,
    val authorThumbnail: String = "",
    val content: String = "",
    val likeCount: Long = 0,
    val publishedText: String = "",
    val isPinned: Boolean = false,
)

/**
 * Wrapper returned by `/api/v1/comments/{videoId}`. The same endpoint is used
 * for pagination through the opaque [continuation] token.
 */
@JsonClass(generateAdapter = true)
data class CommentsPageDto(
    val comments: List<CommentDto> = emptyList(),
    val continuation: String? = null,
    val commentCount: Long = 0,
)
