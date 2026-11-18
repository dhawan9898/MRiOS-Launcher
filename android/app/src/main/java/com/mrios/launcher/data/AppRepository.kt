package com.mrios.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.provider.AlarmClock
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Raw installed launchable activities (labels here are the system labels). */
class AppRepository(private val context: Context, private val scope: CoroutineScope) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val pm = context.packageManager

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps.asStateFlow()
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    /** Bumped whenever a package changes so icon caches can refresh. */
    private val _packageEvents = MutableStateFlow(0)
    val packageEvents: StateFlow<Int> = _packageEvents.asStateFlow()

    private var reloadJob: Job? = null

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = changed()
        override fun onPackageAdded(packageName: String, user: UserHandle) = changed()
        override fun onPackageChanged(packageName: String, user: UserHandle) = changed()
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = changed()
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = changed()
    }

    init {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        reload()
    }

    private fun changed() {
        _packageEvents.value++
        reload()
    }

    fun reload() {
        reloadJob?.cancel()
        reloadJob = scope.launch {
            delay(150) // coalesce bursts of package events
            val list = withContext(Dispatchers.IO) { query() }
            _apps.value = list
            _loaded.value = true
        }
    }

    private fun query(): List<AppEntry> {
        val me = Process.myUserHandle()
        val selfAlias = ComponentName(context, "com.mrios.launcher.SettingsAlias")
        return userManager.userProfiles.flatMap { user ->
            val serial = userManager.getSerialNumberForUser(user)
            runCatching { launcherApps.getActivityList(null, user) }.getOrDefault(emptyList()).map { lai ->
                val label = lai.label?.toString()?.trim().orEmpty().ifEmpty { lai.componentName.packageName }
                val ai: ApplicationInfo = lai.applicationInfo
                AppEntry(
                    key = AppKey(lai.componentName, serial),
                    user = user,
                    originalLabel = label,
                    label = label,
                    category = if (lai.componentName == selfAlias) AppCategory.UTILITIES
                    else Categorizer.categorize(ai, lai.componentName.packageName, label),
                    installTime = lai.firstInstallTime,
                    isSystem = ai.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                    isWorkProfile = user != me,
                )
            }
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }

    fun launcherActivityInfo(entry: AppEntry) =
        runCatching { launcherApps.resolveActivity(Intent().setComponent(entry.key.component), entry.user) }.getOrNull()

    fun launch(entry: AppEntry, bounds: Rect?, options: Bundle?): Boolean = runCatching {
        launcherApps.startMainActivity(entry.key.component, entry.user, bounds, options)
        true
    }.getOrDefault(false)

    fun openAppInfo(entry: AppEntry, bounds: Rect?) = runCatching {
        launcherApps.startAppDetailsActivity(entry.key.component, entry.user, bounds, null)
    }

    fun uninstall(entry: AppEntry) = runCatching {
        val intent = Intent(Intent.ACTION_DELETE, Uri.fromParts("package", entry.packageName, null))
            .putExtra(Intent.EXTRA_USER, entry.user)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    // ---------------------------------------------------------------- shortcuts (quick actions)

    fun hasShortcutPermission(): Boolean = runCatching { launcherApps.hasShortcutHostPermission() }.getOrDefault(false)

    fun shortcuts(entry: AppEntry): List<ShortcutInfo> {
        if (!hasShortcutPermission()) return emptyList()
        val q = LauncherApps.ShortcutQuery()
            .setPackage(entry.packageName)
            .setActivity(entry.key.component)
            .setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
            )
        return runCatching { launcherApps.getShortcuts(q, entry.user).orEmpty() }.getOrDefault(emptyList())
            .filter { it.isEnabled }
            .sortedWith(compareBy({ !it.isDynamic }, { it.rank }))
            .take(4)
    }

    fun shortcutIcon(info: ShortcutInfo) =
        runCatching { launcherApps.getShortcutIconDrawable(info, context.resources.displayMetrics.densityDpi) }.getOrNull()

    fun startShortcut(info: ShortcutInfo, bounds: Rect?) = runCatching {
        launcherApps.startShortcut(info, bounds, null)
    }

    // ---------------------------------------------------------------- defaults

    /** Phone, Messages, Browser, Camera — like a fresh iPhone dock. */
    fun defaultDockIds(): List<String> {
        val serial = userManager.getSerialNumberForUser(Process.myUserHandle())
        val intents = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")),
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
        )
        val launchables = _apps.value.filter { !it.isWorkProfile }
        return intents.mapNotNull { intent ->
            // The default handler, or — when none is chosen (resolver = "android") — the first app that can handle it.
            val default = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
                ?.takeIf { it != "android" }
            val candidates = listOfNotNull(default) +
                pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).map { it.activityInfo.packageName }
            candidates.firstNotNullOfOrNull { pkg ->
                launchables.firstOrNull { it.packageName == pkg && it.key.userSerial == serial }?.id
            }
        }.distinct()
    }

    fun clockIntent() = Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
