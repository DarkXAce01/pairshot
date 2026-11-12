package com.greasepencil.pairshot.domain.gallery

import com.greasepencil.pairshot.data.db.JobEntity
import com.greasepencil.pairshot.data.db.PairRow
import com.greasepencil.pairshot.domain.Rotation
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Everything the client page needs, as data. The phone builds it; the preview and (later) the server
 * drop it into the same template (shared/gallery/gallery.html).
 */
@Serializable
data class GalleryManifest(
    val v: Int = 1,
    val business: Business,
    val job: JobInfo,
    val cover: PairItem?,
    val sections: List<Section>,
    /** "Made with Pairshot" in the footer (free plan). */
    val badge: Boolean,
) {
    @Serializable data class Business(
        val name: String,
        val phone: String? = null,
        val reviewUrl: String? = null,
        val bookingUrl: String? = null,
        val accent: String? = null,
    )
    @Serializable data class JobInfo(val title: String, val place: String? = null, val date: String? = null)
    @Serializable data class Img(val src: String, val w: Int, val h: Int)
    @Serializable data class PairItem(val id: String, val label: String? = null, val before: Img, val after: Img)
    @Serializable data class Section(val label: String? = null, val pairs: List<PairItem>)


    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        val json = Json { encodeDefaults = false; explicitNulls = false }
    }
}

data class BusinessInfo(
    val name: String,
    val phone: String,
    val reviewUrl: String,
    val bookingUrl: String,
    val isPro: Boolean,
)

object GalleryBuilder {
    /**
     * @param imageUrl where a photo will be served from, given its file name and upright rotation
     */
    fun build(
        job: JobEntity,
        pairs: List<PairRow>,
        business: BusinessInfo,
        imageUrl: (fileName: String, rotation: Int) -> String,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): GalleryManifest {
        val shown = pairs.filter { !it.needsAfter && !it.pair.hidden }
        val cover = shown.firstOrNull { it.pair.isCover } ?: shown.minByOrNull { it.pair.createdAt }

        fun item(r: PairRow): GalleryManifest.PairItem {
            val (bw, bh) = Rotation.size(r.beforeWidth, r.beforeHeight, r.beforeRotation)
            val ar = r.afterRotation ?: 0
            val (aw, ah) = Rotation.size(r.afterWidth ?: bw, r.afterHeight ?: bh, ar)
            return GalleryManifest.PairItem(
                id = r.pair.id,
                label = r.areaName,
                before = GalleryManifest.Img(imageUrl(r.beforeFile, r.beforeRotation), bw, bh),
                after = GalleryManifest.Img(imageUrl(r.afterFile!!, ar), aw, ah),
            )
        }

        // Unlabelled first (it's what most crews shoot), then labels in the order they were added.
        val rest = shown.filter { it.pair.id != cover?.pair?.id }
        val sections = rest
            .groupBy { it.pair.areaId }
            .entries
            .sortedBy { (areaId, rows) -> if (areaId == null) -1 else rows.first().areaPosition ?: Int.MAX_VALUE }
            .map { (_, rows) ->
                GalleryManifest.Section(rows.first().areaName, rows.sortedBy { it.pair.createdAt }.map(::item))
            }

        val when_ = job.scheduledAt ?: shown.maxOfOrNull { it.pair.createdAt }
        return GalleryManifest(
            business = GalleryManifest.Business(
                name = business.name.trim(),
                phone = business.phone.trim().ifEmpty { null },
                reviewUrl = business.reviewUrl.trim().ifEmpty { null }?.let(::normaliseUrl),
                bookingUrl = business.bookingUrl.trim().ifEmpty { null }?.let(::normaliseUrl),
            ),
            job = GalleryManifest.JobInfo(
                title = job.service ?: "Your before & after",
                place = job.address?.let(::street),
                date = when_?.let {
                    DateTimeFormatter.ofPattern("d MMMM yyyy", locale).format(Instant.ofEpochMilli(it).atZone(zone))
                },
            ),
            cover = cover?.let(::item),
            sections = sections,
            badge = !business.isPro,
        )
    }

    /** "12 Oak St, Springfield, IL 62704" → "12 Oak St": enough for the client, less to leak. */
    fun street(address: String): String = address.substringBefore(',').trim()

    fun normaliseUrl(u: String): String = if (u.startsWith("http://") || u.startsWith("https://")) u else "https://$u"

    /** Puts the manifest into the template, safe inside a <script> block. */
    fun render(template: String, manifest: GalleryManifest, meta: String = ""): String {
        val safe = manifest.toJson()
            .replace("<", "\\u003c")
            .replace("\u2028", "\\u2028")
            .replace("\u2029", "\\u2029")
        return template.replace("/*DATA*/", safe).replace("<!--META-->", meta)
    }
}
