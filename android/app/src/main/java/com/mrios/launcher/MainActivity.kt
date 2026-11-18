package com.mrios.launcher

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import com.mrios.launcher.ui.LauncherScreen
import com.mrios.launcher.ui.LauncherViewModel
import com.mrios.launcher.ui.MRiOSTheme
import java.util.Locale

class MainActivity : FragmentActivity() {
    private val vm: LauncherViewModel by viewModels()

    override fun attachBaseContext(newBase: Context) {
        val tag = (newBase.applicationContext as? LauncherApp)?.settings?.value?.language?.tag
        if (tag == null) { super.attachBaseContext(newBase); return }
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(Locale.forLanguageTag(tag))
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        if (Build.VERSION.SDK_INT >= 29) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        handleIntent(intent, fresh = true)
        setContent { MRiOSTheme { LauncherScreen(vm, this) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent, fresh = false)
    }

    private fun handleIntent(intent: Intent?, fresh: Boolean) {
        intent ?: return
        if (intent.component?.className?.endsWith("SettingsAlias") == true) {
            vm.requestSettings()
        } else if (!fresh && intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            vm.onHomePressed()
        }
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
        com.mrios.launcher.system.PanelService.hide() // Home pressed while a panel was open over another app

    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // A launcher never finishes on back; Compose BackHandlers handle overlays.
        if (onBackPressedDispatcher.hasEnabledCallbacks()) {
            @Suppress("DEPRECATION") super.onBackPressed()
        }
    }
}
