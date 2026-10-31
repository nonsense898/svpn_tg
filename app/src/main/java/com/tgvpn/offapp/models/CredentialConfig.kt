package com.tgvpn.offapp.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class CredentialConfig(
    val credentials: List<Credential>
)

object ConfigParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseCredentials(jsonString: String): CredentialConfig? {
        return try {
            json.decodeFromString<CredentialConfig>(jsonString)
        } catch (e: Exception) {
            null
        }
    }
}