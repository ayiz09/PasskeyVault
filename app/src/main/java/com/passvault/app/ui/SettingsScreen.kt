package com.passvault.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.passvault.app.security.BiometricVault

private val autoLockOptions = listOf(0, 30, 60, 300, 900, 1800, -1).map { it to durationLabel(it) }
private val clipboardOptions = listOf(10, 20, 30, 60, 120, 0).map { it to if (it == 0) "Never" else durationLabel(it) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel, activity: FragmentActivity) {
    val db = vm.db ?: return
    val uri = vm.dbUri ?: return
    val ctx = LocalContext.current
    val bioAvailable = remember { BiometricVault.isAvailable(ctx) }
    var dialog by remember { mutableStateOf<String?>(null) }
    val info = remember(db) { vm.dbInfo() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
        snackbarHost = { SnackbarHost(LocalSnackbar.current) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            SectionHeader("Security")
            ListItem(
                modifier = Modifier.clickable { dialog = "autolock" },
                headlineContent = { Text("Auto-lock") },
                supportingContent = { Text("Lock after inactivity or in background: ${durationLabel(vm.autoLockSec)}") },
            )
            ListItem(
                modifier = Modifier.clickable { dialog = "clipboard" },
                headlineContent = { Text("Clear clipboard") },
                supportingContent = {
                    Text(if (vm.clipboardTimeoutSec == 0) "Never" else "After ${durationLabel(vm.clipboardTimeoutSec)}")
                },
            )
            if (bioAvailable) {
                ListItem(
                    modifier = Modifier.clickable {
                        if (vm.biometricEnabledForDb) vm.disableBiometric() else dialog = "bio"
                    },
                    headlineContent = { Text("Fingerprint unlock") },
                    supportingContent = { Text("For this database, using the Android Keystore") },
                    trailingContent = {
                        Switch(checked = vm.biometricEnabledForDb, onCheckedChange = {
                            if (it) dialog = "bio" else vm.disableBiometric()
                        })
                    },
                )
            }

            SectionHeader("Database")
            ListItem(
                modifier = Modifier.clickable { dialog = "rename" },
                headlineContent = { Text("Database name") },
                supportingContent = { Text(db.content.meta.name.ifBlank { "(none)" }) },
            )
            ListItem(
                modifier = Modifier.clickable { dialog = "masterkey" },
                headlineContent = { Text("Change master password") },
                supportingContent = { Text("Re-encrypts the database with a new master key") },
            )
            ListItem(
                headlineContent = { Text("File") },
                supportingContent = { Text(vm.displayName(uri)) },
            )
            info?.let {
                ListItem(
                    headlineContent = { Text("Format") },
                    supportingContent = {
                        Text("${it.format} · ${it.cipher}\n${it.kdf}\n${it.entries} entries in ${it.groups} groups")
                    },
                )
            }

            SectionHeader("About")
            ListItem(
                headlineContent = { Text("PassVault 1.0") },
                supportingContent = { Text("Reads and writes KeePass 2.x .kdbx files (KDBX 3.1 and 4.x).") },
            )
            VSpace(32)
        }
    }

    when (dialog) {
        "autolock" -> ChoiceDialog("Auto-lock", autoLockOptions, vm.autoLockSec, { dialog = null }) {
            vm.updateAutoLock(it); dialog = null
        }
        "clipboard" -> ChoiceDialog("Clear clipboard after", clipboardOptions, vm.clipboardTimeoutSec, { dialog = null }) {
            vm.updateClipboardTimeout(it); dialog = null
        }
        "rename" -> TextInputDialog("Database name", db.content.meta.name, "Save", { dialog = null }) {
            vm.renameDatabase(it); dialog = null
        }
        "bio" -> EnableBiometricDialog(
            onDismiss = { dialog = null },
            verify = vm::verifyPassword,
        ) { pw ->
            dialog = null
            try {
                val c = BiometricVault.encryptCipher()
                BiometricVault.prompt(
                    activity, c, "Enable fingerprint unlock", vm.displayName(uri),
                    onSuccess = { cc ->
                        vm.storeBiometric(uri, BiometricVault.seal(cc, pw))
                        vm.message = "Fingerprint unlock enabled"
                    },
                    onError = { vm.message = it },
                )
            } catch (e: Exception) {
                vm.message = "Could not enable fingerprint unlock: ${e.message}"
            }
        }
        "masterkey" -> ChangeMasterKeyDialog(
            hasKeyFile = vm.rememberedKeyFile(uri) != null,
            onDismiss = { dialog = null },
        ) { pw, keep ->
            dialog = null
            vm.changeMasterKey(pw, keep)
        }
    }
}

@Composable
private fun EnableBiometricDialog(onDismiss: () -> Unit, verify: (String) -> Boolean, onVerified: (String) -> Unit) {
    var pw by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    fun submit() {
        if (verify(pw)) onVerified(pw) else error = true
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm master password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PasswordField(pw, { pw = it; error = false }, "Master password", imeAction = ImeAction.Done, onIme = { submit() })
                if (error) Text("Incorrect password", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(onClick = { submit() }) { Text("Continue") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ChangeMasterKeyDialog(hasKeyFile: Boolean, onDismiss: () -> Unit, onConfirm: (String, Boolean) -> Unit) {
    var pw by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var keepKey by remember { mutableStateOf(hasKeyFile) }
    val error = when {
        pw != confirm -> "Passwords do not match"
        pw.isEmpty() && !keepKey -> "Password required"
        else -> null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change master password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PasswordField(pw, { pw = it }, "New password")
                StrengthBar(pw)
                PasswordField(confirm, { confirm = it }, "Confirm", imeAction = ImeAction.Done)
                if (hasKeyFile) {
                    Row(Modifier.fillMaxWidth().clickable { keepKey = !keepKey }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(keepKey, { keepKey = it })
                        Text("Keep using current key file")
                    }
                }
                if (confirm.isNotEmpty() && error != null) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(enabled = error == null, onClick = { onConfirm(pw, keepKey) }) { Text("Change") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
