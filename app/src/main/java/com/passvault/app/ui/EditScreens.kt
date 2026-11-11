package com.passvault.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
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
                navigationIcon = { IconButton(onClick = { close() }) { Icon(Icons.Filled.Close, "Cancel") } },
                actions = {
                    TextButton(onClick = { vm.saveEntry(entryUuid, groupUuid, draft) }) { Text("Save") }
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                draft.title, { draft = draft.copy(title = it) },
                label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                draft.userName, { draft = draft.copy(userName = it) },
                label = { Text("User name") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrectEnabled = false),
            )
            PasswordField(
                draft.password, { draft = draft.copy(password = it) }, "Password",
                extraTrailing = {
                    IconButton(onClick = { showGenerator = true }) { Icon(Icons.Filled.Casino, "Generate password") }
                },
            )
            StrengthBar(draft.password)
            OutlinedTextField(
                draft.url, { draft = draft.copy(url = it) },
                label = { Text("URL") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
            )
            OutlinedTextField(
                draft.notes, { draft = draft.copy(notes = it) },
                label = { Text("Notes") }, minLines = 3, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                draft.tags, { draft = draft.copy(tags = it) },
                label = { Text("Tags (comma separated)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )

            Text("Custom fields", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            draft.customFields.forEachIndexed { i, f ->
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
            OutlinedButton(onClick = {
                draft = draft.copy(customFields = draft.customFields + CustomField("", "", false))
            }) {
                Icon(Icons.Filled.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Add field")
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
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    field.key, { onChange(field.copy(key = it)) },
                    label = { Text("Name") }, singleLine = true, modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onChange(field.copy(protected = !field.protected)) }) {
                    Icon(
                        if (field.protected) Icons.Filled.Lock else Icons.Filled.LockOpen,
                        if (field.protected) "Protected" else "Not protected",
                        tint = if (field.protected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onRemove) { Icon(Icons.Filled.Delete, "Remove field") }
            }
            if (field.protected) {
                PasswordField(field.value, { onChange(field.copy(value = it)) }, "Value (protected)")
            } else {
                OutlinedTextField(
                    field.value, { onChange(field.copy(value = it)) },
                    label = { Text("Value") }, modifier = Modifier.fillMaxWidth(),
                )
            }
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SelectionContainer(Modifier.weight(1f)) {
                        Text(generated, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyLarge)
                    }
                    IconButton(onClick = { generated = PasswordGenerator.generate(options) }) {
                        Icon(Icons.Filled.Refresh, "Regenerate")
                    }
                }
                StrengthBar(generated, PasswordGenerator.entropyBits(options))
                VSpace(12)
                Text("Length: ${options.length}")
                Slider(
                    value = options.length.toFloat(),
                    onValueChange = { update(options.copy(length = it.toInt())) },
                    valueRange = 6f..64f,
                )
                OptionCheck("Uppercase (A–Z)", options.upper) { update(options.copy(upper = it)) }
                OptionCheck("Lowercase (a–z)", options.lower) { update(options.copy(lower = it)) }
                OptionCheck("Digits (0–9)", options.digits) { update(options.copy(digits = it)) }
                OptionCheck("Symbols (!@#…)", options.symbols) { update(options.copy(symbols = it)) }
                OptionCheck("Exclude look-alikes (Il1O0)", options.excludeAmbiguous) { update(options.copy(excludeAmbiguous = it)) }
            }
        },
        confirmButton = { TextButton(onClick = { onUse(generated) }) { Text("Use") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun OptionCheck(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label)
    }
}
