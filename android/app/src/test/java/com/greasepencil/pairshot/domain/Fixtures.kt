package com.greasepencil.pairshot.domain

import com.greasepencil.pairshot.data.db.JobEntity
import com.greasepencil.pairshot.data.db.JobSummary
import com.greasepencil.pairshot.data.db.PairEntity
import com.greasepencil.pairshot.data.db.PairRow

fun pairRow(
    id: String,
    areaPosition: Int,
    position: Int,
    hasAfter: Boolean = false,
    hidden: Boolean = false,
    area: String = "Area $areaPosition",
) = PairRow(
    pair = PairEntity(
        id = id, jobId = "job", areaId = "a$areaPosition", beforePhotoId = "b-$id",
        afterPhotoId = if (hasAfter) "af-$id" else null, position = position, hidden = hidden, createdAt = 0,
    ),
    areaName = area, areaPosition = areaPosition, beforeFile = "b-$id.jpg", beforeZoom = 1f, beforeRotation = 0,
    afterFile = if (hasAfter) "af-$id.jpg" else null, afterRotation = if (hasAfter) 0 else null,
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
