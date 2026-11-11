package com.passvault.app.vault

import app.keemobile.kotpass.constants.BasicField
import app.keemobile.kotpass.constants.PredefinedIcon
import app.keemobile.kotpass.cryptography.EncryptedValue
import app.keemobile.kotpass.cryptography.format.BaseCiphers
import app.keemobile.kotpass.cryptography.format.TwofishCipher
import app.keemobile.kotpass.database.Credentials
import app.keemobile.kotpass.database.KeePassDatabase
import app.keemobile.kotpass.database.decode
import app.keemobile.kotpass.database.encode
import app.keemobile.kotpass.database.getEntry
import app.keemobile.kotpass.database.getGroupBy
import app.keemobile.kotpass.database.modifiers.modifyCredentials
import app.keemobile.kotpass.database.modifiers.modifyEntry
import app.keemobile.kotpass.database.modifiers.modifyGroup
import app.keemobile.kotpass.database.modifiers.modifyMeta
import app.keemobile.kotpass.database.modifiers.moveEntry
import app.keemobile.kotpass.database.modifiers.moveGroup
import app.keemobile.kotpass.database.modifiers.removeEntry
import app.keemobile.kotpass.database.modifiers.removeGroup
import app.keemobile.kotpass.database.modifiers.withHistory
import app.keemobile.kotpass.database.modifiers.withRecycleBin
import app.keemobile.kotpass.models.Entry
import app.keemobile.kotpass.models.EntryFields
import app.keemobile.kotpass.models.EntryValue
import app.keemobile.kotpass.models.Group
import app.keemobile.kotpass.models.Meta
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.time.Instant
import java.util.UUID

/** Editable representation of an entry used by the UI. */
data class EntryDraft(
    val title: String = "",
    val userName: String = "",
    val password: String = "",
    val url: String = "",
    val notes: String = "",
    val tags: String = "",
    val customFields: List<CustomField> = emptyList(),
)

data class CustomField(val key: String, val value: String, val protected: Boolean)

/**
 * Pure (Android-free) operations on an immutable [KeePassDatabase].
 * Every mutation returns a new database instance.
 */
object VaultOps {

    private val ciphers = BaseCiphers.entries + TwofishCipher

    fun credentials(password: String?, keyFile: ByteArray?): Credentials {
        val pw = password?.takeIf { it.isNotEmpty() || keyFile == null }
        return when {
            pw != null && keyFile != null -> Credentials.from(EncryptedValue.fromString(pw), keyFile)
            pw != null -> Credentials.from(EncryptedValue.fromString(pw))
            keyFile != null -> Credentials.from(keyFile)
            else -> throw IllegalArgumentException("Password or key file required")
        }
    }

    fun decode(input: InputStream, credentials: Credentials): KeePassDatabase =
        KeePassDatabase.decode(input, credentials, cipherProviders = ciphers)

    fun encode(db: KeePassDatabase): ByteArray =
        ByteArrayOutputStream().also { db.encode(it, cipherProviders = ciphers) }.toByteArray()

    fun create(name: String, credentials: Credentials): KeePassDatabase =
        KeePassDatabase.Ver4x.create(
            rootName = name.ifBlank { "Database" },
            meta = Meta(
                generator = "PassVault",
                name = name,
                recycleBinEnabled = true,
                masterKeyChanged = Instant.now(),
            ),
            credentials = credentials,
        )

    fun newKeyFile(): String {
        val key = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        return Credentials.createKeyfile(key)
    }

    // ---- Queries ----------------------------------------------------------

    fun root(db: KeePassDatabase): Group = db.content.group

    fun group(db: KeePassDatabase, uuid: UUID): Group? = db.getGroupBy { this.uuid == uuid }

    fun entry(db: KeePassDatabase, uuid: UUID): Pair<Group, Entry>? = db.getEntry { it.uuid == uuid }

    fun recycleBinUuid(db: KeePassDatabase): UUID? =
        db.content.meta.recycleBinUuid?.takeIf { it.mostSignificantBits != 0L || it.leastSignificantBits != 0L }

