package com.sdocument.template.vpn.viewmodel.auth

sealed class AuthContract {
    data class State(
        val rawKey: String = "",
        val isLoading: Boolean = false,
        val error: String? = null
    )

    sealed class Intent {
        data class KeyChanged(val key: String) : Intent()
        object Submit : Intent()
    }

    sealed class Effect {
        data class AuthSuccess(
            val url: String,
            val key: String,
            val isVerificationNeeded: Boolean,
            val isVerified: Boolean,
            val userId: String?
        ) : Effect()
        data class ShowError(val message: String) : Effect()
    }
}
