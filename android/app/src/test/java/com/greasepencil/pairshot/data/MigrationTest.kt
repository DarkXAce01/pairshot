package com.greasepencil.pairshot.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.greasepencil.pairshot.data.db.AppDatabase
import com.greasepencil.pairshot.data.db.MIGRATION_1_2
import com.greasepencil.pairshot.data.db.MIGRATION_2_3
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Jobs shot on an older version must survive the update. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test fun `v1 pairs keep their labels and photos in v2, and labels become optional`() {
        helper.createDatabase("m.db", 1).apply {
            execSQL("INSERT INTO jobs (id, source, clientName, createdAt, updatedAt) VALUES ('j', 'MANUAL', 'Dana', 1, 1)")
            execSQL("INSERT INTO areas (id, jobId, name, position) VALUES ('a', 'j', 'Driveway', 0)")
            execSQL("INSERT INTO photos (id, jobId, fileName, width, height, zoomRatio, uprightRotation, takenAt) VALUES ('b', 'j', 'b.jpg', 3, 4, 1.0, 0, 1)")
            execSQL("INSERT INTO pairs (id, jobId, areaId, beforePhotoId, afterPhotoId, position, hidden, isCover, createdAt) VALUES ('p', 'j', 'a', 'b', NULL, 0, 0, 1, 5)")
            close()
        }
        val db = helper.runMigrationsAndValidate("m.db", 2, true, MIGRATION_1_2)
        db.query("SELECT areaId, isCover, createdAt FROM pairs WHERE id = 'p'").use { c ->
            assertThat(c.moveToFirst()).isTrue()
            assertThat(c.getString(0)).isEqualTo("a")
            assertThat(c.getInt(1)).isEqualTo(1)
            assertThat(c.getLong(2)).isEqualTo(5)
        }
        // Optional now, and removing a label keeps the pair.
        db.execSQL("INSERT INTO pairs (id, jobId, areaId, beforePhotoId, position, hidden, isCover, createdAt) VALUES ('q', 'j', NULL, 'b', 1, 0, 0, 6)")
        db.execSQL("PRAGMA foreign_keys = ON")
        db.execSQL("DELETE FROM areas WHERE id = 'a'")
        db.query("SELECT COUNT(*), SUM(areaId IS NULL) FROM pairs").use { c ->
            c.moveToFirst()
            assertThat(c.getInt(0)).isEqualTo(2)
            assertThat(c.getInt(1)).isEqualTo(2)
        }
    }

    @Test fun `v2 jobs survive v3, which adds client links`() {
        helper.createDatabase("m3.db", 2).apply {
            execSQL("INSERT INTO jobs (id, source, clientName, createdAt, updatedAt) VALUES ('j', 'MANUAL', 'Dana', 1, 1)")
            close()
        }
        val db = helper.runMigrationsAndValidate("m3.db", 3, true, MIGRATION_2_3)
        db.execSQL("INSERT INTO galleries (id, jobId, sendState, viewCount, shareWhenReady, updatedAt) VALUES ('g', 'j', 'QUEUED', 0, 0, 1)")
        db.query("SELECT clientName FROM jobs").use { c -> c.moveToFirst(); assertThat(c.getString(0)).isEqualTo("Dana") }
        db.execSQL("PRAGMA foreign_keys = ON")
        db.execSQL("DELETE FROM jobs WHERE id = 'j'")
        db.query("SELECT COUNT(*) FROM galleries").use { c -> c.moveToFirst(); assertThat(c.getInt(0)).isEqualTo(0) }
    }

    @Test fun `a v1 phone updates straight to the newest version`() {
        helper.createDatabase("m13.db", 1).apply {
            execSQL("INSERT INTO jobs (id, source, clientName, createdAt, updatedAt) VALUES ('j', 'MANUAL', 'Dana', 1, 1)")
            close()
        }
        helper.runMigrationsAndValidate("m13.db", 3, true, MIGRATION_1_2, MIGRATION_2_3)
    }
}
