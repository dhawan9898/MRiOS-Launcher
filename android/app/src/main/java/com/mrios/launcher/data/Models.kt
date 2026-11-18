package com.mrios.launcher.data

import android.content.ComponentName
import android.os.UserHandle
import androidx.annotation.StringRes
import com.mrios.launcher.R

/** Identifies one launchable activity for one user profile (personal / work). */
data class AppKey(val component: ComponentName, val userSerial: Long) {
    val id: String get() = "${component.flattenToString()}#$userSerial"

    companion object {
        fun parse(id: String): AppKey? {
            val hash = id.lastIndexOf('#')
            if (hash <= 0) return null
            val cn = ComponentName.unflattenFromString(id.substring(0, hash)) ?: return null
            val serial = id.substring(hash + 1).toLongOrNull() ?: return null
            return AppKey(cn, serial)
        }
    }
}

data class AppEntry(
    val key: AppKey,
    val user: UserHandle,
    val originalLabel: String,
    val label: String,
    val category: AppCategory,
    val installTime: Long,
    val isSystem: Boolean,
    val isWorkProfile: Boolean,
) {
    val id: String get() = key.id
    val packageName: String get() = key.component.packageName
}

enum class AppCategory(@StringRes val title: Int) {
    SOCIAL(R.string.cat_social),
    ENTERTAINMENT(R.string.cat_entertainment),
    GAMES(R.string.cat_games),
    PRODUCTIVITY(R.string.cat_productivity),
    CREATIVITY(R.string.cat_creativity),
    INFO(R.string.cat_info),
    SHOPPING(R.string.cat_shopping),
    TRAVEL(R.string.cat_travel),
    HEALTH(R.string.cat_health),
    UTILITIES(R.string.cat_utilities),
    OTHER(R.string.cat_other),
}

enum class ThemeMode { GLASS, TRANSPARENT }
enum class SwipeDownAction { SEARCH, NOTIFICATIONS }
enum class WidgetType(@StringRes val title: Int) {
    CLOCK(R.string.widget_clock),
    WEATHER(R.string.widget_weather),
    CALENDAR(R.string.widget_calendar),
    BATTERY(R.string.widget_battery),
}
enum class AppLanguage(val tag: String?) { SYSTEM(null), ENGLISH("en"), HINDI("hi") }

data class WeatherPlace(val name: String, val latitude: Double, val longitude: Double)

data class Settings(
    val theme: ThemeMode = ThemeMode.GLASS,
    val iconPack: String? = null,
    val showLabels: Boolean = true,
    val columns: Int = 4,
    val rows: Int = 6,
    val homeWidgets: List<WidgetType> = listOf(WidgetType.CLOCK, WidgetType.WEATHER),
    val addNewAppsToHome: Boolean = true,
    val swipeDown: SwipeDownAction = SwipeDownAction.SEARCH,
    val weatherPlace: WeatherPlace? = null,   // null = use device location
    val celsius: Boolean = true,
    val badges: Boolean = true,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val hiddenPinHash: String? = null,
    val onboardingDone: Boolean = false,
) {
    /** Rows on the first home page taken up by widgets (widgets are 2x2 each). */
    val widgetRows: Int get() = if (homeWidgets.isEmpty()) 0 else 2
}

/** Grid geometry used when packing apps into home pages. */
data class Grid(val columns: Int, val rows: Int, val widgetRows: Int) {
    fun capacity(page: Int): Int = columns * (if (page == 0) rows - widgetRows else rows)
}
