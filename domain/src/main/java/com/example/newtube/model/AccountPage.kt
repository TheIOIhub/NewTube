package com.example.newtube.model

data class AccountPage<T>(
    val items: List<T>,
    val continuation: String?,
)
