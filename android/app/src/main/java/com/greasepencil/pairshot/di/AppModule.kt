package com.greasepencil.pairshot.di

import android.content.Context
import androidx.room.Room
import com.greasepencil.pairshot.data.db.AppDatabase
import com.greasepencil.pairshot.data.db.MIGRATION_1_2
import com.greasepencil.pairshot.data.repo.Clock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "pairshot.db")
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides
    fun clock(): Clock = Clock.System
}
