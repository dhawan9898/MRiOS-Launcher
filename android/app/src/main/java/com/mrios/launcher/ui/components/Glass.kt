package com.mrios.launcher.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.mrios.launcher.data.ThemeMode
import com.mrios.launcher.data.WallpaperImages
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin

data class GlassContext(val mode: ThemeMode, val wallpaper: WallpaperImages?, val rootSize: IntSize)

val LocalGlass = compositionLocalOf { GlassContext(ThemeMode.GLASS, null, IntSize.Zero) }

/** Draws [img] the way the root wallpaper is drawn (centre-crop to the root), shifted by -[offset]. */
fun DrawScope.drawWallpaper(img: ImageBitmap, root: IntSize, offset: Offset = Offset.Zero, alpha: Float = 1f) {
    if (root.width == 0 || root.height == 0) return
    val scale = max(root.width / img.width.toFloat(), root.height / img.height.toFloat())
    val w = img.width * scale
    val h = img.height * scale
    drawImage(
        img,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(img.width, img.height),
        dstOffset = IntOffset(((root.width - w) / 2 - offset.x).roundToInt(), ((root.height - h) / 2 - offset.y).roundToInt()),
        dstSize = IntSize(w.roundToInt(), h.roundToInt()),
        alpha = alpha,
        filterQuality = FilterQuality.Low,
    )
}

/**
 * Liquid-glass material: the blurred wallpaper that sits behind this element,
 * a light tint, and a specular rim. In Transparent theme it is just a clear tint.
 */
@Composable
fun Modifier.glass(
    shape: Shape,
    tint: Color = Color.White.copy(alpha = 0.14f),
    rim: Float = 0.45f,
): Modifier {
    val g = LocalGlass.current
    var pos by remember { mutableStateOf(Offset.Zero) }
    val wp = g.wallpaper
    val glassy = g.mode == ThemeMode.GLASS && wp != null
    val effectiveTint = if (glassy) tint else tint.copy(alpha = (tint.alpha * 0.9f).coerceAtMost(0.5f))
    return this
        .onGloballyPositioned { pos = it.positionInRoot() }
        .clip(shape)
        .drawBehind {
            if (glassy) drawWallpaper(wp!!.blurred, g.rootSize, pos)
            // Transparent theme sits over an unknown system wallpaper: darken a little so white text stays legible.
            else drawRect(Color.Black.copy(alpha = 0.22f))
            drawRect(effectiveTint)
            // soft top highlight
            drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.10f), 0.45f to Color.Transparent))
        }
        .border(
            0.8.dp,
            Brush.linearGradient(
                listOf(Color.White.copy(alpha = rim), Color.White.copy(alpha = 0.05f), Color.White.copy(alpha = rim * 0.6f)),
            ),
            shape,
        )
}

/** Full-screen frosted backdrop used behind folders, menus, search and dialogs. */
@Composable
fun FrostedBackdrop(visible: Boolean, dim: Float = 0.25f, modifier: Modifier = Modifier, cover: Boolean = false) {
    val g = LocalGlass.current
    // Android 12+ blurs the live home screen behind overlays, so only a tint is needed there;
    // older versions cover it with the pre-blurred wallpaper instead.
    val drawWallpaper = cover || android.os.Build.VERSION.SDK_INT < 31
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val wp = g.wallpaper
                    if (drawWallpaper && g.mode == ThemeMode.GLASS && wp != null) drawWallpaper(wp.blurred, g.rootSize)
                }
                .background(Color.Black.copy(alpha = when { g.mode == ThemeMode.GLASS && drawWallpaper -> dim; g.mode == ThemeMode.GLASS -> dim + 0.15f; else -> (dim + 0.3f).coerceAtMost(0.75f) }))
        )
    }
}

/** iOS-style continuous-corner squircle. */
object SquircleShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        val rx = size.width / 2f
        val ry = size.height / 2f
        val steps = 72
        for (i in 0..steps) {
            val t = 2.0 * Math.PI * i / steps
            val c = cos(t); val s = sin(t)
            val x = rx + rx * (sign(c) * abs(c).pow(0.4)).toFloat()
            val y = ry + ry * (sign(s) * abs(s).pow(0.4)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

fun Dp.half() = this / 2
