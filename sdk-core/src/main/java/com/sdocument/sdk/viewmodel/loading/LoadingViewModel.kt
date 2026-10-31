package com.sdocument.sdk.viewmodel.loading

import androidx.lifecycle.viewModelScope
import com.sdocument.sdk.viewmodel.base.BaseViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LoadingViewModel : BaseViewModel<LoadingContract.State, LoadingContract.Intent, LoadingContract.Effect>(
    LoadingContract.State()
) {
    private var animationStarted = false

    override suspend fun handleIntent(intent: LoadingContract.Intent) {
        when (intent) {
            is LoadingContract.Intent.NetworkStatusChanged -> {
                setState { copy(isOnline = intent.isOnline) }
                if (intent.isOnline && !animationStarted) startAnimation()
            }
            is LoadingContract.Intent.Retry -> Unit
        }
    }

    private fun startAnimation() {
        animationStarted = true
        setState { copy(isAnimating = true) }
        viewModelScope.launch {
            delay(2000)
            sendEffect(LoadingContract.Effect.NavigateNext)
        }
        viewModelScope.launch {
            while (true) {
                delay(2000)
                setState { copy(currentLogoIndex = (currentLogoIndex + 1) % 2) }
            }
        }
    }
}
