package com.greasepencil.b4after.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.greasepencil.b4after.ui.components.CollageExporter
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CenterCropTest {
    @Test fun `wide source is trimmed evenly left and right`() {
        val r = CollageExporter.centerCrop(400, 300, 300, 300)
        assertThat(r.width()).isEqualTo(300)
        assertThat(r.height()).isEqualTo(300)
        assertThat(r.left).isEqualTo(50)
    }

    @Test fun `tall source is trimmed evenly top and bottom`() {
        val r = CollageExporter.centerCrop(300, 500, 300, 300)
        assertThat(r.height()).isEqualTo(300)
        assertThat(r.top).isEqualTo(100)
    }
}
