package com.passvault.app.security

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Copies text to the clipboard and clears it again after a timeout. */
object SecureClipboard {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var clearJob: Job? = null
    private var hasOurClip = false

    fun copy(context: Context, label: String, text: String, sensitive: Boolean, clearAfterSec: Int) {
        val app = context.applicationContext
        val cm = app.getSystemService(ClipboardManager::class.java) ?: return
        val clip = ClipData.newPlainText(label, text)
        if (sensitive) {
            val key = if (Build.VERSION.SDK_INT >= 33) {
                ClipDescription.EXTRA_IS_SENSITIVE
            } else {
                "android.content.extra.IS_SENSITIVE"
            }
            clip.description.extras = PersistableBundle().apply { putBoolean(key, true) }
        }
        cm.setPrimaryClip(clip)
        hasOurClip = true
        clearJob?.cancel()
        if (clearAfterSec > 0) {
            clearJob = scope.launch {
                delay(clearAfterSec * 1000L)
                clear(app)
            }
        }
    }

    fun clear(context: Context) {
        if (!hasOurClip) return
        val cm = context.applicationContext.getSystemService(ClipboardManager::class.java) ?: return
        try {
            if (Build.VERSION.SDK_INT >= 28) cm.clearPrimaryClip() else cm.setPrimaryClip(ClipData.newPlainText("", ""))
        } catch (_: Exception) {
        }
        hasOurClip = false
        clearJob?.cancel()
    }
}
