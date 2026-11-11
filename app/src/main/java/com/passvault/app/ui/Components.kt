package com.passvault.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import app.keemobile.kotpass.models.Entry
import com.passvault.app.vault.PasswordGenerator
import com.passvault.app.vault.VaultOps

val LocalSnackbar = staticCompositionLocalOf { SnackbarHostState() }

@Composable
fun App(vm: AppViewModel, activity: FragmentActivity) {
    val snackbar = remember { SnackbarHostState() }
    val msg = vm.message
    LaunchedEffect(msg) {
        if (msg != null) {
            snackbar.showSnackbar(msg)
            if (vm.message == msg) vm.message = null
        }
    }
    BackHandler(enabled = vm.stack.size > 1) { vm.back() }

    CompositionLocalProvider(LocalSnackbar provides snackbar) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = vm.screen to vm.stack.size,
                    contentKey = { it.first },
                    transitionSpec = {
                        val forward = targetState.second >= initialState.second
                        (slideInHorizontally(tween(260)) { w -> if (forward) w / 6 else -w / 6 } + fadeIn(tween(220)))
                            .togetherWith(
                                slideOutHorizontally(tween(220)) { w -> if (forward) -w / 10 else w / 10 } + fadeOut(tween(160)),
                            )
                    },
                    label = "screen",
                ) { (s, _) ->
                    when (s) {
                        Screen.Unlock -> UnlockScreen(vm, activity)
                        Screen.Create -> CreateScreen(vm)
                        is Screen.GroupList -> GroupScreen(vm, s.uuid)
                        is Screen.EntryView -> EntryScreen(vm, s.uuid)
                        is Screen.EditEntry -> EditEntryScreen(vm, s.entryUuid, s.groupUuid)
                        Screen.Search -> SearchScreen(vm)
                        Screen.Settings -> SettingsScreen(vm, activity)
                    }
                }
                vm.busy?.let { BusyOverlay(it) }
            }
        }
    }
}

@Composable
private fun BusyOverlay(text: String) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(Modifier.padding(horizontal = 28.dp, vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                Spacer(Modifier.width(18.dp))
                Text(text, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

// ---- Building blocks ---------------------------------------------------------

/** Host name of a URL without scheme, path or a leading "www.", or null if there is none. */
fun hostOf(url: String): String? {
    val s = url.trim().substringAfter("://").substringBefore('/').substringBefore('?').substringBefore(':')
    return s.removePrefix("www.").takeIf { it.isNotBlank() }
}

/** Rounded gradient tile with the first letter of [label], coloured deterministically by [seed]. */
@Composable
fun Monogram(seed: String, label: String, modifier: Modifier = Modifier, size: Dp = 44.dp, corner: Dp = 14.dp) {
    val key = seed.lowercase().ifBlank { label.lowercase() }
    val (a, b) = AvatarGradients[(key.hashCode() and 0x7fffffff) % AvatarGradients.size]
    val letter = label.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "•"
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(Brush.linearGradient(listOf(a, b))),
        contentAlignment = Alignment.Center,
    ) {
        Text(letter, color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.42f).sp)
    }
}

@Composable
fun EntryMonogram(entry: Entry, modifier: Modifier = Modifier, size: Dp = 44.dp, corner: Dp = 14.dp) {
    val title = VaultOps.title(entry)
    Monogram(hostOf(VaultOps.url(entry)) ?: title, title.ifBlank { hostOf(VaultOps.url(entry)).orEmpty() }, modifier, size, corner)
}

/** Small rounded-square icon on a translucent tint of [color]. */
@Composable
fun IconBadge(icon: ImageVector, color: Color, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.32f))
            .background(color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(size * 0.55f))
    }
}

/** Rounded card section with an optional caption above it. */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
            )
        }
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(content = content)
        }
    }
}

@Composable
fun CardDivider() = HorizontalDivider(
    Modifier.padding(start = 16.dp),
    color = MaterialTheme.colorScheme.outlineVariant,
)

