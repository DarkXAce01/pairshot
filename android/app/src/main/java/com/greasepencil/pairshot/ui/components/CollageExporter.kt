package com.greasepencil.pairshot.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.greasepencil.pairshot.R
import com.greasepencil.pairshot.data.db.PairRow
import com.greasepencil.pairshot.data.repo.PhotoFiles
import com.greasepencil.pairshot.domain.CollageFrame
import com.greasepencil.pairshot.domain.CollageLayout
import com.greasepencil.pairshot.domain.Crop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Makes the shareable before/after image (for socials, Google Business), entirely on the phone. */
object CollageExporter {
    /** Everything about the business that changes the picture. */
    data class Look(val frame: CollageFrame, val businessName: String, val watermark: Boolean)

    /** Full size, as shared. */
    const val FULL_EDGE = 2048

    /**
     * Draws [pair] in [style] and [look] with the image's long edge at [longEdge] pixels. Blocking:
     * call it off the main thread. One photo is decoded at a time and recycled straight after.
     */
    fun render(context: Context, pair: PairRow, style: CollageLayout.Style, look: Look, longEdge: Int): Bitmap {
        val afterFile = requireNotNull(pair.afterFile) { "pair has no after" }
        val frame = look.frame
        val crop = Crop.of(pair.pair)
        val plan = planOf(pair, style, look, longEdge)
        val out = Bitmap.createBitmap(plan.width, plan.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.parseColor(frame.background))
        val fonts = Fonts(context)
        val accent = Color.parseColor(frame.accent)

        // Bands and the accent rule.
        val fill = Paint().apply { color = accent }
        val headerRect = RectF(0f, 0f, plan.width.toFloat(), plan.headerHeight.toFloat())
        val footerTop = (plan.height - plan.footerHeight).toFloat()
        var footerText = RectF(0f, footerTop, plan.width.toFloat(), plan.height.toFloat())
        if (frame.bandsFilled) {
            if (plan.headerHeight > 0) canvas.drawRect(headerRect, fill)
            if (plan.footerHeight > 0) canvas.drawRect(footerText, fill)
        } else if (plan.footerHeight > 0) {
            val rule = frame.rule(longEdge)
            canvas.drawRect(0f, footerTop, plan.width.toFloat(), footerTop + rule, fill)
            footerText = RectF(0f, footerTop + rule, plan.width.toFloat(), plan.height.toFloat())
        }

        // Photos.
        val split = plan.frame
        fun drawCell(file: String, rotation: Int, box: CollageLayout.Box) {
            val target = split ?: box
            val bmp = Bitmaps.decode(PhotoFiles.file(context, file), maxOf(target.width, target.height), rotation) ?: return
            val dst = RectF(target.left.toFloat(), target.top.toFloat(), target.right.toFloat(), target.bottom.toFloat())
            canvas.save()
            canvas.clipRect(box.left, box.top, box.right, box.bottom)
            canvas.drawBitmap(bmp, cropped(bmp.width, bmp.height, crop, target.width, target.height), dst, Paint(Paint.FILTER_BITMAP_FLAG))
            canvas.restore()
            bmp.recycle()
        }
        drawCell(pair.beforeFile, pair.beforeRotation, plan.before)
        drawCell(afterFile, pair.afterRotation ?: 0, plan.after)
        if (split != null) drawSeam(canvas, split, plan.after.left, longEdge)
        drawTag(canvas, fonts, plan.before, plan.labelHeight, "BEFORE", alignEnd = false)
        drawTag(canvas, fonts, plan.after, plan.labelHeight, "AFTER", alignEnd = split != null)

        // Words.
        val words = wordsOf(fonts, frame, plan, frame.nameText(look.businessName, pair.areaName), look.watermark)
        words.mark?.let { m -> canvas.drawText(m.text, m.left, baselineIn(footerText, m.paint), m.paint) }
        words.name?.let { n -> canvas.drawText(n.text, n.left, baselineIn(if (n.inHeader) headerRect else footerText, n.paint), n.paint) }
        return out
    }

    /** One line of words on the image: what is drawn, with which paint, and where it ends up. */
    internal class Line(val text: String, val paint: Paint, val left: Float, val right: Float, val inHeader: Boolean)

    /** Where the business name and the free mark go. */
    internal class Words(val name: Line?, val mark: Line?, val pad: Float)

    /** The words of [pair] in [style] and [look], laid out as [render] draws them (for tests). */
    internal fun wordsOf(context: Context, pair: PairRow, style: CollageLayout.Style, look: Look, longEdge: Int): Words =
        wordsOf(Fonts(context), look.frame, planOf(pair, style, look, longEdge), look.frame.nameText(look.businessName, pair.areaName), look.watermark)

