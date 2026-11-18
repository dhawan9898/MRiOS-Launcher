package com.mrios.launcher.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CalendarEvent(val id: Long, val title: String, val begin: Long, val end: Long, val allDay: Boolean, val color: Int)

object CalendarRepository {
    fun hasPermission(context: Context) =
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    suspend fun upcoming(context: Context, days: Int = 2, limit: Int = 4): List<CalendarEvent> = withContext(Dispatchers.IO) {
        if (!hasPermission(context)) return@withContext emptyList()
        val now = System.currentTimeMillis()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, now - 6 * 3600_000L)
            ContentUris.appendId(it, now + days * 86_400_000L)
        }.build()
        val proj = arrayOf(
            CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN, CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.DISPLAY_COLOR,
        )
        val out = mutableListOf<CalendarEvent>()
        runCatching {
            context.contentResolver.query(uri, proj, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                while (c.moveToNext()) {
                    val end = c.getLong(3)
                    if (end < now) continue
                    out += CalendarEvent(c.getLong(0), c.getString(1).orEmpty(), c.getLong(2), end, c.getInt(4) == 1, c.getInt(5))
                    if (out.size >= limit) break
                }
            }
        }
        out
    }
}
