package com.passvault.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.passvault.app.security.BiometricVault

@Composable
fun UnlockScreen(vm: AppViewModel, activity: FragmentActivity) {
    val ctx = LocalContext.current
    val selected = vm.selectedDb
    var password by remember(selected) { mutableStateOf("") }
    var keyFile by remember(selected) { mutableStateOf(selected?.let(vm::rememberedKeyFile)) }
    val bioAvailable = remember { BiometricVault.isAvailable(ctx) }
    var blob by remember(selected) { mutableStateOf(selected?.let(vm::biometricBlob)) }
    var enableBio by remember(selected) { mutableStateOf(false) }

    val openDb = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::selectDatabase)
    }
    val pickKey = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.persistPermission(it, write = false); keyFile = it }
    }

    fun bioUnlock() {
        val uri = selected ?: return
        val b = blob ?: return
        val cipher = try {
            BiometricVault.decryptCipher(b)
        } catch (e: BiometricVault.KeyInvalidatedException) {
            vm.clearBiometric(uri)
            blob = null
            vm.message = e.message
            return
        } catch (e: Exception) {
            vm.message = "Biometric error: ${e.message}"
            return
        }
        BiometricVault.prompt(
            activity, cipher, "Unlock database", vm.displayName(uri),
            onSuccess = { c ->
                try {
                    vm.unlock(uri, BiometricVault.open(c, b), keyFile)
                } catch (e: Exception) {
                    vm.message = "Could not decrypt stored password: ${e.message}"
                }
            },
            onError = { vm.message = it },
        )
    }

    fun passwordUnlock() {
        val uri = selected ?: return
        val pw = password
        val wantBio = enableBio && bioAvailable
        vm.unlock(uri, pw, keyFile) {
            if (wantBio) {
                try {
                    val c = BiometricVault.encryptCipher()
                    BiometricVault.prompt(
                        activity, c, "Enable fingerprint unlock", "Confirm to store your master password in the Keystore",
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
        }
    }

    LaunchedEffect(selected) {
        if (blob != null && bioAvailable) bioUnlock()
    }

    Scaffold(snackbarHost = { SnackbarHost(LocalSnackbar.current) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            VSpace(24)
            Icon(Icons.Filled.Lock, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
            Text("PassVault", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "KeePass-compatible password manager",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            VSpace(24)

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Database", style = MaterialTheme.typography.titleSmall)
                    if (vm.recent.isEmpty() && selected == null) {
                        Text("No database yet. Open a .kdbx file or create a new one.")
                    }
                    val shown = (listOfNotNull(selected?.toString()) + vm.recent).distinct()
                    shown.forEach { s ->
                        val uri = Uri.parse(s)
                        val isSel = uri == selected
                        Card(
                            colors = if (isSel) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                            else CardDefaults.outlinedCardColors(),
                            modifier = Modifier.fillMaxWidth().clickable { vm.selectedDb = uri },
                        ) {
                            Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    vm.displayName(uri),
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                                )
                                IconButton(onClick = { vm.forgetRecent(uri) }) {
                                    Icon(Icons.Filled.Close, "Forget")
                                }
                            }
                        }
                    }
                    OutlinedButton(onClick = { openDb.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.FolderOpen, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Open database file")
                    }
                }
            }

            if (selected != null) {
                VSpace(16)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Master key", style = MaterialTheme.typography.titleSmall)
                        PasswordField(
                            value = password,
                            onValueChange = { password = it },
                            label = "Master password",
                            imeAction = ImeAction.Done,
                            onIme = { passwordUnlock() },
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.VpnKey, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                keyFile?.let(vm::displayName) ?: "No key file",
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (keyFile != null) {
                                TextButton(onClick = { keyFile = null }) { Text("Clear") }
                            } else {
                                TextButton(onClick = { pickKey.launch(arrayOf("*/*")) }) { Text("Choose") }
                            }
                        }
                        if (bioAvailable && blob == null) {
                            Row(
                                Modifier.fillMaxWidth().clickable { enableBio = !enableBio },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = enableBio, onCheckedChange = { enableBio = it })
                                Text("Enable fingerprint unlock")
                            }
                        }
                        Button(onClick = { passwordUnlock() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Unlock")
                        }
                        if (bioAvailable && blob != null) {
                            OutlinedButton(onClick = { bioUnlock() }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Filled.Fingerprint, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Unlock with fingerprint")
                            }
                        }
                    }
                }
            }

            VSpace(16)
            TextButton(onClick = { vm.navigate(Screen.Create) }) { Text("Create new database") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateScreen(vm: AppViewModel) {
    var name by remember { mutableStateOf("Passwords") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var keyFile by remember { mutableStateOf<Uri?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val createDb = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        uri?.let { vm.createDatabase(it, name.trim(), password, keyFile) }
    }
    val pickKey = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.persistPermission(it, write = false); keyFile = it }
    }
    val newKey = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        uri?.let { vm.writeKeyFile(it) { saved -> keyFile = saved } }
    }

    fun submit() {
        error = when {
            name.isBlank() -> "Enter a database name."
            password != confirm -> "Passwords do not match."
            password.isEmpty() && keyFile == null -> "Set a master password and/or a key file."
            else -> null
        }
        if (error == null) createDb.launch("${name.trim()}.kdbx")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New database") },
                navigationIcon = {
                    IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
        snackbarHost = { SnackbarHost(LocalSnackbar.current) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(name, { name = it }, label = { Text("Database name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            PasswordField(password, { password = it }, "Master password")
            StrengthBar(password)
            PasswordField(confirm, { confirm = it }, "Confirm password", imeAction = ImeAction.Done, onIme = { submit() })

            Text("Key file (optional)", style = MaterialTheme.typography.titleSmall)
            Text(
                keyFile?.let(vm::displayName) ?: "None — the database is protected by the password only.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickKey.launch(arrayOf("*/*")) }) { Text("Use existing") }
                OutlinedButton(onClick = { newKey.launch("${name.trim().ifBlank { "database" }}.keyx") }) { Text("Generate new") }
                if (keyFile != null) TextButton(onClick = { keyFile = null }) { Text("Remove") }
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Text(
                "New databases use KDBX 4 with AES-256 and Argon2d, and open in KeePass 2.x on Windows. " +
                    "There is no way to recover a forgotten master password.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = { submit() }, modifier = Modifier.fillMaxWidth()) { Text("Choose location and create") }
        }
    }
}
