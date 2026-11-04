package com.booapps.xcpeguard.scan

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.booapps.xcpeguard.Prefs
import kotlin.concurrent.thread

class ScanJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        if (!Prefs.setupDone(this) || !Prefs.scheduled(this)) return false
        if (params.jobId == PERIODIC && System.currentTimeMillis() - Store.lastTime(this) < Store.INTERVAL_MS / 2) return false
        thread(name = "xcpe-job") {
            Scanner.run(this, allowPrompt = false)
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters) = true

    companion object {
        private const val PERIODIC = 1
        private const val AFTER_BOOT = 2

        fun schedule(c: Context) {
            val js = c.getSystemService(JobScheduler::class.java) ?: return
            if (!Prefs.scheduled(c)) {
                js.cancel(PERIODIC)
                js.cancel(AFTER_BOOT)
                return
            }
            if (js.getPendingJob(PERIODIC) != null) return
            js.schedule(JobInfo.Builder(PERIODIC, ComponentName(c, ScanJob::class.java))
                .setPeriodic(Store.INTERVAL_MS).setPersisted(true).build())
        }

        fun afterBoot(c: Context) {
            val js = c.getSystemService(JobScheduler::class.java) ?: return
            js.schedule(JobInfo.Builder(AFTER_BOOT, ComponentName(c, ScanJob::class.java))
                .setMinimumLatency(2 * 60_000L).setOverrideDeadline(5 * 60_000L).build())
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action != Intent.ACTION_BOOT_COMPLETED || !Prefs.setupDone(c) || !Prefs.scheduled(c)) return
        ScanJob.schedule(c)
        ScanJob.afterBoot(c)
    }
}
