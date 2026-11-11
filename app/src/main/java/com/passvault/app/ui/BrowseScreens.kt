package com.passvault.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.passvault.app.vault.VaultOps
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupScreen(vm: AppViewModel, uuid: UUID) {
    val db = vm.db ?: return
    val group = VaultOps.group(db, uuid)
    if (group == null) {
        LaunchedEffect(uuid) { vm.back() }
        return
    }
    val isRoot = uuid == db.content.group.uuid
    val binUuid = VaultOps.recycleBinUuid(db)
    val inBin = VaultOps.isInRecycleBin(db, uuid)
    var menu by remember { mutableStateOf(false) }
    var newGroup by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmEmpty by remember { mutableStateOf(false) }

    val title = if (isRoot) db.content.meta.name.ifBlank { group.name } else group.name
    val groups = group.groups.sortedWith(compareBy({ it.uuid == binUuid }, { it.name.lowercase() }))
    val entries = group.entries.sortedBy { VaultOps.title(it).lowercase() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (!isRoot) {
                        IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    }
                },
                actions = {
                    IconButton(onClick = { vm.navigate(Screen.Search) }) { Icon(Icons.Filled.Search, "Search") }
                    IconButton(onClick = { vm.lock() }) { Icon(Icons.Filled.Lock, "Lock") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            if (!inBin) {
                                DropdownMenuItem(text = { Text("New group") }, onClick = { menu = false; newGroup = true })
                            }
                            if (!isRoot && uuid != binUuid) {
                                DropdownMenuItem(text = { Text("Rename group") }, onClick = { menu = false; rename = true })
                            }
                            if (!isRoot) {
                                DropdownMenuItem(text = { Text("Delete group") }, onClick = { menu = false; confirmDelete = true })
                            }
                            if (uuid == binUuid) {
                                DropdownMenuItem(text = { Text("Empty recycle bin") }, onClick = { menu = false; confirmEmpty = true })
                            }
                            DropdownMenuItem(text = { Text("Settings") }, onClick = { menu = false; vm.navigate(Screen.Settings) })
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (!inBin) {
                ExtendedFloatingActionButton(
                    onClick = { vm.navigate(Screen.EditEntry(null, uuid)) },
                    icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text("Entry") },
                )
            }
        },
        snackbarHost = { SnackbarHost(LocalSnackbar.current) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (vm.saving) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (groups.isEmpty() && entries.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (inBin) "Recycle bin is empty" else "No entries yet.\nTap + to add one.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(groups, key = { it.uuid }) { g ->
                        val isBin = g.uuid == binUuid
                        ListItem(
                            modifier = Modifier.clickable { vm.navigate(Screen.GroupList(g.uuid)) },
                            leadingContent = {
                                Icon(
                                    if (isBin) Icons.Filled.Delete else Icons.Filled.Folder,
                                    null,
                                    tint = if (isBin) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFFF9A825),
                                )
                            },
                            headlineContent = { Text(g.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            trailingContent = {
                                Text("${g.groups.size + g.entries.size}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            },
                        )
                    }
                    if (groups.isNotEmpty() && entries.isNotEmpty()) item { HorizontalDivider() }
                    items(entries, key = { it.uuid }) { e ->
                        EntryRow(vm, e) { vm.navigate(Screen.EntryView(e.uuid)) }
                    }
                    item { VSpace(88) }
                }
            }
        }
    }

    if (newGroup) {
        TextInputDialog("New group", "", "Create", { newGroup = false }) { vm.addGroup(uuid, it); newGroup = false }
    }
    if (rename) {
        TextInputDialog("Rename group", group.name, "Rename", { rename = false }) { vm.renameGroup(uuid, it); rename = false }
    }
    if (confirmDelete) {
        val permanent = inBin || !db.content.meta.recycleBinEnabled || uuid == binUuid
        ConfirmDialog(
            title = "Delete \"${group.name}\"?",
            text = if (permanent) "The group and everything in it will be permanently deleted."
            else "The group and its contents will be moved to the recycle bin.",
            confirm = "Delete",
            onDismiss = { confirmDelete = false },
        ) {
            confirmDelete = false
            vm.deleteGroup(uuid)
        }
    }
    if (confirmEmpty) {
        ConfirmDialog(
            "Empty recycle bin?", "All items in the recycle bin will be permanently deleted.", "Empty",
            { confirmEmpty = false },
        ) { confirmEmpty = false; vm.emptyRecycleBin() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(vm: AppViewModel) {
    val db = vm.db ?: return
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val results = remember(query, db) { VaultOps.search(db, query) }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                title = {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search entries") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                },
            )
        },
        snackbarHost = { SnackbarHost(LocalSnackbar.current) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            if (query.isNotBlank() && results.isEmpty()) {
                item {
                    Text("No matches", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(results, key = { it.uuid }) { e ->
                EntryRow(vm, e) { vm.navigate(Screen.EntryView(e.uuid)) }
            }
        }
    }
}

private val dateFmt = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
private fun fmt(i: Instant?): String = i?.let { dateFmt.format(it.atZone(ZoneId.systemDefault())) } ?: "—"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryScreen(vm: AppViewModel, uuid: UUID) {
    val db = vm.db ?: return
    val found = VaultOps.entry(db, uuid)
    if (found == null) {
        LaunchedEffect(uuid) { vm.back() }
        return
    }
    val (parent, entry) = found
    val inBin = VaultOps.isInRecycleBin(db, parent.uuid)
    var confirmDelete by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(VaultOps.title(entry).ifBlank { "(no title)" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = { vm.navigate(Screen.EditEntry(uuid, parent.uuid)) }) { Icon(Icons.Filled.Edit, "Edit") }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete") }
                },
            )
        },
        snackbarHost = { SnackbarHost(LocalSnackbar.current) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (vm.saving) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (VaultOps.isExpired(entry)) {
                Text("This entry has expired", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleSmall)
            }
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column {
                    FieldRow("User name", VaultOps.userName(entry), protected = false, onCopy = {
                        vm.copy("User name", it, sensitive = false)
                    })
                    FieldRow("Password", VaultOps.password(entry), protected = true, onCopy = {
                        vm.copy("Password", it, sensitive = true)
                    })
                    val url = VaultOps.url(entry)
                    FieldRow("URL", url, protected = false, onCopy = { vm.copy("URL", it, sensitive = false) }) {
                        if (url.isNotBlank()) {
                            IconButton(onClick = {
                                val target = if (url.contains("://")) url else "https://$url"
                                try {
                                    uriHandler.openUri(target)
                                } catch (_: Exception) {
                                    vm.message = "Cannot open URL"
                                }
                            }) { Icon(Icons.AutoMirrored.Filled.OpenInNew, "Open") }
                        }
                    }
                }
            }

            val custom = VaultOps.customFields(entry)
            if (custom.isNotEmpty()) {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column {
                        custom.forEach { f ->
                            FieldRow(f.key, f.value, protected = f.protected, onCopy = { vm.copy(f.key, it, sensitive = f.protected) })
                        }
                    }
                }
            }

            val notes = VaultOps.notes(entry)
            if (notes.isNotBlank()) {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Notes", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        SelectionContainer { Text(notes, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }

            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (entry.tags.isNotEmpty()) InfoLine("Tags", entry.tags.joinToString(", "))
                    InfoLine("Group", parent.name)
                    InfoLine("Created", fmt(entry.times?.creationTime))
                    InfoLine("Modified", fmt(entry.times?.lastModificationTime))
                    if (entry.times?.expires == true) InfoLine("Expires", fmt(entry.times?.expiryTime))
                    if (entry.binaries.isNotEmpty()) InfoLine("Attachments", entry.binaries.joinToString(", ") { it.name })
                    if (entry.history.isNotEmpty()) InfoLine("History", "${entry.history.size} previous version(s)")
                }
            }
        }
    }

    if (confirmDelete) {
        val permanent = inBin || !db.content.meta.recycleBinEnabled
        ConfirmDialog(
            title = "Delete entry?",
            text = if (permanent) "This entry will be permanently deleted." else "This entry will be moved to the recycle bin.",
            confirm = "Delete",
            onDismiss = { confirmDelete = false },
        ) {
            confirmDelete = false
            vm.deleteEntry(uuid)
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row {
        Text("$label: ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun FieldRow(
    label: String,
    value: String,
    protected: Boolean,
    onCopy: (String) -> Unit,
    extra: @Composable () -> Unit = {},
) {
    var reveal by remember { mutableStateOf(false) }
    val shown = when {
        value.isEmpty() -> "—"
        protected && !reveal -> "••••••••••"
        else -> value
    }
    ListItem(
        overlineContent = { Text(label) },
        headlineContent = {
            SelectionContainer {
                Text(
                    shown,
                    fontFamily = if (protected) FontFamily.Monospace else null,
                    maxLines = if (protected && !reveal) 1 else 6,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        trailingContent = {
            Row {
                extra()
                if (protected && value.isNotEmpty()) {
                    IconButton(onClick = { reveal = !reveal }) {
                        Icon(if (reveal) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (reveal) "Hide" else "Show")
                    }
                }
                if (value.isNotEmpty()) {
                    IconButton(onClick = { onCopy(value) }) { Icon(Icons.Filled.ContentCopy, "Copy $label") }
                }
            }
        },
    )
}
