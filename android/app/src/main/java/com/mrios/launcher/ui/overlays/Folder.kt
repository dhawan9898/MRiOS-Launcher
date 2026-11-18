package com.mrios.launcher.ui.overlays

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrios.launcher.data.AppEntry
import com.mrios.launcher.ui.IOSColors
import com.mrios.launcher.ui.components.AppTile
import com.mrios.launcher.ui.components.FrostedBackdrop
import com.mrios.launcher.ui.components.glass

/** An expanded App Library category (or the hidden folder). */
@Composable
fun FolderOverlay(
    title: String,
    apps: List<AppEntry>,
    badges: Map<String, Int>,
    showLabels: Boolean,
    emptyText: String? = null,
    onLaunch: (AppEntry, Rect) -> Unit,
    onMenu: (AppEntry, Rect) -> Unit,
    onDismiss: () -> Unit,
) {
    val screenH = LocalConfiguration.current.screenHeightDp
    Box(Modifier.fillMaxSize()) {
        FrostedBackdrop(true, dim = 0.2f, modifier = Modifier.noRippleClick(onDismiss))
        Column(
            Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 22.dp).popIn(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 18.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp, max = (screenH * 0.62f).dp)
                    .glass(RoundedCornerShape(38.dp), tint = Color.White.copy(alpha = 0.16f))
                    .noRippleClick { },
            ) {
                if (apps.isEmpty() && emptyText != null) {
                    Text(emptyText, color = IOSColors.Secondary, fontSize = 15.sp, modifier = Modifier.align(Alignment.Center).padding(24.dp))
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        contentPadding = PaddingValues(vertical = 22.dp, horizontal = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        items(apps, key = { it.id }) { app ->
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                AppTile(
                                    entry = app, iconSize = 58.dp, showLabel = showLabels,
                                    badge = badges[app.packageName] ?: 0,
                                    onTap = { onLaunch(app, it) },
                                    onLongPress = { onMenu(app, it) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
