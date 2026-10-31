package com.sdocument.sdk.viewmodel.permission

object LocationPermissionContract {

    data class State(
        val title: String = "Доступ к геолокации",
        val description: String = "Мы собираем геолокацию для подбора наиболее оптимальных серверов для обеспечения высокой скорости и стабильности соединения."
    )

    sealed class Intent {
        data object AllowClicked : Intent()
        data object SkipClicked : Intent()
        data class PermissionResult(val granted: Boolean) : Intent()
        data object LocationSettingsDismissed : Intent()
    }

    sealed class Effect {
        data object RequestLocationPermission : Effect()
        data object ShowLocationSettingsDialog : Effect()
        data object ScrapeLocationFallback : Effect()
        data object NavigateNext : Effect()
    }
}
