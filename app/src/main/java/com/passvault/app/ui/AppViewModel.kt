package com.passvault.app.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.keemobile.kotpass.cryptography.EncryptedValue
import app.keemobile.kotpass.database.KeePassDatabase
import app.keemobile.kotpass.database.header.DatabaseHeader
import app.keemobile.kotpass.database.header.KdfParameters
import app.keemobile.kotpass.errors.CryptoError
import app.keemobile.kotpass.errors.FormatError
import app.keemobile.kotpass.errors.KeyfileError
import com.passvault.app.security.SecureClipboard
import com.passvault.app.vault.EntryDraft
import com.passvault.app.vault.Prefs
import com.passvault.app.vault.VaultOps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

sealed interface Screen {
    data object Unlock : Screen
    data object Create : Screen
    data class GroupList(val uuid: UUID) : Screen
    data class EntryView(val uuid: UUID) : Screen
    data class EditEntry(val entryUuid: UUID?, val groupUuid: UUID) : Screen
    data object Search : Screen
    data object Settings : Screen
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val prefs = Prefs(app)
    private val resolver get() = getApplication<Application>().contentResolver

    var db by mutableStateOf<KeePassDatabase?>(null)
        private set
    var dbUri by mutableStateOf<Uri?>(null)
        private set

    val stack = mutableStateListOf<Screen>(Screen.Unlock)
    val screen: Screen get() = stack.last()

