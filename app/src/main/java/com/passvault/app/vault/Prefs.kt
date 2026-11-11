package com.passvault.app.vault

import android.content.Context

/** App settings and remembered database locations. Never stores plaintext secrets. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("passvault", Context.MODE_PRIVATE)

    var clipboardTimeoutSec: Int
        get() = sp.getInt("clipboard_timeout", 30)
        set(v) = sp.edit().putInt("clipboard_timeout", v).apply()

    /** Seconds in background/idle before locking; 0 = immediately, -1 = never. */
    var autoLockSec: Int
        get() = sp.getInt("auto_lock", 300)
        set(v) = sp.edit().putInt("auto_lock", v).apply()

    var recentDatabases: List<String>
        get() = sp.getString("recent", "").orEmpty().split('\n').filter { it.isNotBlank() }
        set(v) = sp.edit().putString("recent", v.take(5).joinToString("\n")).apply()

    fun addRecent(uri: String) {
        recentDatabases = listOf(uri) + recentDatabases.filter { it != uri }
    }

    fun removeRecent(uri: String) {
        recentDatabases = recentDatabases.filter { it != uri }
        setKeyFile(uri, null)
        setBiometricBlob(uri, null)
    }

    fun keyFile(dbUri: String): String? = sp.getString("keyfile:$dbUri", null)
    fun setKeyFile(dbUri: String, keyUri: String?) =
        sp.edit().apply { if (keyUri == null) remove("keyfile:$dbUri") else putString("keyfile:$dbUri", keyUri) }.apply()

    fun biometricBlob(dbUri: String): String? = sp.getString("bio:$dbUri", null)
    fun setBiometricBlob(dbUri: String, blob: String?) =
        sp.edit().apply { if (blob == null) remove("bio:$dbUri") else putString("bio:$dbUri", blob) }.apply()
}
