package com.mrios.launcher.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange

/**
 * Tap, long-press, and press-and-drag in one recogniser, like iOS home-screen icons:
 * tap → [onTap]; hold → [onLongPress]; keep holding and move → [onDragStart]/[onDrag]/[onDragEnd].
 * A quick swipe is not consumed, so the pager can still scroll.
 *
 * Callbacks are read through rememberUpdatedState so that state changes made *during* a gesture
 * (e.g. entering edit mode) never restart the recogniser and drop the drag.
 */
@Composable
fun Modifier.iconGestures(
    key: Any?,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onDragStart: (Offset) -> Unit = {},
    onDrag: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onLongPressRelease: () -> Unit = {},
): Modifier {
    val tap by rememberUpdatedState(onTap)
    val longPress by rememberUpdatedState(onLongPress)
    val dragStart by rememberUpdatedState(onDragStart)
    val drag by rememberUpdatedState(onDrag)
    val dragEnd by rememberUpdatedState(onDragEnd)
    val release by rememberUpdatedState(onLongPressRelease)
    return pointerInput(key) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            down.consume()
            var cancelled = false
            val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                waitForUpOrCancellation().also { if (it == null) cancelled = true }
            }
            if (up != null) { up.consume(); tap(); return@awaitEachGesture }
            if (cancelled) return@awaitEachGesture

            longPress()
            var dragging = false
            var total = Offset.Zero
            try {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) { change.consume(); break }
                    val delta = change.positionChange()
                    if (delta != Offset.Zero) {
                        total += delta
                        if (!dragging && total.getDistance() > viewConfiguration.touchSlop) {
                            dragging = true
                            dragStart(change.position)
                        } else if (dragging) drag(delta)
                        change.consume()
                    }
                }
            } finally {
                // Also runs if the gesture is cancelled, so a drag can never get stuck.
                if (dragging) dragEnd() else release()
            }
        }
    }
}
