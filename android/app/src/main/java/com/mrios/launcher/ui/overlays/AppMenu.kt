package com.mrios.launcher.ui.overlays

import android.content.pm.ShortcutInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrios.launcher.R
import com.mrios.launcher.data.AppEntry
import com.mrios.launcher.launcherApp
import com.mrios.launcher.ui.IOSColors
import com.mrios.launcher.ui.LauncherViewModel
import com.mrios.launcher.ui.components.AppIcon
import com.mrios.launcher.ui.components.FrostedBackdrop
import com.mrios.launcher.ui.components.glass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

data class MenuItem(
    val label: String,
    val icon: ImageVector? = null,
    val image: ImageBitmap? = null,
    val destructive: Boolean = false,
    val separatorBefore: Boolean = false,
    val onClick: () -> Unit,
)

/** Places [content] next to [anchor] (below if it fits, else above), clamped to the screen. */
@Composable
fun AnchoredMenu(anchor: Rect, onDismiss: () -> Unit, highlight: @Composable () -> Unit = {}, items: List<MenuItem>) {
    val density = LocalDensity.current
    Box(Modifier.fillMaxSize()) {
        FrostedBackdrop(true, modifier = Modifier.noRippleClick(onDismiss))
        highlight()
        Layout(
            content = {
                Column(
                    Modifier
                        .popIn(0.6f)
                        .width(250.dp)
                        .glass(RoundedCornerShape(16.dp), tint = DialogTint)
                ) {
                    items.forEachIndexed { i, item ->
                        if (i > 0) HorizontalDivider(
                            color = if (item.separatorBefore) Color.Black.copy(alpha = 0.25f) else IOSColors.Separator,
                            thickness = if (item.separatorBefore) 6.dp else 0.5.dp,
                        )
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onDismiss(); item.onClick() }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            val color = if (item.destructive) IOSColors.Red else Color.White
                            Text(item.label, color = color, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            when {
                                item.image != null -> Image(item.image, null, Modifier.size(22.dp))
                                item.icon != null -> Icon(item.icon, null, tint = color, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) { measurables, constraints ->
            val p = measurables.first().measure(Constraints(maxWidth = constraints.maxWidth, maxHeight = constraints.maxHeight))
            layout(constraints.maxWidth, constraints.maxHeight) {
                val margin = with(density) { 14.dp.roundToPx() }
                val gap = with(density) { 12.dp.roundToPx() }
                val safeTop = with(density) { 48.dp.roundToPx() }
                val safeBottom = constraints.maxHeight - with(density) { 40.dp.roundToPx() }
                val x = anchor.left.roundToInt().coerceIn(margin, (constraints.maxWidth - p.width - margin).coerceAtLeast(margin))
                val below = anchor.bottom.roundToInt() + gap
                val y = if (below + p.height < safeBottom) below
                else (anchor.top.roundToInt() - gap - p.height).coerceAtLeast(safeTop)
                p.place(x, y)
            }
        }
    }
}

@Composable
fun AppMenuOverlay(
    vm: LauncherViewModel,
    entry: AppEntry,
    bounds: Rect,
    onHome: Boolean,
    fromHidden: Boolean,
    onDismiss: () -> Unit,
    onEditHome: () -> Unit,
    onRename: () -> Unit,
    onChangeIcon: () -> Unit,
    onRemove: () -> Unit,
    onShortcut: (ShortcutInfo) -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val icons = context.launcherApp.icons
    val shortcuts by produceState(emptyList<Pair<ShortcutInfo, ImageBitmap?>>(), entry.id) {
        value = withContext(Dispatchers.IO) {
            vm.shortcuts(entry).map { s -> s to vm.shortcutIcon(s)?.let { icons.renderPlain(it) } }
        }
    }
    val items = buildList {
        shortcuts.forEach { (s, img) ->
            add(MenuItem((s.shortLabel ?: s.longLabel ?: "").toString(), image = img, icon = Icons.Outlined.Apps) { onShortcut(s) })
        }
        if (onHome) add(MenuItem(stringResource(R.string.menu_edit_home), Icons.Outlined.Apps, separatorBefore = shortcuts.isNotEmpty()) { onEditHome() })
        add(MenuItem(stringResource(R.string.menu_rename), Icons.Outlined.Edit, separatorBefore = !onHome && shortcuts.isNotEmpty()) { onRename() })
        add(MenuItem(stringResource(R.string.menu_change_icon), Icons.Outlined.Image) { onChangeIcon() })
        if (fromHidden) add(MenuItem(stringResource(R.string.menu_unhide), Icons.Outlined.Visibility) { vm.setHidden(entry, false) })
        else add(MenuItem(stringResource(R.string.menu_hide), Icons.Outlined.VisibilityOff) { vm.setHidden(entry, true) })
        if (!fromHidden) {
            if (onHome) add(MenuItem(stringResource(R.string.menu_remove_home), Icons.Outlined.RemoveCircleOutline) { onRemove() })
            else add(MenuItem(stringResource(R.string.menu_add_home), Icons.Outlined.AddBox) { vm.addToHome(entry) })
        }
        add(MenuItem(stringResource(R.string.menu_app_info), Icons.Outlined.Info) { vm.appInfo(entry, null) })
        if (!entry.isSystem) add(MenuItem(stringResource(R.string.menu_delete_app), Icons.Outlined.Delete, destructive = true) { vm.uninstall(entry) })
    }
    AnchoredMenu(
        anchor = bounds,
        onDismiss = onDismiss,
        highlight = {
            val size = with(density) { bounds.width.toDp() }
            AppIcon(
                entry, size,
                Modifier
                    .offset { IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()) }
                    .popIn(0.9f),
            )
        },
        items = items,
    )
}

@Composable
fun PageMenuOverlay(at: androidx.compose.ui.geometry.Offset, onDismiss: () -> Unit, onEdit: () -> Unit, onWallpaper: () -> Unit, onSettings: () -> Unit) {
    AnchoredMenu(
        anchor = Rect(at, at),
        onDismiss = onDismiss,
        items = listOf(
            MenuItem(stringResource(R.string.menu_edit_home), Icons.Outlined.Apps) { onEdit() },
            MenuItem(stringResource(R.string.menu_wallpaper), Icons.Outlined.Wallpaper) { onWallpaper() },
            MenuItem(stringResource(R.string.menu_settings), Icons.Outlined.Settings) { onSettings() },
        ),
    )
}

