package com.example.sothuchi.sharedwallet.identity

import android.content.Context
import android.os.Build
import android.provider.Settings

class DeviceIdentityStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun getDeviceId(): String {
        return Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown-device"
    }

    fun getDeviceName(): String = Build.MODEL ?: "Android Device"

    fun getNickname(): String = prefs.getString(KEY_NICKNAME, "")?.trim().orEmpty()

    fun saveNickname(nickname: String) {
        prefs.edit().putString(KEY_NICKNAME, nickname.trim()).apply()
    }

    companion object {
        private const val PREF_NAME = "shared_wallet_identity"
        private const val KEY_NICKNAME = "nickname"
    }
}
