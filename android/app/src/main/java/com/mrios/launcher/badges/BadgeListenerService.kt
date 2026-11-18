package com.mrios.launcher.badges

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Counts active notifications per package to draw red badges on icons. */
class BadgeListenerService : NotificationListenerService() {

    override fun onListenerConnected() = refresh()
    override fun onListenerDisconnected() { _counts.value = emptyMap() }
    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()

    private fun refresh() {
        val list = runCatching { activeNotifications }.getOrNull() ?: return
        _counts.value = list.asSequence()
            .filter { !it.isOngoing && it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 }
            .groupBy { it.packageName }
            .mapValues { (_, v) -> v.sumOf { sbn -> sbn.notification.number.takeIf { it > 0 } ?: 1 } }
    }

    companion object {
        private val _counts = MutableStateFlow<Map<String, Int>>(emptyMap())
        val counts: StateFlow<Map<String, Int>> = _counts.asStateFlow()

        fun isEnabled(context: Context) =
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

        fun component(context: Context) = ComponentName(context, BadgeListenerService::class.java)
    }
}
