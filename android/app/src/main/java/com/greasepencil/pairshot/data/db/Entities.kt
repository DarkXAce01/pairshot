package com.greasepencil.pairshot.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class JobSource { MANUAL, CRM }

@Entity(tableName = "jobs", indices = [Index(value = ["externalId"], unique = true)])
data class JobEntity(
    @PrimaryKey val id: String,
    val externalId: String? = null,
    val source: JobSource = JobSource.MANUAL,
    val clientName: String,
    val clientPhone: String? = null,
    val clientEmail: String? = null,
    val address: String? = null,
    val service: String? = null,
    /** Epoch millis of the scheduled visit, if known. */
    val scheduledAt: Long? = null,
    val notes: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "areas",
    foreignKeys = [ForeignKey(JobEntity::class, ["id"], ["jobId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("jobId")],
)
data class AreaEntity(
    @PrimaryKey val id: String,
    val jobId: String,
    val name: String,
    val position: Int,
)

/**
 * One photo file. Pixels are stored exactly as the preview showed them (portrait, sensor-natural
 * frame), so a before can be laid over the live preview with no maths. [uprightRotation] is how far
 * to turn it clockwise for upright display when the phone was held sideways.
 */
@Entity(
    tableName = "photos",
    foreignKeys = [ForeignKey(JobEntity::class, ["id"], ["jobId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("jobId")],
)
data class PhotoEntity(
    @PrimaryKey val id: String,
    val jobId: String,
    val fileName: String,
    val width: Int,
    val height: Int,
    val zoomRatio: Float,
    val uprightRotation: Int,
    val takenAt: Long,
)

@Entity(
    tableName = "pairs",
    foreignKeys = [
        ForeignKey(JobEntity::class, ["id"], ["jobId"], onDelete = ForeignKey.CASCADE),
        // Deleting a label keeps its photos; they just lose the label.
        ForeignKey(AreaEntity::class, ["id"], ["areaId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("jobId"), Index("areaId"), Index("beforePhotoId"), Index("afterPhotoId")],
)
data class PairEntity(
    @PrimaryKey val id: String,
    val jobId: String,
    /** The label ("Driveway"). Optional: null means no label. */
    val areaId: String? = null,
    val beforePhotoId: String,
    val afterPhotoId: String? = null,
    val position: Int,
    val hidden: Boolean = false,
    val isCover: Boolean = false,
    val createdAt: Long,
)

/** A pair with what the screens need from its area and photos. */
data class PairRow(
    @Embedded val pair: PairEntity,
    val areaName: String?,
    val areaPosition: Int?,
    val beforeFile: String,
    val beforeZoom: Float,
    val beforeRotation: Int,
    val beforeWidth: Int,
    val beforeHeight: Int,
    val afterFile: String?,
    val afterRotation: Int?,
    val afterWidth: Int?,
    val afterHeight: Int?,
) {
    val needsAfter: Boolean get() = pair.afterPhotoId == null
}

data class JobSummary(
    @Embedded val job: JobEntity,
    val pairCount: Int,
    val doneCount: Int,
    val coverFile: String?,
    val coverRotation: Int?,
    @ColumnInfo(name = "lastShotAt") val lastShotAt: Long?,
)

/** Where a job's client link is at. One job has at most one gallery (one link). */
enum class SendState { QUEUED, SENDING, SENT, FAILED }

@Entity(
    tableName = "galleries",
    foreignKeys = [ForeignKey(JobEntity::class, ["id"], ["jobId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["jobId"], unique = true)],
)
data class GalleryEntity(
    /** Made on the phone, so a send that is retried can never make two galleries. */
    @PrimaryKey val id: String,
    val jobId: String,
    val sendState: SendState,
    /** The server's refusal or problem, in words for the user. */
    val error: String? = null,
    val url: String? = null,
    /** live, expired or revoked, as the server last said. */
    val remoteState: String? = null,
    val publishedAt: Long? = null,
    val expiresAt: Long? = null,
    val firstViewedAt: Long? = null,
    val viewCount: Int = 0,
    /** What the page looked like when it was last sent, to tell "changes not sent yet". */
    val sentFingerprint: String? = null,
    /** The user is waiting to share the link as soon as it's ready. */
    val shareWhenReady: Boolean = false,
    val updatedAt: Long,
) {
    val isLive: Boolean get() = url != null && remoteState == "live"
}
