package com.mrios.launcher

import android.app.Application
import android.content.Context
import com.mrios.launcher.data.AppRepository
import com.mrios.launcher.data.IconLoader
import com.mrios.launcher.data.IconPacks
import com.mrios.launcher.data.LayoutStore
import com.mrios.launcher.data.SettingsStore
import com.mrios.launcher.data.WallpaperStore
import com.mrios.launcher.data.WeatherRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class LauncherApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    lateinit var settings: SettingsStore; private set
    lateinit var layout: LayoutStore; private set
    lateinit var apps: AppRepository; private set
    lateinit var icons: IconLoader; private set
    lateinit var wallpaper: WallpaperStore; private set
    lateinit var weather: WeatherRepository; private set
    val controls by lazy { com.mrios.launcher.system.ControlCenterController(this) }
    val media by lazy { com.mrios.launcher.system.MediaState(this) }

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        layout = LayoutStore(this)
        apps = AppRepository(this, scope)
        icons = IconLoader(this, apps, layout, settings, IconPacks(this))
        wallpaper = WallpaperStore(this, scope)
        weather = WeatherRepository(this, settings, scope)
    }
}

val Context.launcherApp: LauncherApp get() = applicationContext as LauncherApp
