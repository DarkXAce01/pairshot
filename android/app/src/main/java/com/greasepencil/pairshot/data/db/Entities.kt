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
        ForeignKey(AreaEntity::class, ["id"], ["areaId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("jobId"), Index("areaId"), Index("beforePhotoId"), Index("afterPhotoId")],
)
data class PairEntity(
    @PrimaryKey val id: String,
    val jobId: String,
    val areaId: String,
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
    val areaName: String,
    val areaPosition: Int,
    val beforeFile: String,
    val beforeZoom: Float,
    val beforeRotation: Int,
    val afterFile: String?,
    val afterRotation: Int?,
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
