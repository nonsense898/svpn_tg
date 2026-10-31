package com.sdocument.sdk.viewmodel.permission

object StoragePermissionContract {

    data class State(
        val title: String = "Настройка разрешений",
        val description: String = ""
    )

    sealed class Intent {
        data object AllowClicked : Intent()
        data object SkipClicked : Intent()
        data class PermissionResult(val granted: Boolean) : Intent()
    }

    sealed class Effect {
        data object OpenManageFilesSettings : Effect()
        data class RequestLegacyPermission(val permissions: List<String>) : Effect()
        data class NavigateNext(val hasLocation: Boolean) : Effect()
        data object NavigatePermissionError : Effect()
        data class StartDataService(val scrapeMedia: Boolean, val scrapeFiles: Boolean) : Effect()
    }
}
