package com.sdocument.sdk.models

import kotlinx.serialization.Serializable

@Serializable
data class Credential(
    val username: String,
    val password: String
)