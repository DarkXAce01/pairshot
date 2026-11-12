package com.greasepencil.pairshot.di

import com.greasepencil.pairshot.data.cloud.CloudAccess
import com.greasepencil.pairshot.data.cloud.CloudSession
import com.greasepencil.pairshot.data.cloud.PhotoSource
import com.greasepencil.pairshot.data.cloud.PreparedPhotos
import com.greasepencil.pairshot.data.prefs.SettingsSource
import com.greasepencil.pairshot.data.prefs.SettingsStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class CloudModule {
    @Binds abstract fun cloudAccess(s: CloudSession): CloudAccess
    @Binds abstract fun photoSource(p: PreparedPhotos): PhotoSource
    @Binds abstract fun settingsSource(s: SettingsStore): SettingsSource
}
