package com.mrios.launcher.badges

import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One notification as shown in the iOS-style Notification Centre. */
data class NotificationItem(
    val key: String,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val postTime: Long,
    val clearable: Boolean,
    val autoCancel: Boolean,
    val intent: PendingIntent?,
)

/**
 * Notification access powers three things: icon badges, the Notification Centre list and
 * (via MediaSessionManager) the music controls in Control Centre.
 */
class BadgeListenerService : NotificationListenerService() {

    private val labels = HashMap<String, String>()

    override fun onListenerConnected() { instance = this; refresh() }
    override fun onListenerDisconnected() {
        if (instance === this) instance = null
        _counts.value = emptyMap(); _items.value = emptyList()
    }
    override fun onDestroy() { if (instance === this) instance = null; super.onDestroy() }
    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()

    private fun refresh() {
        val list = runCatching { activeNotifications }.getOrNull() ?: return
        val visible = list.filter { !it.isOngoing && it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 }
        _counts.value = visible.groupBy { it.packageName }
            .mapValues { (_, v) -> v.sumOf { sbn -> sbn.notification.number.takeIf { it > 0 } ?: 1 } }
        _items.value = visible.mapNotNull { toItem(it) }.sortedByDescending { it.postTime }
    }

    private fun toItem(sbn: StatusBarNotification): NotificationItem? {
        val n = sbn.notification
        val ex = n.extras
        if (ex.containsKey(Notification.EXTRA_MEDIA_SESSION)) return null // shown as Now Playing instead
        val title = (ex.getCharSequence(Notification.EXTRA_TITLE) ?: ex.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE))
            ?.toString().orEmpty()
        val text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT))
            ?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return null
        return NotificationItem(
            key = sbn.key,
            packageName = sbn.packageName,
            appLabel = label(sbn.packageName),
            title = title,
            text = text,
            postTime = sbn.postTime,
            clearable = sbn.isClearable,
            autoCancel = n.flags and Notification.FLAG_AUTO_CANCEL != 0,
            intent = n.contentIntent,
        )
    }

    private fun label(pkg: String): String = labels.getOrPut(pkg) {
        runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
    }

    companion object {
        private val _counts = MutableStateFlow<Map<String, Int>>(emptyMap())
        val counts: StateFlow<Map<String, Int>> = _counts.asStateFlow()

        private val _items = MutableStateFlow<List<NotificationItem>>(emptyList())
        val items: StateFlow<List<NotificationItem>> = _items.asStateFlow()

        @Volatile private var instance: BadgeListenerService? = null

        fun isEnabled(context: Context) =
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

        fun component(context: Context) = ComponentName(context, BadgeListenerService::class.java)

        fun dismiss(item: NotificationItem) { runCatching { instance?.cancelNotification(item.key) } }

        fun dismissAll(items: List<NotificationItem>) {
            val keys = items.filter { it.clearable }.map { it.key }.toTypedArray()
            if (keys.isNotEmpty()) runCatching { instance?.cancelNotifications(keys) }
        }

        /** Opens the notification's target, as tapping it in the system shade would. */
        fun open(context: Context, item: NotificationItem): Boolean {
            val pi = item.intent ?: return false
            val ok = runCatching {
                val opts = if (Build.VERSION.SDK_INT >= 34) ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                    .toBundle() else null
                pi.send(context, 0, null, null, null, null, opts)
            }.isSuccess
            if (ok && item.autoCancel) dismiss(item)
            return ok
        }
    }
}
