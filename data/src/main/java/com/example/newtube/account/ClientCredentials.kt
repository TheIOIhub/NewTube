package com.example.newtube.account

data class ClientCredentials(
    val clientId: String,
    val clientSecret: String,
) {
    /** The secret is scraped from a public page but must still stay out of logs. */
    override fun toString(): String = "ClientCredentials(clientId=$clientId, clientSecret=***)"
}
