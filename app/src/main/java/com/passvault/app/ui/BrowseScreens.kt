package com.passvault.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.keemobile.kotpass.models.Group
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
        // Only pop if this screen is still on top (it may be animating out after a delete).
        LaunchedEffect(uuid) { if (vm.screen == Screen.GroupList(uuid)) vm.back() }
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
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (!isRoot) {
                        IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                    }
                },
                actions = {
                    IconButton(onClick = { vm.lock() }) { Icon(Icons.Rounded.Lock, "Lock") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                        DropdownMenu(
                            expanded = menu,
                            onDismissRequest = { menu = false },
                            shape = RoundedCornerShape(16.dp),
                        ) {
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
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                scrollBehavior = scroll,
            )
        },
        floatingActionButton = {
            if (!inBin) {
                ExtendedFloatingActionButton(
                    onClick = { vm.navigate(Screen.EditEntry(null, uuid)) },
                    icon = { Icon(Icons.Rounded.Add, null) },
                    text = { Text("New entry") },
                    shape = RoundedCornerShape(20.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
        snackbarHost = { SnackbarHost(LocalSnackbar.current) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 104.dp),
        ) {
            item(key = "search") {
                SearchPill(onClick = { vm.navigate(Screen.Search) })
            }
            if (vm.saving) {
                item(key = "saving") {
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
                }
            }
            if (groups.isNotEmpty()) {
                item(key = "groups-header") { ListCaption("Folders") }
                item(key = "groups") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(groups, key = { it.uuid }) { g ->
                            FolderTile(g, isBin = g.uuid == binUuid) { vm.navigate(Screen.GroupList(g.uuid)) }
                        }
                    }
                }
            }
            if (entries.isNotEmpty()) {
                item(key = "entries-header") { ListCaption(if (inBin) "Deleted · ${entries.size}" else "Entries · ${entries.size}") }
                items(entries, key = { it.uuid }) { e ->
                    EntryRow(vm, e) { vm.navigate(Screen.EntryView(e.uuid)) }
                }
            } else if (groups.isEmpty() || inBin) {
                item(key = "empty") {
                    if (inBin) {
                        EmptyState(Icons.Rounded.Delete, "Recycle bin is empty", "Deleted entries show up here until you remove them for good.")
                    } else {
                        EmptyState(Icons.Rounded.Key, "No entries yet", "Tap + to add one.")
                    }
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

@Composable
private fun ListCaption(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 10.dp),
    )
}

@Composable
private fun SearchPill(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Search, "Search", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Text("Search entries", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FolderTile(g: Group, isBin: Boolean, onClick: () -> Unit) {
    val count = g.groups.size + g.entries.size
    val accent = if (isBin) MaterialTheme.colorScheme.onSurfaceVariant else accentFor(g.name)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.width(148.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            IconBadge(if (isBin) Icons.Rounded.Delete else Icons.Rounded.Folder, accent)
            Spacer(Modifier.height(14.dp))
            Text(g.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (count == 1) "1 item" else "$count items",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                Spacer(Modifier.width(4.dp))
                androidx.compose.material3.TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search entries") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear") }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    shape = RoundedCornerShape(18.dp),
                    colors = pvFieldColors(),
                    modifier = Modifier.weight(1f).focusRequester(focus),
                )
            }
        },
        snackbarHost = { SnackbarHost(LocalSnackbar.current) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(vertical = 8.dp)) {
            when {
                query.isBlank() -> item {
                    EmptyState(Icons.Rounded.Search, "Find anything", "Search titles, user names, URLs, notes and tags.")
                }
                results.isEmpty() -> item { EmptyState(Icons.Rounded.SearchOff, "No matches", "Try a different word.") }
                else -> {
                    item { ListCaption(if (results.size == 1) "1 result" else "${results.size} results") }
                    items(results, key = { it.uuid }) { e ->
                        EntryRow(vm, e) { vm.navigate(Screen.EntryView(e.uuid)) }
                    }
                }
            }
        }
    }
}

private val dateFmt = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
private fun fmt(i: Instant?): String = i?.let { dateFmt.format(it.atZone(ZoneId.systemDefault())) } ?: "—"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EntryScreen(vm: AppViewModel, uuid: UUID) {
    val db = vm.db ?: return
    val found = VaultOps.entry(db, uuid)
    if (found == null) {
        // Only pop if this screen is still on top (it may be animating out after a delete).
        LaunchedEffect(uuid) { if (vm.screen == Screen.EntryView(uuid)) vm.back() }
        return
    }
    val (parent, entry) = found
    val inBin = VaultOps.isInRecycleBin(db, parent.uuid)
    var confirmDelete by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val title = VaultOps.title(entry).ifBlank { "(no title)" }
    val user = VaultOps.userName(entry)
    val password = VaultOps.password(entry)
    val url = VaultOps.url(entry)

    fun openUrl() {
        val target = if (url.contains("://")) url else "https://$url"
        try {
            uriHandler.openUri(target)
        } catch (_: Exception) {
            vm.message = "Cannot open URL"
        }
    }

    Box(Modifier.fillMaxSize()) {
        AmbientGlow()
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {},
                    navigationIcon = {
                        IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                    },
                    actions = {
                        if (inBin) {
                            IconButton(onClick = { vm.restoreEntry(uuid) }) { Icon(Icons.Rounded.RestoreFromTrash, "Restore") }
                        }
                        IconButton(onClick = { vm.navigate(Screen.EditEntry(uuid, parent.uuid)) }) { Icon(Icons.Rounded.Edit, "Edit") }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, "Delete") }
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
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (vm.saving) LinearProgressIndicator(Modifier.fillMaxWidth())

                // Header
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    EntryMonogram(entry, size = 84.dp, corner = 28.dp)
                    Spacer(Modifier.height(16.dp))
                    Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val sub = hostOf(url) ?: user
                    if (sub.isNotEmpty()) {
                        Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (inBin || VaultOps.isExpired(entry)) {
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (inBin) StatusChip(Icons.Rounded.Delete, "In recycle bin")
                            if (VaultOps.isExpired(entry)) StatusChip(Icons.Rounded.Warning, "This entry has expired")
                        }
                    }
                }

                // Quick actions
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickAction(Icons.Rounded.Person, "Username", enabled = user.isNotEmpty(), modifier = Modifier.weight(1f)) {
                        vm.copy("User name", user, sensitive = false)
                    }
                    QuickAction(Icons.Rounded.Key, "Password", enabled = password.isNotEmpty(), modifier = Modifier.weight(1f)) {
                        vm.copy("Password", password, sensitive = true)
                    }
                    QuickAction(Icons.AutoMirrored.Rounded.OpenInNew, "Website", enabled = url.isNotBlank(), modifier = Modifier.weight(1f)) {
                        openUrl()
                    }
                }

                SectionCard(title = "Login") {
                    FieldRow("User name", user, protected = false, onCopy = { vm.copy("User name", it, sensitive = false) })
                    CardDivider()
                    FieldRow("Password", password, protected = true, onCopy = { vm.copy("Password", it, sensitive = true) })
                    CardDivider()
                    FieldRow("URL", url, protected = false, onCopy = { vm.copy("URL", it, sensitive = false) }) {
                        if (url.isNotBlank()) {
                            IconButton(onClick = { openUrl() }) { Icon(Icons.AutoMirrored.Rounded.OpenInNew, "Open") }
                        }
                    }
                }

                val custom = VaultOps.customFields(entry)
                if (custom.isNotEmpty()) {
                    SectionCard(title = "Custom fields") {
                        custom.forEachIndexed { i, f ->
                            if (i > 0) CardDivider()
                            FieldRow(f.key, f.value, protected = f.protected, onCopy = { vm.copy(f.key, it, sensitive = f.protected) })
                        }
                    }
                }

                val notes = VaultOps.notes(entry)
                if (notes.isNotBlank()) {
                    SectionCard(title = "Notes") {
                        SelectionContainer {
                            Text(notes, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth().padding(16.dp))
                        }
                    }
                }

                if (entry.tags.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        entry.tags.forEach { tag ->
                            Surface(shape = RoundedCornerShape(50), color = accentFor(tag).copy(alpha = 0.16f)) {
                                Text(
                                    "#$tag",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = accentFor(tag),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                }

                SectionCard(title = "Details") {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        InfoLine("Group", parent.name)
                        InfoLine("Created", fmt(entry.times?.creationTime))
                        InfoLine("Modified", fmt(entry.times?.lastModificationTime))
                        if (entry.times?.expires == true) InfoLine("Expires", fmt(entry.times?.expiryTime))
                        if (entry.binaries.isNotEmpty()) InfoLine("Attachments", entry.binaries.joinToString(", ") { it.name })
                        if (entry.history.isNotEmpty()) InfoLine("History", "${entry.history.size} previous version(s)")
                    }
                }
                VSpace(24)
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
private fun StatusChip(icon: ImageVector, text: String) {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.errorContainer) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val alpha = if (enabled) 1f else 0.4f
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier,
    ) {
        Column(Modifier.padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f * alpha), RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha), modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(110.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
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
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
            SelectionContainer {
                Text(
                    shown,
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = if (protected) FontFamily.Monospace else null,
                    color = if (value.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = if (protected && !reveal) 1 else 6,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
            extra()
            if (protected && value.isNotEmpty()) {
                IconButton(onClick = { reveal = !reveal }) {
                    Icon(if (reveal) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (reveal) "Hide" else "Show")
                }
            }
            if (value.isNotEmpty()) {
                IconButton(onClick = { onCopy(value) }) { Icon(Icons.Rounded.ContentCopy, "Copy $label") }
            }
        }
    }
}
