package com.tgvpn.offapp.di

import android.content.Context
import com.tgvpn.offapp.utils.UserPrefs
import com.wgvpn.core.utils.UserIdProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object VpnCoreModule {

    @Provides
    @Singleton
    fun provideUserIdProvider(@ApplicationContext context: Context): UserIdProvider {
        return UserIdProvider { UserPrefs.getUsername(context) }
    }
}
