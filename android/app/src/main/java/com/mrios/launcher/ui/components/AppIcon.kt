package com.mrios.launcher.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrios.launcher.data.AppEntry
import com.mrios.launcher.launcherApp
import com.mrios.launcher.ui.IOSColors

@Composable
fun rememberAppIcon(entry: AppEntry): ImageBitmap? {
    val loader = LocalContext.current.launcherApp.icons
    val version by loader.version.collectAsState()
    val state by produceState(loader.cached(entry), entry.id, version) { value = loader.load(entry) }
    return state
}

@Composable
fun AppIcon(entry: AppEntry, size: Dp, modifier: Modifier = Modifier) {
    val bmp = rememberAppIcon(entry)
    Box(modifier.size(size)) {
        if (bmp != null) Image(bmp, contentDescription = entry.label, modifier = Modifier.fillMaxSize(), filterQuality = FilterQuality.Medium)
        else Box(Modifier.fillMaxSize().clip(SquircleShape).background(Color.White.copy(alpha = 0.2f)))
    }
}

val LabelStyle = TextStyle(
    color = Color.White,
    fontSize = 11.5.sp,
    fontWeight = FontWeight.Medium,
    textAlign = TextAlign.Center,
    shadow = Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, 1f), 4f),
)

@Composable
fun Badge(count: Int, modifier: Modifier = Modifier) {
    if (count <= 0) return
    Box(
        modifier
            .sizeIn(minWidth = 22.dp, minHeight = 22.dp)
            .clip(CircleShape)
            .background(IOSColors.Red)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (count > 99) "99+" else count.toString(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * An app on the home screen / folder: icon, name, badge, jiggle in edit mode,
 * and iOS gestures (tap / hold for menu / hold-and-drag to move).
 */
@Composable
fun AppTile(
    entry: AppEntry,
    iconSize: Dp,
    showLabel: Boolean,
    badge: Int = 0,
    editMode: Boolean = false,
    hiddenWhileDragging: Boolean = false,
    onTap: (Rect) -> Unit,
    onLongPress: (Rect) -> Unit,
    onDragStart: ((Offset) -> Unit)? = null,
    onDrag: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onRemove: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    var bounds by remember { mutableStateOf(Rect.Zero) }
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, label = "press")

    val rotation = if (editMode) {
        val phase = remember(entry.id) { (entry.id.hashCode() and 0xFF) % 3 }
        val t = rememberInfiniteTransition(label = "jiggle")
        t.animateFloat(
            initialValue = -1.6f, targetValue = 1.6f,
            animationSpec = infiniteRepeatable(tween(120 + phase * 15, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "rot",
        ).value
    } else 0f

    Column(
        modifier
            .graphicsLayer { alpha = if (hiddenWhileDragging) 0f else 1f }
            .width(iconSize + 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(iconSize)
                .onGloballyPositioned { bounds = it.boundsInRoot() }
                .graphicsLayer { rotationZ = rotation; scaleX = scale; scaleY = scale }
                .iconGestures(
                    key = entry.id,
                    onTap = { if (!editMode) onTap(bounds) },
                    onLongPress = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (!editMode) onLongPress(bounds)
                    },
                    onDragStart = { local -> onDragStart?.invoke(bounds.topLeft + local) },
                    onDrag = { if (onDragStart != null) onDrag(it) },
                    onDragEnd = { if (onDragStart != null) onDragEnd() },
                ),
        ) {
            AppIcon(entry, iconSize)
            Badge(badge, Modifier.align(Alignment.TopEnd).offset(x = 7.dp, y = (-7).dp))
            if (editMode && onRemove != null) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .offset(x = (-7).dp, y = (-7).dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color(0xE6555559))
                        .clickable(remember { MutableInteractionSource() }, null) { onRemove() },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Remove, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
            }
        }
        if (showLabel) Text(
            entry.label,
            style = LabelStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 5.dp),
        )
    }
}
