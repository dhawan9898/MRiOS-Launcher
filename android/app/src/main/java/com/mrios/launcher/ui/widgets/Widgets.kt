package com.mrios.launcher.ui.widgets

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.text.format.DateFormat
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dehaze
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbCloudy
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrios.launcher.R
import com.mrios.launcher.data.CalendarEvent
import com.mrios.launcher.data.CalendarRepository
import com.mrios.launcher.data.WeatherState
import com.mrios.launcher.data.WidgetType
import com.mrios.launcher.ui.IOSColors
import com.mrios.launcher.ui.components.glass
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.Date
import kotlin.math.min

class WidgetActions(
    val onWeather: () -> Unit,
    val onCalendar: () -> Unit,
    val onClock: () -> Unit,
    val onBattery: () -> Unit,
)

val WidgetShape = RoundedCornerShape(24.dp)

@Composable
fun rememberNow(periodMs: Long = 1000L): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(periodMs) {
        while (true) {
            delay(periodMs - System.currentTimeMillis() % periodMs)
            now = System.currentTimeMillis()
        }
    }
    return now
}

@Composable
private fun WidgetBox(onClick: () -> Unit, modifier: Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .glass(WidgetShape, tint = Color.White.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(14.dp),
        content = content,
    )
}

/** A small (2x2) widget for the home screen / Today view. */
@Composable
fun SmallWidget(type: WidgetType, weather: WeatherState, actions: WidgetActions, modifier: Modifier = Modifier) {
    when (type) {
        WidgetType.CLOCK -> WidgetBox(actions.onClock, modifier) { AnalogClock(Modifier.fillMaxSize()) }
        WidgetType.WEATHER -> WidgetBox(actions.onWeather, modifier) { WeatherSmall(weather) }
        WidgetType.CALENDAR -> WidgetBox(actions.onCalendar, modifier) { CalendarSmall() }
        WidgetType.BATTERY -> WidgetBox(actions.onBattery, modifier) { BatterySmall() }
    }
}

// ------------------------------------------------------------------ clock

@Composable
fun AnalogClock(modifier: Modifier = Modifier) {
    val now = rememberNow()
    val cal = remember(now) { Calendar.getInstance().apply { timeInMillis = now } }
    val h = cal.get(Calendar.HOUR) + cal.get(Calendar.MINUTE) / 60f
    val m = cal.get(Calendar.MINUTE) + cal.get(Calendar.SECOND) / 60f
    val s = cal.get(Calendar.SECOND)
    Canvas(modifier) {
        val r = min(size.width, size.height) / 2f
        val c = center
        drawCircle(Color.White.copy(alpha = 0.92f), r, c)
        for (i in 0 until 60) {
            rotate(i * 6f, c) {
                val major = i % 5 == 0
                drawLine(
                    if (major) Color.Black else Color.Black.copy(alpha = 0.35f),
                    Offset(c.x, c.y - r * 0.93f), Offset(c.x, c.y - r * (if (major) 0.80f else 0.87f)),
                    strokeWidth = if (major) r * 0.035f else r * 0.015f, cap = StrokeCap.Round,
                )
            }
        }
        rotate(h * 30f, c) { drawLine(Color.Black, c, Offset(c.x, c.y - r * 0.48f), r * 0.07f, StrokeCap.Round) }
        rotate(m * 6f, c) { drawLine(Color.Black, c, Offset(c.x, c.y - r * 0.72f), r * 0.05f, StrokeCap.Round) }
        rotate(s * 6f, c) { drawLine(IOSColors.Orange, Offset(c.x, c.y + r * 0.15f), Offset(c.x, c.y - r * 0.82f), r * 0.02f, StrokeCap.Round) }
        drawCircle(IOSColors.Orange, r * 0.05f, c)
        drawCircle(Color.White, r * 0.02f, c)
    }
}

