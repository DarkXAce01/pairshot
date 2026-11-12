package com.greasepencil.pairshot.domain

import com.google.common.truth.Truth.assertThat
import com.greasepencil.pairshot.data.db.JobEntity
import com.greasepencil.pairshot.domain.gallery.BusinessInfo
import com.greasepencil.pairshot.domain.gallery.GalleryBuilder
import com.greasepencil.pairshot.domain.gallery.GalleryManifest
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale

class GalleryBuilderTest {
    private val zone = ZoneOffset.UTC
    private val job = JobEntity(
        id = "job", clientName = "Dana Ruiz", service = "House wash", address = "12 Oak St, Springfield, IL",
        scheduledAt = LocalDateTime.of(2026, 11, 12, 9, 0).toInstant(zone).toEpochMilli(), createdAt = 0, updatedAt = 0,
    )
    private val biz = BusinessInfo("Shine Bros", "555 0100", "g.page/r/shine", "", isPro = false)
    private fun build(rows: List<com.greasepencil.pairshot.data.db.PairRow>, b: BusinessInfo = biz) =
        GalleryBuilder.build(job, rows, b, { f, r -> "/p/$r/$f" }, zone, Locale.UK)

    @Test fun `only finished, visible pairs reach the client`() {
        val m = build(listOf(pairRow("done", 1, hasAfter = true), pairRow("open", 2), pairRow("hid", 3, hasAfter = true, hidden = true)))
        assertThat(m.cover?.id).isEqualTo("done")
        assertThat(m.sections.flatMap { it.pairs }).isEmpty()
    }

    @Test fun `chosen cover leads, and is not repeated below`() {
        val m = build(listOf(pairRow("a", 1, hasAfter = true), pairRow("b", 2, hasAfter = true, cover = true)))
        assertThat(m.cover?.id).isEqualTo("b")
        assertThat(m.sections.flatMap { it.pairs }.map { it.id }).containsExactly("a")
    }

    @Test fun `without a chosen cover the first shot leads`() {
        val m = build(listOf(pairRow("late", 5, hasAfter = true), pairRow("early", 1, hasAfter = true)))
        assertThat(m.cover?.id).isEqualTo("early")
    }

    @Test fun `unlabelled first, then labels in order, pairs in shooting order`() {
        val m = build(
            listOf(
                pairRow("cover", 0, hasAfter = true, cover = true),
                pairRow("patio2", 6, hasAfter = true, areaPosition = 1, area = "Patio"),
                pairRow("drive", 3, hasAfter = true, areaPosition = 0, area = "Driveway"),
                pairRow("loose", 4, hasAfter = true),
                pairRow("patio1", 2, hasAfter = true, areaPosition = 1, area = "Patio"),
            ),
        )
        assertThat(m.sections.map { it.label }).containsExactly(null, "Driveway", "Patio").inOrder()
        assertThat(m.sections.last().pairs.map { it.id }).containsExactly("patio1", "patio2").inOrder()
    }

    @Test fun `sideways photos get swapped sizes and their rotation in the url`() {
        val m = build(listOf(pairRow("s", 1, hasAfter = true, beforeRotation = 90)))
        val before = m.cover!!.before
        assertThat(before.w to before.h).isEqualTo(4000 to 3000)
        assertThat(before.src).isEqualTo("/p/90/b-s.jpg")
    }

    @Test fun `job details are trimmed to what the client needs`() {
        val m = build(listOf(pairRow("a", 1, hasAfter = true)))
        assertThat(m.job.title).isEqualTo("House wash")
        assertThat(m.job.place).isEqualTo("12 Oak St")
        assertThat(m.job.date).isEqualTo("12 November 2026")
        assertThat(m.toJson()).doesNotContain("Dana")
    }

    @Test fun `links get https, empty ones disappear, free plan shows the badge`() {
        val m = build(listOf(pairRow("a", 1, hasAfter = true)))
        assertThat(m.business.reviewUrl).isEqualTo("https://g.page/r/shine")
        assertThat(m.business.bookingUrl).isNull()
        assertThat(m.badge).isTrue()
        assertThat(build(listOf(pairRow("a", 1, hasAfter = true)), biz.copy(isPro = true)).badge).isFalse()
    }

    @Test fun `render can't be broken out of the script tag`() {
        val m = GalleryManifest(
            business = GalleryManifest.Business("</script><script>alert(1)</script>"),
            job = GalleryManifest.JobInfo("x"), cover = null, sections = emptyList(), badge = false,
        )
        val html = GalleryBuilder.render("<script>/*DATA*/</script><!--META-->", m, "<meta x>")
        assertThat(html).doesNotContain("</script><script>alert")
        assertThat(html).contains("\\u003c/script>")
        assertThat(html).endsWith("<meta x>")
    }

    @Test fun `street keeps the first line only`() {
        assertThat(GalleryBuilder.street("12 Oak St, Springfield")).isEqualTo("12 Oak St")
        assertThat(GalleryBuilder.street("Unit 4")).isEqualTo("Unit 4")
    }
}
