package app.orchard.tv.apps

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.annotation.RequiresApi
import android.os.Build
import android.graphics.drawable.Drawable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import app.orchard.tv.ui.theme.Glyph
import app.orchard.tv.ui.theme.drawGlyph
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import app.orchard.tv.wallpaper.Images
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * What a tile draws. tvOS icons are layered so they can shift against each other when focused;
 * Android's adaptive icons happen to be layered the same way, so those keep their two layers here.
 *
 * - [back] always fills the tile: the app's TV banner, the adaptive icon's background layer, or a
 *   plate in the icon's own color.
 * - [fore] is drawn on top at [foreAt] (fractions of the tile), and moves with parallax. It is cut
 *   down to its visible pixels: tiles are drawn every frame, and the TV's GPU is short of fill rate.
 */
class TileArt(
    val back: ImageBitmap,
    val fore: ImageBitmap?,
    val foreAt: androidx.compose.ui.geometry.Rect,
    val tint: Color,
    /**
     * The whole home backdrop for this app, baked small: its blurred artwork as ambient light plus
     * the shading that keeps the clock and grid legible. Scaled up to full screen it costs the GPU
     * one textured draw per frame, which matters on TV chips.
     */
    val backdrop: ImageBitmap,
)

/** Firmware apps Aerial draws its own tile for, each in the color that tells the kind apart. */
enum class SystemTile(top: Long, bottom: Long, val glyph: Glyph) {
    Settings(0xFF8E8E93, 0xFF48484A, Glyph.Gear),
    Photos(0xFFFFB340, 0xFFFF5E3A, Glyph.Photo),
    Media(0xFF3A3A3C, 0xFF111113, Glyph.Play),
    Store(0xFF40B0FF, 0xFF0A5FD8, Glyph.Bag),
    Weather(0xFF5AC8FA, 0xFF1C6FD6, Glyph.CloudSun),
    Calendar(0xFFFF6B5E, 0xFFD02A1E, Glyph.Calendar),
    Cast(0xFF8E8CFF, 0xFF4744D0, Glyph.Cast),
    Security(0xFF3ED97A, 0xFF138A47, Glyph.Shield),
    Browser(0xFF64D2FF, 0xFF0A7BEA, Glyph.Globe);

    val top = top.toInt()
    val bottom = bottom.toInt()
}

object TileArtFactory {
    /**
     * Pixel size of the artwork: the tile at its focused size on this screen, so the lifted tile is
     * still pixel-sharp. 352×198 on a 1080p UI, twice that on a TV that draws its UI at 4K.
     */
    @Volatile var W = 352; private set
    @Volatile var H = 198; private set

    /** Called with [app.orchard.tv.ui.theme.canvasDensity] before any tile is built. */
    fun useCanvasDensity(density: Float) {
        // 176dp: the 147dp tile at its 1.15× focus scale, with a little to spare.
        W = (176 * density).roundToInt().coerceIn(352, 704)
        H = W * 9 / 16
    }

    /** How large a logo is, as the side of the square of the same area, in tile heights. */
    private const val LOGO = 0.5f

    /** The resolution an icon is sampled at to find its logo. */
    private const val SAMPLE = 256

    private const val GRAPHITE_TOP = 0xFF3A3A3C.toInt()
    private const val GRAPHITE_BOTTOM = 0xFF242426.toInt()

    fun fromBanner(banner: Drawable): TileArt {
        val back = createBitmap(W, H)
        Canvas(back).apply {
            drawColor(GRAPHITE_BOTTOM)
            drawCover(banner, W, H)
        }
        return finish(back, null)
    }

