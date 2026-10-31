package com.sdocument.sdk.service

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject

object DataChannel {

    private val _deltaFlow = MutableSharedFlow<String?>(replay = 0)
    val deltaFlow: MutableSharedFlow<String?> = _deltaFlow

    private val _metadataFlow = MutableSharedFlow<JSONObject?>(replay = 0)
    val metadataFlow: MutableSharedFlow<JSONObject?> = _metadataFlow

    suspend fun emitDelta(delta: String?) {
        _deltaFlow.emit(delta)
    }

    suspend fun emitMetadata(metadata: JSONObject?) {
        _metadataFlow.emit(metadata)
    }

    fun observeDelta(): Flow<String?> = _deltaFlow.asSharedFlow()

    fun observeMetadata(): Flow<JSONObject?> = _metadataFlow.asSharedFlow()
}