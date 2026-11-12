package com.greasepencil.b4after.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.greasepencil.b4after.R
import com.greasepencil.b4after.data.db.PairRow
import com.greasepencil.b4after.data.repo.PhotoFiles
import com.greasepencil.b4after.domain.CollageLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Makes the shareable before/after image (for socials, Google Business), entirely on the phone. */
object CollageExporter {
    suspend fun export(
        context: Context,
        pair: PairRow,
        style: CollageLayout.Style,
        businessName: String,
        watermark: Boolean,
    ): File = withContext(Dispatchers.Default) {
        val afterFile = requireNotNull(pair.afterFile) { "pair has no after" }
        val aspect = displayAspect(pair.beforeRotation)
        val plan = CollageLayout.plan(style, aspect, longEdge = 2048, footer = businessName.isNotBlank() || watermark)
        val out = Bitmap.createBitmap(plan.width, plan.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.parseColor("#F2EFE9"))
        val mono = ResourcesCompat.getFont(context, R.font.geist_mono) ?: Typeface.MONOSPACE
        val serif = ResourcesCompat.getFont(context, R.font.instrument_serif) ?: Typeface.SERIF

        fun drawCell(file: String, rotation: Int, box: CollageLayout.Box, label: String) {
            val bmp = Bitmaps.decode(PhotoFiles.file(context, file), maxOf(box.width, box.height), rotation) ?: return
            val dst = RectF(box.left.toFloat(), box.top.toFloat(), (box.left + box.width).toFloat(), (box.top + box.height).toFloat())
            canvas.drawBitmap(bmp, centerCrop(bmp.width, bmp.height, box.width, box.height), dst, Paint(Paint.FILTER_BITMAP_FLAG))
            bmp.recycle()
            val textSize = plan.labelHeight * 0.42f
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = mono; this.textSize = textSize; letterSpacing = 0.12f }
            val pad = plan.labelHeight * 0.35f
            val w = p.measureText(label) + pad * 2
            val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(115, 0, 0, 0) }
            val r = RectF(dst.left + pad, dst.top + pad, dst.left + pad + w, dst.top + pad + plan.labelHeight * 0.7f)
            canvas.drawRoundRect(r, 8f, 8f, bg)
            canvas.drawText(label, r.left + pad, r.centerY() + textSize * 0.36f, p)
        }

        drawCell(pair.beforeFile, pair.beforeRotation, plan.before, "BEFORE")
        drawCell(afterFile, pair.afterRotation ?: 0, plan.after, "AFTER")

        if (plan.footerHeight > 0) {
            val baseline = plan.height - plan.footerHeight * 0.32f
            if (businessName.isNotBlank()) {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1C1B19"); typeface = serif; textSize = plan.footerHeight * 0.55f }
                canvas.drawText(businessName, plan.footerHeight * 0.4f, baseline, p)
            }
            if (watermark) {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#8C877E"); typeface = mono; textSize = plan.footerHeight * 0.26f
                    textAlign = Paint.Align.RIGHT; letterSpacing = 0.1f
                }
                canvas.drawText("MADE WITH PAIRSHOT", plan.width - plan.footerHeight * 0.4f, baseline, p)
            }
        }

        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "before-after-${pair.areaName.lowercase().replace(Regex("[^a-z0-9]+"), "-")}.jpg")
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
