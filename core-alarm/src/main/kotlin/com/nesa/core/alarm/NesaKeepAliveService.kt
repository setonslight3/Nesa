package com.nesa.core.alarm

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.nesa.core.notifications.NesaChannels
import com.nesa.core.notifications.R as NotificationsR
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Keeps NESA's process alive so its alarms survive leaving the app.
 *
 * Some phones — Infinix, Tecno, itel and others in that family are the most
 * aggressive — freeze an app the moment it leaves the screen, and a frozen app's
 * alarms do not fire. No permission fixes this, because it is not a permission:
 * the system simply stops running the process.
 *
 * A foreground service is the one thing Android guarantees will not be killed
 * that way. The cost is an ongoing notification the user cannot dismiss, which
 * is why this is off by default and offered rather than imposed — a permanent
 * notification is a real intrusion, and most phones do not need it.
 *
 * It deliberately does nothing but exist. All the scheduling still runs through
 * AlarmManager, so if this service is killed anyway, nothing is lost that was
 * not already lost.
 */
@AndroidEntryPoint
class NesaKeepAliveService : Service() {

    @Inject lateinit var events: AlarmEventLog

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        events.record("keep-alive: started")
    }

    /**
     * Recorded because this is the evidence that matters. If the trace shows
     * the service dying and an alarm arriving late afterwards, the phone is
     * killing it despite the foreground notification, and no amount of
     * application code will change that.
     */
    override fun onDestroy() {
        events.record("keep-alive: STOPPED")
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        return try {
            startForeground(NOTIFICATION_ID, buildNotification())
            // The whole point is to come back if the system does kill it.
            START_STICKY
        } catch (refused: IllegalStateException) {
            Log.w(TAG, "The platform refused to keep NESA running", refused)
            stopSelf()
            START_NOT_STICKY
        }
    }

    private fun buildNotification(): Notification {
        NesaChannels.ensureCreated(this)
        val launch = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(
                this,
                0,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        return NotificationCompat.Builder(this, NesaChannels.SERVICE)
            .setSmallIcon(NotificationsR.drawable.ic_nesa_notification)
            .setContentTitle(getString(NotificationsR.string.nesa_keep_alive_title))
            .setContentText(getString(NotificationsR.string.nesa_keep_alive_text))
            // Lowest possible profile: this is a technical necessity, not news.
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(launch)
            .build()
    }

    companion object {
        private const val TAG = "NesaKeepAlive"
        private const val NOTIFICATION_ID = 1002
        private const val ACTION_STOP = "com.nesa.action.KEEP_ALIVE_STOP"

        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, NesaKeepAliveService::class.java)
                )
            }.onFailure { Log.w(TAG, "Could not start the keep-alive service", it) }
        }

        fun stop(context: Context) {
            runCatching {
                context.startService(
                    Intent(context, NesaKeepAliveService::class.java).apply { action = ACTION_STOP }
                )
            }
        }
    }
}
