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
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import com.mrios.launcher.data.IconStyle
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

/** Icon style + tint, provided at the root of every screen that draws icons. */
data class IconAppearance(val style: IconStyle = IconStyle.GLASS, val tint: Color = Color(0xFFFFD27A))

val LocalIconAppearance = compositionLocalOf { IconAppearance() }

fun com.mrios.launcher.data.Settings.iconAppearance() =
    IconAppearance(iconStyle, Color(com.mrios.launcher.data.IconTints[iconTint.coerceIn(0, com.mrios.launcher.data.IconTints.lastIndex)].second))

@Composable
fun rememberAppGlyph(entry: AppEntry): ImageBitmap? {
    val loader = LocalContext.current.launcherApp.icons
    val version by loader.version.collectAsState()
    val state by produceState(loader.cachedGlyph(entry), entry.id, version) { value = loader.loadGlyph(entry) }
    return state
}

/**
 * Liquid-glass finish drawn over an icon: a specular sheen from the top, a soft lens highlight
 * and a bright rim, all clipped to the squircle.
 */
fun Modifier.glassIconFinish(strength: Float = 1f): Modifier = drawWithCache {
    val path = (SquircleShape.createOutline(size, layoutDirection, this) as Outline.Generic).path
    val sheen = Brush.verticalGradient(
        0f to Color.White.copy(alpha = 0.42f * strength),
        0.36f to Color.White.copy(alpha = 0.10f * strength),
        0.52f to Color.Transparent,
        1f to Color.Black.copy(alpha = 0.16f * strength),
    )
    val lens = Brush.radialGradient(
        listOf(Color.White.copy(alpha = 0.38f * strength), Color.Transparent),
        center = Offset(size.width * 0.30f, size.height * 0.16f),
        radius = size.width * 0.42f,
    )
    val rim = Brush.linearGradient(
        listOf(Color.White.copy(alpha = 0.90f * strength), Color.White.copy(alpha = 0.12f * strength), Color.White.copy(alpha = 0.55f * strength)),
        start = Offset.Zero, end = Offset(size.width, size.height),
    )
    val stroke = Stroke(width = size.minDimension * 0.024f)
    onDrawWithContent {
        drawContent()
        clipPath(path) {
            drawRect(sheen)
            drawRect(lens)
        }
        drawPath(path, rim, style = stroke)
    }
}

@Composable
fun AppIcon(entry: AppEntry, size: Dp, modifier: Modifier = Modifier) {
    val look = LocalIconAppearance.current
    when (look.style) {
        IconStyle.ORIGINAL, IconStyle.GLASS -> {
            val bmp = rememberAppIcon(entry)
            Box(modifier.size(size).let { if (look.style == IconStyle.GLASS) it.glassIconFinish() else it }) {
                if (bmp != null) Image(bmp, contentDescription = entry.label, modifier = Modifier.fillMaxSize(), filterQuality = FilterQuality.Medium)
                else Box(Modifier.fillMaxSize().clip(SquircleShape).background(Color.White.copy(alpha = 0.2f)))
            }
        }
        IconStyle.CLEAR, IconStyle.TINTED -> {
            val glyph = rememberAppGlyph(entry)
            val clear = look.style == IconStyle.CLEAR
            Box(
                modifier
                    .size(size)
                    .glass(SquircleShape, tint = if (clear) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.38f), rim = 0.6f)
                    .glassIconFinish(if (clear) 0.8f else 0.55f),
            ) {
                if (glyph != null) Image(
                    glyph, contentDescription = entry.label,
                    modifier = Modifier.fillMaxSize(),
                    filterQuality = FilterQuality.Medium,
                    colorFilter = ColorFilter.tint(if (clear) Color.White.copy(alpha = 0.95f) else look.tint),
                )
            }
        }
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
