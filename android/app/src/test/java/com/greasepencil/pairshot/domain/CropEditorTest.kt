package com.greasepencil.pairshot.domain

import com.google.common.truth.Truth.assertThat
import com.greasepencil.pairshot.ui.pair.Grip
import com.greasepencil.pairshot.ui.pair.centredCrop
import com.greasepencil.pairshot.ui.pair.dragCrop
import org.junit.Test

class CropEditorTest {
    @Test fun squareInPortraitPhoto() {
        val c = centredCrop(1f, 3f / 4f)
        assertThat(c.left).isEqualTo(0f)
        assertThat(c.aspect(3, 4)).isWithin(1e-4f).of(1f)
    }

    @Test fun moveStaysInside() {
        val c = dragCrop(Crop(0.2f, 0.2f, 0.8f, 0.8f), Grip.MOVE, 0.5f, -0.5f, null)
        assertThat(c.left).isWithin(1e-5f).of(0.4f)
        assertThat(c.top).isEqualTo(0f)
        assertThat(c.right).isEqualTo(1f)
        assertThat(c.bottom).isWithin(1e-5f).of(0.6f)
    }

    @Test fun cornerResizeKeepsMinimum() {
        val c = dragCrop(Crop(0.2f, 0.2f, 0.8f, 0.8f), Grip.BR, -0.9f, -0.9f, null)
        assertThat(c.width).isWithin(1e-4f).of(Crop.MIN)
        assertThat(c.height).isWithin(1e-4f).of(Crop.MIN)
    }

    @Test fun lockedResizeKeepsShape() {
        val start = Crop(0.1f, 0.1f, 0.5f, 0.5f)
        val c = dragCrop(start, Grip.BR, 0.2f, 0.05f, lockAspect = 1f)
        assertThat(c.width).isWithin(1e-4f).of(c.height)
        assertThat(c.left).isEqualTo(0.1f)
        assertThat(c.right).isAtMost(1f)
    }
}
