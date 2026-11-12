package com.greasepencil.pairshot.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter fun sourceToString(s: JobSource): String = s.name
    @TypeConverter fun stringToSource(s: String): JobSource = JobSource.valueOf(s)
}

@Database(
    entities = [JobEntity::class, AreaEntity::class, PhotoEntity::class, PairEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun jobs(): JobDao
    abstract fun areas(): AreaDao
    abstract fun photos(): PhotoDao
    abstract fun pairs(): PairDao
}
