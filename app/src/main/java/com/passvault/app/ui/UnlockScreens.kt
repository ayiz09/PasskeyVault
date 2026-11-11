package com.passvault.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.passvault.app.security.BiometricVault

/** Soft brand-coloured glow behind the top of a screen. */
@Composable
fun AmbientGlow(modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme.primary
    Box(
        modifier
            .fillMaxWidth()
            .height(460.dp)
            .background(
                Brush.verticalGradient(
                    listOf(c.copy(alpha = 0.22f), c.copy(alpha = 0.06f), Color.Transparent),
                ),
            ),
    )
}

@Composable
fun AppLogo(size: Int = 88) {
    val shape = RoundedCornerShape((size * 0.32f).dp)
    Box(
        Modifier
            .size(size.dp)
            .shadow(28.dp, shape, ambientColor = Brand.Violet, spotColor = Brand.Violet)
            .clip(shape)
            .background(Brand.primaryGradient),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Shield, null, tint = Color.White, modifier = Modifier.size((size * 0.5f).dp))
    }
}

@Composable
private fun FeaturePill(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier,
    ) {
        Column(Modifier.padding(vertical = 14.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(6.dp))
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

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

    val hasAny = vm.recent.isNotEmpty() || selected != null

    Box(Modifier.fillMaxSize()) {
        AmbientGlow()
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(LocalSnackbar.current) },
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                VSpace(40)
                AppLogo()
                VSpace(24)
                Text(
                    if (selected != null) "Welcome back" else "PassVault",
                    style = MaterialTheme.typography.headlineLarge,
                )
                VSpace(6)
                Text(
                    if (selected != null) "Unlock your vault to continue" else "KeePass-compatible password manager",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                VSpace(32)

                if (!hasAny) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FeaturePill(Icons.Rounded.VerifiedUser, "AES-256\nArgon2", Modifier.weight(1f))
                        FeaturePill(Icons.Rounded.CloudOff, "Fully\noffline", Modifier.weight(1f))
                        FeaturePill(Icons.Rounded.SwapHoriz, "KeePass\ncompatible", Modifier.weight(1f))
                    }
                    VSpace(28)
                    Text(
                        "No database yet. Open a .kdbx file or create a new one.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    VSpace(16)
                    PrimaryButton("Create new database", { vm.navigate(Screen.Create) }, icon = Icons.Rounded.Add)
                    VSpace(12)
                    SecondaryButton(
                        "Open database file",
                        { openDb.launch(arrayOf("*/*")) },
                        Modifier.fillMaxWidth(),
                        icon = Icons.Rounded.FolderOpen,
                        height = 56.dp,
                    )
                } else {
                    SectionCard(title = "Vault") {
                        val shown = (listOfNotNull(selected?.toString()) + vm.recent).distinct()
                        shown.forEachIndexed { i, s ->
                            val uri = Uri.parse(s)
                            val isSel = uri == selected
                            if (i > 0) CardDivider()
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { vm.selectedDb = uri }
                                    .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                IconBadge(
                                    Icons.Rounded.Description,
                                    if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        vm.displayName(uri),
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (isSel) {
                                        Text("Selected", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                if (isSel) {
                                    Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                }
                                IconButton(onClick = { vm.forgetRecent(uri) }) {
                                    Icon(Icons.Rounded.Close, "Forget", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        CardDivider()
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { openDb.launch(arrayOf("*/*")) }
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.FolderOpen, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Text("Open database file", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                if (selected != null) {
                    VSpace(20)
                    SectionCard(title = "Master key") {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            PasswordField(
                                value = password,
                                onValueChange = { password = it },
                                label = "Master password",
                                imeAction = ImeAction.Done,
                                onIme = { passwordUnlock() },
                                leadingIcon = { Icon(Icons.Rounded.Lock, null) },
                            )
                            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                                Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.VpnKey, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        keyFile?.let(vm::displayName) ?: "No key file",
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (keyFile != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (keyFile != null) {
                                        TextButton(onClick = { keyFile = null }) { Text("Clear") }
                                    } else {
                                        TextButton(onClick = { pickKey.launch(arrayOf("*/*")) }) { Text("Choose") }
                                    }
                                }
                            }
                            if (bioAvailable && blob == null) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable { enableBio = !enableBio }
                                        .padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Rounded.Fingerprint, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.width(12.dp))
                                    Text("Enable fingerprint unlock", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                    Switch(checked = enableBio, onCheckedChange = { enableBio = it })
                                }
                            }
                        }
                    }
                    VSpace(24)
                    PrimaryButton("Unlock", { passwordUnlock() }, icon = Icons.Rounded.LockOpen)
                    if (bioAvailable && blob != null) {
                        VSpace(12)
                        SecondaryButton(
                            "Unlock with fingerprint",
                            { bioUnlock() },
                            Modifier.fillMaxWidth(),
                            icon = Icons.Rounded.Fingerprint,
                            height = 56.dp,
                        )
                    }
                }

                if (hasAny) {
                    VSpace(20)
                    TextButton(onClick = { vm.navigate(Screen.Create) }) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Create new database")
                    }
                }
                VSpace(32)
            }
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

    Box(Modifier.fillMaxSize()) {
        AmbientGlow()
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("New database") },
                    navigationIcon = {
                        IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
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
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Column {
                    Text("Create your vault", style = MaterialTheme.typography.headlineMedium)
                    VSpace(4)
                    Text(
                        "Everything is encrypted on this device. Only you hold the key.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                SectionCard(title = "Details") {
                    Column(Modifier.padding(14.dp)) {
                        PvField(name, { name = it }, "Database name")
                    }
                }

                SectionCard(title = "Master password") {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        PasswordField(password, { password = it }, "Master password", leadingIcon = { Icon(Icons.Rounded.Lock, null) })
                        StrengthBar(password)
                        PasswordField(
                            confirm, { confirm = it }, "Confirm password",
                            imeAction = ImeAction.Done, onIme = { submit() },
                            leadingIcon = { Icon(Icons.Rounded.Lock, null) },
                        )
                    }
                }

                SectionCard(title = "Key file (optional)") {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(
                                Icons.Rounded.VpnKey,
                                if (keyFile != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                keyFile?.let(vm::displayName) ?: "None — the database is protected by the password only.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (keyFile != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SecondaryButton("Use existing", { pickKey.launch(arrayOf("*/*")) }, Modifier.weight(1f), height = 46.dp)
                            SecondaryButton(
                                "Generate new",
                                { newKey.launch("${name.trim().ifBlank { "database" }}.keyx") },
                                Modifier.weight(1f),
                                height = 46.dp,
                            )
                        }
                        if (keyFile != null) {
                            TextButton(onClick = { keyFile = null }) { Text("Remove") }
                        }
                    }
                }

                error?.let {
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.errorContainer) {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                        )
                    }
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                        .padding(14.dp),
                ) {
                    Icon(Icons.Rounded.Shield, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "New databases use KDBX 4 with AES-256 and Argon2d, and open in KeePass 2.x on Windows. " +
                            "There is no way to recover a forgotten master password.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                PrimaryButton("Choose location and create", { submit() })
                VSpace(12)
            }
        }
    }
}
