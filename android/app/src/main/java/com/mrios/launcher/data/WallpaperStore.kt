package com.mrios.launcher.data

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class WallpaperImages(val sharp: ImageBitmap, val blurred: ImageBitmap, val isCustom: Boolean)

/**
 * The launcher keeps its own copy of the wallpaper so it can draw real frosted glass:
 * glass panels paint a pre-blurred copy of the wallpaper behind themselves.
 */
class WallpaperStore(private val context: Context, scope: CoroutineScope) {
    private val file = File(context.filesDir, "wallpaper.jpg")
    private val _state = MutableStateFlow<WallpaperImages?>(null)
    val state: StateFlow<WallpaperImages?> = _state.asStateFlow()

    init {
        scope.launch(Dispatchers.Default) { reload() }
    }

    private fun screenSize(): Pair<Int, Int> {
        val wm = context.getSystemService(WindowManager::class.java)
        val (w, h) = if (Build.VERSION.SDK_INT >= 30) {
            val b = wm.maximumWindowMetrics.bounds; b.width() to b.height()
        } else {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(dm)
            dm.widthPixels to dm.heightPixels
        }
        // Portrait, and capped so the bitmap stays light in memory.
        val pw = min(w, h); val ph = max(w, h)
        val scale = min(1f, 1080f / pw)
        return (pw * scale).roundToInt() to (ph * scale).roundToInt()
    }

    private suspend fun reload() = withContext(Dispatchers.Default) {
        val (w, h) = screenSize()
        val custom = if (file.exists()) BitmapFactory.decodeFile(file.path) else null
        val sharp = custom ?: defaultWallpaper(w, h)
        _state.value = WallpaperImages(sharp.asImageBitmap(), frost(sharp).asImageBitmap(), custom != null)
    }

    /** Decodes a picked photo, cropped to the screen, for previewing. */
    suspend fun decode(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        val (w, h) = screenSize()
        runCatching {
            val src: Bitmap = if (Build.VERSION.SDK_INT >= 28) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { dec, info, _ ->
                    val s = max(w.toFloat() / info.size.width, h.toFloat() / info.size.height).coerceAtMost(1f)
                    dec.setTargetSize((info.size.width * s).roundToInt().coerceAtLeast(1), (info.size.height * s).roundToInt().coerceAtLeast(1))
                    dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
                var sample = 1
                while (o.outWidth / (sample * 2) >= w && o.outHeight / (sample * 2) >= h) sample *= 2
                context.contentResolver.openInputStream(uri)!!.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })!!
                }
            }
            cropTo(src, w, h)
        }.getOrNull()
    }

    /** @param systemFlags 0 to keep the system wallpaper, otherwise WallpaperManager.FLAG_* bits. */
    suspend fun apply(bitmap: Bitmap, systemFlags: Int) = withContext(Dispatchers.IO) {
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        reload()
        if (systemFlags != 0) runCatching {
            WallpaperManager.getInstance(context).setBitmap(bitmap, null, true, systemFlags)
        }
    }

    suspend fun resetToDefault(applySystem: Boolean) = withContext(Dispatchers.IO) {
        file.delete()
        reload()
        if (applySystem) runCatching {
            val (w, h) = screenSize()
            WallpaperManager.getInstance(context).setBitmap(defaultWallpaper(w, h), null, true, WallpaperManager.FLAG_SYSTEM)
        }
    }

    private fun cropTo(src: Bitmap, w: Int, h: Int): Bitmap {
        val scale = max(w.toFloat() / src.width, h.toFloat() / src.height)
        val sw = (w / scale).roundToInt().coerceAtMost(src.width)
        val sh = (h / scale).roundToInt().coerceAtMost(src.height)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val left = (src.width - sw) / 2
        val top = (src.height - sh) / 2
        Canvas(out).drawBitmap(src, Rect(left, top, left + sw, top + sh), Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    companion object {
        /** Original abstract "liquid" wallpaper in the MRiOS palette. */
        fun defaultWallpaper(w: Int, h: Int): Bitmap {
            val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(out)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), intArrayOf(0xFF0B1240.toInt(), 0xFF1B0F3B.toInt(), 0xFF071A2E.toInt()), null, Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
            fun blob(cx: Float, cy: Float, r: Float, color: Int) {
                p.shader = RadialGradient(cx * w, cy * h, r * w, intArrayOf(color, color and 0x00FFFFFF), null, Shader.TileMode.CLAMP)
                c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
            }
            blob(0.15f, 0.18f, 0.95f, 0xCC2FB7E8.toInt())
            blob(0.95f, 0.38f, 0.85f, 0xCC8B5CF6.toInt())
            blob(0.25f, 0.72f, 0.9f, 0xBBEC4899.toInt())
            blob(0.85f, 0.95f, 0.75f, 0xAA3B82F6.toInt())
            blob(0.55f, 0.5f, 0.45f, 0x66FFFFFF)
            return out
        }

        /** Heavy, slightly saturated blur — the "glass" material. */
        fun frost(src: Bitmap): Bitmap {
            val tw = 108
            val th = (src.height * tw.toFloat() / src.width).roundToInt().coerceAtLeast(1)
            val small = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(1.6f) })
            }
            Canvas(small).drawBitmap(src, Rect(0, 0, src.width, src.height), Rect(0, 0, tw, th), paint)
            return boxBlur(small, radius = 4, passes = 3)
        }

        private fun boxBlur(bmp: Bitmap, radius: Int, passes: Int): Bitmap {
            val w = bmp.width; val h = bmp.height
            val a = IntArray(w * h); val b = IntArray(w * h)
            bmp.getPixels(a, 0, w, 0, 0, w, h)
            repeat(passes) {
                pass(a, b, w, h, radius, horizontal = true)
                pass(b, a, w, h, radius, horizontal = false)
            }
            return Bitmap.createBitmap(a, w, h, Bitmap.Config.ARGB_8888)
        }

        private fun pass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
            val n = 2 * r + 1
            for (y in 0 until h) for (x in 0 until w) {
                var sa = 0; var sr = 0; var sg = 0; var sb = 0
                for (k in -r..r) {
                    val px = if (horizontal) src[y * w + (x + k).coerceIn(0, w - 1)]
                    else src[(y + k).coerceIn(0, h - 1) * w + x]
                    sa += px ushr 24; sr += (px shr 16) and 0xFF; sg += (px shr 8) and 0xFF; sb += px and 0xFF
                }
                dst[y * w + x] = ((sa / n) shl 24) or ((sr / n) shl 16) or ((sg / n) shl 8) or (sb / n)
            }
        }
    }
}
