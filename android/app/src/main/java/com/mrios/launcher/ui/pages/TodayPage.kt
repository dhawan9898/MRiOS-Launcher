package com.mrios.launcher.ui.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrios.launcher.R
import com.mrios.launcher.data.WeatherState
import com.mrios.launcher.data.WidgetType
import com.mrios.launcher.ui.IOSColors
import com.mrios.launcher.ui.components.glass
import com.mrios.launcher.ui.widgets.CalendarWide
import com.mrios.launcher.ui.widgets.DigitalClockHeader
import com.mrios.launcher.ui.widgets.SmallWidget
import com.mrios.launcher.ui.widgets.WeatherWide
import com.mrios.launcher.ui.widgets.WidgetActions

/** The "Today View" to the left of the first home page. */
@Composable
fun TodayPage(
    weather: WeatherState,
    actions: WidgetActions,
    topPadding: Dp,
    bottomPadding: Dp,
    onSearch: () -> Unit,
    onEdit: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = topPadding + 10.dp, bottom = bottomPadding + 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().height(44.dp).glass(RoundedCornerShape(14.dp)).clickable(onClick = onSearch).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, null, tint = IOSColors.Secondary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.search_hint), color = IOSColors.Secondary, fontSize = 17.sp)
            }
        }
        item { DigitalClockHeader(Modifier.fillMaxWidth().padding(vertical = 8.dp)) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SmallWidget(WidgetType.CLOCK, weather, actions, Modifier.weight(1f).aspectRatio(1f))
                SmallWidget(WidgetType.BATTERY, weather, actions, Modifier.weight(1f).aspectRatio(1f))
            }
        }
        item { WeatherWide(weather, actions.onWeather, Modifier.fillMaxWidth()) }
        item { CalendarWide(actions.onCalendar, Modifier.fillMaxWidth()) }
        item {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.edit), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                    modifier = Modifier.glass(RoundedCornerShape(50)).clickable(onClick = onEdit).padding(horizontal = 22.dp, vertical = 9.dp),
                )
            }
        }
    }
}
