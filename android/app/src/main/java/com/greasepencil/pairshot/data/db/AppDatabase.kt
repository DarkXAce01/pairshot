package com.greasepencil.pairshot.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Converters {
    @TypeConverter fun sourceToString(s: JobSource): String = s.name
    @TypeConverter fun stringToSource(s: String): JobSource = JobSource.valueOf(s)
    @TypeConverter fun sendStateToString(s: SendState): String = s.name
    @TypeConverter fun stringToSendState(s: String): SendState = SendState.valueOf(s)
}

@Database(
    entities = [JobEntity::class, AreaEntity::class, PhotoEntity::class, PairEntity::class, GalleryEntity::class],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun jobs(): JobDao
    abstract fun areas(): AreaDao
    abstract fun photos(): PhotoDao
    abstract fun pairs(): PairDao
    abstract fun galleries(): GalleryDao
}

/** v2: a pair's label (area) became optional, and deleting a label keeps its photos. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `pairs_new` (`id` TEXT NOT NULL, `jobId` TEXT NOT NULL, `areaId` TEXT,
               `beforePhotoId` TEXT NOT NULL, `afterPhotoId` TEXT, `position` INTEGER NOT NULL,
               `hidden` INTEGER NOT NULL, `isCover` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL,
               PRIMARY KEY(`id`),
               FOREIGN KEY(`jobId`) REFERENCES `jobs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE ,
               FOREIGN KEY(`areaId`) REFERENCES `areas`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )""",
        )
        db.execSQL(
            """INSERT INTO pairs_new (id, jobId, areaId, beforePhotoId, afterPhotoId, position, hidden, isCover, createdAt)
               SELECT id, jobId, areaId, beforePhotoId, afterPhotoId, position, hidden, isCover, createdAt FROM pairs""",
        )
        db.execSQL("DROP TABLE pairs")
        db.execSQL("ALTER TABLE pairs_new RENAME TO pairs")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pairs_jobId` ON `pairs` (`jobId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pairs_areaId` ON `pairs` (`areaId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pairs_beforePhotoId` ON `pairs` (`beforePhotoId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pairs_afterPhotoId` ON `pairs` (`afterPhotoId`)")
    }
}

/** v3: client links (galleries). */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `galleries` (`id` TEXT NOT NULL, `jobId` TEXT NOT NULL, `sendState` TEXT NOT NULL,
               `error` TEXT, `url` TEXT, `remoteState` TEXT, `publishedAt` INTEGER, `expiresAt` INTEGER,
               `firstViewedAt` INTEGER, `viewCount` INTEGER NOT NULL, `sentFingerprint` TEXT,
               `shareWhenReady` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`),
               FOREIGN KEY(`jobId`) REFERENCES `jobs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_galleries_jobId` ON `galleries` (`jobId`)")
    }
}

val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
