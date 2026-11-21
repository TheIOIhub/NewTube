package com.example.newtube.account

interface ClientCredentialsProvider {
    suspend fun get(): ClientCredentials
}
