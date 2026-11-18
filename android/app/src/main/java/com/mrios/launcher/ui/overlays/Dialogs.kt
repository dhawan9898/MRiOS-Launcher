package com.mrios.launcher.ui.overlays

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrios.launcher.R
import com.mrios.launcher.data.AppEntry
import com.mrios.launcher.launcherApp
import com.mrios.launcher.system.HiddenAuth
import com.mrios.launcher.ui.IOSColors
import com.mrios.launcher.ui.LauncherViewModel
import com.mrios.launcher.ui.components.AppIcon
import com.mrios.launcher.ui.components.FrostedBackdrop
import com.mrios.launcher.ui.components.glass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun FieldBox(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(40.dp).background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(10.dp)).padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) { content() }
}

@Composable
fun RemoveDialog(vm: LauncherViewModel, entry: AppEntry, onDismiss: () -> Unit) {
    GlassDialog(
        title = stringResource(R.string.remove_title, entry.label),
        message = stringResource(R.string.remove_message),
        onDismiss = onDismiss,
        buttons = buildList {
            if (!entry.isSystem) add(DialogButton(stringResource(R.string.menu_delete_app), ButtonRole.DESTRUCTIVE) { onDismiss(); vm.uninstall(entry) })
            add(DialogButton(stringResource(R.string.menu_remove_home)) { onDismiss(); vm.removeFromHome(entry) })
            add(DialogButton(stringResource(R.string.cancel), ButtonRole.CANCEL, onDismiss))
        },
    )
}

@Composable
fun RenameDialog(vm: LauncherViewModel, entry: AppEntry, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(TextFieldValue(entry.label, TextRange(0, entry.label.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    GlassDialog(
        title = stringResource(R.string.rename_title),
        message = entry.originalLabel,
        onDismiss = onDismiss,
        buttons = listOf(
            DialogButton(stringResource(R.string.save), ButtonRole.PRIMARY) { vm.rename(entry, text.text); onDismiss() },
            DialogButton(stringResource(R.string.reset_name)) { vm.rename(entry, null); onDismiss() },
            DialogButton(stringResource(R.string.cancel), ButtonRole.CANCEL, onDismiss),
        ),
    ) {
        FieldBox {
            BasicTextField(
                text, { text = it }, singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                cursorBrush = SolidColor(IOSColors.Blue),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
    }
}

/** PIN entry for hidden apps when the phone has no screen lock / biometrics. */
@Composable
fun PinDialog(mode: PinMode, storedHash: String?, onDone: (newHash: String?) -> Unit, onSuccess: () -> Unit, onDismiss: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var first by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<Int?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val title = when {
        mode == PinMode.VERIFY -> R.string.pin_enter
        first == null -> R.string.pin_create
        else -> R.string.pin_confirm
    }
    fun submit() {
        if (pin.length < 4) return
        when {
            mode == PinMode.VERIFY -> if (HiddenAuth.hashPin(pin) == storedHash) { onDismiss(); onSuccess() } else { error = R.string.pin_wrong; pin = "" }
            first == null -> { first = pin; pin = ""; error = null }
            first == pin -> { onDone(HiddenAuth.hashPin(pin)); onDismiss(); onSuccess() }
            else -> { error = R.string.pin_mismatch; first = null; pin = "" }
        }
    }
    GlassDialog(
        title = stringResource(title),
        message = error?.let { stringResource(it) },
        onDismiss = onDismiss,
        buttons = listOf(
            DialogButton(stringResource(R.string.ok), ButtonRole.PRIMARY) { submit() },
            DialogButton(stringResource(R.string.cancel), ButtonRole.CANCEL, onDismiss),
        ),
    ) {
        FieldBox {
            BasicTextField(
                pin, { v -> pin = v.filter { it.isDigit() }.take(4); if (pin.length == 4) submit() },
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 22.sp, textAlign = TextAlign.Center, letterSpacing = 8.sp),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { submit() }),
                cursorBrush = SolidColor(IOSColors.Blue),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
    }
}

/** Bottom sheet for swapping an app's icon: photo, icon-pack icon, or reset. */
@Composable
fun IconPickerSheet(vm: LauncherViewModel, entry: AppEntry, onPickPhoto: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val loader = context.launcherApp.icons
    val packs = remember { vm.iconPacks() }
    var pack by remember { mutableStateOf(vm.settings.value.iconPack ?: packs.firstOrNull()?.packageName) }
    var filter by remember { mutableStateOf("") }
    val names by produceState(emptyList<String>(), pack) {
        value = pack?.let { p -> withContext(Dispatchers.IO) { vm.packIconNames(p) } } ?: emptyList()
    }
    val shown = remember(names, filter, entry) {
        val f = filter.ifBlank { entry.label }.lowercase().replace(" ", "")
        val matches = names.filter { it.lowercase().replace("_", "").contains(f) }
        if (filter.isBlank()) matches + names.filter { it !in matches } else matches
    }

    Box(Modifier.fillMaxSize()) {
        FrostedBackdrop(true, dim = 0.3f, modifier = Modifier.noRippleClick(onDismiss))
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.72f)
                .popIn(0.95f)
                .glass(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp), tint = DialogTint)
                .noRippleClick { }
                .navigationBarsPadding()
                .imePadding()
                .padding(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(entry, 48.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.icon_title), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(entry.label, color = IOSColors.Secondary, fontSize = 14.sp)
                }
                Text(stringResource(R.string.done), color = IOSColors.Blue, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(onClick = onDismiss))
            }
            Spacer(Modifier.height(14.dp))
            Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(14.dp), tint = Color.White.copy(alpha = 0.08f))) {
                SheetRow(Icons.Outlined.Photo, stringResource(R.string.icon_from_gallery)) { onPickPhoto() }
                HorizontalDivider(color = IOSColors.Separator, thickness = 0.5.dp)
                SheetRow(Icons.Outlined.RestartAlt, stringResource(R.string.icon_reset)) { vm.resetIcon(entry); onDismiss() }
            }
            Spacer(Modifier.height(14.dp))
            if (packs.isEmpty()) {
                Text(stringResource(R.string.icon_no_pack), color = IOSColors.Secondary, fontSize = 14.sp)
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(packs, key = { it.packageName }) { p ->
                        val selected = p.packageName == pack
                        Text(
                            p.label, color = if (selected) Color.Black else Color.White, fontSize = 14.sp,
                            modifier = Modifier
                                .background(if (selected) Color.White else Color.White.copy(alpha = 0.12f), RoundedCornerShape(50))
                                .clickable { pack = p.packageName }
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                SearchField(filter, { filter = it }, stringResource(R.string.search_hint), Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                LazyVerticalGrid(GridCells.Adaptive(64.dp), contentPadding = PaddingValues(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(shown, key = { it }) { name ->
                        val img by produceState<ImageBitmap?>(null, pack, name) {
                            value = withContext(Dispatchers.IO) { pack?.let { p -> loader.packs.drawable(p, name)?.let { loader.renderPlain(it) } } }
                        }
                        Box(
                            Modifier.aspectRatio(1f).clickable { pack?.let { vm.setPackIcon(entry, it, name) }; onDismiss() }.padding(6.dp),
                            contentAlignment = Alignment.Center,
                        ) { img?.let { Image(it, name, Modifier.fillMaxSize()) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = IOSColors.Blue, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, color = Color.White, fontSize = 16.sp)
    }
}

