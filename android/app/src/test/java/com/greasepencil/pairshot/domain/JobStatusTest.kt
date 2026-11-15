package com.greasepencil.pairshot.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class JobStatusTest {
    @Test fun statuses() {
        assertThat(JobStatus.of(0, 0, false, false, false)).isEqualTo(JobStatus.UPCOMING)
        assertThat(JobStatus.of(3, 1, false, false, false)).isEqualTo(JobStatus.IN_PROGRESS)
        assertThat(JobStatus.of(3, 3, false, false, false)).isEqualTo(JobStatus.DONE)
        assertThat(JobStatus.of(3, 3, true, true, false)).isEqualTo(JobStatus.SENT)
        assertThat(JobStatus.of(3, 3, true, true, true)).isEqualTo(JobStatus.SEEN)
        // A closed or expired link: back to where the photos stand.
        assertThat(JobStatus.of(3, 3, true, false, true)).isEqualTo(JobStatus.DONE)
    }

    @Test fun cropAspect() {
        assertThat(Crop(0f, 0f, 0.5f, 1f).aspect(4, 3)).isWithin(1e-4f).of(2f / 3f)
        assertThat(Crop.FULL.isFull).isTrue()
    }
}
