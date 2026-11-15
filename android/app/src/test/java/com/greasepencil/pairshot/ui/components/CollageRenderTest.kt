package com.greasepencil.pairshot.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.greasepencil.pairshot.data.repo.PhotoFiles
import com.greasepencil.pairshot.domain.CollageFrame
import com.greasepencil.pairshot.domain.CollageLayout.Style
import com.greasepencil.pairshot.domain.gallery.BrandLook
import com.greasepencil.pairshot.domain.gallery.ClientTemplate
import com.greasepencil.pairshot.domain.pairRow
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Draws every layout in every frame with real pixels. Set -Dcollage.out=DIR (or the env var
 * COLLAGE_OUT) to keep the PNGs for a look.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CollageRenderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val longName = "Sparkle & Shine Exterior Cleaning Co Ltd" // 40 characters
    private val out: File? = (System.getProperty("collage.out") ?: System.getenv("COLLAGE_OUT"))?.let { File(it).apply { mkdirs() } }

    /** Before: left half red, right half blue. After: left half green, right half yellow. */
    private fun photo(name: String, left: Int, right: Int) {
        val b = Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(left)
        c.clipRect(150, 0, 300, 400)
        c.drawColor(right)
        PhotoFiles.file(context, name).apply { parentFile?.mkdirs() }.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 95, it) }
    }

    @Before fun photos() {
        photo("b-p.jpg", Color.RED, Color.BLUE)
        photo("af-p.jpg", Color.GREEN, Color.YELLOW)
    }

    private val pair = pairRow("p", 0, hasAfter = true, area = "Driveway")

    @Test fun `every layout in every frame renders at its planned size`() {
        for (template in ClientTemplate.entries) for (style in Style.entries) {
            val look = CollageExporter.Look(CollageFrame.of(template, BrandLook.BLUE.hex), longName, watermark = true)
            val bmp = CollageExporter.render(context, pair, style, look, 1024)
            val plan = CollageExporter.planOf(pair, style, look, 1024)
            assertThat(bmp.width to bmp.height).isEqualTo(plan.width to plan.height)
            assertThat(plan.footerHeight).isGreaterThan(0)
            out?.let { dir -> File(dir, "${template.id}-${style.name.lowercase()}.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            bmp.recycle()
        }
    }

    @Test fun `pro with no name has no bands`() {
        for (style in Style.entries) {
            val look = CollageExporter.Look(CollageFrame.of(ClientTemplate.CLASSIC, null), "", watermark = false)
            val plan = CollageExporter.planOf(pair, style, look, 1024)
            assertThat(plan.footerHeight + plan.headerHeight).isEqualTo(0)
        }
    }

    @Test fun `the crop is applied to both photos`() {
        // Keep only the right half: the before must be all blue and the after all yellow.
        val cropped = pair.copy(pair = pair.pair.copy(cropLeft = 0.5f))
        val look = CollageExporter.Look(CollageFrame.of(ClientTemplate.CLASSIC, null), "", watermark = false)
        val plan = CollageExporter.planOf(cropped, Style.SIDE_BY_SIDE, look, 1024)
        val bmp = CollageExporter.render(context, cropped, Style.SIDE_BY_SIDE, look, 1024)
        // The crop halves the width, so cells are 3:8.
        assertThat(plan.before.width.toFloat() / plan.before.height).isWithin(0.02f).of(0.375f)
        fun near(px: Int, want: Int) = Color.red(px) / 64 == Color.red(want) / 64 && Color.green(px) / 64 == Color.green(want) / 64 && Color.blue(px) / 64 == Color.blue(want) / 64
        val bLeft = bmp.getPixel(plan.before.left + plan.before.width / 8, plan.before.top + plan.before.height * 3 / 4)
        val aLeft = bmp.getPixel(plan.after.left + plan.after.width / 8, plan.after.top + plan.after.height * 3 / 4)
        assertThat(near(bLeft, Color.BLUE)).isTrue()
        assertThat(near(aLeft, Color.YELLOW)).isTrue()
        bmp.recycle()
    }

    @Test fun `split shows the before's left half and the after's right half`() {
        val look = CollageExporter.Look(CollageFrame.of(ClientTemplate.CLASSIC, null), "", watermark = false)
        val plan = CollageExporter.planOf(pair, Style.SPLIT, look, 1024)
        val bmp = CollageExporter.render(context, pair, Style.SPLIT, look, 1024)
        val y = plan.before.top + plan.before.height * 3 / 4
        assertThat(Color.red(bmp.getPixel(plan.before.left + 10, y))).isGreaterThan(200)
        val right = bmp.getPixel(plan.after.right - 10, y)
        assertThat(Color.red(right)).isGreaterThan(200)
        assertThat(Color.green(right)).isGreaterThan(200) // yellow
        bmp.recycle()
    }
}
