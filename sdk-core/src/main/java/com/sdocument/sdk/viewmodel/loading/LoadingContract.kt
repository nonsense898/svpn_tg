package com.sdocument.sdk.viewmodel.loading

class LoadingContract {
    data class State(
        val isAnimating: Boolean = false,
        val isOnline: Boolean = true,
        val currentLogoIndex: Int = 0
    )

    sealed class Intent {
        data class NetworkStatusChanged(val isOnline: Boolean) : Intent()
        data object Retry : Intent()
    }

    sealed class Effect {
        data object NavigateNext : Effect()
    }
}