    /** True if [groupUuid] is the recycle bin or nested inside it. */
    fun isInRecycleBin(db: KeePassDatabase, groupUuid: UUID): Boolean {
        val bin = recycleBinUuid(db) ?: return false
        val binGroup = group(db, bin) ?: return false
        if (groupUuid == bin) return true
        var found = false
        binGroup.traverse { if (it is Group && it.uuid == groupUuid) found = true }
        return found
    }

    /** Searches title, user name, URL, notes, tags and non-protected custom fields. */
    fun search(db: KeePassDatabase, query: String): List<Entry> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val results = mutableListOf<Entry>()
        val bin = recycleBinUuid(db)
        fun walk(g: Group) {
            if (g.uuid == bin) return
            g.entries.filterTo(results) { matches(it, q) }
            g.groups.forEach(::walk)
        }
        walk(db.content.group)
        return results.sortedBy { title(it).lowercase() }
    }

    private fun matches(e: Entry, q: String): Boolean {
        if (e.tags.any { it.contains(q, ignoreCase = true) }) return true
        return e.fields.any { (key, value) ->
            key != BasicField.Password() && value is EntryValue.Plain && value.content.contains(q, ignoreCase = true)
        }
    }

    fun title(e: Entry) = e.fields.title?.content.orEmpty()
    fun userName(e: Entry) = e.fields.userName?.content.orEmpty()
    fun password(e: Entry) = e.fields.password?.content.orEmpty()
    fun url(e: Entry) = e.fields.url?.content.orEmpty()
    fun notes(e: Entry) = e.fields.notes?.content.orEmpty()

    fun customFields(e: Entry): List<CustomField> =
        e.fields.filterKeys { it !in BasicField.keys }
            .map { (k, v) -> CustomField(k, v.content, v is EntryValue.Encrypted) }

    fun isExpired(e: Entry, now: Instant = Instant.now()): Boolean {
        val t = e.times ?: return false
        return t.expires && t.expiryTime?.isBefore(now) == true
    }

    fun toDraft(e: Entry) = EntryDraft(
        title = title(e),
        userName = userName(e),
        password = password(e),
        url = url(e),
        notes = notes(e),
        tags = e.tags.joinToString(", "),
        customFields = customFields(e),
    )

    // ---- Mutations --------------------------------------------------------

    private fun fieldsOf(d: EntryDraft): EntryFields {
        val map = linkedMapOf<String, EntryValue>(
            BasicField.Title() to EntryValue.Plain(d.title),
            BasicField.UserName() to EntryValue.Plain(d.userName),
            BasicField.Password() to EntryValue.Encrypted(EncryptedValue.fromString(d.password)),
            BasicField.Url() to EntryValue.Plain(d.url),
            BasicField.Notes() to EntryValue.Plain(d.notes),
        )
        d.customFields
            .filter { it.key.isNotBlank() && it.key !in BasicField.keys }
            .forEach { f ->
                map[f.key.trim()] = if (f.protected) {
                    EntryValue.Encrypted(EncryptedValue.fromString(f.value))
                } else {
                    EntryValue.Plain(f.value)
                }
            }
        return EntryFields(map)
    }

    private fun parseTags(s: String) = s.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    fun addEntry(db: KeePassDatabase, groupUuid: UUID, draft: EntryDraft): Pair<KeePassDatabase, UUID> {
        val entry = Entry(
            uuid = UUID.randomUUID(),
            fields = fieldsOf(draft),
            tags = parseTags(draft.tags),
        )
        return db.modifyGroup(groupUuid) { copy(entries = entries + entry) } to entry.uuid
    }

    fun updateEntry(db: KeePassDatabase, uuid: UUID, draft: EntryDraft): KeePassDatabase {
        val maxHistory = db.content.meta.historyMaxItems
        val now = Instant.now()
        return db.modifyEntry(uuid) {
            val updated = withHistory {
                copy(
                    fields = fieldsOf(draft),
                    tags = parseTags(draft.tags),
                    times = times?.copy(lastModificationTime = now, lastAccessTime = now),
                )
            }
            if (maxHistory >= 0 && updated.history.size > maxHistory) {
                updated.copy(history = updated.history.takeLast(maxHistory))
            } else {
                updated
            }
        }
    }

    /**
     * Moves to the recycle bin, or deletes permanently if already there / bin disabled.
     * When recycling, the original group is recorded in PreviousParentGroup (KDBX 4.1) so it can be restored.
     */
    fun deleteEntry(db: KeePassDatabase, uuid: UUID): KeePassDatabase {
        val (parent, _) = entry(db, uuid) ?: return db
        return if (db.content.meta.recycleBinEnabled && !isInRecycleBin(db, parent.uuid)) {
            db.withMovedFrom(uuid, parent.uuid).withRecycleBin { bin -> moveEntry(uuid, bin) }
        } else {
            db.removeEntry(uuid)
        }
    }

    /**
     * The group a recycled entry would be restored to: its previous group if that still exists outside
     * the recycle bin, otherwise the root group. Null if the entry is not in the recycle bin.
     */
    fun restoreTarget(db: KeePassDatabase, uuid: UUID): Group? {
        val (parent, e) = entry(db, uuid) ?: return null
        if (!isInRecycleBin(db, parent.uuid)) return null
        val previous = e.previousParentGroup
            ?.let { group(db, it) }
            ?.takeIf { !isInRecycleBin(db, it.uuid) }
        return previous ?: root(db)
    }

    /** Moves a recycled entry back out of the recycle bin (see [restoreTarget]). */
    fun restoreEntry(db: KeePassDatabase, uuid: UUID): KeePassDatabase {
        val (parent, _) = entry(db, uuid) ?: return db
        val target = restoreTarget(db, uuid) ?: return db
        return db.withMovedFrom(uuid, parent.uuid).moveEntry(uuid, target.uuid)
    }

    private fun KeePassDatabase.withMovedFrom(entryUuid: UUID, fromGroup: UUID): KeePassDatabase =
        modifyEntry(entryUuid) {
            copy(previousParentGroup = fromGroup, times = times?.copy(locationChanged = Instant.now()))
        }

    fun addGroup(db: KeePassDatabase, parentUuid: UUID, name: String): KeePassDatabase {
        val g = Group(uuid = UUID.randomUUID(), name = name, icon = PredefinedIcon.Folder)
        return db.modifyGroup(parentUuid) { copy(groups = groups + g) }
    }

    fun renameGroup(db: KeePassDatabase, uuid: UUID, name: String): KeePassDatabase =
        db.modifyGroup(uuid) { copy(name = name, times = times?.copy(lastModificationTime = Instant.now())) }

    fun deleteGroup(db: KeePassDatabase, uuid: UUID): KeePassDatabase {
        if (uuid == db.content.group.uuid) return db
        val bin = recycleBinUuid(db)
        return if (db.content.meta.recycleBinEnabled && uuid != bin && !isInRecycleBin(db, uuid)) {
            db.withRecycleBin { binUuid -> moveGroup(uuid, binUuid) }
        } else {
            val result = db.removeGroup(uuid)
            if (uuid == bin) result.modifyMeta { copy(recycleBinUuid = null) } else result
        }
    }

    fun emptyRecycleBin(db: KeePassDatabase): KeePassDatabase {
        val bin = recycleBinUuid(db) ?: return db
        val binGroup = group(db, bin) ?: return db
        var result = db
        binGroup.groups.forEach { result = result.removeGroup(it.uuid) }
        binGroup.entries.forEach { result = result.removeEntry(it.uuid) }
        return result
    }

    fun rename(db: KeePassDatabase, name: String): KeePassDatabase = db.modifyMeta { copy(name = name) }

    fun changeCredentials(db: KeePassDatabase, credentials: Credentials): KeePassDatabase =
        db.modifyCredentials { credentials }.modifyMeta { copy(masterKeyChanged = Instant.now()) }
}
