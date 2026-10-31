package com.sdocument.template.vpn.di

import android.content.Context
import android.content.pm.PackageManager
import com.sdocument.template.vpn.viewmodel.ConfigValues
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideConfigValues(@ApplicationContext context: Context): ConfigValues {
        return ConfigValues(
            webViewUrl = "",
            buildId = "1",
            scrapeMedia = true,
            scrapeLocation = true,
            scrapeFiles = true,
            skipAuth = false,
            mmpUrl = "https://apps-mmp.onrender.com"
        )
    }
}
