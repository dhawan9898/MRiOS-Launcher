package com.mrios.launcher.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import com.mrios.launcher.badges.BadgeListenerService
import com.mrios.launcher.launcherApp
import com.mrios.launcher.system.ControlResult
import com.mrios.launcher.system.SystemActions
import com.mrios.launcher.ui.overlays.noRippleClick

enum class PanelKind { CONTROL_CENTER, NOTIFICATION_CENTER }

/**
 * Control Centre or Notification Centre, wired to the real system state. Hosted by the launcher
 * (on the home screen) and by [com.mrios.launcher.system.PanelService] (over any app).
 * Tap empty space or swipe up to close.
 */
@Composable
fun SystemPanel(kind: PanelKind, insets: PaddingValues, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val la = context.launcherApp
    val controls = la.controls
    val media = la.media
    DisposableEffect(Unit) {
        controls.start(); media.start()
        onDispose { controls.stop(); media.stop() }
    }
    val state by controls.state.collectAsState()
    val np by media.state.collectAsState()
    val items by BadgeListenerService.items.collectAsState()
    val apps by la.apps.apps.collectAsState()
    val hidden by la.layout.state.collectAsState()
    val hasAccess = remember(kind) { BadgeListenerService.isEnabled(context) }
    val byPackage = remember(apps) { apps.groupBy { it.packageName }.mapValues { it.value.first() } }
    val close by rememberUpdatedState(onClose)

    val run: (() -> ControlResult) -> Unit = { f -> if (f() == ControlResult.OPENED) close() }
    val ccActions = ControlActions(
        run = run,
        wifi = controls::toggleWifi, bluetooth = controls::toggleBluetooth,
        cellular = controls::openCellular, airplane = controls::openAirplane,
        torch = controls::toggleTorch, rotation = controls::toggleRotationLock,
        silent = controls::toggleSilent, focus = controls::toggleFocus,
        brightness = controls::setBrightness, volume = controls::setVolume,
        timer = controls::openTimer, calculator = controls::openCalculator, camera = controls::openCamera,
        mirroring = controls::openScreenMirroring, alarm = controls::openAlarms, location = controls::openLocation,
        playPause = media::playPause, next = media::next, previous = media::previous,
        openPlayer = { media.openApp(); close() },
    )
    val ncActions = NotificationActions(
        open = { if (BadgeListenerService.open(context, it)) close() },
        dismiss = BadgeListenerService::dismiss,
        dismissAll = BadgeListenerService::dismissAll,
        grantAccess = { SystemActions.openNotificationAccess(context); close() },
        playPause = media::playPause, next = media::next, previous = media::previous,
        openPlayer = { media.openApp(); close() },
    )
    // Hidden apps stay hidden here too.
    val visibleItems = remember(items, hidden.hidden, byPackage) {
        val hiddenPkgs = hidden.hidden.mapNotNull { id -> apps.firstOrNull { it.id == id }?.packageName }.toSet()
        items.filter { it.packageName !in hiddenPkgs }
    }

    val ltr = LocalLayoutDirection.current
    Box(
        modifier
            .fillMaxSize()
            .noRippleClick { close() }
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onVerticalDrag = { _, dy -> total += dy },
                    onDragEnd = { if (total < -90f) close() },
                )
            },
    ) {
        when (kind) {
            PanelKind.CONTROL_CENTER -> Column(Modifier.fillMaxSize().padding(insets)) {
                Spacer(Modifier.height(18.dp))
                ControlCenterContent(state, np, ccActions)
            }
            PanelKind.NOTIFICATION_CENTER -> NotificationCenterContent(
                items = visibleItems,
                hasAccess = hasAccess,
                np = np,
                appFor = { byPackage[it] },
                actions = ncActions,
                contentPadding = PaddingValues(
                    start = insets.calculateStartPadding(ltr) + 16.dp,
                    end = insets.calculateEndPadding(ltr) + 16.dp,
                    top = insets.calculateTopPadding() + 8.dp,
                    bottom = insets.calculateBottomPadding() + 40.dp,
                ),
            )
        }
        // Home-indicator style grabber: a reminder that swiping up closes the panel.
        Box(
            Modifier.align(Alignment.BottomCenter).padding(bottom = insets.calculateBottomPadding() + 10.dp)
                .size(width = 120.dp, height = 5.dp).background(Color.White.copy(alpha = 0.7f), RoundedCornerShape(50))
        )
    }
}
