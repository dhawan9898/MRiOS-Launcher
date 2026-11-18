package com.mrios.launcher.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.coroutines.resume

data class HourForecast(val hour: String, val temp: Int, val code: Int, val isDay: Boolean)
data class Weather(
    val place: String?, val isCurrentLocation: Boolean, val temp: Int, val code: Int, val isDay: Boolean,
    val high: Int, val low: Int, val hourly: List<HourForecast>, val fetchedAt: Long,
)

sealed interface WeatherState {
    data object Loading : WeatherState
    data object NeedsLocation : WeatherState      // no permission and no city chosen
    data object LocationUnavailable : WeatherState // permission granted but no fix yet
    data object Error : WeatherState
    data class Ready(val weather: Weather) : WeatherState
}

/** Weather from Open-Meteo (free, no API key). */
class WeatherRepository(
    private val context: Context,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<WeatherState>(WeatherState.Loading)
    val state: StateFlow<WeatherState> = _state.asStateFlow()
    private var job: Job? = null
    private var lastKey: String? = null

    fun refresh(force: Boolean = false) {
        val s = settings.value
        val key = "${s.weatherPlace}|${s.celsius}"
        val current = _state.value
        if (!force && key == lastKey && current is WeatherState.Ready &&
            System.currentTimeMillis() - current.weather.fetchedAt < 30 * 60_000
        ) return
        if (job?.isActive == true && key == lastKey && !force) return
        lastKey = key
        job?.cancel()
        job = scope.launch {
            if (_state.value !is WeatherState.Ready) _state.value = WeatherState.Loading
            val place = s.weatherPlace
            val (lat, lon, name) = if (place != null) Triple(place.latitude, place.longitude, place.name.substringBefore(" · ")) else {
                if (!hasLocationPermission()) { _state.value = WeatherState.NeedsLocation; return@launch }
                val loc = deviceLocation()
                if (loc == null) { _state.value = WeatherState.LocationUnavailable; return@launch }
                Triple(loc.latitude, loc.longitude, placeName(loc))
            }
            _state.value = runCatching { fetch(lat, lon, name, place == null, s.celsius) }
                .map { WeatherState.Ready(it) }
                .getOrElse { (_state.value as? WeatherState.Ready) ?: WeatherState.Error }
        }
    }

    suspend fun searchCity(query: String): List<WeatherPlace> = withContext(Dispatchers.IO) {
        runCatching {
            val q = URLEncoder.encode(query, "UTF-8")
            val json = JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=8&language=${Locale.getDefault().language}&format=json"))
            val arr = json.optJSONArray("results") ?: return@runCatching emptyList()
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val sub = listOfNotNull(o.optString("admin1").ifEmpty { null }, o.optString("country").ifEmpty { null }).joinToString(", ")
                WeatherPlace(o.getString("name") + if (sub.isNotEmpty()) " · $sub" else "", o.getDouble("latitude"), o.getDouble("longitude"))
            }
        }.getOrDefault(emptyList())
    }

    private suspend fun fetch(lat: Double, lon: Double, name: String?, current: Boolean, celsius: Boolean): Weather = withContext(Dispatchers.IO) {
        val unit = if (celsius) "" else "&temperature_unit=fahrenheit"
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,weather_code,is_day&hourly=temperature_2m,weather_code,is_day" +
            "&daily=temperature_2m_max,temperature_2m_min&forecast_days=2&timezone=auto$unit"
        val o = JSONObject(get(url))
        val cur = o.getJSONObject("current")
        val daily = o.getJSONObject("daily")
        val hourly = o.getJSONObject("hourly")
        val times = hourly.getJSONArray("time")
        val nowHour = cur.getString("time").take(13) // yyyy-MM-ddTHH
        var start = (0 until times.length()).firstOrNull { times.getString(it).take(13) == nowHour } ?: 0
        val hours = (start until minOf(start + 6, times.length())).map { i ->
            HourForecast(
                hour = times.getString(i).substring(11, 13),
                temp = hourly.getJSONArray("temperature_2m").getDouble(i).toInt(),
                code = hourly.getJSONArray("weather_code").getInt(i),
                isDay = hourly.getJSONArray("is_day").getInt(i) == 1,
            )
        }
        Weather(
            place = name,
            isCurrentLocation = current,
            temp = Math.round(cur.getDouble("temperature_2m")).toInt(),
            code = cur.getInt("weather_code"),
            isDay = cur.optInt("is_day", 1) == 1,
            high = Math.round(daily.getJSONArray("temperature_2m_max").getDouble(0)).toInt(),
            low = Math.round(daily.getJSONArray("temperature_2m_min").getDouble(0)).toInt(),
            hourly = hours,
            fetchedAt = System.currentTimeMillis(),
        )
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000; c.readTimeout = 10_000
        c.setRequestProperty("User-Agent", "MRiOS-Launcher")
        try {
            if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }

    fun hasLocationPermission() =
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private suspend fun deviceLocation(): Location? {
        if (!hasLocationPermission()) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = runCatching { lm.getProviders(true) }.getOrDefault(emptyList())
        providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }?.let { return it }
        // Ask every enabled provider at once and take the first fix.
        val candidates = providers.filter { it != LocationManager.PASSIVE_PROVIDER }.ifEmpty { return null }
        return withTimeoutOrNull(20_000) {
            suspendCancellableCoroutine { cont ->
                val listeners = mutableListOf<LocationListener>()
                val signals = mutableListOf<android.os.CancellationSignal>()
                fun deliver(l: Location?) {
                    if (l != null && cont.isActive) {
                        cont.resume(l)
                        listeners.forEach { lm.removeUpdates(it) }
                        signals.forEach { it.cancel() }
                    }
                }
                candidates.forEach { p ->
                    runCatching {
                        if (Build.VERSION.SDK_INT >= 30) {
                            val sig = android.os.CancellationSignal().also { signals += it }
                            lm.getCurrentLocation(p, sig, context.mainExecutor) { deliver(it) }
                        } else {
                            val listener = object : LocationListener {
                                override fun onLocationChanged(location: Location) = deliver(location)
                                @Deprecated("Deprecated in Java")
                                override fun onStatusChanged(p: String?, s: Int, e: android.os.Bundle?) {}
                                override fun onProviderEnabled(p: String) {}
                                override fun onProviderDisabled(p: String) {}
                            }
                            listeners += listener
                            @Suppress("DEPRECATION") lm.requestSingleUpdate(p, listener, Looper.getMainLooper())
                        }
                    }
                }
                cont.invokeOnCancellation { listeners.forEach { lm.removeUpdates(it) }; signals.forEach { it.cancel() } }
            }
        }
    }

    private suspend fun placeName(loc: Location): String? = withContext(Dispatchers.IO) {
        runCatching {
            @Suppress("DEPRECATION")
            Geocoder(context).getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()?.let {
                it.locality ?: it.subAdminArea ?: it.adminArea
            }
        }.getOrNull()
    }
}
