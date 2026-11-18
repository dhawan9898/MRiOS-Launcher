package com.mrios.launcher.ui

import android.app.ActivityOptions
import android.app.Application
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import android.view.View
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mrios.launcher.R
import com.mrios.launcher.badges.BadgeListenerService
import com.mrios.launcher.data.AppCategory
import com.mrios.launcher.data.AppEntry
import com.mrios.launcher.data.DropTarget
import com.mrios.launcher.data.Grid
import com.mrios.launcher.data.Settings
import com.mrios.launcher.launcherApp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min

val Settings.grid: Grid get() = Grid(columns, rows, widgetRows)

data class HomeState(val pages: List<List<AppEntry>>, val dock: List<AppEntry>) {
    val onHome: Set<String> = (pages.flatten() + dock).map { it.id }.toSet()
}

enum class GroupKind { SUGGESTIONS, RECENT, CATEGORY }

data class LibraryGroup(val id: String, @StringRes val title: Int, val apps: List<AppEntry>, val kind: GroupKind)

class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    /**
     * Open overlays and edit mode live here (not in composition) so they survive the activity being
     * recreated — e.g. when a new wallpaper changes the system colours or the language is switched.
     */
    val overlays = androidx.compose.runtime.mutableStateListOf<com.mrios.launcher.ui.overlays.Overlay>()
    var editMode by androidx.compose.runtime.mutableStateOf(false)
    private val la = application.launcherApp
    private val eager = SharingStarted.Eagerly

    val settings: StateFlow<Settings> = la.settings.state
    val layout = la.layout.state
    val wallpaper = la.wallpaper.state
    val weather = la.weather.state

    val badges: StateFlow<Map<String, Int>> = combine(BadgeListenerService.counts, settings) { c, s ->
        if (s.badges) c else emptyMap()
    }.stateIn(viewModelScope, eager, emptyMap())

    /** Installed apps with the user's renames applied. */
    val allApps: StateFlow<List<AppEntry>> = combine(
        la.apps.apps, layout.map { it.renames }.distinctUntilChanged()
    ) { list, renames ->
        list.map { e -> renames[e.id]?.let { e.copy(label = it) } ?: e }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }.stateIn(viewModelScope, eager, emptyList())

    val visibleApps: StateFlow<List<AppEntry>> = combine(allApps, layout) { apps, l -> apps.filter { it.id !in l.hidden } }
        .stateIn(viewModelScope, eager, emptyList())

    val hiddenApps: StateFlow<List<AppEntry>> = combine(allApps, layout) { apps, l -> apps.filter { it.id in l.hidden } }
        .stateIn(viewModelScope, eager, emptyList())

    val home: StateFlow<HomeState> = combine(allApps, layout) { apps, l ->
        val byId = apps.associateBy { it.id }
        HomeState(l.pages.map { p -> p.mapNotNull(byId::get) }, l.dock.mapNotNull(byId::get))
    }.stateIn(viewModelScope, eager, HomeState(listOf(emptyList()), emptyList()))

    val library: StateFlow<List<LibraryGroup>> = combine(visibleApps, layout) { apps, l ->
        val launches = l.launches
        val groups = mutableListOf<LibraryGroup>()
        val recentUse = apps.filter { launches[it.id] != null }.sortedByDescending { launches[it.id]!!.last }.take(8)
        if (recentUse.isNotEmpty()) groups += LibraryGroup("suggestions", R.string.cat_suggestions, recentUse, GroupKind.SUGGESTIONS)
        val added = apps.filter { !it.isSystem }.sortedByDescending { it.installTime }.take(8)
        if (added.isNotEmpty()) groups += LibraryGroup("recent", R.string.cat_recent, added, GroupKind.RECENT)
        val byCat = apps.groupBy { it.category }
        AppCategory.entries
            .mapNotNull { cat -> byCat[cat]?.let { cat to it } }
            .sortedByDescending { (_, list) -> list.sumOf { launches[it.id]?.count ?: 0 } }
            .forEach { (cat, list) ->
                groups += LibraryGroup(cat.name, cat.title,
                    list.sortedWith(compareByDescending<AppEntry> { launches[it.id]?.count ?: 0 }.thenBy { it.label.lowercase() }),
                    GroupKind.CATEGORY)
            }
        groups
    }.stateIn(viewModelScope, eager, emptyList())

    val recents: StateFlow<List<AppEntry>> = combine(visibleApps, layout) { apps, l ->
        apps.filter { l.launches[it.id] != null }.sortedByDescending { l.launches[it.id]!!.last }.take(8)
    }.stateIn(viewModelScope, eager, emptyList())

    private val _homePressed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val homePressed: SharedFlow<Unit> = _homePressed
    private val _openSettings = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val openSettings: SharedFlow<Unit> = _openSettings

    init {
        // Keep stored layout in sync with installed apps.
        viewModelScope.launch {
            combine(la.apps.apps, la.apps.loaded) { a, loaded -> a to loaded }
                .filter { it.second }
                .collect { (apps, _) ->
                    val s = settings.value
                    la.layout.reconcile(apps.map { it.id }, la.apps.defaultDockIds(), s.grid, s.addNewAppsToHome)
                }
        }
        viewModelScope.launch {
            settings.map { it.grid }.distinctUntilChanged().drop(1).collect { la.layout.repack(it) }
        }
        viewModelScope.launch {
            combine(
                settings.map { it.iconPack }.distinctUntilChanged(),
                layout.map { it.customIcons }.distinctUntilChanged(),
                la.apps.packageEvents,
            ) { _, _, _ -> }.drop(1).collect { la.icons.invalidate() }
        }
        viewModelScope.launch {
            settings.map { it.weatherPlace to it.celsius }.distinctUntilChanged().drop(1).collect { la.weather.refresh(force = true) }
        }
    }

    private val grid get() = settings.value.grid

    // ---------------------------------------------------------------- events
    fun onHomePressed() { _homePressed.tryEmit(Unit) }
    fun requestSettings() { _openSettings.tryEmit(Unit) }
    fun onResume() {
        la.weather.refresh()
        la.apps.reload()
    }
    fun refreshWeather(force: Boolean = true) = la.weather.refresh(force)

    // ---------------------------------------------------------------- app actions
    fun launch(entry: AppEntry, bounds: Rect?, view: View?) {
        val opts = if (bounds != null && view != null) ActivityOptions.makeClipRevealAnimation(
            view, bounds.left, bounds.top, bounds.width(), bounds.height()
        ).toBundle() else null
        if (la.apps.launch(entry, bounds, opts)) la.layout.recordLaunch(entry.id)
        else toast(R.string.open_failed)
    }

    fun appInfo(entry: AppEntry, bounds: Rect?) = la.apps.openAppInfo(entry, bounds)
    fun uninstall(entry: AppEntry) {
        if (entry.isSystem) toast(R.string.toast_cannot_uninstall) else la.apps.uninstall(entry)
    }
    fun shortcuts(entry: AppEntry): List<ShortcutInfo> = la.apps.shortcuts(entry)
    fun shortcutIcon(info: ShortcutInfo) = la.apps.shortcutIcon(info)
    fun startShortcut(info: ShortcutInfo, bounds: Rect?) = la.apps.startShortcut(info, bounds)

    fun move(entry: AppEntry, target: DropTarget) = la.layout.move(entry.id, target, grid)
    fun removeFromHome(entry: AppEntry) = la.layout.removeFromHome(entry.id, grid)
    fun addToHome(entry: AppEntry) = la.layout.addToHome(entry.id, grid)
    fun setHidden(entry: AppEntry, hidden: Boolean) = la.layout.setHidden(entry.id, hidden, grid)
    fun rename(entry: AppEntry, label: String?) = la.layout.rename(entry.id, label?.takeIf { it != entry.originalLabel })
    fun customIconSpec(entry: AppEntry): String? = layout.value.customIcons[entry.id]
    fun setPackIcon(entry: AppEntry, pack: String, drawable: String) = la.layout.setCustomIcon(entry.id, "pack:$pack:$drawable")
    fun resetIcon(entry: AppEntry) = la.layout.setCustomIcon(entry.id, null)

    fun setIconFromUri(entry: AppEntry, uri: Uri) = viewModelScope.launch {
        val app = getApplication<Application>()
        val path = withContext(Dispatchers.IO) {
            runCatching {
                val src = app.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it) }!!
                val s = min(src.width, src.height)
                val sq = Bitmap.createBitmap(src, (src.width - s) / 2, (src.height - s) / 2, s, s)
                val scaled = Bitmap.createScaledBitmap(sq, 256, 256, true)
                val dir = File(app.filesDir, "icons").apply { mkdirs() }
                val f = File(dir, "${entry.id.hashCode()}_${System.currentTimeMillis()}.png")
                f.outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
                f.path
            }.getOrNull()
        }
        if (path != null) la.layout.setCustomIcon(entry.id, "file:$path")
    }

    // ---------------------------------------------------------------- settings
    fun updateSettings(block: (Settings) -> Settings) = la.settings.update(block)

    suspend fun searchCity(q: String) = la.weather.searchCity(q)
    fun iconPacks() = la.icons.packs.installed()
    fun packIconNames(pack: String) = la.icons.packs.allIconNames(pack)

    suspend fun decodeWallpaper(uri: Uri) = la.wallpaper.decode(uri)
    fun applyWallpaper(bitmap: Bitmap, flags: Int) = viewModelScope.launch { la.wallpaper.apply(bitmap, flags) }
    fun resetWallpaper() = viewModelScope.launch { la.wallpaper.resetToDefault(applySystem = true) }

    private fun toast(@StringRes res: Int) =
        Toast.makeText(getApplication(), res, Toast.LENGTH_SHORT).show()
}
