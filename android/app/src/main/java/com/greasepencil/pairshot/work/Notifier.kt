package com.greasepencil.pairshot.work

import android.Manifest
import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.greasepencil.pairshot.MainActivity
import com.greasepencil.pairshot.R

/** Whether any screen of the app is showing (so a finished send doesn't also buzz the phone). */
object AppVisibility : Application.ActivityLifecycleCallbacks {
    private var started = 0
    val foreground get() = started > 0
    override fun onActivityStarted(a: Activity) { started++ }
    override fun onActivityStopped(a: Activity) { started = (started - 1).coerceAtLeast(0) }
    override fun onActivityCreated(a: Activity, b: Bundle?) {}
    override fun onActivityResumed(a: Activity) {}
    override fun onActivityPaused(a: Activity) {}
    override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
    override fun onActivityDestroyed(a: Activity) {}
}

object Notifier {
    private const val CHANNEL = "links"
    const val EXTRA_JOB = "open_job"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL, "Client links", NotificationManager.IMPORTANCE_DEFAULT)
            ch.description = "When a link that was waiting for signal has gone out"
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    fun linkReady(context: Context, jobId: String, client: String, updated: Boolean) =
        post(context, jobId, if (updated) "Link updated" else "Link ready for $client", if (updated) "$client's page shows your latest photos." else "Tap to send it.")

    fun sendFailed(context: Context, jobId: String, why: String) = post(context, jobId, "Link not sent", why)

    private fun post(context: Context, jobId: String, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val open = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_JOB, jobId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(context, jobId.hashCode(), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(jobId.hashCode(), n) }
    }
}
