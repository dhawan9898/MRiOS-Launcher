package com.mrios.launcher.ui.panels

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrios.launcher.R
import com.mrios.launcher.badges.NotificationItem
import com.mrios.launcher.data.AppEntry
import com.mrios.launcher.system.NowPlaying
import com.mrios.launcher.ui.IOSColors
import com.mrios.launcher.ui.components.AppIcon
import com.mrios.launcher.ui.components.glass
import com.mrios.launcher.ui.widgets.DigitalClockHeader
import com.mrios.launcher.ui.widgets.rememberNow

class NotificationActions(
    val open: (NotificationItem) -> Unit,
    val dismiss: (NotificationItem) -> Unit,
    val dismissAll: (List<NotificationItem>) -> Unit,
    val grantAccess: () -> Unit,
    val playPause: () -> Unit,
    val next: () -> Unit,
    val previous: () -> Unit,
    val openPlayer: () -> Unit,
)

private val CardShape = RoundedCornerShape(22.dp)
private val CardTint = Color.White.copy(alpha = 0.14f)

@Composable
private fun relativeTime(t: Long, now: Long): String {
    val mins = ((now - t) / 60_000).toInt()
    return when {
        mins < 1 -> stringResource(R.string.time_now)
        mins < 60 -> stringResource(R.string.time_minutes, mins)
        mins < 24 * 60 -> stringResource(R.string.time_hours, mins / 60)
        else -> android.text.format.DateFormat.format("d MMM", t).toString()
    }
}

@Composable
private fun NotificationCard(item: NotificationItem, app: AppEntry?, now: Long, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().glass(CardShape, tint = CardTint, rim = 0.3f).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (app != null) AppIcon(app, 38.dp)
        else Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Notifications, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.title.ifBlank { item.appLabel }, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(relativeTime(item.postTime, now), color = IOSColors.Secondary, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))
            }
            if (item.text.isNotBlank()) Text(item.text, color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableCard(item: NotificationItem, app: AppEntry?, now: Long, actions: NotificationActions, onClick: () -> Unit) {
    if (!item.clearable) { NotificationCard(item, app, now, onClick); return }
    val state = rememberSwipeToDismissBoxState(confirmValueChange = {
        if (it == SwipeToDismissBoxValue.EndToStart) { actions.dismiss(item); true } else false
    })
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            // Only while swiping — otherwise it would show through the translucent card.
            if (state.dismissDirection == SwipeToDismissBoxValue.EndToStart) Box(Modifier.fillMaxSize().padding(start = 40.dp).clip(CardShape).background(Color(0xFF8E8E93).copy(alpha = 0.6f)), contentAlignment = Alignment.CenterEnd) {
                Text(stringResource(R.string.nc_clear), color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(end = 20.dp))
            }
        },
    ) { NotificationCard(item, app, now, onClick) }
}

@Composable
private fun RoundButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.size(30.dp).clip(CircleShape).glass(CircleShape, tint = CardTint, rim = 0.3f).clickable(onClick = onClick), contentAlignment = Alignment.Center) { content() }
}

@Composable
fun NowPlayingWide(np: NowPlaying, playPause: () -> Unit, next: () -> Unit, previous: () -> Unit, open: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().glass(CardShape, tint = CardTint, rim = 0.3f).clickable(onClick = open).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            if (np.art != null) Image(np.art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Icon(Icons.Filled.MusicNote, null, tint = Color.White)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(np.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(np.artist, color = IOSColors.Secondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Filled.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(30.dp).clickable(onClick = previous))
        Icon(if (np.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.padding(horizontal = 6.dp).size(36.dp).clickable(onClick = playPause))
        Icon(Icons.Filled.SkipNext, null, tint = Color.White, modifier = Modifier.size(30.dp).clickable(onClick = next))
    }
}

@Composable
fun NotificationCenterContent(
    items: List<NotificationItem>,
    hasAccess: Boolean,
    np: NowPlaying?,
    appFor: (String) -> AppEntry?,
    actions: NotificationActions,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val now = rememberNow(30_000)
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    val groups = remember(items) { items.groupBy { it.packageName }.values.sortedByDescending { g -> g.maxOf { it.postTime } } }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "clock") { DigitalClockHeader(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 18.dp)) }
        if (np != null) item(key = "media") { NowPlayingWide(np, actions.playPause, actions.next, actions.previous, actions.openPlayer) }

        if (!hasAccess) {
            item(key = "access") {
                Column(Modifier.fillMaxWidth().glass(CardShape, tint = CardTint, rim = 0.3f).padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Notifications, null, tint = Color.White, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.nc_access_title), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text(stringResource(R.string.nc_access_desc), color = IOSColors.Secondary, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.nc_allow), color = Color.White, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(IOSColors.Blue).clickable(onClick = actions.grantAccess).padding(horizontal = 20.dp, vertical = 9.dp),
                    )
                }
            }
            return@LazyColumn
        }

        item(key = "header") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.nc_title), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (items.any { it.clearable }) RoundButton({ actions.dismissAll(items) }) {
                    Icon(Icons.Filled.Close, stringResource(R.string.nc_clear_all), tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }

        if (items.isEmpty()) {
            item(key = "empty") {
                Text(stringResource(R.string.nc_empty), color = IOSColors.Secondary, fontSize = 15.sp, modifier = Modifier.fillMaxWidth().padding(top = 40.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }

        groups.forEach { group ->
            val pkg = group.first().packageName
            val app = appFor(pkg)
            val isOpen = expanded[pkg] == true
            if (group.size > 1 && isOpen) {
                item(key = "g_$pkg") {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(group.first().appLabel, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(
                            stringResource(R.string.nc_show_less), color = Color.White, fontSize = 13.sp,
                            modifier = Modifier.clip(RoundedCornerShape(50)).glass(RoundedCornerShape(50), tint = CardTint, rim = 0.3f)
                                .clickable { expanded[pkg] = false }.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        RoundButton({ actions.dismissAll(group) }) { Icon(Icons.Filled.Close, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
                    }
                }
                items(group, key = { it.key }) { n -> SwipeableCard(n, app, now, actions) { actions.open(n) } }
            } else if (group.size > 1) {
                // Collapsed stack: newest on top, one or two cards peeking out underneath.
                item(key = "s_$pkg") {
                    Column(Modifier.fillMaxWidth().animateContentSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                        NotificationCard(group.first(), app, now, onClick = { expanded[pkg] = true })
                        // The cards underneath peek out below the top one.
                        for (i in 1..minOf(group.size - 1, 2)) {
                            Box(
                                Modifier.fillMaxWidth(1f - 0.07f * i).height(10.dp)
                                    .alpha(1f - 0.3f * (i - 1))
                                    .glass(RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp), tint = Color.White.copy(alpha = 0.20f), rim = 0.3f)
                            )
                        }
                    }
                }
            } else {
                items(group, key = { it.key }) { n -> SwipeableCard(n, app, now, actions) { actions.open(n) } }
            }
        }
        item(key = "end") { Spacer(Modifier.height(40.dp)) }
    }
}
