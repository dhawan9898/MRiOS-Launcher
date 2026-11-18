package com.mrios.launcher.system

import android.annotation.SuppressLint
import android.app.Activity
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.mrios.launcher.badges.BadgeListenerService

object SystemActions {

    /** Pulls down the notification shade (needs EXPAND_STATUS_BAR, a normal permission). */
    @SuppressLint("WrongConstant", "PrivateApi")
    fun expandNotifications(context: Context) {
        runCatching {
            val sbs = context.getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sbs)
        }
    }

    fun isDefaultLauncher(context: Context): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val res = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return res?.activityInfo?.packageName == context.packageName
    }

    const val REQUEST_HOME_ROLE = 41

    fun requestDefaultLauncher(activity: Activity) {
        if (Build.VERSION.SDK_INT >= 29) {
            val rm = activity.getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                runCatching { activity.startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), REQUEST_HOME_ROLE) }
                    .onSuccess { return }
            }
        }
        start(activity, Intent(Settings.ACTION_HOME_SETTINGS)) || start(activity, Intent(Settings.ACTION_SETTINGS))
    }

    fun openNotificationAccess(context: Context) {
        val detail = if (Build.VERSION.SDK_INT >= 30) Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, BadgeListenerService.component(context).flattenToString())
        else null
        if (detail == null || !start(context, detail)) start(context, Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
    }

    fun openAppSettings(context: Context) =
        start(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))

    fun webSearch(context: Context, query: String) {
        val ok = start(context, Intent(Intent.ACTION_WEB_SEARCH).putExtra("query", query))
        if (!ok) start(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))))
    }

    fun storeSearch(context: Context, query: String) {
        val ok = start(context, Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=" + Uri.encode(query))))
        if (!ok) start(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=" + Uri.encode(query))))
    }

    fun openCalendar(context: Context) {
        val uri = Uri.parse("content://com.android.calendar/time/" + System.currentTimeMillis())
        start(context, Intent(Intent.ACTION_VIEW, uri)) ||
            start(context, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR))
    }

    fun openClock(context: Context) {
        start(context, Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS))
    }

    fun start(context: Context, intent: Intent): Boolean = try {
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent); true
    } catch (e: ActivityNotFoundException) { false } catch (e: SecurityException) { false }
}
