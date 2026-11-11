package com.passvault.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.passvault.app.vault.CustomField
import com.passvault.app.vault.EntryDraft
import com.passvault.app.vault.GeneratorOptions
import com.passvault.app.vault.PasswordGenerator
import com.passvault.app.vault.VaultOps
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditEntryScreen(vm: AppViewModel, entryUuid: UUID?, groupUuid: UUID) {
    val db = vm.db ?: return
    val initial = remember(entryUuid) {
        entryUuid?.let { VaultOps.entry(db, it)?.second }?.let(VaultOps::toDraft)
            ?: EntryDraft(
                userName = db.content.meta.defaultUser,
                password = PasswordGenerator.generate(GeneratorOptions()),
            )
    }
    var draft by remember(entryUuid) { mutableStateOf(initial) }
    var showGenerator by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }

    fun close() {
        if (draft != initial) confirmDiscard = true else vm.back()
    }
    BackHandler { close() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (entryUuid == null) "New entry" else "Edit entry") },
                navigationIcon = { IconButton(onClick = { close() }) { Icon(Icons.Rounded.Close, "Cancel") } },
                actions = {
                    Button(
                        onClick = { vm.saveEntry(entryUuid, groupUuid, draft) },
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        modifier = Modifier.padding(end = 8.dp),
                    ) { Text("Save") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Live preview of the entry's monogram and title.
            Row(Modifier.padding(top = 4.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Monogram(hostOf(draft.url) ?: draft.title, draft.title.ifBlank { hostOf(draft.url).orEmpty() }, size = 56.dp, corner = 18.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        draft.title.ifBlank { "Untitled" },
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        hostOf(draft.url) ?: "Add a website to personalise the icon",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionCard(title = "Login") {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PvField(draft.title, { draft = draft.copy(title = it) }, "Title", leadingIcon = { Icon(Icons.Rounded.Title, null) })
                    PvField(
                        draft.userName, { draft = draft.copy(userName = it) }, "User name",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrectEnabled = false),
                        leadingIcon = { Icon(Icons.Rounded.Person, null) },
                    )
                    PasswordField(
                        draft.password, { draft = draft.copy(password = it) }, "Password",
                        leadingIcon = { Icon(Icons.Rounded.Lock, null) },
                        extraTrailing = {
                            IconButton(onClick = { showGenerator = true }) { Icon(Icons.Rounded.Casino, "Generate password") }
                        },
                    )
                    StrengthBar(draft.password)
                    PvField(
                        draft.url, { draft = draft.copy(url = it) }, "URL",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                        leadingIcon = { Icon(Icons.Rounded.Language, null) },
                    )
                }
            }

            SectionCard(title = "Notes & tags") {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PvField(
                        draft.notes, { draft = draft.copy(notes = it) }, "Notes",
                        singleLine = false, minLines = 3,
                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Notes, null) },
                    )
                    PvField(
                        draft.tags, { draft = draft.copy(tags = it) }, "Tags (comma separated)",
                        leadingIcon = { Icon(Icons.Rounded.Sell, null) },
                    )
                }
            }

            SectionCard(title = "Custom fields") {
                draft.customFields.forEachIndexed { i, f ->
                    if (i > 0) CardDivider()
                    CustomFieldEditor(
                        field = f,
                        onChange = { nf ->
                            draft = draft.copy(customFields = draft.customFields.toMutableList().also { it[i] = nf })
                        },
                        onRemove = {
                            draft = draft.copy(customFields = draft.customFields.toMutableList().also { it.removeAt(i) })
                        },
                    )
                }
                if (draft.customFields.isNotEmpty()) CardDivider()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { draft = draft.copy(customFields = draft.customFields + CustomField("", "", false)) }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text("Add field", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
            VSpace(40)
        }
    }

    if (showGenerator) {
        GeneratorDialog(onDismiss = { showGenerator = false }) {
            draft = draft.copy(password = it)
            showGenerator = false
        }
    }
    if (confirmDiscard) {
        ConfirmDialog("Discard changes?", "Your unsaved changes will be lost.", "Discard", { confirmDiscard = false }) {
            confirmDiscard = false
            vm.back()
        }
    }
}

@Composable
private fun CustomFieldEditor(field: CustomField, onChange: (CustomField) -> Unit, onRemove: () -> Unit) {
    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PvField(field.key, { onChange(field.copy(key = it)) }, "Name", modifier = Modifier.weight(1f))
            IconButton(onClick = { onChange(field.copy(protected = !field.protected)) }) {
                Icon(
                    if (field.protected) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                    if (field.protected) "Protected" else "Not protected",
                    tint = if (field.protected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRemove) { Icon(Icons.Rounded.Delete, "Remove field", tint = MaterialTheme.colorScheme.error) }
        }
        if (field.protected) {
            PasswordField(field.value, { onChange(field.copy(value = it)) }, "Value (protected)")
        } else {
            PvField(field.value, { onChange(field.copy(value = it)) }, "Value", singleLine = false)
        }
    }
}

@Composable
fun GeneratorDialog(onDismiss: () -> Unit, onUse: (String) -> Unit) {
    var options by remember { mutableStateOf(GeneratorOptions()) }
    var generated by remember { mutableStateOf(PasswordGenerator.generate(options)) }
    fun update(o: GeneratorOptions) {
        options = o
        generated = PasswordGenerator.generate(o)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Password generator") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                    Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        SelectionContainer(Modifier.weight(1f)) {
                            Text(generated, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleMedium)
                        }
                        IconButton(onClick = { generated = PasswordGenerator.generate(options) }) {
                            Icon(Icons.Rounded.Refresh, "Regenerate", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                VSpace(12)
                StrengthBar(generated, PasswordGenerator.entropyBits(options))
                VSpace(16)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Length", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)) {
                        Text(
                            "${options.length}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
                Slider(
                    value = options.length.toFloat(),
                    onValueChange = { update(options.copy(length = it.toInt())) },
                    valueRange = 6f..64f,
                )
                OptionSwitch("Uppercase (A–Z)", options.upper) { update(options.copy(upper = it)) }
                OptionSwitch("Lowercase (a–z)", options.lower) { update(options.copy(lower = it)) }
                OptionSwitch("Digits (0–9)", options.digits) { update(options.copy(digits = it)) }
                OptionSwitch("Symbols (!@#…)", options.symbols) { update(options.copy(symbols = it)) }
                OptionSwitch("Exclude look-alikes (Il1O0)", options.excludeAmbiguous) { update(options.copy(excludeAmbiguous = it)) }
            }
        },
        confirmButton = {
            Button(onClick = { onUse(generated) }, shape = RoundedCornerShape(14.dp)) { Text("Use") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun OptionSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
    Spacer(Modifier.height(2.dp))
}
