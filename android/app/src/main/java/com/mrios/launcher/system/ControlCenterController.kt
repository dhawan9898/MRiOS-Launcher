package com.mrios.launcher.system

import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ControlState(
    val wifi: Boolean = false,
    val bluetooth: Boolean = false,
    val airplane: Boolean = false,
    val cellular: Boolean = false,
    val torch: Boolean = false,
    val torchAvailable: Boolean = false,
    val volume: Float = 0f,
    val brightness: Float = 0f,
    val canWriteSettings: Boolean = false,
    val rotationLocked: Boolean = false,
    val focus: Boolean = false,
    val focusAccess: Boolean = false,
    val silent: Boolean = false,
)

/** What a control did: changed something directly, or opened a system screen (so the panel should close). */
enum class ControlResult { DONE, OPENED }

/**
 * Reads and drives the toggles in Control Centre. Where Android lets an app change something
 * directly (torch, volume, brightness, rotation, Do Not Disturb, ringer) it does; otherwise it
 * opens the matching system panel (Wi-Fi, Bluetooth, mobile data, aeroplane mode on new Android).
 */
class ControlCenterController(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val wifiManager = context.applicationContext.getSystemService(WifiManager::class.java)
    private val btAdapter: BluetoothAdapter? = context.getSystemService(BluetoothManager::class.java)?.adapter
    private val audio = context.getSystemService(AudioManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val camera = context.getSystemService(CameraManager::class.java)
    private val torchId: String? = runCatching {
        camera.cameraIdList.firstOrNull { camera.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
    }.getOrNull()
    @Volatile private var torchOn = false

    private val _state = MutableStateFlow(ControlState())
    val state: StateFlow<ControlState> = _state.asStateFlow()

    private var users = 0

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) = refresh()
    }
    private val observer = object : ContentObserver(main) {
        override fun onChange(selfChange: Boolean) = refresh()
    }
    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == torchId) { torchOn = enabled; refresh() }
        }
    }
    private val netCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = refresh()
        override fun onLost(network: Network) = refresh()
    }

    /** Start observing while a panel is visible. Calls are reference-counted. */
    fun start() {
        if (users++ > 0) { refresh(); return }
        val filter = IntentFilter().apply {
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction("android.media.VOLUME_CHANGED_ACTION")
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        val cr = context.contentResolver
        cr.registerContentObserver(Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS), false, observer)
        cr.registerContentObserver(Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION), false, observer)
        runCatching { camera.registerTorchCallback(torchCallback, main) }
        runCatching { connectivity.registerDefaultNetworkCallback(netCallback) }
        refresh()
    }

    fun stop() {
        if (users == 0 || --users > 0) return
        runCatching { context.unregisterReceiver(receiver) }
        context.contentResolver.unregisterContentObserver(observer)
        runCatching { camera.unregisterTorchCallback(torchCallback) }
        runCatching { connectivity.unregisterNetworkCallback(netCallback) }
    }

    fun refresh() {
        val cr = context.contentResolver
        val maxVol = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        _state.value = ControlState(
            wifi = runCatching { wifiManager.isWifiEnabled }.getOrDefault(false),
            bluetooth = runCatching { btAdapter?.isEnabled == true }.getOrDefault(false),
            airplane = Settings.Global.getInt(cr, Settings.Global.AIRPLANE_MODE_ON, 0) == 1,
            cellular = runCatching {
                connectivity.getNetworkCapabilities(connectivity.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
            }.getOrDefault(false),
            torch = torchOn,
            torchAvailable = torchId != null,
            volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC) / maxVol.toFloat(),
            brightness = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f,
            canWriteSettings = Settings.System.canWrite(context),
            rotationLocked = Settings.System.getInt(cr, Settings.System.ACCELEROMETER_ROTATION, 1) == 0,
            focus = notifications.currentInterruptionFilter.let { it != NotificationManager.INTERRUPTION_FILTER_ALL && it != NotificationManager.INTERRUPTION_FILTER_UNKNOWN },
            focusAccess = notifications.isNotificationPolicyAccessGranted,
            silent = audio.ringerMode != AudioManager.RINGER_MODE_NORMAL,
        )
    }

    // ------------------------------------------------------------------ actions

    @Suppress("DEPRECATION")
    fun toggleWifi(): ControlResult =
        if (Build.VERSION.SDK_INT < 29 && runCatching { wifiManager.isWifiEnabled = !wifiManager.isWifiEnabled }.isSuccess) {
            done()
        } else open(Intent(if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_WIFI else Settings.ACTION_WIFI_SETTINGS))

    @Suppress("DEPRECATION", "MissingPermission")
    fun toggleBluetooth(): ControlResult {
        val a = btAdapter
        if (Build.VERSION.SDK_INT <= 30 && a != null && runCatching { if (a.isEnabled) a.disable() else a.enable() }.getOrDefault(false)) {
            return done()
        }
        return open(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
    }

    fun openCellular(): ControlResult =
        open(Intent(if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_INTERNET_CONNECTIVITY else Settings.ACTION_DATA_ROAMING_SETTINGS))

    fun openAirplane(): ControlResult = open(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS))

    fun toggleTorch(): ControlResult {
        val id = torchId ?: return ControlResult.DONE
        runCatching { camera.setTorchMode(id, !torchOn) }
        return ControlResult.DONE
    }

    fun setVolume(v: Float) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, (v * max).toInt().coerceIn(0, max), 0) }
        refresh()
    }

    /** Returns OPENED when the "modify system settings" permission has to be granted first. */
    fun setBrightness(v: Float): ControlResult {
        if (!Settings.System.canWrite(context)) return openWriteSettings()
        val cr = context.contentResolver
        runCatching {
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, (v * 255).toInt().coerceIn(1, 255))
        }
        refresh()
        return ControlResult.DONE
    }

    fun toggleRotationLock(): ControlResult {
        if (!Settings.System.canWrite(context)) return openWriteSettings()
        val locked = _state.value.rotationLocked
        runCatching { Settings.System.putInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (locked) 1 else 0) }
        refresh()
        return ControlResult.DONE
    }

    fun toggleFocus(): ControlResult {
        if (!notifications.isNotificationPolicyAccessGranted) return open(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        val on = _state.value.focus
        runCatching {
            notifications.setInterruptionFilter(if (on) NotificationManager.INTERRUPTION_FILTER_ALL else NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        }
        refresh()
        return ControlResult.DONE
    }

    fun toggleSilent(): ControlResult {
        val silent = audio.ringerMode != AudioManager.RINGER_MODE_NORMAL
        runCatching { audio.ringerMode = if (silent) AudioManager.RINGER_MODE_NORMAL else AudioManager.RINGER_MODE_VIBRATE }
        refresh()
        return ControlResult.DONE
    }

    fun openTimer() = open(Intent(AlarmClock.ACTION_SHOW_TIMERS), Intent(AlarmClock.ACTION_SET_TIMER))
    fun openAlarms() = open(Intent(AlarmClock.ACTION_SHOW_ALARMS))
    fun openCalculator() = open(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALCULATOR))
    fun openCamera() = open(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
    fun openScreenMirroring() = open(Intent(Settings.ACTION_CAST_SETTINGS), Intent(Settings.ACTION_WIRELESS_SETTINGS))
    fun openLocation() = open(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))

    fun openWriteSettings(): ControlResult =
        open(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + context.packageName)))

    fun openFocusAccess(): ControlResult = open(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))

    private fun done(): ControlResult { main.postDelayed({ refresh() }, 400); return ControlResult.DONE }

    private fun open(vararg intents: Intent): ControlResult {
        intents.firstOrNull { SystemActions.start(context, it) }
        return ControlResult.OPENED
    }
}