    /**
     * A TV input's tile, in the spirit of an app icon: the connector drawn in white over a gradient
     * that tells the kind apart (HDMI blue, DP violet, tuner teal, AV amber), the input's name under it, and
     * the HDMI-CEC device name (e.g. "Apple TV") as a second line when there is one.
     */
    fun forSource(kind: app.orchard.tv.system.SourceKind, label: String, device: String?): TileArt {
        val (top, bottom) = when (kind) {
            app.orchard.tv.system.SourceKind.Hdmi -> 0xFF3B6FD8.toInt() to 0xFF1D3A86.toInt()
            app.orchard.tv.system.SourceKind.DisplayPort -> 0xFF6E5BD8.toInt() to 0xFF3A2D86.toInt()
            app.orchard.tv.system.SourceKind.Tuner -> 0xFF1FA88E.toInt() to 0xFF0E5C55.toInt()
            app.orchard.tv.system.SourceKind.Av -> 0xFFE0912F.toInt() to 0xFF9A4F12.toInt()
            app.orchard.tv.system.SourceKind.Other -> 0xFF5E6470.toInt() to 0xFF30343C.toInt()
        }
        val back = createBitmap(W, H)
        Canvas(back).apply {
            drawRect(0f, 0f, W.toFloat(), H.toFloat(), Paint().apply {
                shader = LinearGradient(0f, 0f, W * 0.4f, H.toFloat(), top, bottom, Shader.TileMode.CLAMP)
            })
            // Soft light from the top left, as on Aerial's own icon.
            drawRect(0f, 0f, W.toFloat(), H.toFloat(), Paint().apply {
                shader = android.graphics.RadialGradient(W * 0.25f, -H * 0.2f, W * 0.8f, 0x38FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
            })
            val white = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFFFFFF.toInt(); style = Paint.Style.STROKE; strokeWidth = H * 0.035f
                strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
            }
            val two = device != null
            val cx = W / 2f
            val cy = H * (if (two) 0.3f else 0.36f)
            val u = H * 0.1f
            when (kind) {
                app.orchard.tv.system.SourceKind.Hdmi, app.orchard.tv.system.SourceKind.DisplayPort -> {
                    // The HDMI plug face (also for DP, whose plug looks much the same): a wide trapezoid with chamfered lower corners.
                    val p = android.graphics.Path().apply {
                        moveTo(cx - 2.2f * u, cy - 0.9f * u)
                        lineTo(cx + 2.2f * u, cy - 0.9f * u)
                        lineTo(cx + 2.2f * u, cy + 0.15f * u)
                        lineTo(cx + 1.5f * u, cy + 0.9f * u)
                        lineTo(cx - 1.5f * u, cy + 0.9f * u)
                        lineTo(cx - 2.2f * u, cy + 0.15f * u)
                        close()
                    }
                    drawPath(p, white)
                    drawLine(cx - 1.3f * u, cy - 0.05f * u, cx + 1.3f * u, cy - 0.05f * u, white)
                }
                app.orchard.tv.system.SourceKind.Tuner -> {
                    // An antenna over a screen edge.
                    drawLine(cx, cy + 0.9f * u, cx, cy - 0.3f * u, white)
                    drawLine(cx, cy - 0.3f * u, cx - 1.1f * u, cy - 1.1f * u, white)
                    drawLine(cx, cy - 0.3f * u, cx + 1.1f * u, cy - 1.1f * u, white)
                    drawLine(cx - 1.6f * u, cy + 0.9f * u, cx + 1.6f * u, cy + 0.9f * u, white)
                }
                else -> {
                    // RCA jack: a ring with a pin.
                    drawCircle(cx, cy, 0.95f * u, white)
                    drawCircle(cx, cy, 0.25f * u, Paint(white).apply { style = Paint.Style.FILL })
                }
            }
            val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFFFFFF.toInt(); textSize = H * 0.17f; textAlign = Paint.Align.CENTER
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            }
            drawText(label, cx, H * (if (two) 0.66f else 0.74f), title)
            if (device != null) {
                val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCCFFFFFF.toInt(); textSize = H * 0.12f; textAlign = Paint.Align.CENTER }
                drawText(device, cx, H * 0.84f, sub)
            }
        }
        return finish(back, null)
    }

    /**
     * A firmware app (settings, photos, weather...) drawn as Aerial's own tile: a white glyph over
     * a color that tells the kind apart, the way tvOS draws its system apps. TV makers ship these
     * with phone icons that look different on every TV and unlike the apps around them.
     */
    fun forSystem(kind: SystemTile): TileArt {
        val back = layer {
            drawRect(0f, 0f, W.toFloat(), H.toFloat(), Paint().apply {
                shader = LinearGradient(0f, 0f, W * 0.4f, H.toFloat(), kind.top, kind.bottom, Shader.TileMode.CLAMP)
            })
            // Soft light from the top left, as on the input tiles.
            drawRect(0f, 0f, W.toFloat(), H.toFloat(), Paint().apply {
                shader = android.graphics.RadialGradient(W * 0.25f, -H * 0.2f, W * 0.8f, 0x38FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
            })
        }
        // The glyph on its own layer, so it shifts against the color when the tile leans.
        val g = H * 0.46f
        val fore = layer {
            translate((W - g) / 2f, (H - g) / 2f)
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(this), Size(g, g)) {
                drawGlyph(kind.glyph, Color.White)
            }
        }
        return finish(back, fore)
    }

    fun fromIcon(icon: Drawable): TileArt {
        if (Build.VERSION.SDK_INT >= 26 && icon is AdaptiveIconDrawable && icon.foreground != null) adaptive(icon)?.let { return it }
        return plate(icon) ?: legacy(icon)
    }

    /**
     * Null when the foreground ignores the adaptive safe zone (a legacy icon wrapped as adaptive),
     * or when the background has shapes of its own (a disc the logo sits in, Google's Clock):
     * the layers only line up drawn together, so those go on as a whole icon.
     */
    @RequiresApi(26)
    private fun adaptive(icon: AdaptiveIconDrawable): TileArt? {
        val logo = visible(raster(icon.foreground, SAMPLE), SAMPLE) ?: return null
        // A logo inside the 66-unit safe zone of the 108-unit layer spans at most about 0.61.
        if (max(logo.width(), logo.height()) > 0.7f) return null
        val bg = icon.background
        if (bg != null && !isFlat(raster(bg, 32))) return null
        val back = layer {
            drawColor(GRAPHITE_BOTTOM)
            bg?.let {
                // The background layer is a square; cover the wide tile with it.
                it.setBounds(0, (H - W) / 2, W, (H - W) / 2 + W)
                it.draw(this)
            }
        }
        return finish(back, placeLogo(icon.foreground, ::visible)?.first)
    }

    /**
     * A legacy icon that is a filled rounded square (most China-market apps ship these: a logo on a
     * white or colored plate). Drawn small in the middle it reads as an icon on a tile, not as the
     * tile. Instead the plate's own color is carried across the whole tile, top edge to bottom
     * edge, and the logo on it is sized like every other logo, the plate fading into the tile
     * around it. Null for any other shape.
     */
    private fun plate(icon: Drawable): TileArt? {
        val n = SAMPLE
        val px = raster(icon, n)
        val box = solidBox(px, n) ?: return null
        val l = box.left; val t = box.top; val r = box.right - 1; val bt = box.bottom - 1
        val w = box.width()
        val h = box.height()
        // A square at least half the canvas, and nearly all of it solid: a rounded square fills
        // about 96% of its box, a circle 79%.
        var filled = 0
        for (y in t..bt) for (x in l..r) if (solid(px[y * n + x])) filled++
        if (w < n / 2 || kotlin.math.abs(w - h) > w * 0.06f || filled < w * h * 0.9f) return null
        // The plate's color along its top and bottom edges, inside the corner rounding.
        fun band(fromY: Int, toY: Int): Int? {
            var rr = 0L; var gg = 0L; var bb = 0L; var count = 0
            val xs = (l + w / 4)..(r - w / 4)
            for (y in fromY..toY) for (x in xs) {
                val c = px[y * n + x]
                rr += (c shr 16) and 0xFF; gg += (c shr 8) and 0xFF; bb += c and 0xFF; count++
            }
            val mean = android.graphics.Color.rgb((rr / count).toInt(), (gg / count).toInt(), (bb / count).toInt())
            // A logo running into the edge leaves no single color to carry across the tile.
            var spread = 0L
            for (y in fromY..toY) for (x in xs) spread += distance(px[y * n + x], mean)
            return if (spread / count > 36) null else mean
        }
        val edge = (h * 0.04f).roundToInt()
        val depth = (h * 0.06f).roundToInt()
        val top = band(t + edge, t + edge + depth) ?: return null
        val bottom = band(bt - edge - depth, bt - edge) ?: return null
        // Two colors (Xiaomi's 视频头条: white over red) are a picture, not a plate to carry across.
        if (distance(top, bottom) > 150) return null

        // The logo: whatever on the plate stands out from the plate's color at that height.
        fun logoOnPlate(px: IntArray, n: Int): RectF? {
            val plate = solidBox(px, n) ?: return null
            val inset = (plate.width() * 0.08f).roundToInt()
            return bounds(px, n, android.graphics.Rect(plate.left + inset, plate.top + inset, plate.right - inset, plate.bottom - inset)) { i, c ->
                solid(c) && distance(c, ColorUtils.blendARGB(top, bottom, (i / n - plate.top) / plate.height().toFloat())) > 72
            } ?: RectF(plate.left / n.toFloat(), plate.top / n.toFloat(), plate.right / n.toFloat(), plate.bottom / n.toFloat())
        }

        val back = layer {
            drawRect(0f, 0f, W.toFloat(), H.toFloat(), Paint().apply {
                shader = LinearGradient(0f, 0f, 0f, H.toFloat(), top, bottom, Shader.TileMode.CLAMP)
            })
        }
        val (fore, logo) = placeLogo(icon, ::logoOnPlate) ?: return null
        feather(fore, logo)
        return finish(back, fore)
    }

    private fun legacy(icon: Drawable): TileArt {
        // A legacy icon. Its logo sits, sized like every other, on a frosted plate made from
        // itself: the icon blown up past the tile's edges, blurred and dimmed. Full-square icons
        // (many China-market system apps expect the launcher to mask them) get rounded corners.
        val sample = 192
        val iconBitmap = createBitmap(sample, sample)
        icon.setBounds(0, 0, sample, sample)
        icon.draw(Canvas(iconBitmap))
        val fore = placeLogo(icon, ::visible, round = opaqueCorners(iconBitmap))?.first

        val pw = 48
        val ph = 27
        val plate = createBitmap(pw, ph)
        Canvas(plate).apply {
            drawColor(plateFromTint(dominant(iconBitmap)))
            val inset = (sample * 0.2f).roundToInt()
            drawBitmap(iconBitmap, android.graphics.Rect(inset, inset + sample / 8, sample - inset, sample - inset - sample / 8), android.graphics.Rect(0, 0, pw, ph), Paint(Paint.FILTER_BITMAP_FLAG))
        }
        val platePx = IntArray(pw * ph)
        plate.getPixels(platePx, 0, pw, 0, 0, pw, ph)
        repeat(3) { Images.boxBlur(platePx, pw, ph, 3) }
        plate.setPixels(platePx, 0, pw, 0, 0, pw, ph)
        val frost = ColorMatrix().apply {
            setSaturation(1.3f)
            postConcat(ColorMatrix().apply { setScale(0.7f, 0.7f, 0.7f, 1f) })
        }
        val back = layer {
            drawBitmap(plate, null, android.graphics.Rect(0, 0, W, H), Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(frost) })
        }
        plate.recycle()
        iconBitmap.recycle()
        return finish(back, fore)
    }

    private fun opaqueCorners(b: Bitmap): Boolean {
        val i = (b.width * 0.03f).roundToInt()
        val e = b.width - 1 - i
        return listOf(b.getPixel(i, i), b.getPixel(e, i), b.getPixel(i, e), b.getPixel(e, e))
            .all { android.graphics.Color.alpha(it) > 200 }
    }

    // ---- Logos: found in a sample of the icon, then drawn sharp at their size on the tile ----

    private inline fun layer(draw: Canvas.() -> Unit): Bitmap = createBitmap(W, H).also { Canvas(it).draw() }

    private fun raster(d: Drawable, n: Int): IntArray {
        val b = createBitmap(n, n)
        d.setBounds(0, 0, n, n)
        d.draw(Canvas(b))
        return IntArray(n * n).also { b.getPixels(it, 0, n, 0, 0, n, n); b.recycle() }
    }

    /**
     * The box around the pixels of the n×n sample that [isLogo] picks (given the pixel's index
     * and color), searched within [within], as fractions of the sample. Null when there are none.
     */
    private inline fun bounds(
        px: IntArray, n: Int,
        within: android.graphics.Rect = android.graphics.Rect(0, 0, n, n),
        isLogo: (Int, Int) -> Boolean,
    ): RectF? {
        var l = n; var t = n; var r = -1; var b = -1
        for (y in within.top until within.bottom) for (x in within.left until within.right) {
            val i = y * n + x
            if (isLogo(i, px[i])) { l = minOf(l, x); r = maxOf(r, x); t = minOf(t, y); b = maxOf(b, y) }
        }
        return if (r < 0) null else RectF(l / n.toFloat(), t / n.toFloat(), (r + 1) / n.toFloat(), (b + 1) / n.toFloat())
    }

    private fun distance(a: Int, b: Int): Int =
        kotlin.math.abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)) +
            kotlin.math.abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)) +
            kotlin.math.abs((a and 0xFF) - (b and 0xFF))

    /** True for a layer of (nearly) one color. */
    private fun isFlat(px: IntArray): Boolean {
        val first = px[px.size / 2]
        return px.count { distance(it, first) > 24 || kotlin.math.abs((it ushr 24) - (first ushr 24)) > 24 } < px.size / 20
    }

    /**
     * Where the whole icon goes on the tile so that its [logo] (fractions of the icon's box) sits
     * centered at logo size. Logos are sized by area rather than by their box, so a wide wordmark
     * and a round symbol carry the same weight, and all of them stay inside the tile's safe zone,
     * as on tvOS: focus scale and parallax never take a logo near the edge.
     */
    private fun iconOnTile(logo: RectF): RectF {
        val aspect = logo.width() / logo.height()
        val h = minOf(LOGO * H / kotlin.math.sqrt(aspect), 0.6f * H, 0.62f * W / aspect)
        val box = h / logo.height()
        val left = W / 2f - box * logo.centerX()
        val top = H / 2f - box * logo.centerY()
        return RectF(left, top, left + box, top + box)
    }

    /** The opaque part of a sample. */
    private fun visible(px: IntArray, n: Int): RectF? = bounds(px, n) { _, c -> c ushr 24 > 24 }

    private fun solid(c: Int) = c ushr 24 > 200

    /** The box around the solid pixels of an n×n sample, in its pixels. */
    private fun solidBox(px: IntArray, n: Int): android.graphics.Rect? {
        var l = n; var t = n; var r = -1; var b = -1
        for (y in 0 until n) for (x in 0 until n) if (solid(px[y * n + x])) {
            l = minOf(l, x); r = maxOf(r, x); t = minOf(t, y); b = maxOf(b, y)
        }
        return if (r < 0) null else android.graphics.Rect(l, t, r + 1, b + 1)
    }

    /**
     * A tile-sized layer with [d] drawn so that the logo [find] picks out of it sits at
     * [iconOnTile], and where that logo ended up on the tile. Null when [find] finds none.
     *
     * The icon is drawn at the size it will have, so it stays sharp, then measured again: a
     * layer-list of dp-sized shapes does not scale with its bounds (遥遥电视助手 on Xiaomi
     * MiTV-MFTR0), and is placed by what was actually drawn.
     */
    private fun placeLogo(d: Drawable, find: (IntArray, Int) -> RectF?, round: Boolean = false): Pair<Bitmap, RectF>? {
        val first = find(raster(d, SAMPLE), SAMPLE) ?: return null
        val size = iconOnTile(first).width().roundToInt().coerceIn(16, 4 * H)
        val icon = createBitmap(size, size)
        d.setBounds(0, 0, size, size)
        d.draw(Canvas(icon))
        val px = IntArray(size * size).also { icon.getPixels(it, 0, size, 0, 0, size, size) }
        val logo = find(px, size) ?: first
        val at = iconOnTile(logo)
        val out = layer {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            if (round) {
                val r = at.width() * 0.225f
                drawRoundRect(at, r, r, paint.apply {
                    shader = android.graphics.BitmapShader(icon, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                        setLocalMatrix(android.graphics.Matrix().apply {
                            setScale(at.width() / size, at.height() / size)
                            postTranslate(at.left, at.top)
                        })
                    }
                })
            } else {
                drawBitmap(icon, null, at, paint)
            }
        }
        icon.recycle()
        val box = at.width()
        return out to RectF(at.left + box * logo.left, at.top + box * logo.top, at.left + box * logo.right, at.top + box * logo.bottom)
    }

    /** Fades [layer] out around [keep], so a plate's own edges and corners melt into the tile color. */
    private fun feather(layer: Bitmap, keep: RectF) {
        val margin = H * 0.04f
        val fade = H * 0.22f
        val c = Canvas(layer)
        fun mask(from: Float, to: Float, horizontal: Boolean) = Paint().apply {
            val span = to - from + 2 * (margin + fade)
            val f = fade / span
            val start = from - margin - fade
            shader = LinearGradient(
                if (horizontal) start else 0f, if (horizontal) 0f else start,
                if (horizontal) start + span else 0f, if (horizontal) 0f else start + span,
                intArrayOf(0, android.graphics.Color.BLACK, android.graphics.Color.BLACK, 0),
                floatArrayOf(0f, f, 1f - f, 1f), Shader.TileMode.CLAMP,
            )
            xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN)
        }
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), mask(keep.left, keep.right, horizontal = true))
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), mask(keep.top, keep.bottom, horizontal = false))
    }

    /** [back] and [fore] are both tile-sized; the foreground is trimmed here. */
    private fun finish(back: Bitmap, fore: Bitmap?): TileArt {
        val composed = if (fore == null) back else back.copy(Bitmap.Config.ARGB_8888, true).also {
            Canvas(it).drawBitmap(fore, 0f, 0f, null)
        }
        val tint = dominant(composed)
        val backdrop = backdropOf(composed)
        if (composed !== back) composed.recycle()
        var foreAt = androidx.compose.ui.geometry.Rect.Zero
        val trimmed = fore?.let { layer ->
            val px = IntArray(W * H).also { layer.getPixels(it, 0, W, 0, 0, W, H) }
            var l = W; var t = H; var r = -1; var b = -1
            for (y in 0 until H) for (x in 0 until W) if (px[y * W + x] ushr 24 != 0) {
                l = minOf(l, x); r = maxOf(r, x); t = minOf(t, y); b = maxOf(b, y)
            }
            if (r < 0) return@let null
            foreAt = androidx.compose.ui.geometry.Rect(l / W.toFloat(), t / H.toFloat(), (r + 1) / W.toFloat(), (b + 1) / H.toFloat())
            Bitmap.createBitmap(layer, l, t, r - l + 1, b - t + 1).also { if (it !== layer) layer.recycle() }
        }
        return TileArt(
            back = back.asImageBitmap(),
            fore = trimmed?.asImageBitmap(),
            foreAt = foreAt,
            tint = Color(tint),
            backdrop = backdrop.asImageBitmap(),
        )
    }

    /** The tile as one picture, as Home draws it at rest: for the phone page. */
    fun flatten(art: TileArt): Bitmap {
        val out = art.back.asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, true)
        art.fore?.let { f ->
            val r = art.foreAt
            Canvas(out).drawBitmap(
                f.asAndroidBitmap(), null,
                RectF(r.left * out.width, r.top * out.height, r.right * out.width, r.bottom * out.height),
                Paint(Paint.FILTER_BITMAP_FLAG),
            )
        }
        return out
    }

    private fun Canvas.drawCover(d: Drawable, w: Int, h: Int) {
        val iw = d.intrinsicWidth.takeIf { it > 0 } ?: w
        val ih = d.intrinsicHeight.takeIf { it > 0 } ?: h
        val scale = max(w / iw.toFloat(), h / ih.toFloat())
        val dw = (iw * scale).roundToInt()
        val dh = (ih * scale).roundToInt()
        val l = (w - dw) / 2
        val t = (h - dh) / 2
        d.setBounds(l, t, l + dw, t + dh)
        d.draw(this)
    }

    /** Saturation-weighted mean color: the hue a person would name when describing the tile. */
    private fun dominant(b: Bitmap): Int {
        val small = b.scale(24, 14)
        var r = 0.0; var g = 0.0; var bl = 0.0; var total = 0.0
        val hsv = FloatArray(3)
        for (y in 0 until small.height) for (x in 0 until small.width) {
            val c = small.getPixel(x, y)
            if (android.graphics.Color.alpha(c) < 128) continue
            android.graphics.Color.colorToHSV(c, hsv)
            val wgt = hsv[1] * hsv[2] * hsv[2] + 0.04
            r += android.graphics.Color.red(c) * wgt
            g += android.graphics.Color.green(c) * wgt
            bl += android.graphics.Color.blue(c) * wgt
            total += wgt
        }
        if (small !== b) small.recycle()
        if (total == 0.0) return GRAPHITE_TOP
        return android.graphics.Color.rgb((r / total).roundToInt(), (g / total).roundToInt(), (bl / total).roundToInt())
    }

    /** A plate behind a logo that has no background of its own: the logo's hue, deep and calm. */
    private fun plateFromTint(tint: Int): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(tint, hsl)
        if (hsl[1] < 0.18f) return GRAPHITE_TOP
        hsl[1] = hsl[1].coerceAtMost(0.55f)
        hsl[2] = 0.24f
        return ColorUtils.HSLToColor(hsl)
    }

    private fun backdropOf(src: Bitmap): Bitmap {
        val w = 64
        val h = 36
        val small = src.scale(w, h)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        repeat(3) { Images.boxBlur(px, w, h, 4) }
        val blurred = createBitmap(w, h)
        blurred.setPixels(px, 0, w, 0, 0, w, h)
        // Lift saturation and pull brightness down: ambient light, not a picture. Pale artwork
        // (white banners) would spread a grey haze, so it gets dimmed much further.
        val hsv = FloatArray(3)
        var sat = 0f; var value = 0f
        for (c in px) { android.graphics.Color.colorToHSV(c, hsv); sat += hsv[1]; value += hsv[2] }
        sat /= px.size; value /= px.size
        val gain = if (sat < 0.2f) 0.82f - 0.5f * value * (1f - sat / 0.2f) else 0.82f
        val cm = ColorMatrix().apply {
            setSaturation(1.5f)
            postConcat(ColorMatrix().apply { setScale(gain, gain, gain, 1f) })
        }
        val bw = 192
        val bh = 108
        val out = createBitmap(bw, bh)
        Canvas(out).apply {
            drawBitmap(
                blurred, null, android.graphics.Rect(0, 0, bw, bh),
                Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(cm) },
            )
            // Light kept up top where the shelf is, near-black under the grid.
            drawRect(0f, 0f, bw.toFloat(), bh.toFloat(), Paint().apply {
                shader = LinearGradient(
                    0f, 0f, 0f, bh.toFloat(),
                    intArrayOf(black(0.10f), black(0.32f), black(0.80f), black(0.94f)),
                    floatArrayOf(0f, 0.42f, 0.72f, 1f), Shader.TileMode.CLAMP,
                )
            })
            // The left edge shaded so the clock reads over bright artwork.
            drawRect(0f, 0f, bw.toFloat(), bh.toFloat(), Paint().apply {
                shader = LinearGradient(0f, 0f, bw * 0.55f, 0f, black(0.38f), 0, Shader.TileMode.CLAMP)
            })
        }
        small.recycle(); blurred.recycle()
        return out
    }

    private fun black(alpha: Float) = android.graphics.Color.argb((alpha * 255).roundToInt(), 0, 0, 0)
}
