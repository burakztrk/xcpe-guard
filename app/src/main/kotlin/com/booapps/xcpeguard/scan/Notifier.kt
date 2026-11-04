package com.booapps.xcpeguard.scan

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.booapps.xcpeguard.R
import com.booapps.xcpeguard.ui.MainActivity

object Notifier {
    private const val CHANNEL = "xcpe"

    fun alert(c: Context, r: Report) {
        val open = Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching {
            val nm = c.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, c.getString(R.string.notif_channel),
                NotificationManager.IMPORTANCE_HIGH))
            val pi = PendingIntent.getActivity(c, 0, open, PendingIntent.FLAG_IMMUTABLE)
            nm.notify(1, Notification.Builder(c, CHANNEL).setSmallIcon(R.drawable.ic_shield)
                .setContentTitle(c.getString(R.string.notif_title))
                .setContentText(c.getString(R.string.notif_text, r.count(Level.FIXED), r.count(Level.ALARM)))
                .setContentIntent(pi).setAutoCancel(true).build())
        }
        if (Settings.canDrawOverlays(c)) runCatching { c.startActivity(open) }
    }
}
