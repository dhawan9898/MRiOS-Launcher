package com.mrios.launcher.system

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.mrios.launcher.LauncherApp
import com.mrios.launcher.data.ThemeMode
import com.mrios.launcher.launcherApp
import com.mrios.launcher.ui.MRiOSTheme
import com.mrios.launcher.ui.components.GlassContext
import com.mrios.launcher.ui.components.LocalGlass
import com.mrios.launcher.ui.components.drawWallpaper
import com.mrios.launcher.ui.components.LocalIconAppearance
import com.mrios.launcher.ui.components.iconAppearance
import com.mrios.launcher.ui.panels.PanelKind
import com.mrios.launcher.ui.panels.SystemPanel
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Shows Control Centre / Notification Centre over any app. Two invisible strips sit on the
 * status bar (top-left → Notification Centre, top-right → Control Centre); the middle is left
 * free so the system shade still works. It reads no screen content.
 */
class PanelService : AccessibilityService() {
    private lateinit var wm: WindowManager
    private val scope = MainScope()
    private val main = Handler(Looper.getMainLooper())
    private val strips = mutableListOf<View>()
    private var edges = true to true

    private var panelRoot: View? = null
    private var owner: OverlayOwner? = null
    private val panelKind = mutableStateOf(PanelKind.CONTROL_CENTER)
    private val panelVisible = mutableStateOf(false)
    private var shownAt = 0L

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) = hidePanel()
    }

    override fun attachBaseContext(base: Context) {
        val tag = (base.applicationContext as? LauncherApp)?.settings?.value?.language?.tag
        if (tag == null) { super.attachBaseContext(base); return }
        val config = Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
        super.attachBaseContext(base.createConfigurationContext(config))
    }

    override fun onServiceConnected() {
        instance = this
        wm = getSystemService(WindowManager::class.java)
        ContextCompat.registerReceiver(this, screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_EXPORTED)
        scope.launch {
            launcherApp.settings.state.map { it.edgeNotifications to it.edgeControlCenter }.distinctUntilChanged().collect {
                edges = it; layoutStrips()
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Close the panel only when a real app screen comes to the front (e.g. one we just opened).
        // System UI (heads-up notifications, the shade) and dialogs/popups are ignored.
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || panelRoot == null) return
        val pkg = event.packageName?.toString() ?: return
        val cls = event.className?.toString() ?: return
        if (pkg == packageName || pkg == "com.android.systemui") return
        if (SystemClock.uptimeMillis() - shownAt < 700) return
        val isActivity = runCatching { packageManager.getActivityInfo(ComponentName(pkg, cls), 0) }.isSuccess
        if (isActivity) hidePanel()
    }

    override fun onInterrupt() = Unit

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        layoutStrips()
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        runCatching { unregisterReceiver(screenOff) }
        removePanelNow()
        strips.forEach { runCatching { wm.removeView(it) } }
        strips.clear()
        scope.cancel()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ edge strips

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    @SuppressLint("DiscouragedApi", "InternalInsetResource")
    private fun systemDimen(name: String): Int {
        val id = resources.getIdentifier(name, "dimen", "android")
        return if (id != 0) resources.getDimensionPixelSize(id) else 0
    }

    private fun layoutStrips() {
        strips.forEach { runCatching { wm.removeView(it) } }
        strips.clear()
        val width = resources.displayMetrics.widthPixels
        val height = systemDimen("status_bar_height").coerceAtLeast(dp(24))
        if (edges.first) addStrip(Gravity.START, (width * 0.40f).toInt(), height, PanelKind.NOTIFICATION_CENTER)
        if (edges.second) addStrip(Gravity.END, (width * 0.35f).toInt(), height, PanelKind.CONTROL_CENTER)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addStrip(gravity: Int, width: Int, height: Int, kind: PanelKind) {
        val v = View(this)
        var downY = 0f
        var fired = false
        v.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downY = e.rawY; fired = false }
                // System gesture monitors may take the pointer over (ACTION_CANCEL) after a few pixels,
                // so trigger early, and treat a cancelled downward drag as a swipe too.
                MotionEvent.ACTION_MOVE, MotionEvent.ACTION_CANCEL -> if (!fired && e.rawY - downY > dp(10)) {
                    fired = true
                    showPanel(kind)
                }
            }
            true
        }
        val p = WindowManager.LayoutParams(
            width, height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            this.gravity = Gravity.TOP or gravity
            if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        runCatching { wm.addView(v, p); strips += v }
    }

    // ------------------------------------------------------------------ panel window

    fun showPanel(kind: PanelKind) {
        panelKind.value = kind
        if (panelRoot != null) { panelVisible.value = true; return }
        val o = OverlayOwner().also { it.create() }
        val root = object : FrameLayout(this) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                    if (event.action == KeyEvent.ACTION_UP) hidePanel()
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }
        root.setViewTreeLifecycleOwner(o)
        root.setViewTreeSavedStateRegistryOwner(o)
        val density = resources.displayMetrics.density
        val insets = PaddingValues(
            top = (systemDimen("status_bar_height") / density).dp,
            bottom = (systemDimen("navigation_bar_height") / density).dp,
        )
        root.addView(ComposeView(this).apply {
            setContent {
                val settings by launcherApp.settings.state.collectAsState()
                val visible = panelVisible.value
                CompositionLocalProvider(
                    // The window itself is blurred by the compositor, so panels use the plain tinted material.
                    LocalGlass provides GlassContext(ThemeMode.TRANSPARENT, null, IntSize.Zero),
                    LocalIconAppearance provides settings.iconAppearance(),
                ) {
                    MRiOSTheme {
                        AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
                            if (Build.VERSION.SDK_INT >= 31) {
                                // The compositor blurs whatever app is behind the window.
                                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.36f)))
                            } else {
                                // No cross-window blur before Android 12: cover the app with the frosted wallpaper.
                                val wp by launcherApp.wallpaper.state.collectAsState()
                                Box(
                                    Modifier.fillMaxSize()
                                        .drawBehind {
                                            val img = wp?.blurred
                                            if (img != null) drawWallpaper(img, IntSize(size.width.toInt(), size.height.toInt()))
                                        }
                                        .background(Color.Black.copy(alpha = if (wp != null) 0.35f else 0.85f))
                                )
                            }
                        }
                        AnimatedVisibility(
                            visible,
                            enter = slideInVertically { -it / 5 } + fadeIn(),
                            exit = slideOutVertically { -it / 5 } + fadeOut(),
                        ) { SystemPanel(panelKind.value, insets, onClose = ::hidePanel) }
                    }
                }
            }
        })
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            if (Build.VERSION.SDK_INT >= 31) {
                flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                blurBehindRadius = 90
            }
        }
        runCatching { wm.addView(root, p) }.onFailure {
            android.util.Log.w("PanelService", "Could not show panel", it)
            o.destroy(); return
        }
        o.resume()
        panelRoot = root
        owner = o
        shownAt = SystemClock.uptimeMillis()
        main.post { panelVisible.value = true }
    }

    fun hidePanel() {
        if (panelRoot == null) return
        panelVisible.value = false
        main.postDelayed({ if (!panelVisible.value) removePanelNow() }, 280)
    }

    private fun removePanelNow() {
        panelRoot?.let { runCatching { wm.removeView(it) } }
        owner?.destroy()
        panelRoot = null
        owner = null
    }

    companion object {
        @Volatile private var instance: PanelService? = null

        fun hide() { instance?.hidePanel() }

        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            val me = ComponentName(context, PanelService::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }

        fun openSettings(context: Context) = SystemActions.start(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }
}

/** Minimal lifecycle + saved-state owner so Compose can run inside a service window. */
private class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    fun create() {
        savedState.performRestore(null)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }
    fun resume() {
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }
    fun destroy() {
        if (registry.currentState != Lifecycle.State.DESTROYED && registry.currentState != Lifecycle.State.INITIALIZED) {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
    }
}
