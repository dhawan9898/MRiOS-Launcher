package com.mrios.launcher.ui.panels

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ScreenLockRotation
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrios.launcher.R
import com.mrios.launcher.system.ControlResult
import com.mrios.launcher.system.ControlState
import com.mrios.launcher.system.NowPlaying
import com.mrios.launcher.ui.IOSColors
import com.mrios.launcher.ui.components.glass
import com.mrios.launcher.ui.widgets.rememberBattery

/** Everything Control Centre needs, so the same UI runs in the launcher and over other apps. */
class ControlActions(
    val run: (() -> ControlResult) -> Unit,
    val wifi: () -> ControlResult,
    val bluetooth: () -> ControlResult,
    val cellular: () -> ControlResult,
    val airplane: () -> ControlResult,
    val torch: () -> ControlResult,
    val rotation: () -> ControlResult,
    val silent: () -> ControlResult,
    val focus: () -> ControlResult,
    val brightness: (Float) -> ControlResult,
    val volume: (Float) -> Unit,
    val timer: () -> ControlResult,
    val calculator: () -> ControlResult,
    val camera: () -> ControlResult,
    val mirroring: () -> ControlResult,
    val alarm: () -> ControlResult,
    val location: () -> ControlResult,
    val playPause: () -> Unit,
    val next: () -> Unit,
    val previous: () -> Unit,
    val openPlayer: () -> Unit,
)

private val ModuleTint = Color.White.copy(alpha = 0.10f)

@Composable
private fun Module(shape: Shape, modifier: Modifier, active: Boolean = false, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Box(
        modifier
            .glass(shape, tint = ModuleTint, rim = 0.35f)
            .let { if (active) it.background(Color.White.copy(alpha = 0.92f), shape) else it }
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun CircleToggle(icon: ImageVector, on: Boolean, activeColor: Color, size: Dp, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (on) activeColor else Color.White.copy(alpha = 0.18f))
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = Color.White, modifier = Modifier.size(size * 0.48f)) }
}

/** iOS-style tall slider: drag or tap anywhere to set; white fill rises from the bottom. */
@Composable
private fun VerticalSlider(value: Float, icon: ImageVector, label: String, shape: Shape, modifier: Modifier, onChange: (Float) -> Unit) {
    var local by remember { mutableFloatStateOf(value) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(value) { if (!dragging) local = value }
    val change by rememberUpdatedState(onChange)
    Box(
        modifier
            .glass(shape, tint = ModuleTint, rim = 0.35f)
            .drawBehind {
                val h = size.height * local.coerceIn(0f, 1f)
                drawRect(Color.White.copy(alpha = 0.95f), topLeft = Offset(0f, size.height - h), size = Size(size.width, h))
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                    onVerticalDrag = { ch, dy ->
                        ch.consume()
                        local = (local - dy / size.height).coerceIn(0f, 1f)
                        change(local)
                    },
                )
            }
            .pointerInput(Unit) {
                detectTapGestures { pos -> local = (1f - pos.y / size.height).coerceIn(0f, 1f); change(local) }
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Icon(
            icon, label,
            tint = if (local > 0.15f) Color(0xFF6E6E73) else Color.White,
            modifier = Modifier.padding(bottom = 18.dp).size(26.dp),
        )
    }
}

@Composable
private fun NowPlayingModule(np: NowPlaying?, actions: ControlActions, modifier: Modifier, shape: Shape) {
    Module(shape, modifier, onClick = if (np != null) actions.openPlayer else null) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    if (np?.art != null) Image(np.art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Icon(Icons.Filled.MusicNote, null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(np?.title?.ifBlank { null } ?: stringResource(R.string.cc_not_playing), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!np?.artist.isNullOrBlank()) Text(np!!.artist, color = IOSColors.Secondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                val tint = if (np != null) Color.White else Color.White.copy(alpha = 0.35f)
                Icon(Icons.Filled.SkipPrevious, null, tint = tint, modifier = Modifier.size(30.dp).clickable(enabled = np != null) { actions.previous() })
                Icon(if (np?.playing == true) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = tint, modifier = Modifier.size(40.dp).clickable(enabled = np != null) { actions.playPause() })
                Icon(Icons.Filled.SkipNext, null, tint = tint, modifier = Modifier.size(30.dp).clickable(enabled = np != null) { actions.next() })
            }
        }
    }
}

@Composable
private fun SmallTile(icon: ImageVector, label: String, on: Boolean, activeTint: Color, cell: Dp, shape: Shape, onClick: () -> Unit) {
    Module(shape, Modifier.size(cell), active = on, onClick = onClick) {
        Icon(icon, label, tint = if (on) activeTint else Color.White, modifier = Modifier.size(cell * 0.38f))
    }
}

@Composable
private fun WideTile(icon: ImageVector, title: String, subtitle: String?, on: Boolean, iconBg: Color, width: Dp, height: Dp, shape: Shape, onClick: () -> Unit) {
    Module(shape, Modifier.width(width).height(height), active = on, onClick = onClick) {
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(height * 0.5f).clip(CircleShape).background(if (on) iconBg else Color.White.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(height * 0.28f))
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, color = if (on) Color.Black else Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, color = if (on) Color.Black.copy(alpha = 0.6f) else IOSColors.Secondary, fontSize = 12.sp, maxLines = 1)
            }
        }
    }
}

