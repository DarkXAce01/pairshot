package com.greasepencil.pairshot.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface JobDao {
    @Query(
        """
        SELECT j.*,
          (SELECT COUNT(*) FROM pairs p WHERE p.jobId = j.id) AS pairCount,
          (SELECT COUNT(*) FROM pairs p WHERE p.jobId = j.id AND p.afterPhotoId IS NOT NULL) AS doneCount,
          (SELECT ph.fileName FROM pairs p JOIN photos ph ON ph.id = COALESCE(p.afterPhotoId, p.beforePhotoId)
             WHERE p.jobId = j.id AND p.hidden = 0 ORDER BY p.isCover DESC, p.createdAt LIMIT 1) AS coverFile,
          (SELECT ph.uprightRotation FROM pairs p JOIN photos ph ON ph.id = COALESCE(p.afterPhotoId, p.beforePhotoId)
             WHERE p.jobId = j.id AND p.hidden = 0 ORDER BY p.isCover DESC, p.createdAt LIMIT 1) AS coverRotation,
          (SELECT MAX(ph.takenAt) FROM photos ph WHERE ph.jobId = j.id) AS lastShotAt,
          g.sendState AS linkState, g.remoteState AS linkRemoteState, g.firstViewedAt AS linkViewedAt
        FROM jobs j LEFT JOIN galleries g ON g.jobId = j.id
        """
    )
    fun observeSummaries(): Flow<List<JobSummary>>

    @Query("SELECT * FROM jobs WHERE id = :id")
    fun observe(id: String): Flow<JobEntity?>

    @Query("SELECT * FROM jobs WHERE id = :id")
    suspend fun get(id: String): JobEntity?

    @Query("SELECT * FROM jobs WHERE externalId = :externalId")
    suspend fun byExternalId(externalId: String): JobEntity?

    @Query("SELECT COUNT(*) FROM pairs WHERE jobId = :jobId")
    suspend fun pairCount(jobId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(job: JobEntity)

    @Update
    suspend fun update(job: JobEntity)

    @Query("DELETE FROM jobs WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE jobs SET updatedAt = :now WHERE id = :id")
    suspend fun touch(id: String, now: Long)

    @Query("UPDATE jobs SET template = :template WHERE id = :id")
    suspend fun setTemplate(id: String, template: String?)
}

@Dao
interface AreaDao {
    @Query("SELECT * FROM areas WHERE jobId = :jobId ORDER BY position")
    fun observeForJob(jobId: String): Flow<List<AreaEntity>>

    @Query("SELECT * FROM areas WHERE jobId = :jobId ORDER BY position")
    suspend fun forJob(jobId: String): List<AreaEntity>

    @Query("SELECT * FROM areas WHERE id = :id")
    suspend fun get(id: String): AreaEntity?

    @Insert
    suspend fun insert(area: AreaEntity)

    @Query("UPDATE areas SET name = :name WHERE id = :id")
    suspend fun rename(id: String, name: String)

    @Query("DELETE FROM areas WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT name FROM areas")
    suspend fun allNames(): List<String>
}

@Dao
interface PhotoDao {
    @Insert
    suspend fun insert(photo: PhotoEntity)

    @Query("SELECT * FROM photos WHERE id = :id")
    suspend fun get(id: String): PhotoEntity?

    @Query("SELECT * FROM photos WHERE jobId = :jobId")
    suspend fun forJob(jobId: String): List<PhotoEntity>

    @Query("SELECT * FROM photos")
    suspend fun all(): List<PhotoEntity>

    @Query("DELETE FROM photos WHERE id = :id")
    suspend fun delete(id: String)

    /** Photos no pair points at any more (after a retake or a delete). */
    @Query(
        """SELECT * FROM photos WHERE jobId = :jobId AND id NOT IN
           (SELECT beforePhotoId FROM pairs WHERE jobId = :jobId)
           AND id NOT IN (SELECT afterPhotoId FROM pairs WHERE jobId = :jobId AND afterPhotoId IS NOT NULL)"""
    )
    suspend fun orphans(jobId: String): List<PhotoEntity>
}

@Dao
interface PairDao {
    @Query(
        """
        SELECT p.*, a.name AS areaName, a.position AS areaPosition,
               b.fileName AS beforeFile, b.zoomRatio AS beforeZoom, b.uprightRotation AS beforeRotation,
               b.width AS beforeWidth, b.height AS beforeHeight,
               af.fileName AS afterFile, af.uprightRotation AS afterRotation,
               af.width AS afterWidth, af.height AS afterHeight
        FROM pairs p
        LEFT JOIN areas a ON a.id = p.areaId
        JOIN photos b ON b.id = p.beforePhotoId
        LEFT JOIN photos af ON af.id = p.afterPhotoId
        WHERE p.jobId = :jobId
        ORDER BY p.position, p.createdAt
        """
    )
    fun observeForJob(jobId: String): Flow<List<PairRow>>

    @Query(
        """
        SELECT p.*, a.name AS areaName, a.position AS areaPosition,
               b.fileName AS beforeFile, b.zoomRatio AS beforeZoom, b.uprightRotation AS beforeRotation,
               b.width AS beforeWidth, b.height AS beforeHeight,
               af.fileName AS afterFile, af.uprightRotation AS afterRotation,
               af.width AS afterWidth, af.height AS afterHeight
        FROM pairs p
        LEFT JOIN areas a ON a.id = p.areaId
        JOIN photos b ON b.id = p.beforePhotoId
        LEFT JOIN photos af ON af.id = p.afterPhotoId
        WHERE p.id = :id
        """
    )
    fun observe(id: String): Flow<PairRow?>

    /** The cover (or first finished) pair of the job shot most recently, for the look thumbnails. */
    @Query(
        """
        SELECT p.*, a.name AS areaName, a.position AS areaPosition,
               b.fileName AS beforeFile, b.zoomRatio AS beforeZoom, b.uprightRotation AS beforeRotation,
               b.width AS beforeWidth, b.height AS beforeHeight,
               af.fileName AS afterFile, af.uprightRotation AS afterRotation,
               af.width AS afterWidth, af.height AS afterHeight
        FROM pairs p
        LEFT JOIN areas a ON a.id = p.areaId
        JOIN photos b ON b.id = p.beforePhotoId
        JOIN photos af ON af.id = p.afterPhotoId
        WHERE p.hidden = 0 AND p.jobId = (
            SELECT jobId FROM pairs WHERE afterPhotoId IS NOT NULL AND hidden = 0 ORDER BY createdAt DESC LIMIT 1
        )
        ORDER BY p.isCover DESC, p.createdAt
        LIMIT 1
        """
    )
    fun observeLatestFinished(): Flow<PairRow?>

    @Query("SELECT * FROM pairs WHERE id = :id")
    suspend fun get(id: String): PairEntity?

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM pairs WHERE jobId = :jobId AND areaId IS :areaId")
    suspend fun nextPosition(jobId: String, areaId: String?): Int

    @Insert
    suspend fun insert(pair: PairEntity)

    /** The pairs sharing [areaId] (null: unlabelled) in a job, in their order. */
    @Query("SELECT * FROM pairs WHERE jobId = :jobId AND areaId IS :areaId ORDER BY position, createdAt")
    suspend fun inArea(jobId: String, areaId: String?): List<PairEntity>

    @Query("SELECT * FROM pairs WHERE jobId = :jobId")
    suspend fun forJob(jobId: String): List<PairEntity>

    @Query("SELECT * FROM pairs WHERE beforePhotoId IN (:photoIds) OR afterPhotoId IN (:photoIds)")
    suspend fun usingPhotos(photoIds: List<String>): List<PairEntity>

    @Update
    suspend fun update(pair: PairEntity)

    @Query("DELETE FROM pairs WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE pairs SET isCover = 0 WHERE jobId = :jobId")
    suspend fun clearCover(jobId: String)

    @Transaction
    suspend fun setCover(jobId: String, pairId: String) {
        clearCover(jobId)
        get(pairId)?.let { update(it.copy(isCover = true)) }
    }
}

@Dao
interface GalleryDao {
    @Query("SELECT * FROM galleries WHERE jobId = :jobId")
    fun observeForJob(jobId: String): Flow<GalleryEntity?>

    @Query("SELECT * FROM galleries WHERE jobId = :jobId")
    suspend fun forJob(jobId: String): GalleryEntity?

    @Query("SELECT * FROM galleries WHERE id = :id")
    suspend fun get(id: String): GalleryEntity?

    @Query("SELECT * FROM galleries")
    suspend fun all(): List<GalleryEntity>

    @Query("SELECT * FROM galleries")
    fun observeAll(): Flow<List<GalleryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(g: GalleryEntity)

    @Query("DELETE FROM galleries WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM galleries")
    suspend fun deleteAll()

    /** Jobs whose link was first sent before [before]: the ones "free up space" can remove. */
    @Query("SELECT jobId FROM galleries WHERE publishedAt IS NOT NULL AND publishedAt < :before")
    suspend fun jobsSentBefore(before: Long): List<String>
}
