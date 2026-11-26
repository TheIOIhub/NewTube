package com.example.newtube.account

import com.example.newtube.model.UserAccount
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Decodes the stored profile; corrupt or missing JSON yields null. */
internal fun parseStoredAccount(raw: String?): UserAccount? {
    if (raw == null) return null
    return runCatching {
        val obj = Json.parseToJsonElement(raw.removePrefix("\uFEFF")).jsonObject
        UserAccount(
            name = obj["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            email = obj["email"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            avatarUrl = obj["avatarUrl"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            pageId = obj["pageId"]?.jsonPrimitive?.contentOrNull,
        )
    }.getOrNull()
}
