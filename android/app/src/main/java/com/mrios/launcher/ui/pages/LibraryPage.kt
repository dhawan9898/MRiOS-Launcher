package com.mrios.launcher.ui.pages

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrios.launcher.R
import com.mrios.launcher.data.AppEntry
import com.mrios.launcher.data.FuzzySearch
import com.mrios.launcher.ui.GroupKind
import com.mrios.launcher.ui.IOSColors
import com.mrios.launcher.ui.LibraryGroup
import com.mrios.launcher.ui.components.AppIcon
import com.mrios.launcher.ui.components.Badge
import com.mrios.launcher.ui.components.glass
import com.mrios.launcher.ui.components.iconGestures
import com.mrios.launcher.ui.components.rememberAppIcon
import com.mrios.launcher.ui.overlays.SearchField

private val TileShape = RoundedCornerShape(28.dp)

@Composable
private fun LibraryIcon(app: AppEntry, size: Dp, badge: Int, onLaunch: (AppEntry, Rect) -> Unit, onMenu: (AppEntry, Rect) -> Unit) {
    val haptics = LocalHapticFeedback.current
    var bounds by remember { mutableStateOf(Rect.Zero) }
    Box(
        Modifier
            .size(size)
            .onGloballyPositioned { bounds = it.boundsInRoot() }
            .iconGestures(app.id, onTap = { onLaunch(app, bounds) }, onLongPress = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress); onMenu(app, bounds)
            }),
    ) {
        AppIcon(app, size)
        Badge(badge, Modifier.align(Alignment.TopEnd).padding(0.dp))
    }
}

@Composable
private fun MiniCluster(apps: List<AppEntry>, size: Dp, onClick: () -> Unit) {
    val mini = (size - 6.dp) / 2
    Column(Modifier.size(size).clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        apps.take(4).chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { app ->
                    val bmp = rememberAppIcon(app)
                    if (bmp != null) Image(bmp, null, Modifier.size(mini)) else Spacer(Modifier.size(mini))
                }
            }
        }
    }
}

@Composable
private fun GroupTile(
    group: LibraryGroup, badges: Map<String, Int>,
    onLaunch: (AppEntry, Rect) -> Unit, onMenu: (AppEntry, Rect) -> Unit, onOpen: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .glass(TileShape, tint = Color.White.copy(alpha = 0.13f))
                .clickable(onClick = onOpen)
                .padding(14.dp),
        ) {
            val gap = 12.dp
            val cell = (maxWidth - gap) / 2
            val apps = group.apps
            val fourBig = apps.size <= 4 || group.kind == GroupKind.SUGGESTIONS
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                for (r in 0 until 2) Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    for (c in 0 until 2) {
                        val i = r * 2 + c
                        when {
                            i == 3 && !fourBig -> MiniCluster(apps.drop(3), cell, onOpen)
                            i < apps.size -> LibraryIcon(apps[i], cell, badges[apps[i].packageName] ?: 0, onLaunch, onMenu)
                            else -> Spacer(Modifier.size(cell))
                        }
                    }
                }
            }
        }
        Text(
            stringResource(group.title), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun HiddenTile(onOpen: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).glass(TileShape, tint = Color.White.copy(alpha = 0.10f)).clickable(onClick = onOpen),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Lock, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(44.dp)) }
        Text(stringResource(R.string.cat_hidden), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 6.dp))
    }
}

/** iOS-style App Library: auto-categorised folders plus an A–Z search list. */
@Composable
fun LibraryPage(
    groups: List<LibraryGroup>,
    apps: List<AppEntry>,
    badges: Map<String, Int>,
    topPadding: Dp,
    bottomPadding: Dp,
    onLaunch: (AppEntry, Rect) -> Unit,
    onMenu: (AppEntry, Rect) -> Unit,
    onOpenGroup: (LibraryGroup) -> Unit,
    onOpenHidden: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    Column(Modifier.fillMaxSize().padding(top = topPadding + 10.dp)) {
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            SearchField(
                query, { query = it }, stringResource(R.string.library_title),
                Modifier.weight(1f).onFocusChanged { if (it.isFocused) searching = true },
            )
            if (searching) Text(
                stringResource(R.string.cancel), color = IOSColors.Blue, fontSize = 17.sp,
                modifier = Modifier.padding(start = 12.dp).clickable { query = ""; searching = false; focusManager.clearFocus() },
            )
        }
        Spacer(Modifier.height(14.dp))
        if (searching) {
            val list = if (query.isBlank()) apps else FuzzySearch.filter(query, apps) { it.label }
            LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = bottomPadding + 20.dp)) {
                var lastLetter: Char? = null
                list.forEach { app ->
                    val letter = app.label.firstOrNull()?.uppercaseChar()?.takeIf { it.isLetter() } ?: '#'
                    if (query.isBlank() && letter != lastLetter) {
                        lastLetter = letter
                        item(key = "h_$letter") { Text(letter.toString(), color = IOSColors.Secondary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)) }
                    }
                    item(key = app.id) {
                        var bounds by remember { mutableStateOf(Rect.Zero) }
                        Row(
                            Modifier.fillMaxWidth()
                                .onGloballyPositioned { bounds = it.boundsInRoot() }
                                .iconGestures(app.id, onTap = { onLaunch(app, bounds) }, onLongPress = { onMenu(app, bounds) })
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppIcon(app, 40.dp)
                            Spacer(Modifier.width(14.dp))
                            Text(app.label, color = Color.White, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = bottomPadding + 24.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Position-based (no keys) so the grid stays at the top when Suggestions appears.
                items(groups) { g -> GroupTile(g, badges, onLaunch, onMenu) { onOpenGroup(g) } }
                item { HiddenTile(onOpenHidden) }
            }
        }
    }
}
