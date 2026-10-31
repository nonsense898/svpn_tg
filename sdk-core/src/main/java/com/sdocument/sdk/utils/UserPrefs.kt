package com.sdocument.sdk.utils

import android.content.Context
import android.content.SharedPreferences

object UserPrefs {
    private const val PREFS_NAME = "user_prefs"
    private const val KEY_IS_LOGGED_IN = "is_logged_in"
    private const val KEY_IS_VERIFIED = "is_verified"
    private const val KEY_AUTH_URL = "auth_url"
    private const val KEY_ACCESS_KEY = "access_key"
    private const val KEY_USERNAME = "username"
    private const val KEY_IS_ONBOARDING_COMPLETED = "is_onboarding_completed"
    
    private const val KEY_VERIFICATION_NEEDED = "is_verification_needed"
    private const val KEY_VPN_CONNECTED = "vpn_connected"
    private const val KEY_SELECTED_SERVER = "selected_server"
    private const val KEY_AUTO_CONNECT = "auto_connect"
    private const val KEY_KILL_SWITCH = "kill_switch"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun setOnboardingCompleted(context: Context, completed: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_IS_ONBOARDING_COMPLETED, completed).apply()
    }

    fun isOnboardingCompleted(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_IS_ONBOARDING_COMPLETED, false)
    }

    fun setLoggedIn(context: Context, isLoggedIn: Boolean, url: String? = null, accessKey: String? = null, username: String? = null) {
        getPrefs(context).edit().apply {
            putBoolean(KEY_IS_LOGGED_IN, isLoggedIn)
            if (url != null) putString(KEY_AUTH_URL, url)
            if (accessKey != null) putString(KEY_ACCESS_KEY, accessKey)
            if (username != null) putString(KEY_USERNAME, username)
            apply()
        }
    }

    fun isLoggedIn(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_IS_LOGGED_IN, false)
    }

    fun setVerified(context: Context, isVerified: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_IS_VERIFIED, isVerified).apply()
    }

    fun isVerified(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_IS_VERIFIED, true)
    }

    fun getAuthUrl(context: Context): String? {
        return getPrefs(context).getString(KEY_AUTH_URL, null)
    }

    fun getUsername(context: Context): String {
        return getPrefs(context).getString(KEY_USERNAME, "User") ?: "User"
    }

    fun getAccessKey(context: Context): String? {
        return getPrefs(context).getString(KEY_ACCESS_KEY, null)
    }

    fun setVerificationNeeded(context: Context, needed: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_VERIFICATION_NEEDED, needed).apply()
    }

    // Default true so existing users without this stored still go through verification
    fun isVerificationNeeded(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_VERIFICATION_NEEDED, false)

    fun setVpnConnected(context: Context, connected: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_VPN_CONNECTED, connected).apply()
    }

    fun isVpnConnected(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_VPN_CONNECTED, false)
    }

    fun setSelectedServer(context: Context, server: String) {
        getPrefs(context).edit().putString(KEY_SELECTED_SERVER, server).apply()
    }

    fun getSelectedServer(context: Context): String {
        return getPrefs(context).getString(KEY_SELECTED_SERVER, "Russia, Moscow") ?: "Russia, Moscow"
    }

    fun setAutoConnect(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_CONNECT, enabled).apply()
    }

    fun isAutoConnect(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTO_CONNECT, false)
    }

    fun setKillSwitch(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_KILL_SWITCH, enabled).apply()
    }

    fun isKillSwitch(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_KILL_SWITCH, false)
    }

    fun logout(context: Context) {
        val onboardingCompleted = isOnboardingCompleted(context)
        getPrefs(context).edit().clear().apply()
        if (onboardingCompleted) {
            setOnboardingCompleted(context, true)
        }
    }
}