/** Large gradient call-to-action button. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(18.dp)
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        contentPadding = PaddingValues(),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent),
        modifier = modifier.fillMaxWidth().height(56.dp),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(shape)
                .background(
                    if (enabled) Brand.primaryGradient
                    else Brush.linearGradient(listOf(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.surfaceContainerHighest)),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val tint = if (enabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                if (icon != null) {
                    Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                }
                Text(text, style = MaterialTheme.typography.titleMedium, color = tint)
            }
        }
    }
}

/** Neutral tonal button to pair with [PrimaryButton]. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    height: Dp = 52.dp,
) {
    FilledTonalButton(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = modifier.height(height),
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SmallAction(icon: ImageVector, description: String, onClick: () -> Unit) {
    FilledTonalIconButton(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        modifier = Modifier.size(40.dp),
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (subtitle != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

// ---- Entries -------------------------------------------------------------------

@Composable
fun EntryRow(vm: AppViewModel, entry: Entry, onClick: () -> Unit) {
    val title = VaultOps.title(entry).ifBlank { "(no title)" }
    val user = VaultOps.userName(entry)
    val subtitle = user.ifEmpty { hostOf(VaultOps.url(entry)).orEmpty() }
    val expired = VaultOps.isExpired(entry)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(Modifier.padding(start = 12.dp, end = 10.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (expired) {
                IconBadge(Icons.Rounded.Warning, MaterialTheme.colorScheme.error, size = 44.dp)
            } else {
                EntryMonogram(entry)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotEmpty()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (expired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            if (user.isNotEmpty()) {
                SmallAction(Icons.Rounded.Person, "Copy user name") { vm.copy("User name", user, sensitive = false) }
                Spacer(Modifier.width(6.dp))
            }
            SmallAction(Icons.Rounded.ContentCopy, "Copy password") { vm.copy("Password", VaultOps.password(entry), sensitive = true) }
        }
    }
}

// ---- Inputs --------------------------------------------------------------------

val FieldShape = RoundedCornerShape(16.dp)

@Composable
fun pvFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    errorContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
    errorIndicatorColor = Color.Transparent,
)

/** Filled, rounded text field used throughout the app. */
@Composable
fun PvField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    textStyle: TextStyle = LocalTextStyle.current,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    placeholder: String? = null,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = minLines,
        modifier = modifier.fillMaxWidth(),
        textStyle = textStyle,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        shape = FieldShape,
        colors = pvFieldColors(),
    )
}

@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Next,
    onIme: () -> Unit = {},
    leadingIcon: (@Composable () -> Unit)? = null,
    extraTrailing: @Composable () -> Unit = {},
) {
    var visible by remember { mutableStateOf(false) }
    PvField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = modifier,
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            autoCorrectEnabled = false,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(onAny = { onIme() }),
        leadingIcon = leadingIcon,
        trailingIcon = {
            Row {
                extraTrailing()
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = if (visible) "Hide" else "Show",
                    )
                }
            }
        },
    )
}

@Composable
fun StrengthBar(password: String, bits: Double = PasswordGenerator.estimateBits(password)) {
    val (level, label, color) = when {
        password.isEmpty() -> Triple(0, "", MaterialTheme.colorScheme.outline)
        bits < 40 -> Triple(1, "Weak", Brand.Weak)
        bits < 64 -> Triple(2, "Fair", Brand.Fair)
        bits < 100 -> Triple(3, "Strong", Brand.Strong)
        else -> Triple(4, "Very strong", Brand.VeryStrong)
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(4) { i ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(if (i < level) color else MaterialTheme.colorScheme.surfaceContainerHighest),
                )
            }
        }
        if (label.isNotEmpty()) {
            Text(
                "$label · ~${bits.toInt()} bits",
                style = MaterialTheme.typography.labelMedium,
                color = color,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

// ---- Dialogs -------------------------------------------------------------------

private val destructiveWords = setOf("Delete", "Empty", "Discard", "Remove")

@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    confirm: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { PvField(text, { text = it }, "Name") },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { onConfirm(text) }) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val destructive = confirm in destructiveWords
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = if (destructive) ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                else ButtonDefaults.textButtonColors(),
            ) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun <T> ChoiceDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                options.forEach { (value, label) ->
                    val isSel = value == selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isSel) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
                            .selectable(selected = isSel, onClick = { onSelect(value) })
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = isSel, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

fun durationLabel(sec: Int): String = when {
    sec < 0 -> "Never"
    sec == 0 -> "Immediately"
    sec < 60 -> "$sec seconds"
    sec % 60 == 0 && sec < 3600 -> "${sec / 60} minute${if (sec == 60) "" else "s"}"
    else -> "${sec / 60} minutes"
}

@Composable
fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 22.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
fun VSpace(h: Int) = Spacer(Modifier.height(h.dp))
