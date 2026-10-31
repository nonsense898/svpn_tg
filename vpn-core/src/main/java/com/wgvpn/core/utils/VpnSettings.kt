package com.wgvpn.core.utils

import android.content.Context

object VpnSettings {
    private const val PREFS_NAME = "vpn_core_settings"
    private const val KEY_AUTO_CONNECT = "auto_connect"
    private const val KEY_KILL_SWITCH = "kill_switch"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isAutoConnect(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_CONNECT, true)

    fun setAutoConnect(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_CONNECT, enabled).apply()
    }

    fun isKillSwitch(context: Context): Boolean =
        prefs(context).getBoolean(KEY_KILL_SWITCH, false)

    fun setKillSwitch(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_KILL_SWITCH, enabled).apply()
    }
}