@Composable
fun DigitalClockHeader(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val now = rememberNow(1000)
    val date = remember(now / 60_000) {
        DateFormat.format(DateFormat.getBestDateTimePattern(java.util.Locale.getDefault(), "EEEEdMMMM"), Date(now)).toString()
    }
    val time = remember(now / 1000) { DateFormat.getTimeFormat(context).format(Date(now)) }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(date, color = Color.White.copy(alpha = 0.85f), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text(time, color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
    }
}

// ------------------------------------------------------------------ weather

@StringRes
fun weatherText(code: Int): Int = when (code) {
    0 -> R.string.wc_clear
    1 -> R.string.wc_mostly_clear
    2 -> R.string.wc_partly_cloudy
    3 -> R.string.wc_cloudy
    45, 48 -> R.string.wc_fog
    in 51..57 -> R.string.wc_drizzle
    in 61..67 -> R.string.wc_rain
    in 71..77, 85, 86 -> R.string.wc_snow
    in 80..82 -> R.string.wc_showers
    in 95..99 -> R.string.wc_thunder
    else -> R.string.wc_cloudy
}

fun weatherIcon(code: Int, isDay: Boolean): Pair<ImageVector, Color> = when (code) {
    0, 1 -> if (isDay) Icons.Filled.WbSunny to IOSColors.Yellow else Icons.Filled.NightsStay to Color(0xFFE5E5EA)
    2 -> Icons.Filled.WbCloudy to Color.White
    3 -> Icons.Filled.Cloud to Color.White
    45, 48 -> Icons.Filled.Dehaze to Color.White
    in 51..57 -> Icons.Filled.Grain to Color(0xFF64D2FF)
    in 61..67 -> Icons.Filled.WaterDrop to Color(0xFF64D2FF)
    in 71..77, 85, 86 -> Icons.Filled.AcUnit to Color.White
    in 80..82 -> Icons.Filled.Umbrella to Color(0xFF64D2FF)
    in 95..99 -> Icons.Filled.FlashOn to IOSColors.Yellow
    else -> Icons.Filled.Cloud to Color.White
}

@Composable
private fun PlaceRow(name: String?, current: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            name ?: stringResource(R.string.weather_current_location),
            color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
        )
        if (current) Icon(Icons.Filled.NearMe, null, tint = Color.White, modifier = Modifier.padding(start = 3.dp).size(11.dp))
    }
}

@Composable
private fun WeatherMessage(state: WeatherState) {
    val text = when (state) {
        WeatherState.Loading -> R.string.weather_loading
        WeatherState.NeedsLocation -> R.string.weather_tap_setup
        WeatherState.LocationUnavailable -> R.string.weather_no_fix
        else -> R.string.weather_unavailable
    }
    Column {
        Icon(Icons.Filled.WbCloudy, null, tint = Color.White, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.widget_weather), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Text(stringResource(text), color = IOSColors.Secondary, fontSize = 13.sp)
    }
}

