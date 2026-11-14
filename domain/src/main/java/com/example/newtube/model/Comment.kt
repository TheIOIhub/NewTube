package com.example.newtube.model

data class Comment(
    val id: String,
    val author: String,
    val authorChannelId: String,
    val authorAvatarUrl: String,
    val content: String,
    val likeCount: Long,
    val publishedText: String,
    val isPinned: Boolean,
)

/**
 * One page of comments. [continuation] is the opaque token for the next page,
 * `null` when there are no more comments.
 */
data class CommentPage(
    val comments: List<Comment>,
    val continuation: String?,
    val totalCount: Long,
)
