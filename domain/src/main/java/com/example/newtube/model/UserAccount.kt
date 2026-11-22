package com.example.newtube.model

data class UserAccount(
    val name: String,
    val email: String,
    val avatarUrl: String,
    val pageId: String? = null,
)
