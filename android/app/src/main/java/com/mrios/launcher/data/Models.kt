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

/** How app icons are drawn. GLASS adds a specular sheen; CLEAR/TINTED show a glyph on a glass tile. */
enum class IconStyle(@StringRes val title: Int) {
    ORIGINAL(R.string.icon_style_original),
    GLASS(R.string.icon_style_glass),
    CLEAR(R.string.icon_style_clear),
    TINTED(R.string.icon_style_tinted),
}

/** Tint colours for the TINTED icon style (ARGB). */
val IconTints: List<Pair<Int, Long>> = listOf(
    R.string.tint_gold to 0xFFFFD27A,
    R.string.tint_blue to 0xFF7CC4FF,
    R.string.tint_pink to 0xFFFF9AD2,
    R.string.tint_green to 0xFF8BE8A8,
    R.string.tint_purple to 0xFFC9B2FF,
    R.string.tint_white to 0xFFFFFFFF,
)

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
    val iconStyle: IconStyle = IconStyle.GLASS,
    val iconTint: Int = 0,
    val edgeNotifications: Boolean = true,
    val edgeControlCenter: Boolean = true,
) {
    /** Rows on the first home page taken up by widgets (widgets are 2x2 each). */
    val widgetRows: Int get() = if (homeWidgets.isEmpty()) 0 else 2
}

/** Grid geometry used when packing apps into home pages. */
data class Grid(val columns: Int, val rows: Int, val widgetRows: Int) {
    fun capacity(page: Int): Int = columns * (if (page == 0) rows - widgetRows else rows)
}
