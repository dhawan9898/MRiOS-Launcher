package com.mrios.launcher.ui.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.mrios.launcher.R
import com.mrios.launcher.data.AppEntry
import com.mrios.launcher.data.Settings
import com.mrios.launcher.data.WeatherState
import com.mrios.launcher.ui.IOSColors
import com.mrios.launcher.ui.components.AppTile
import com.mrios.launcher.ui.components.glass
import com.mrios.launcher.ui.widgets.SmallWidget
import com.mrios.launcher.ui.widgets.WidgetActions

data class GridGeom(val rect: Rect, val cellW: Float, val cellH: Float, val cols: Int)

/** Callbacks shared by every icon on the home screen and dock. */
class TileCallbacks(
    val onTap: (AppEntry, Rect) -> Unit,
    val onLongPress: (AppEntry, Rect) -> Unit,
    val onDragStart: (AppEntry, Offset, Boolean) -> Unit,
    val onDrag: (Offset) -> Unit,
    val onDragEnd: () -> Unit,
    val onRemove: (AppEntry) -> Unit,
)

fun iconSizeFor(cellW: Dp, cellH: Dp): Dp = min(62.dp, min(cellW * 0.74f, cellH * 0.6f))

@Composable
fun HomePage(
    pageIndex: Int,
    items: List<AppEntry>,
    settings: Settings,
    weather: WeatherState,
    widgetActions: WidgetActions,
    badges: Map<String, Int>,
    editMode: Boolean,
    draggingId: String?,
    topPadding: Dp,
    bottomPadding: Dp,
    callbacks: TileCallbacks,
    onGeom: (Int, GridGeom) -> Unit,
    onEmptyTap: () -> Unit,
    onEmptyLongPress: (Offset) -> Unit,
) {
    val density = LocalDensity.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInRoot() }
            .pointerInput(editMode) {
                detectTapGestures(onTap = { onEmptyTap() }, onLongPress = { onEmptyLongPress(origin + it) })
            }
            .padding(top = topPadding + 8.dp, bottom = bottomPadding, start = 6.dp, end = 6.dp),
    ) {
        val cols = settings.columns
        val rows = settings.rows
        val cellW = maxWidth / cols
        val cellH = maxHeight / rows
        val widgetRows = if (pageIndex == 0) settings.widgetRows else 0
        val iconSize = iconSizeFor(cellW, cellH)
        Column(Modifier.fillMaxSize()) {
            if (widgetRows > 0) {
                Row(Modifier.fillMaxWidth().height(cellH * widgetRows)) {
                    settings.homeWidgets.take(2).forEach { type ->
                        Box(Modifier.width(cellW * 2).fillMaxHeight().padding(horizontal = 10.dp, vertical = 6.dp)) {
                            SmallWidget(type, weather, widgetActions, Modifier.fillMaxSize())
                        }
                    }
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .onGloballyPositioned {
                        onGeom(pageIndex, GridGeom(it.boundsInRoot(), with(density) { cellW.toPx() }, with(density) { cellH.toPx() }, cols))
                    },
            ) {
                items.forEachIndexed { i, app ->
                    val col = i % cols
                    val row = i / cols
                    Box(
                        Modifier.offset(x = cellW * col, y = cellH * row).size(cellW, cellH),
                        contentAlignment = Alignment.Center,
                    ) {
                        AppTile(
                            entry = app,
                            iconSize = iconSize,
                            showLabel = settings.showLabels,
                            badge = badges[app.packageName] ?: 0,
                            editMode = editMode,
                            hiddenWhileDragging = draggingId == app.id,
                            onTap = { callbacks.onTap(app, it) },
                            onLongPress = { callbacks.onLongPress(app, it) },
                            onDragStart = { callbacks.onDragStart(app, it, false) },
                            onDrag = callbacks.onDrag,
                            onDragEnd = callbacks.onDragEnd,
                            onRemove = { callbacks.onRemove(app) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun Dock(
    apps: List<AppEntry>,
    iconSize: Dp,
    badges: Map<String, Int>,
    editMode: Boolean,
    draggingId: String?,
    callbacks: TileCallbacks,
    onBounds: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .height(iconSize + 34.dp)
            .onGloballyPositioned { onBounds(it.boundsInRoot()) }
            .glass(RoundedCornerShape(36.dp), tint = Color.White.copy(alpha = 0.18f)),
    ) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            apps.forEach { app ->
                AppTile(
                    entry = app,
                    iconSize = iconSize,
                    showLabel = false,
                    badge = badges[app.packageName] ?: 0,
                    editMode = editMode,
                    hiddenWhileDragging = draggingId == app.id,
                    onTap = { callbacks.onTap(app, it) },
                    onLongPress = { callbacks.onLongPress(app, it) },
                    onDragStart = { callbacks.onDragStart(app, it, true) },
                    onDrag = callbacks.onDrag,
                    onDragEnd = callbacks.onDragEnd,
                    onRemove = { callbacks.onRemove(app) },
                )
            }
        }
    }
}

/** Page dots inside a small glass pill (or "Search" when there is one page). Tapping opens search. */
@Composable
fun PageIndicator(count: Int, current: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .glass(RoundedCornerShape(50), tint = Color.White.copy(alpha = 0.14f), rim = 0.25f)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (count <= 1) {
            Icon(Icons.Filled.Search, null, tint = Color.White, modifier = Modifier.size(14.dp))
            Text(stringResource(R.string.search_hint), color = Color.White, fontSize = 13.sp)
        } else {
            Icon(Icons.Filled.Search, null, tint = IOSColors.Secondary, modifier = Modifier.size(12.dp))
            repeat(count) { i ->
                Box(Modifier.size(7.dp).clip(CircleShape).background(if (i == current) Color.White else Color.White.copy(alpha = 0.35f)))
            }
        }
    }
}

@Composable
fun DoneButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        stringResource(R.string.done), color = Color.White, fontSize = 15.sp,
        modifier = modifier.glass(RoundedCornerShape(50), tint = Color.White.copy(alpha = 0.22f)).clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
    )
}