@Composable
fun WeatherSmall(state: WeatherState) {
    if (state !is WeatherState.Ready) { WeatherMessage(state); return }
    val w = state.weather
    val (icon, tint) = weatherIcon(w.code, w.isDay)
    Column(Modifier.fillMaxSize()) {
        PlaceRow(w.place, w.isCurrentLocation)
        Text("${w.temp}°", color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.weight(1f))
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        Text(stringResource(weatherText(w.code)), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(stringResource(R.string.weather_hl, w.high, w.low), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun WeatherWide(state: WeatherState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    WidgetBox(onClick, modifier) {
        if (state !is WeatherState.Ready) { WeatherMessage(state); return@WidgetBox }
        val w = state.weather
        val (icon, tint) = weatherIcon(w.code, w.isDay)
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    PlaceRow(w.place, w.isCurrentLocation)
                    Text("${w.temp}°", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Light)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
                    Text(stringResource(weatherText(w.code)), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.weather_hl, w.high, w.low), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                w.hourly.forEachIndexed { i, h ->
                    val (hi, ht) = weatherIcon(h.code, h.isDay)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (i == 0) stringResource(R.string.weather_now) else h.hour, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Icon(hi, null, tint = ht, modifier = Modifier.padding(vertical = 6.dp).size(18.dp))
                        Text("${h.temp}°", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ calendar

@Composable
fun rememberEvents(limit: Int): List<CalendarEvent> {
    val context = LocalContext.current
    val minute = rememberNow(60_000)
    val granted = CalendarRepository.hasPermission(context)
    val events by produceState(emptyList<CalendarEvent>(), minute / 300_000, granted) {
        value = CalendarRepository.upcoming(context, limit = limit)
    }
    return events
}

@Composable
private fun EventLine(e: CalendarEvent) {
    val context = LocalContext.current
    Row(Modifier.padding(vertical = 3.dp).height(IntrinsicSizeHeight)) {
        Box(Modifier.width(3.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(Color(e.color or 0xFF000000.toInt())))
        Column(Modifier.padding(start = 6.dp)) {
            Text(e.title.ifBlank { "—" }, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (e.allDay) stringResource(R.string.all_day)
                else DateFormat.getTimeFormat(context).format(Date(e.begin)) + " – " + DateFormat.getTimeFormat(context).format(Date(e.end)),
                color = IOSColors.Secondary, fontSize = 12.sp, maxLines = 1,
            )
        }
    }
}

private val IntrinsicSizeHeight = 36.dp

@Composable
fun CalendarSmall() {
    val context = LocalContext.current
    val now = rememberNow(60_000)
    val events = rememberEvents(2)
    Column(Modifier.fillMaxSize()) {
        Text(DateFormat.format("EEEE", now).toString().uppercase(), color = IOSColors.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(DateFormat.format("d", now).toString(), color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.weight(1f))
        when {
            !CalendarRepository.hasPermission(context) -> Text(stringResource(R.string.calendar_grant), color = IOSColors.Secondary, fontSize = 12.sp)
            events.isEmpty() -> Text(stringResource(R.string.calendar_no_events), color = IOSColors.Secondary, fontSize = 13.sp)
            else -> EventLine(events.first())
        }
    }
}

@Composable
fun CalendarWide(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val now = rememberNow(60_000)
    val events = rememberEvents(3)
    WidgetBox(onClick, modifier) {
        Row {
            Column(Modifier.width(96.dp)) {
                Text(DateFormat.format("EEEE", now).toString().uppercase(), color = IOSColors.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(DateFormat.format("d", now).toString(), color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Light)
                Text(DateFormat.format("MMMM", now).toString(), color = IOSColors.Secondary, fontSize = 13.sp)
            }
            Column(Modifier.weight(1f)) {
                when {
                    !CalendarRepository.hasPermission(context) ->
                        Text(stringResource(R.string.calendar_grant), color = IOSColors.Secondary, fontSize = 14.sp)
                    events.isEmpty() -> Text(stringResource(R.string.calendar_no_events), color = IOSColors.Secondary, fontSize = 14.sp)
                    else -> events.forEach { EventLine(it) }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ battery

@Composable
fun rememberBattery(): Pair<Int, Boolean> {
    val context = LocalContext.current
    var level by remember { mutableIntStateOf(-1) }
    var charging by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                i ?: return
                val l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val s = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                level = if (l >= 0 && s > 0) l * 100 / s else -1
                val st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let { receiver.onReceive(context, it) }
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    return level to charging
}

@Composable
fun BatterySmall() {
    val (level, charging) = rememberBattery()
    val color = when {
        charging -> IOSColors.Green
        level in 0..20 -> IOSColors.Red
        else -> IOSColors.Green
    }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(78.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 8.dp.toPx()
                drawCircle(Color.White.copy(alpha = 0.18f), style = Stroke(stroke), radius = size.minDimension / 2 - stroke / 2)
                drawArc(
                    color, -90f, 360f * level.coerceAtLeast(0) / 100f, false,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            Icon(Icons.Filled.Bolt, null, tint = if (charging) IOSColors.Green else Color.White, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(if (level >= 0) "$level%" else "—", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        if (charging) Text(stringResource(R.string.battery_charging), color = IOSColors.Secondary, fontSize = 12.sp)
    }
}
