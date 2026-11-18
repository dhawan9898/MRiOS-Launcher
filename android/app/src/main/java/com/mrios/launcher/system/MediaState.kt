package com.mrios.launcher.system

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.mrios.launcher.badges.BadgeListenerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NowPlaying(
    val title: String,
    val artist: String,
    val art: ImageBitmap?,
    val playing: Boolean,
    val packageName: String,
)

/** "Now Playing" for Control Centre, from the active media session (needs notification access). */
class MediaState(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val sessions = context.getSystemService(MediaSessionManager::class.java)
    private val _state = MutableStateFlow<NowPlaying?>(null)
    val state: StateFlow<NowPlaying?> = _state.asStateFlow()

    private var controller: MediaController? = null
    private var users = 0

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onSessionDestroyed() = pick()
    }
    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { pick() }

    fun start() {
        if (users++ > 0) { pick(); return }
        runCatching { sessions.addOnActiveSessionsChangedListener(sessionsListener, BadgeListenerService.component(context), main) }
        pick()
    }

    fun stop() {
        if (users == 0 || --users > 0) return
        runCatching { sessions.removeOnActiveSessionsChangedListener(sessionsListener) }
        controller?.unregisterCallback(controllerCallback)
        controller = null
    }

    private fun pick() {
        val list = runCatching { sessions.getActiveSessions(BadgeListenerService.component(context)) }.getOrDefault(emptyList())
        val next = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list.firstOrNull()
        if (next?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(controllerCallback)
            controller = next
            next?.registerCallback(controllerCallback, main)
        }
        publish()
    }

    private fun publish() {
        val c = controller
        val md = c?.metadata
        if (c == null || md == null) { _state.value = null; return }
        val title = md.getString(MediaMetadata.METADATA_KEY_TITLE) ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE).orEmpty()
        if (title.isBlank()) { _state.value = null; return } // idle sessions with no track
        val art: Bitmap? = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        _state.value = NowPlaying(
            title = title,
            artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).orEmpty(),
            art = art?.let { runCatching { it.asImageBitmap() }.getOrNull() },
            playing = c.playbackState?.state == PlaybackState.STATE_PLAYING,
            packageName = c.packageName,
        )
    }

    fun playPause() {
        val c = controller ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
    }

    fun next() { controller?.transportControls?.skipToNext() }
    fun previous() { controller?.transportControls?.skipToPrevious() }

    fun openApp() {
        val c = controller ?: return
        c.sessionActivity?.let { runCatching { it.send() }.onSuccess { return } }
        context.packageManager.getLaunchIntentForPackage(c.packageName)?.let { SystemActions.start(context, it) }
    }
}
