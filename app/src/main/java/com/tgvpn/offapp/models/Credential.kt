package com.tgvpn.offapp.models

import kotlinx.serialization.Serializable

@Serializable
data class Credential(
    val username: String,
    val password: String
)