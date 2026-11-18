package com.mrios.launcher.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/** Superellipse ("squircle") outline, the continuous-corner shape used for app icons. */
fun squirclePath(width: Float, height: Float, left: Float = 0f, top: Float = 0f, n: Double = 5.0): Path {
    val path = Path()
    val rx = width / 2f
    val ry = height / 2f
    val steps = 96
    for (i in 0..steps) {
        val t = 2.0 * Math.PI * i / steps
        val c = cos(t)
        val s = sin(t)
        val x = left + rx + rx * (sign(c) * abs(c).pow(2.0 / n)).toFloat()
        val y = top + ry + ry * (sign(s) * abs(s).pow(2.0 / n)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

class IconLoader(
    private val context: Context,
    private val repo: AppRepository,
    private val layout: LayoutStore,
    private val settings: SettingsStore,
    val packs: IconPacks,
) {
    private val size = 192
    private val cache = LruCache<String, ImageBitmap>(500)
    private val _version = MutableStateFlow(0)
    /** Changes whenever cached icons become stale. Compose keys icon loads on this. */
    val version: StateFlow<Int> = _version.asStateFlow()

    private val maskPath = squirclePath(size.toFloat(), size.toFloat())
    private val density = context.resources.displayMetrics.densityDpi

    fun invalidate() {
        cache.evictAll()
        glyphCache.evictAll()
        packs.clear()
        _version.value++
    }

    private fun keyFor(entry: AppEntry) =
        "${entry.id}|${layout.value.customIcons[entry.id]}|${settings.value.iconPack}"

    fun cached(entry: AppEntry): ImageBitmap? = cache.get(keyFor(entry))

    // ------------------------------------------------------------------ glyphs (Clear / Tinted icon styles)

    private val glyphCache = LruCache<String, ImageBitmap>(500)

    fun cachedGlyph(entry: AppEntry): ImageBitmap? = glyphCache.get(keyFor(entry))

    /** A white, alpha-only silhouette of the app's logo, positioned like the full icon. */
    suspend fun loadGlyph(entry: AppEntry): ImageBitmap {
        val key = keyFor(entry)
        glyphCache.get(key)?.let { return it }
        return withContext(Dispatchers.Default) {
            val bmp = runCatching { glyph(entry) }.getOrElse { Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888) }
                .asImageBitmap()
            glyphCache.put(key, bmp)
            bmp
        }
    }

    private fun glyph(entry: AppEntry): Bitmap {
        val custom = layout.value.customIcons[entry.id] != null
        val pack = settings.value.iconPack?.let { packs.iconFor(it, entry.key.component) } != null
        if (custom || pack) return whiten(contrastMask(render(entry)))
        val d = repo.launcherActivityInfo(entry)?.getIcon(density) ?: context.packageManager.defaultActivityIcon
        if (d is AdaptiveIconDrawable) {
            val inset = (size * 0.25f).toInt()
            val b = Rect(-inset, -inset, size + inset, size + inset)
            // Android 13+ apps can ship a dedicated monochrome layer — exactly what we want.
            if (android.os.Build.VERSION.SDK_INT >= 33) d.monochrome?.let { m ->
                val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                m.bounds = b; m.draw(Canvas(out))
                return whiten(out)
            }
            d.foreground?.let { fg ->
                val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                fg.bounds = b; fg.draw(Canvas(out))
                if (coverage(out) < 0.55f) return whiten(out)
            }
        }
        return whiten(contrastMask(mask(fill(d))))
    }

    /** Fraction of the tile covered by opaque pixels. */
    private fun coverage(b: Bitmap): Float {
        val px = IntArray(size * size); b.getPixels(px, 0, size, 0, 0, size, size)
        return px.count { (it ushr 24) > 128 }.toFloat() / px.size
    }

    /** Keeps pixels that differ from the icon's background colour (sampled near the edges). */
    private fun contrastMask(src: Bitmap): Bitmap {
        val px = IntArray(size * size); src.getPixels(px, 0, size, 0, 0, size, size)
        val m = (size * 0.12f).toInt(); val c = size / 2; val e = size - 1 - m
        val samples = listOf(c to m, c to e, m to c, e to c, m * 2 to m * 2, e - m to m * 2, m * 2 to e - m, e - m to e - m)
            .map { (x, y) -> px[y * size + x] }.filter { (it ushr 24) > 128 }
        if (samples.isEmpty()) return src
        val br = samples.map { (it shr 16) and 0xFF }.sorted()[samples.size / 2]
        val bg = samples.map { (it shr 8) and 0xFF }.sorted()[samples.size / 2]
        val bb = samples.map { it and 0xFF }.sorted()[samples.size / 2]
        for (i in px.indices) {
            val p = px[i]; val a = p ushr 24
            if (a == 0) continue
            val dr = ((p shr 16) and 0xFF) - br; val dg = ((p shr 8) and 0xFF) - bg; val db = (p and 0xFF) - bb
            val dist = kotlin.math.sqrt((dr * dr + dg * dg + db * db).toFloat())
            val na = ((dist - 36f) * 2.4f).coerceIn(0f, 255f) * a / 255f
            px[i] = (na.toInt() shl 24) or 0xFFFFFF
        }
        return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
    }

    private fun whiten(src: Bitmap): Bitmap {
        val px = IntArray(size * size); src.getPixels(px, 0, size, 0, 0, size, size)
        for (i in px.indices) px[i] = (px[i] and 0xFF000000.toInt()) or 0xFFFFFF
        return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
    }

    suspend fun load(entry: AppEntry): ImageBitmap {
        val key = keyFor(entry)
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.Default) {
            val bmp = runCatching { render(entry) }.getOrElse { fallback() }.asImageBitmap()
            cache.put(key, bmp)
            bmp
        }
    }

    /** Renders an arbitrary drawable as a full icon (used for icon-pack previews and shortcuts). */
    fun renderPlain(d: Drawable, masked: Boolean = false): ImageBitmap =
        (if (masked) mask(fill(d)) else plain(d)).asImageBitmap()

    private fun render(entry: AppEntry): Bitmap {
        layout.value.customIcons[entry.id]?.let { spec -> custom(spec)?.let { return badge(it, entry) } }
        settings.value.iconPack?.let { pack ->
            packs.iconFor(pack, entry.key.component)?.let { return badge(plain(it), entry) }
        }
        val d = repo.launcherActivityInfo(entry)?.getIcon(density)
            ?: context.packageManager.defaultActivityIcon
        return badge(mask(fill(d)), entry)
    }

    private fun custom(spec: String): Bitmap? = when {
        spec.startsWith("file:") -> {
            val f = File(spec.removePrefix("file:"))
            if (!f.exists()) null else {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(f.path, opts)
                var sample = 1
                while (min(opts.outWidth, opts.outHeight) / (sample * 2) >= size) sample *= 2
                BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
                    ?.let { mask(centerCrop(it)) }
            }
        }
        spec.startsWith("pack:") -> {
            val parts = spec.split(":", limit = 3)
            if (parts.size == 3) packs.drawable(parts[1], parts[2])?.let { plain(it) } else null
        }
        else -> null
    }

    /** Draws the icon so it fills a square, iOS style. Adaptive icons fill edge to edge. */
    private fun fill(d: Drawable): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        if (d is AdaptiveIconDrawable) {
            val inset = (size * 0.25f).toInt() // the 72dp safe zone of a 108dp layer fills the tile
            val b = Rect(-inset, -inset, size + inset, size + inset)
            val bg = d.background
            if (bg != null) { bg.bounds = b; bg.draw(c) } else c.drawColor(Color.WHITE)
            d.foreground?.let { it.bounds = b; it.draw(c) }
            return out
        }
        // Legacy icon: if it already fills its square use it as-is, otherwise put it on a white tile.
        val probe = plain(d)
        if (isFullBleed(probe)) return probe
        c.drawColor(Color.WHITE)
        val inset = (size * 0.14f).toInt()
        d.setBounds(inset, inset, size - inset, size - inset)
        d.draw(c)
        return out
    }

    private fun isFullBleed(b: Bitmap): Boolean {
        val m = (size * 0.12f).toInt()
        val pts = listOf(m to m, size - m to m, m to size - m, size - m to size - m, size / 2 to 2, 2 to size / 2)
        return pts.all { (x, y) -> Color.alpha(b.getPixel(x.coerceIn(0, size - 1), y.coerceIn(0, size - 1))) > 200 }
    }

    private fun plain(d: Drawable): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val iw = d.intrinsicWidth.takeIf { it > 0 } ?: size
        val ih = d.intrinsicHeight.takeIf { it > 0 } ?: size
        val scale = min(size.toFloat() / iw, size.toFloat() / ih)
        val w = (iw * scale).toInt()
        val h = (ih * scale).toInt()
        d.setBounds((size - w) / 2, (size - h) / 2, (size + w) / 2, (size + h) / 2)
        d.draw(c)
        return out
    }

    private fun centerCrop(src: Bitmap): Bitmap {
        val s = min(src.width, src.height)
        val sq = Bitmap.createBitmap(src, (src.width - s) / 2, (src.height - s) / 2, s, s)
        return Bitmap.createScaledBitmap(sq, size, size, true)
    }

    private fun mask(content: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        c.drawPath(maskPath, p)
        p.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        c.drawBitmap(content, 0f, 0f, p)
        return out
    }

    private fun badge(b: Bitmap, entry: AppEntry): Bitmap {
        if (!entry.isWorkProfile) return b
        val badged = context.packageManager.getUserBadgedIcon(BitmapDrawable(context.resources, b), entry.user)
        return plain(badged)
    }

    private fun fallback(): Bitmap = mask(fill(context.packageManager.defaultActivityIcon))
}
