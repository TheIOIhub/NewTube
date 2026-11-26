package com.example.newtube.account

interface ClientCredentialsProvider {
    suspend fun get(): ClientCredentials

    /** Drops the cached pair so the next [get] scrapes the TV page again. */
    fun invalidate()
}
