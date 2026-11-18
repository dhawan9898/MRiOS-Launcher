package com.mrios.launcher.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<Settings> = _state.asStateFlow()
    val value: Settings get() = _state.value

    fun update(block: (Settings) -> Settings) {
        val next = block(_state.value)
        _state.value = next
        save(next)
    }

    private fun load(): Settings {
        val raw = prefs.getString("json", null) ?: return Settings()
        return runCatching {
            val o = JSONObject(raw)
            val d = Settings()
            Settings(
                theme = enumOr(o.optString("theme"), d.theme),
                iconPack = o.optString("iconPack").ifEmpty { null },
                showLabels = o.optBoolean("showLabels", d.showLabels),
                columns = o.optInt("columns", d.columns).coerceIn(3, 6),
                rows = o.optInt("rows", d.rows).coerceIn(4, 8),
                homeWidgets = o.optJSONArray("homeWidgets")?.let { a ->
                    (0 until a.length()).mapNotNull { runCatching { WidgetType.valueOf(a.getString(it)) }.getOrNull() }
                } ?: d.homeWidgets,
                addNewAppsToHome = o.optBoolean("addNew", d.addNewAppsToHome),
                swipeDown = enumOr(o.optString("swipeDown"), d.swipeDown),
                weatherPlace = o.optJSONObject("place")?.let {
                    WeatherPlace(it.getString("name"), it.getDouble("lat"), it.getDouble("lon"))
                },
                celsius = o.optBoolean("celsius", d.celsius),
                badges = o.optBoolean("badges", d.badges),
                language = enumOr(o.optString("language"), d.language),
                hiddenPinHash = o.optString("pin").ifEmpty { null },
                onboardingDone = o.optBoolean("onboarded", false),
                iconStyle = enumOr(o.optString("iconStyle"), d.iconStyle),
                iconTint = o.optInt("iconTint", d.iconTint).coerceIn(0, IconTints.lastIndex),
                edgeNotifications = o.optBoolean("edgeNotif", d.edgeNotifications),
                edgeControlCenter = o.optBoolean("edgeControl", d.edgeControlCenter),
            )
        }.getOrDefault(Settings())
    }

    private fun save(s: Settings) {
        val o = JSONObject()
            .put("theme", s.theme.name)
            .put("iconPack", s.iconPack ?: "")
            .put("showLabels", s.showLabels)
            .put("columns", s.columns)
            .put("rows", s.rows)
            .put("homeWidgets", JSONArray(s.homeWidgets.map { it.name }))
            .put("addNew", s.addNewAppsToHome)
            .put("swipeDown", s.swipeDown.name)
            .put("celsius", s.celsius)
            .put("badges", s.badges)
            .put("language", s.language.name)
            .put("pin", s.hiddenPinHash ?: "")
            .put("onboarded", s.onboardingDone)
            .put("iconStyle", s.iconStyle.name)
            .put("iconTint", s.iconTint)
            .put("edgeNotif", s.edgeNotifications)
            .put("edgeControl", s.edgeControlCenter)
        s.weatherPlace?.let {
            o.put("place", JSONObject().put("name", it.name).put("lat", it.latitude).put("lon", it.longitude))
        }
        prefs.edit().putString("json", o.toString()).apply()
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        runCatching { enumValueOf<E>(name!!) }.getOrDefault(default)
}