    /**
     * The name has priority over the mark: the mark is capped at 38 % of the band width and shrinks
     * first; the name is only ellipsised once the mark is at its floor and the name is at its minimum.
     */
    private fun wordsOf(fonts: Fonts, frame: CollageFrame, plan: CollageLayout.Plan, name: String, watermark: Boolean): Words {
        val band = maxOf(plan.headerHeight, plan.footerHeight).coerceAtLeast(1)
        val pad = band * 0.4f
        val width = plan.width.toFloat()
        val markPaint = if (watermark && plan.footerHeight > 0) {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor(frame.markColour); typeface = fonts.mono; letterSpacing = 0.1f
                isSubpixelText = true; isLinearText = true
                textSize = plan.footerHeight * 0.26f
                val cap = width * 0.38f
                if (inkWidth(MARK, this) > cap) textSize = maxOf(plan.footerHeight * 0.16f, textSize * cap / inkWidth(MARK, this))
            }
        } else null
        val markFloor = plan.footerHeight * 0.16f
        var nameLine: Line? = null
        if (name.isNotEmpty()) {
            val inHeader = plan.headerHeight > 0
            val shared = !inHeader && markPaint != null
            val bandH = if (inHeader) plan.headerHeight else plan.footerHeight
            val p = namePaint(frame, fonts).apply { isSubpixelText = true; isLinearText = true }
            val preferred = bandH * when (frame.nameFont) {
                CollageFrame.NameFont.SERIF -> 0.55f
                CollageFrame.NameFont.SANS_CAPS -> 0.34f
                CollageFrame.NameFont.MONO_CAPS -> 0.3f
            }
            val min = preferred * 0.55f
            p.textSize = 100f
            val widthAtOne = inkWidth(name, p) / 100f
            fun maxWidth() = width - pad * 2 - if (shared) inkWidth(MARK, markPaint!!) + pad else 0f
            // Glyph widths do not scale exactly with size, so re-measure at the chosen size and
            // step down until it really fits (or reaches the minimum).
            fun fits(): Boolean {
                val limit = maxWidth()
                var size = CollageFrame.fitSize(widthAtOne, limit, preferred, min) ?: return false
                repeat(4) {
                    p.textSize = size
                    val w = inkWidth(name, p)
                    if (w <= limit) return true
                    size *= limit / w * 0.995f
                    if (size < min) return false
                }
                return false
            }
            var fit = fits()
            if (!fit && shared && markPaint!!.textSize > markFloor) {
                markPaint.textSize = markFloor
                fit = fits()
            }
            if (!fit) p.textSize = min
            val limit = maxWidth()
            val text = if (inkWidth(name, p) > limit) ellipsize(name, p, limit) else name
            nameLine = Line(text, p, pad, pad + inkWidth(text, p), inHeader)
        }
        val markLine = markPaint?.let {
            it.textAlign = Paint.Align.LEFT
            val w = inkWidth(MARK, it)
            Line(MARK, it, width - pad - w, width - pad, inHeader = false)
        }
        return Words(nameLine, markLine, pad)
    }

    /** The wider of the advance and the drawn ink, so a glyph's overhang is never left out. */
    private fun inkWidth(text: String, p: Paint): Float {
        val bounds = Rect()
        p.getTextBounds(text, 0, text.length, bounds)
        return maxOf(p.measureText(text), bounds.right.toFloat())
    }

    /**
     * The layout of [pair] in [style]: the crop sets the photo shape; the name and the free mark
     * decide which bands exist (with no name and Pro there are none).
     */
    fun planOf(pair: PairRow, style: CollageLayout.Style, look: Look, longEdge: Int): CollageLayout.Plan {
        val crop = Crop.of(pair.pair)
        val aspect = displayAspect(pair.beforeRotation) * crop.width / crop.height
        val (header, footer) = CollageLayout.bands(style, look.businessName.isNotBlank(), look.watermark)
        return CollageLayout.plan(style, aspect, longEdge = longEdge, footer = footer, header = header)
    }

    /** Renders at full size and writes a JPEG to `cacheDir/exports`. */
    suspend fun export(context: Context, pair: PairRow, style: CollageLayout.Style, look: Look): File = withContext(Dispatchers.Default) {
        val out = render(context, pair, style, look, FULL_EDGE)
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "before-after-${(pair.areaName ?: "photo").lowercase().replace(Regex("[^a-z0-9]+"), "-")}.jpg")
        file.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        out.recycle()
        file
    }

    fun share(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private const val MARK = "MADE WITH PAIRSHOT"

    private class Fonts(context: Context) {
        val mono: Typeface = ResourcesCompat.getFont(context, R.font.geist_mono) ?: Typeface.MONOSPACE
        val serif: Typeface = ResourcesCompat.getFont(context, R.font.instrument_serif) ?: Typeface.SERIF
        val sans: Typeface = ResourcesCompat.getFont(context, R.font.geist) ?: Typeface.SANS_SERIF
    }

    private fun namePaint(frame: CollageFrame, fonts: Fonts) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor(frame.nameColour)
        letterSpacing = frame.nameTracking
        when (frame.nameFont) {
            CollageFrame.NameFont.SERIF -> typeface = fonts.serif
            CollageFrame.NameFont.SANS_CAPS -> { typeface = fonts.sans; fontVariationSettings = "'wght' 600" }
            CollageFrame.NameFont.MONO_CAPS -> { typeface = fonts.mono; fontVariationSettings = "'wght' 500" }
        }
    }

    /** The baseline that centres [p]'s text vertically in [rect]. */
    private fun baselineIn(rect: RectF, p: Paint): Float {
        val fm = p.fontMetrics
        return rect.centerY() - (fm.ascent + fm.descent) / 2f
    }

    private fun ellipsize(text: String, p: Paint, maxWidth: Float): String {
        var end = text.length
        while (end > 1 && inkWidth(text.substring(0, end).trimEnd() + "…", p) > maxWidth) end--
        return text.substring(0, end).trimEnd() + "…"
    }

    /** BEFORE / AFTER: mono caps, white on 55 % black, at the box's top corner. */
    private fun drawTag(canvas: Canvas, fonts: Fonts, box: CollageLayout.Box, labelHeight: Int, label: String, alignEnd: Boolean) {
        val textSize = labelHeight * 0.42f
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(CollageFrame.TAG_TEXT); typeface = fonts.mono; this.textSize = textSize; letterSpacing = 0.12f
        }
        val pad = labelHeight * 0.35f
        val w = p.measureText(label) + pad * 2
        val left = if (alignEnd) box.right - pad - w else box.left + pad
        val r = RectF(left, box.top + pad, left + w, box.top + pad + labelHeight * 0.7f)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(CollageFrame.TAG_FILL) }
        val radius = labelHeight * 0.12f
        canvas.drawRoundRect(r, radius, radius, bg)
        canvas.drawText(label, r.left + pad, baselineIn(r, p), p)
    }

    /** The white centre line and the round "‹ ›" knob, as the client page slider at the middle. */
    private fun drawSeam(canvas: Canvas, frame: CollageLayout.Box, x: Int, longEdge: Int) {
        val line = (longEdge * 0.004f).coerceAtLeast(2f)
        val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        canvas.drawRect(x - line / 2f, frame.top.toFloat(), x + line / 2f, frame.bottom.toFloat(), white)
        val cy = frame.top + frame.height / 2f
        val r = longEdge * 0.03f
        canvas.drawCircle(x.toFloat(), cy, r, white)
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1B1A18"); style = Paint.Style.STROKE
            strokeWidth = r * 0.12f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        val a = r * 0.22f; val gap = r * 0.18f
        val left = Path().apply { moveTo(x - gap, cy - a); lineTo(x - gap - a, cy); lineTo(x - gap, cy + a) }
        val right = Path().apply { moveTo(x + gap, cy - a); lineTo(x + gap + a, cy); lineTo(x + gap, cy + a) }
        canvas.drawPath(left, ink)
        canvas.drawPath(right, ink)
    }

    /** The pair's [crop] of a srcW×srcH image, then centre-cut to fill a dstW×dstH box. */
    fun cropped(srcW: Int, srcH: Int, crop: Crop, dstW: Int, dstH: Int): Rect {
        val l = (crop.left * srcW).toInt(); val t = (crop.top * srcH).toInt()
        val w = ((crop.right - crop.left) * srcW).toInt().coerceAtLeast(1)
        val h = ((crop.bottom - crop.top) * srcH).toInt().coerceAtLeast(1)
        return centerCrop(w, h, dstW, dstH).apply { offset(l, t) }
    }

    /** The part of a srcW×srcH image that fills a dstW×dstH box, cut evenly from the long sides. */
    fun centerCrop(srcW: Int, srcH: Int, dstW: Int, dstH: Int): Rect {
        val srcAspect = srcW.toFloat() / srcH
        val dstAspect = dstW.toFloat() / dstH
        return if (srcAspect > dstAspect) {
            val w = (srcH * dstAspect).toInt()
            val x = (srcW - w) / 2
            Rect(x, 0, x + w, srcH)
        } else {
            val h = (srcW / dstAspect).toInt()
            val y = (srcH - h) / 2
            Rect(0, y, srcW, y + h)
        }
    }
}
