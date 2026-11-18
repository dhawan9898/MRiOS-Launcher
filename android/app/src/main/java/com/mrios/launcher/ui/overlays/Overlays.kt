package com.mrios.launcher.ui.overlays

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrios.launcher.data.AppEntry
import com.mrios.launcher.ui.IOSColors
import com.mrios.launcher.ui.LibraryGroup
import com.mrios.launcher.ui.components.FrostedBackdrop
import com.mrios.launcher.ui.components.glass

enum class PinMode { CREATE, VERIFY }

sealed interface Overlay {
    data object Search : Overlay
    data class Folder(val groupId: String) : Overlay
    data object HiddenFolder : Overlay
    data class AppMenu(val entry: AppEntry, val bounds: Rect, val fromHidden: Boolean = false) : Overlay
    data class PageMenu(val at: Offset) : Overlay
    data class RemoveConfirm(val entry: AppEntry) : Overlay
    data class Rename(val entry: AppEntry) : Overlay
    data class IconPicker(val entry: AppEntry) : Overlay
    data object Settings : Overlay
    data object WeatherSettings : Overlay
    data class WallpaperPreview(val bitmap: Bitmap) : Overlay
    data class Pin(val mode: PinMode) : Overlay
    data object Onboarding : Overlay
}

/** Spring "pop" entrance used by all overlays. */
@Composable
fun Modifier.popIn(from: Float = 0.86f): Modifier {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = 420f)) }
    return graphicsLayer {
        val v = a.value
        alpha = v.coerceIn(0f, 1f)
        val s = from + (1f - from) * v
        scaleX = s; scaleY = s
    }
}

fun Modifier.noRippleClick(onClick: () -> Unit): Modifier = this.then(
    Modifier.clickable(interactionSource = MutableInteractionSource(), indication = null, onClick = onClick)
)

enum class ButtonRole { DEFAULT, PRIMARY, CANCEL, DESTRUCTIVE }
data class DialogButton(val text: String, val role: ButtonRole = ButtonRole.DEFAULT, val onClick: () -> Unit)

val DialogTint = Color(0xFF1C1C1E).copy(alpha = 0.45f)

/** iOS-style alert with stacked buttons, rendered on frosted glass. */
@Composable
fun GlassDialog(
    title: String,
    message: String? = null,
    onDismiss: () -> Unit,
    buttons: List<DialogButton>,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Box(Modifier.fillMaxSize()) {
        FrostedBackdrop(true, dim = 0.35f, modifier = Modifier.noRippleClick(onDismiss))
        Column(
            Modifier
                .align(Alignment.Center)
                .imePadding()
                .popIn(1.15f)
                .width(290.dp)
                .glass(RoundedCornerShape(22.dp), tint = DialogTint)
                .noRippleClick { },
        ) {
            Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, textAlign = TextAlign.Center)
                if (message != null) Text(
                    message, color = IOSColors.Secondary, fontSize = 13.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
                content?.let { Column(Modifier.fillMaxWidth().padding(top = 12.dp), content = it) }
            }
            buttons.forEach { b ->
                HorizontalDivider(color = IOSColors.Separator, thickness = 0.5.dp)
                Box(
                    Modifier.fillMaxWidth().height(46.dp).clickable(onClick = b.onClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        b.text,
                        color = if (b.role == ButtonRole.DESTRUCTIVE) IOSColors.Red else IOSColors.Blue,
                        fontWeight = if (b.role == ButtonRole.PRIMARY || b.role == ButtonRole.CANCEL) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 17.sp,
                    )
                }
            }
        }
    }
}

/** Groups referenced by [Overlay.Folder] are looked up live so folders refresh after edits. */
fun List<LibraryGroup>.byId(id: String) = firstOrNull { it.id == id }

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp,
        modifier = modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp),
    )
}

@Composable
fun Pill(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.glass(RoundedCornerShape(50), tint = Color.White.copy(alpha = 0.16f)).background(Color.Transparent)) { content() }
}
