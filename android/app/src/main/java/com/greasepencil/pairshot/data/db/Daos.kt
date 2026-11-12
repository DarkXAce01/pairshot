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
          (SELECT MAX(ph.takenAt) FROM photos ph WHERE ph.jobId = j.id) AS lastShotAt
        FROM jobs j
        """
    )
    fun observeSummaries(): Flow<List<JobSummary>>

    @Query("SELECT * FROM jobs WHERE id = :id")
    fun observe(id: String): Flow<JobEntity?>

    @Query("SELECT * FROM jobs WHERE id = :id")
    suspend fun get(id: String): JobEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(job: JobEntity)

    @Update
    suspend fun update(job: JobEntity)

    @Query("DELETE FROM jobs WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE jobs SET updatedAt = :now WHERE id = :id")
    suspend fun touch(id: String, now: Long)
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
        ORDER BY p.createdAt
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

    @Query("SELECT * FROM pairs WHERE id = :id")
    suspend fun get(id: String): PairEntity?

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM pairs WHERE jobId = :jobId AND areaId IS :areaId")
    suspend fun nextPosition(jobId: String, areaId: String?): Int

    @Insert
    suspend fun insert(pair: PairEntity)

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
