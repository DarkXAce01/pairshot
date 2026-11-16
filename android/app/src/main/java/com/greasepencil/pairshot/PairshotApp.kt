package com.greasepencil.pairshot

import android.app.Application
import com.greasepencil.pairshot.work.AppVisibility
import com.greasepencil.pairshot.work.CrmSyncWorker
import com.greasepencil.pairshot.work.Notifier
import com.greasepencil.pairshot.data.repo.JobRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class PairshotApp : Application() {
    @Inject lateinit var repo: JobRepository

    override fun onCreate() {
        super.onCreate()
        // Pairs whose photo files are gone (e.g. restored from an old backup) would block every send.
        @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            runCatching { repo.repairMissingPhotos() }.onSuccess { n -> if (n > 0) android.util.Log.i("Pairshot", "repaired $n pairs with missing photos") }
        }
        registerActivityLifecycleCallbacks(AppVisibility)
        Notifier.createChannel(this)
        // Does nothing until the account is Pro and signed in; cheap otherwise.
        CrmSyncWorker.schedule(this)
    }
}
