package com.mrios.launcher.ui.overlays

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mrios.launcher.R
import com.mrios.launcher.badges.BadgeListenerService
import com.mrios.launcher.data.CalendarRepository
import com.mrios.launcher.launcherApp
import com.mrios.launcher.system.SystemActions
import com.mrios.launcher.ui.IOSColors
import com.mrios.launcher.ui.components.FrostedBackdrop
import com.mrios.launcher.ui.components.SquircleShape
import com.mrios.launcher.ui.components.glass
import com.mrios.launcher.ui.widgets.DigitalClockHeader

class OnboardingActions(
    val setDefault: () -> Unit,
    val location: () -> Unit,
    val calendar: () -> Unit,
    val badges: () -> Unit,
    val panels: () -> Unit,
)

@Composable
private fun StepRow(icon: ImageVector, color: Color, text: String, done: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = !done, onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp).background(color, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(text, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
        if (done) Icon(Icons.Filled.CheckCircle, null, tint = IOSColors.Green)
        else Text(stringResource(R.string.allow), color = IOSColors.Blue, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun OnboardingOverlay(actions: OnboardingActions, onFinish: () -> Unit) {
    val context = LocalContext.current
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val isDefault = remember(lifecycleState) { SystemActions.isDefaultLauncher(context) }
    val location = remember(lifecycleState) { context.launcherApp.weather.hasLocationPermission() }
    val calendar = remember(lifecycleState) { CalendarRepository.hasPermission(context) }
    val badges = remember(lifecycleState) { BadgeListenerService.isEnabled(context) }
    val panels = remember(lifecycleState) { com.mrios.launcher.system.PanelService.isEnabled(context) }

    Box(Modifier.fillMaxSize()) {
        FrostedBackdrop(true, dim = 0.35f, modifier = Modifier.noRippleClick { }, cover = true)
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().popIn(0.95f)) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            Box(Modifier.size(88.dp).glass(SquircleShape, tint = Color.White.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(120.dp))
            }
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.onboard_title), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(stringResource(R.string.onboard_sub), color = IOSColors.Secondary, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(18.dp))
            DigitalClockHeader()
            Spacer(Modifier.height(18.dp))
            Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp), tint = Color.White.copy(alpha = 0.1f))) {
                StepRow(Icons.Filled.Home, IOSColors.Green, stringResource(R.string.onboard_default), isDefault, actions.setDefault)
                StepRow(Icons.Filled.NearMe, IOSColors.Blue, stringResource(R.string.onboard_location), location, actions.location)
                StepRow(Icons.Filled.CalendarMonth, IOSColors.Red, stringResource(R.string.onboard_calendar), calendar, actions.calendar)
                StepRow(Icons.Filled.Notifications, IOSColors.Orange, stringResource(R.string.onboard_badges), badges, actions.badges)
                StepRow(Icons.Filled.Tune, Color(0xFF5E5CE6), stringResource(R.string.onboard_panels), panels, actions.panels)
            }
            Text(stringResource(R.string.onboard_hint), color = IOSColors.Secondary, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
        }
            Text(
                stringResource(R.string.onboard_continue), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp).fillMaxWidth().background(IOSColors.Blue, RoundedCornerShape(14.dp)).clickable(onClick = onFinish).padding(vertical = 15.dp),
            )
        }
    }
}

/** Full-screen preview of a picked photo with the glass UI on top, then apply. */
@Composable
fun WallpaperPreviewOverlay(bitmap: Bitmap, onApply: (both: Boolean) -> Unit, onDismiss: () -> Unit) {
    val img = remember(bitmap) { bitmap.asImageBitmap() }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Image(img, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        DigitalClockHeader(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 60.dp))
        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(20.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            listOf(
                stringResource(R.string.wallpaper_set_home) to { onApply(false) },
                stringResource(R.string.wallpaper_set_both) to { onApply(true) },
                stringResource(R.string.cancel) to onDismiss,
            ).forEach { (label, action) ->
                Text(
                    label, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                        .clickable(onClick = action).padding(vertical = 14.dp),
                )
            }
        }
    }
}