@Composable
fun ControlCenterContent(state: ControlState, np: NowPlaying?, actions: ControlActions, modifier: Modifier = Modifier) {
    val (battery, charging) = rememberBattery()
    BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal = 22.dp)) {
        val gap = 14.dp
        val cell = (maxWidth - gap * 3) / 4
        val big = cell * 2 + gap
        val round = RoundedCornerShape(cell * 0.32f)
        val bigRound = RoundedCornerShape(cell * 0.36f)
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.End) {
                if (battery >= 0) Text(
                    "$battery%" + if (charging) " ⚡" else "",
                    color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                Module(bigRound, Modifier.size(big)) {
                    val circle = (big - 14.dp * 3) / 2
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            CircleToggle(Icons.Filled.AirplanemodeActive, state.airplane, IOSColors.Orange, circle, stringResource(R.string.cc_airplane)) { actions.run(actions.airplane) }
                            CircleToggle(Icons.Filled.SignalCellularAlt, state.cellular, IOSColors.Green, circle, stringResource(R.string.cc_cellular)) { actions.run(actions.cellular) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            CircleToggle(Icons.Filled.Wifi, state.wifi, IOSColors.Blue, circle, stringResource(R.string.cc_wifi)) { actions.run(actions.wifi) }
                            CircleToggle(Icons.Filled.Bluetooth, state.bluetooth, IOSColors.Blue, circle, stringResource(R.string.cc_bluetooth)) { actions.run(actions.bluetooth) }
                        }
                    }
                }
                NowPlayingModule(np, actions, Modifier.size(big), bigRound)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        SmallTile(Icons.Filled.ScreenLockRotation, stringResource(R.string.cc_rotation), state.rotationLocked, IOSColors.Red, cell, round) { actions.run(actions.rotation) }
                        SmallTile(Icons.Filled.NotificationsOff, stringResource(R.string.cc_silent), state.silent, IOSColors.Red, cell, round) { actions.run(actions.silent) }
                    }
                    WideTile(
                        Icons.Filled.DarkMode, stringResource(R.string.cc_focus),
                        if (state.focus) stringResource(R.string.cc_focus_on) else null,
                        state.focus, Color(0xFF5E5CE6), big, cell, round,
                    ) { actions.run(actions.focus) }
                }
                VerticalSlider(state.brightness, Icons.Filled.WbSunny, stringResource(R.string.cc_brightness), bigRound, Modifier.width(cell).height(big)) { v ->
                    actions.run { actions.brightness(v) }
                }
                VerticalSlider(state.volume, Icons.Filled.VolumeUp, stringResource(R.string.cc_volume), bigRound, Modifier.width(cell).height(big)) { v ->
                    actions.volume(v)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                SmallTile(Icons.Filled.FlashlightOn, stringResource(R.string.cc_flashlight), state.torch, Color(0xFF1C1C1E), cell, round) { actions.run(actions.torch) }
                SmallTile(Icons.Filled.Timer, stringResource(R.string.cc_timer), false, Color.Black, cell, round) { actions.run(actions.timer) }
                SmallTile(Icons.Filled.Calculate, stringResource(R.string.cc_calculator), false, Color.Black, cell, round) { actions.run(actions.calculator) }
                SmallTile(Icons.Filled.CameraAlt, stringResource(R.string.cc_camera), false, Color.Black, cell, round) { actions.run(actions.camera) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                WideTile(Icons.Filled.Cast, stringResource(R.string.cc_mirroring), null, false, IOSColors.Blue, big, cell, round) { actions.run(actions.mirroring) }
                SmallTile(Icons.Filled.Alarm, stringResource(R.string.cc_alarm), false, Color.Black, cell, round) { actions.run(actions.alarm) }
                SmallTile(Icons.Filled.LocationOn, stringResource(R.string.cc_location), false, Color.Black, cell, round) { actions.run(actions.location) }
            }
        }
    }
}

