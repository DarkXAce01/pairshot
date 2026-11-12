package com.greasepencil.pairshot

import android.app.Application
import com.greasepencil.pairshot.work.AppVisibility
import com.greasepencil.pairshot.work.CrmSyncWorker
import com.greasepencil.pairshot.work.Notifier
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PairshotApp : Application() {
    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(AppVisibility)
        Notifier.createChannel(this)
        // Does nothing until the account is Pro and signed in; cheap otherwise.
        CrmSyncWorker.schedule(this)
    }
}
