package com.passvault.app

import com.passvault.app.vault.CustomField
import com.passvault.app.vault.EntryDraft
import com.passvault.app.vault.VaultOps
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Cross-checks against another KDBX implementation. Skipped unless
 * -Dinterop.dir=<folder> is passed (folder must contain from_desktop.kdbx, password "desk-pw").
 */
class InteropTest {
    private val dir = System.getProperty("interop.dir")?.let(::File)

    @Test
    fun readsExternalDatabaseAndWritesOneBack() {
        assumeTrue(dir != null)
        val external = File(dir, "from_desktop.kdbx")
        val db = external.inputStream().use { VaultOps.decode(it, VaultOps.credentials("desk-pw", null)) }
        val e = VaultOps.search(db, "MyBank").single()
        assertEquals("alice", VaultOps.userName(e))
        assertEquals("B@nk-Pass-9", VaultOps.password(e))
        assertEquals("from desktop", VaultOps.notes(e))

        var out = VaultOps.create("FromAndroid", VaultOps.credentials("android-pw", null))
        out = VaultOps.addGroup(out, out.content.group.uuid, "Social")
        val g = out.content.group.groups.single().uuid
        out = VaultOps.addEntry(
            out, g,
            EntryDraft(title = "Mastodon", userName = "bob", password = "M@st-7", customFields = listOf(CustomField("PIN", "4321", true))),
        ).first
        File(dir, "from_android.kdbx").writeBytes(VaultOps.encode(out))
    }
}
