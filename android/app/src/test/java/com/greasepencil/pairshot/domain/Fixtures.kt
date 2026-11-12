package com.greasepencil.pairshot.domain

import com.greasepencil.pairshot.data.db.JobEntity
import com.greasepencil.pairshot.data.db.JobSummary
import com.greasepencil.pairshot.data.db.PairEntity
import com.greasepencil.pairshot.data.db.PairRow

/** A pair shot at time [shotAt]; [areaPosition] null = no label. */
fun pairRow(
    id: String,
    shotAt: Long,
    hasAfter: Boolean = false,
    hidden: Boolean = false,
    areaPosition: Int? = null,
    area: String? = areaPosition?.let { "Area $it" },
    cover: Boolean = false,
    beforeRotation: Int = 0,
) = PairRow(
    pair = PairEntity(
        id = id, jobId = "job", areaId = areaPosition?.let { "a$it" }, beforePhotoId = "b-$id",
        afterPhotoId = if (hasAfter) "af-$id" else null, position = 0, hidden = hidden, isCover = cover, createdAt = shotAt,
    ),
    areaName = area, areaPosition = areaPosition, beforeFile = "b-$id.jpg", beforeZoom = 1f, beforeRotation = beforeRotation,
    beforeWidth = 3000, beforeHeight = 4000,
    afterFile = if (hasAfter) "af-$id.jpg" else null, afterRotation = if (hasAfter) 0 else null,
    afterWidth = if (hasAfter) 3000 else null, afterHeight = if (hasAfter) 4000 else null,
)

fun summary(
    id: String,
    name: String = id,
    scheduledAt: Long? = null,
    updatedAt: Long = 0,
    lastShotAt: Long? = null,
    address: String? = null,
    service: String? = null,
) = JobSummary(
    job = JobEntity(
        id = id, clientName = name, scheduledAt = scheduledAt, createdAt = 0, updatedAt = updatedAt,
        address = address, service = service,
    ),
    pairCount = 0, doneCount = 0, coverFile = null, coverRotation = null, lastShotAt = lastShotAt,
)