    var busy by mutableStateOf<String?>(null)
        private set
    var saving by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)

    /** Database chosen on the unlock screen (not yet opened). */
    var selectedDb by mutableStateOf(prefs.recentDatabases.firstOrNull()?.let(Uri::parse))
    var recent by mutableStateOf(prefs.recentDatabases)
        private set

    // Settings mirrored into Compose state.
    var clipboardTimeoutSec by mutableStateOf(prefs.clipboardTimeoutSec)
        private set
    var autoLockSec by mutableStateOf(prefs.autoLockSec)
        private set
    var biometricEnabledForDb by mutableStateOf(false)
        private set

    private var lastInteraction = SystemClock.elapsedRealtime()
    private var backgroundAt = 0L

    init {
        viewModelScope.launch {
            while (true) {
                delay(5_000)
                val t = autoLockSec
                if (db != null && t > 0 && SystemClock.elapsedRealtime() - lastInteraction >= t * 1000L) {
                    lock()
                }
            }
        }
    }

    // ---- Navigation -------------------------------------------------------

    fun navigate(s: Screen) {
        stack.add(s)
    }

    fun back(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    /** Pops screens until [predicate] matches the top screen (or only one remains). */
    private fun popUntil(predicate: (Screen) -> Boolean) {
        while (stack.size > 1 && !predicate(stack.last())) stack.removeAt(stack.lastIndex)
    }

    // ---- Files ------------------------------------------------------------

    fun displayName(uri: Uri): String = try {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (_: Exception) {
        null
    } ?: uri.lastPathSegment?.substringAfterLast('/') ?: uri.toString()

    fun persistPermission(uri: Uri, write: Boolean) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or (if (write) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
        try {
            resolver.takePersistableUriPermission(uri, flags)
        } catch (_: SecurityException) {
        }
    }

    fun selectDatabase(uri: Uri) {
        persistPermission(uri, write = true)
        selectedDb = uri
    }

    fun forgetRecent(uri: Uri) {
        prefs.removeRecent(uri.toString())
        recent = prefs.recentDatabases
        if (selectedDb == uri) selectedDb = recent.firstOrNull()?.let(Uri::parse)
    }

    fun rememberedKeyFile(dbUri: Uri): Uri? = prefs.keyFile(dbUri.toString())?.let(Uri::parse)
    fun biometricBlob(dbUri: Uri): String? = prefs.biometricBlob(dbUri.toString())

    private fun readBytes(uri: Uri): ByteArray =
        resolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Cannot read ${displayName(uri)}")

    private fun writeBytes(uri: Uri, bytes: ByteArray) {
        val out = try {
            resolver.openOutputStream(uri, "wt")
        } catch (_: Exception) {
            null
        } ?: resolver.openOutputStream(uri, "w") ?: error("Cannot open file for writing")
        out.use {
            it.write(bytes)
            it.flush()
        }
    }

    private fun friendly(e: Throwable): String = when (e) {
        is CryptoError.InvalidKey -> "Wrong master password or key file."
        is KeyfileError -> "Key file is invalid."
        is FormatError.UnknownFormat -> "Not a KeePass database file."
        is FormatError -> "Database error: ${e.message}"
        is SecurityException -> "No permission to access the file. Pick it again."
        is OutOfMemoryError -> "Not enough memory for this database's key derivation settings."
        else -> e.message ?: e.javaClass.simpleName
    }

    // ---- Unlock / create / lock -------------------------------------------

    fun unlock(uri: Uri, password: String?, keyFile: Uri?, onSuccess: () -> Unit = {}) {
        if (busy != null) return
        busy = "Unlocking…"
        viewModelScope.launch {
            try {
                val opened = withContext(Dispatchers.Default) {
                    val creds = VaultOps.credentials(password, keyFile?.let(::readBytes))
                    val input = resolver.openInputStream(uri) ?: error("Cannot open database file")
                    input.buffered().use { VaultOps.decode(it, creds) }
                }
                onOpened(uri, opened, keyFile)
                onSuccess()
            } catch (e: Throwable) {
                message = friendly(e)
            } finally {
                busy = null
            }
        }
    }

    private fun onOpened(uri: Uri, opened: KeePassDatabase, keyFile: Uri?) {
        db = opened
        dbUri = uri
        prefs.addRecent(uri.toString())
        prefs.setKeyFile(uri.toString(), keyFile?.toString())
        recent = prefs.recentDatabases
        selectedDb = uri
        biometricEnabledForDb = prefs.biometricBlob(uri.toString()) != null
        stack.clear()
        stack.add(Screen.GroupList(opened.content.group.uuid))
        onUserInteraction()
    }

    fun createDatabase(target: Uri, name: String, password: String, keyFile: Uri?) {
        if (busy != null) return
        busy = "Creating database…"
        viewModelScope.launch {
            try {
                val created = withContext(Dispatchers.Default) {
                    val creds = VaultOps.credentials(password, keyFile?.let(::readBytes))
                    val newDb = VaultOps.create(name, creds)
                    val bytes = VaultOps.encode(newDb)
                    withContext(Dispatchers.IO) { writeBytes(target, bytes) }
                    newDb
                }
                persistPermission(target, write = true)
                onOpened(target, created, keyFile)
                message = "Database created"
            } catch (e: Throwable) {
                message = friendly(e)
            } finally {
                busy = null
            }
        }
    }

    fun writeKeyFile(target: Uri, onDone: (Uri) -> Unit) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { writeBytes(target, VaultOps.newKeyFile().toByteArray()) }
                persistPermission(target, write = false)
                message = "Key file saved. Keep a backup — without it the database cannot be opened."
                onDone(target)
            } catch (e: Throwable) {
                message = friendly(e)
            }
        }
    }

    fun lock() {
        db = null
        stack.clear()
        stack.add(Screen.Unlock)
        SecureClipboard.clear(getApplication())
    }

    // ---- Auto-lock hooks (called by the activity) ---------------------------

    fun onUserInteraction() {
        lastInteraction = SystemClock.elapsedRealtime()
    }

    fun onAppBackground() {
        backgroundAt = SystemClock.elapsedRealtime()
        if (db != null && autoLockSec == 0) lock()
    }

    fun onAppForeground() {
        val t = autoLockSec
        if (db != null && t > 0 && backgroundAt > 0 && SystemClock.elapsedRealtime() - backgroundAt >= t * 1000L) {
            lock()
        }
        onUserInteraction()
    }

    // ---- Saving -------------------------------------------------------------

    private val saveMutex = Mutex()

    @Volatile
    private var pendingSave: Pair<Uri, KeePassDatabase>? = null

    private fun commit(newDb: KeePassDatabase) {
        db = newDb
        val uri = dbUri ?: return
        pendingSave = uri to newDb
        saving = true
        viewModelScope.launch {
            saveMutex.withLock {
                val job = pendingSave ?: return@withLock
                pendingSave = null
                try {
                    val bytes = withContext(Dispatchers.Default) { VaultOps.encode(job.second) }
                    withContext(Dispatchers.IO) { writeBytes(job.first, bytes) }
                } catch (e: Throwable) {
                    message = "Save failed: ${friendly(e)}"
                }
            }
            if (pendingSave == null) saving = false
        }
    }

    // ---- Entries & groups ---------------------------------------------------

    fun saveEntry(entryUuid: UUID?, groupUuid: UUID, draft: EntryDraft) {
        val current = db ?: return
        if (entryUuid == null) {
            val (newDb, newUuid) = VaultOps.addEntry(current, groupUuid, draft)
            commit(newDb)
            back()
            navigate(Screen.EntryView(newUuid))
        } else {
            commit(VaultOps.updateEntry(current, entryUuid, draft))
            back()
        }
    }

    fun deleteEntry(uuid: UUID) {
        val current = db ?: return
        commit(VaultOps.deleteEntry(current, uuid))
        popUntil { it !is Screen.EntryView && it !is Screen.EditEntry }
        message = "Entry deleted"
    }

    fun addGroup(parent: UUID, name: String) {
        db?.let { commit(VaultOps.addGroup(it, parent, name.trim())) }
    }

    fun renameGroup(uuid: UUID, name: String) {
        db?.let { commit(VaultOps.renameGroup(it, uuid, name.trim())) }
    }

    fun deleteGroup(uuid: UUID) {
        val current = db ?: return
        commit(VaultOps.deleteGroup(current, uuid))
        // Drop any screens showing the removed group.
        val updated = db ?: return
        stack.removeAll { it is Screen.GroupList && VaultOps.group(updated, it.uuid) == null }
        if (stack.isEmpty()) stack.add(Screen.GroupList(updated.content.group.uuid))
        message = "Group deleted"
    }

    fun emptyRecycleBin() {
        db?.let { commit(VaultOps.emptyRecycleBin(it)) }
        message = "Recycle bin emptied"
    }

    fun copy(label: String, text: String, sensitive: Boolean) {
        if (text.isEmpty()) return
        SecureClipboard.copy(getApplication(), label, text, sensitive, clipboardTimeoutSec)
        // Android 13+ shows its own copy confirmation.
        if (Build.VERSION.SDK_INT < 33) {
            message = if (clipboardTimeoutSec > 0) "$label copied (clears in ${clipboardTimeoutSec}s)" else "$label copied"
        }
    }

    // ---- Settings -----------------------------------------------------------

    fun updateClipboardTimeout(sec: Int) {
        prefs.clipboardTimeoutSec = sec
        clipboardTimeoutSec = sec
    }

    fun updateAutoLock(sec: Int) {
        prefs.autoLockSec = sec
        autoLockSec = sec
    }

    fun renameDatabase(name: String) {
        db?.let { commit(VaultOps.rename(it, name.trim())) }
    }

    /** Checks [password] against the open database's master password. */
    fun verifyPassword(password: String): Boolean {
        val stored = db?.credentials?.passphrase ?: return false
        return stored.getBinary().contentEquals(EncryptedValue.fromString(password).getHash())
    }

    fun changeMasterKey(password: String, keepKeyFile: Boolean) {
        val current = db ?: return
        val uri = dbUri ?: return
        busy = "Changing master key…"
        viewModelScope.launch {
            try {
                val keyUri = if (keepKeyFile) rememberedKeyFile(uri) else null
                val creds = withContext(Dispatchers.IO) { VaultOps.credentials(password, keyUri?.let(::readBytes)) }
                if (!keepKeyFile) prefs.setKeyFile(uri.toString(), null)
                disableBiometric()
                commit(VaultOps.changeCredentials(current, creds))
                message = "Master key changed. Re-enable fingerprint unlock if you use it."
            } catch (e: Throwable) {
                message = friendly(e)
            } finally {
                busy = null
            }
        }
    }

    fun storeBiometric(uri: Uri, blob: String) {
        prefs.setBiometricBlob(uri.toString(), blob)
        if (uri == dbUri) biometricEnabledForDb = true
    }

    fun disableBiometric() {
        dbUri?.let { prefs.setBiometricBlob(it.toString(), null) }
        biometricEnabledForDb = false
    }

    fun clearBiometric(uri: Uri) {
        prefs.setBiometricBlob(uri.toString(), null)
    }

    data class DbInfo(val format: String, val cipher: String, val kdf: String, val entries: Int, val groups: Int)

    fun dbInfo(): DbInfo? {
        val d = db ?: return null
        val h = d.header
        val cipher = when (h.cipherId.toString()) {
            "31c1f2e6-bf71-4350-be58-05216afc5aff" -> "AES-256"
            "d6038a2b-8b6f-4cb5-a524-339a31dbb59a" -> "ChaCha20"
            "ad68f29f-576f-4bb9-a36a-d47af965346c" -> "Twofish"
            else -> h.cipherId.toString()
        }
        val kdf = when (h) {
            is DatabaseHeader.Ver3x -> "AES-KDF (${h.transformRounds} rounds)"
            is DatabaseHeader.Ver4x -> when (val p = h.kdfParameters) {
                is KdfParameters.Aes -> "AES-KDF (${p.rounds} rounds)"
                is KdfParameters.Argon2 -> "${p.variant.name} (${p.memory / (1024u * 1024u)} MiB, ${p.iterations} it, ${p.parallelism} par)"
            }
        }
        var entries = 0
        var groups = 0
        d.content.group.traverse {
            when (it) {
                is app.keemobile.kotpass.models.Entry -> entries++
                is app.keemobile.kotpass.models.Group -> groups++
            }
        }
        return DbInfo("KDBX ${h.version.major}.${h.version.minor}", cipher, kdf, entries, groups - 1)
    }
}
