package com.sdocument.template.vpn.viewmodel.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sdocument.template.vpn.network.RetrofitClient
import com.sdocument.template.vpn.network.models.LoginRequest
import com.sdocument.sdk.utils.NetworkLogger
import com.sdocument.template.vpn.viewmodel.ConfigValues
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val configValues: ConfigValues
) : ViewModel() {

    private val _state = MutableStateFlow(AuthContract.State())
    val state: StateFlow<AuthContract.State> = _state.asStateFlow()

    private val _effect = MutableSharedFlow<AuthContract.Effect>()
    val effect: SharedFlow<AuthContract.Effect> = _effect.asSharedFlow()

    fun handleIntent(intent: AuthContract.Intent) {
        when (intent) {
            is AuthContract.Intent.KeyChanged -> {
                val cleaned = intent.key.uppercase().filter { it.isLetterOrDigit() }.take(12)
                _state.update { it.copy(rawKey = cleaned, error = null) }
            }
            is AuthContract.Intent.Submit -> {
                viewModelScope.launch { submit() }
            }
        }
    }

    private suspend fun submit() {
        val key = _state.value.rawKey
        if (key.length < 12) {
            _state.update { it.copy(error = "Ключ должен содержать 12 символов") }
            return
        }

        _state.update { it.copy(isLoading = true, error = null) }

        try {
            val formattedKey = buildString {
                for (i in key.indices) {
                    append(key[i])
                    if ((i == 3 || i == 7) && i != key.lastIndex) append("-")
                }
            }

            val request = LoginRequest(
                accessKey = key,
                deviceId = NetworkLogger.getDeviceId(),
                buildId = configValues.buildId
            )

            val response = withContext(Dispatchers.IO) {
                RetrofitClient.apiService.login(request)
            }

            if (response.isSuccessful && response.body()?.status == "success") {
                val body = response.body()!!
                _state.update { it.copy(isLoading = false) }
                
                NetworkLogger.log("authentication", "Access Key Activated ✅ \n Key: $key")
                
                _effect.emit(
                    AuthContract.Effect.AuthSuccess(
                        url = configValues.webViewUrl,
                        key = formattedKey,
                        isVerificationNeeded = body.isVerificationNeeded ?: false,
                        isVerified = body.isVerified ?: false,
                        userId = body.userId
                    )
                )
            } else {
                val msg = response.body()?.error ?: "Неверный или неактивный ключ"
                _state.update { it.copy(isLoading = false, error = msg) }
                NetworkLogger.log("authentication", "Access Key Failed ❌ \n Key: $key \n Error: $msg")
            }
        } catch (e: Exception) {
            _state.update { it.copy(isLoading = false, error = "Ошибка подключения к серверу") }
            NetworkLogger.log("authentication", "Access Key Failed ❌ \n Error: ${e.message}")
        }
    }
}
