package com.passvault.app

import app.keemobile.kotpass.errors.CryptoError
import com.passvault.app.vault.CustomField
import com.passvault.app.vault.EntryDraft
import com.passvault.app.vault.GeneratorOptions
import com.passvault.app.vault.PasswordGenerator
import com.passvault.app.vault.VaultOps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class VaultOpsTest {

    private fun roundTrip(db: app.keemobile.kotpass.database.KeePassDatabase, password: String?, key: ByteArray? = null) =
        VaultOps.decode(VaultOps.encode(db).inputStream(), VaultOps.credentials(password, key))

    @Test
    fun createEditSaveAndReopen() {
        var db = VaultOps.create("Test", VaultOps.credentials("correct horse", null))
        val root = db.content.group.uuid
        db = VaultOps.addGroup(db, root, "Email")
        val email = db.content.group.groups.single { it.name == "Email" }.uuid
        val (withEntry, id) = VaultOps.addEntry(
            db, email,
            EntryDraft(
                title = "Gmail", userName = "me@example.com", password = "s3cret!", url = "mail.google.com",
                notes = "line1\nline2", tags = "personal, mail",
                customFields = listOf(CustomField("PIN", "1234", true), CustomField("Recovery", "abc", false)),
            ),
        )
        db = VaultOps.updateEntry(withEntry, id, VaultOps.toDraft(VaultOps.entry(withEntry, id)!!.second).copy(password = "n3w"))

        val reopened = roundTrip(db, "correct horse")
        val (parent, e) = VaultOps.entry(reopened, id)!!
        assertEquals("Email", parent.name)
        assertEquals("Gmail", VaultOps.title(e))
        assertEquals("me@example.com", VaultOps.userName(e))
        assertEquals("n3w", VaultOps.password(e))
        assertEquals("line1\nline2", VaultOps.notes(e))
        assertEquals(listOf("personal", "mail"), e.tags)
        assertEquals(1, e.history.size)
        assertEquals("s3cret!", VaultOps.password(e.history.single()))
        val custom = VaultOps.customFields(e).associateBy { it.key }
        assertEquals(CustomField("PIN", "1234", true), custom["PIN"])
        assertEquals(CustomField("Recovery", "abc", false), custom["Recovery"])

        assertEquals(listOf(id), VaultOps.search(reopened, "gmail").map { it.uuid })
        assertEquals(listOf(id), VaultOps.search(reopened, "MAIL.GOOGLE").map { it.uuid })
        assertTrue("password must not be searchable", VaultOps.search(reopened, "n3w").isEmpty())
    }

    @Test
    fun wrongPasswordIsRejected() {
        val db = VaultOps.create("T", VaultOps.credentials("right", null))
        try {
            roundTrip(db, "wrong")
            fail("expected InvalidKey")
        } catch (_: CryptoError.InvalidKey) {
        }
    }

    @Test
    fun keyFileAndPassword() {
        val keyXml = VaultOps.newKeyFile().toByteArray()
        val db = VaultOps.create("T", VaultOps.credentials("pw", keyXml))
        assertNotNull(roundTrip(db, "pw", keyXml))
        try {
            roundTrip(db, "pw", null)
            fail("expected InvalidKey without key file")
        } catch (_: CryptoError.InvalidKey) {
        }
    }

    @Test
    fun deleteGoesToRecycleBinThenPermanently() {
        var db = VaultOps.create("T", VaultOps.credentials("pw", null))
        val root = db.content.group.uuid
        val (d1, id) = VaultOps.addEntry(db, root, EntryDraft(title = "X"))
        db = VaultOps.deleteEntry(d1, id)
        val bin = VaultOps.recycleBinUuid(db)
        assertNotNull(bin)
        assertEquals(bin, VaultOps.entry(db, id)!!.first.uuid)
        assertTrue(VaultOps.search(db, "X").isEmpty())

        db = VaultOps.deleteEntry(db, id)
        assertNull(VaultOps.entry(db, id))

        db = VaultOps.addGroup(db, root, "G")
        val g = db.content.group.groups.single { it.name == "G" }.uuid
        db = VaultOps.deleteGroup(db, g)
        assertTrue(VaultOps.isInRecycleBin(db, g))
        db = VaultOps.emptyRecycleBin(db)
        assertNull(VaultOps.group(db, g))
    }

    @Test
    fun restoreReturnsEntryToOriginalGroupAfterSave() {
        var db = VaultOps.create("T", VaultOps.credentials("pw", null))
        val root = db.content.group.uuid
        db = VaultOps.addGroup(db, root, "Email")
        val email = db.content.group.groups.single { it.name == "Email" }.uuid
        val (d1, id) = VaultOps.addEntry(db, email, EntryDraft(title = "Gmail"))
        assertNull("not in bin, nothing to restore", VaultOps.restoreTarget(d1, id))

        // The recorded original group must survive saving and reopening the file.
        db = roundTrip(VaultOps.deleteEntry(d1, id), "pw")
        assertTrue(VaultOps.isInRecycleBin(db, VaultOps.entry(db, id)!!.first.uuid))
        assertEquals(email, VaultOps.restoreTarget(db, id)!!.uuid)

        db = roundTrip(VaultOps.restoreEntry(db, id), "pw")
        val (parent, e) = VaultOps.entry(db, id)!!
        assertEquals(email, parent.uuid)
        assertEquals("Gmail", VaultOps.title(e))
        assertEquals(listOf(id), VaultOps.search(db, "Gmail").map { it.uuid })
    }

    @Test
    fun restoreFallsBackToRootWhenOriginalGroupIsGone() {
        var db = VaultOps.create("T", VaultOps.credentials("pw", null))
        val root = db.content.group.uuid
        db = VaultOps.addGroup(db, root, "Temp")
        val temp = db.content.group.groups.single { it.name == "Temp" }.uuid
        val (d1, id) = VaultOps.addEntry(db, temp, EntryDraft(title = "X"))
        db = VaultOps.deleteEntry(d1, id)
        // Remove the original group entirely (to the bin, then permanently).
        db = VaultOps.deleteGroup(VaultOps.deleteGroup(db, temp), temp)
        assertNull(VaultOps.group(db, temp))

        assertEquals(root, VaultOps.restoreTarget(db, id)!!.uuid)
        db = VaultOps.restoreEntry(db, id)
        assertEquals(root, VaultOps.entry(db, id)!!.first.uuid)
    }

    @Test
    fun generatorRespectsOptions() {
        repeat(50) {
            val p = PasswordGenerator.generate(GeneratorOptions(length = 16, symbols = false, excludeAmbiguous = true))
            assertEquals(16, p.length)
            assertTrue(p.any { it.isUpperCase() })
            assertTrue(p.any { it.isLowerCase() })
            assertTrue(p.any { it.isDigit() })
            assertFalse(p.any { !it.isLetterOrDigit() })
            assertFalse(p.any { it in "Il1O0o" })
        }
    }
}
