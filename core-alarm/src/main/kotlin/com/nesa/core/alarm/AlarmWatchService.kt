package com.nesa.core.alarm

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.nesa.core.notifications.NesaNotifier

/**
 * Keeps NESA's process resident while an alarm is pending.
 *
 * It registers a dynamic BroadcastReceiver at runtime so that when AlarmManager fires,
 * delivery reaches the already-running foreground process directly without having to
 * pass through aggressive OEM auto-start/manifest-broadcast freeze gates (like Transsion XOS).
 */
class AlarmWatchService : Service() {

    private var isReceiverRegistered = false

    private val dynamicAlarmReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AlarmReceiver.ACTION_FIRE) {
                AlarmEventLog.write(context, "watch dynamic receiver caught alarm")
                AlarmReceiver.processAlarmIntent(context, intent, source = "watch dynamic receiver")
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        registerDynamicReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val label = intent?.getStringExtra(EXTRA_NEXT_LABEL)

        if (intent?.action == ACTION_STOP) {
            stopForegroundAndSelf()
            return START_NOT_STICKY
        }

        registerDynamicReceiver()

        return try {
            startForeground(
                NesaNotifier.WATCH_NOTIFICATION_ID,
                NesaNotifier(this).buildWatchNotification(label)
            )
            AlarmEventLog.write(this, "watch running${label?.let { " (next $it)" } ?: ""}")
            START_STICKY
        } catch (refused: IllegalStateException) {
            Log.w(TAG, "The platform refused to start the alarm watch", refused)
            AlarmEventLog.write(this, "watch could not start — alarms may be delivered late")
            stopSelf()
            START_NOT_STICKY
        }
    }

    private fun registerDynamicReceiver() {
        if (!isReceiverRegistered) {
            val filter = IntentFilter(AlarmReceiver.ACTION_FIRE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(dynamicAlarmReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(dynamicAlarmReceiver, filter)
            }
            isReceiverRegistered = true
            Log.d(TAG, "Dynamic alarm receiver registered in watch service")
        }
    }

    private fun unregisterDynamicReceiver() {
        if (isReceiverRegistered) {
            runCatching { unregisterReceiver(dynamicAlarmReceiver) }
            isReceiverRegistered = false
            Log.d(TAG, "Dynamic alarm receiver unregistered")
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        AlarmEventLog.write(this, "app swiped from recents — watch still running")
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        unregisterDynamicReceiver()
        AlarmEventLog.write(this, "watch stopped")
        super.onDestroy()
    }

    private fun stopForegroundAndSelf() {
        unregisterDynamicReceiver()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        private const val TAG = "NesaAlarmWatch"
        private const val ACTION_STOP = "com.nesa.action.WATCH_STOP"
        private const val EXTRA_NEXT_LABEL = "com.nesa.extra.NEXT_LABEL"

        private const val PREFS = "nesa_alarm_watch"
        private const val KEY_ENABLED = "enabled"

        fun isEnabled(context: Context): Boolean =
            preferences(context).getBoolean(KEY_ENABLED, true)

        fun setEnabled(context: Context, enabled: Boolean) {
            preferences(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
            if (!enabled) stop(context)
        }

        fun start(context: Context, nextLabel: String?) {
            if (!isEnabled(context)) {
                AlarmEventLog.write(context, "watch is switched off — alarms may arrive late")
                return
            }
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, AlarmWatchService::class.java)
                        .putExtra(EXTRA_NEXT_LABEL, nextLabel)
                )
            }.onFailure {
                Log.w(TAG, "Could not start the alarm watch", it)
                AlarmEventLog.write(context, "watch refused at start: ${it.javaClass.simpleName}")
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.startService(
                    Intent(context, AlarmWatchService::class.java).setAction(ACTION_STOP)
                )
            }.onFailure { Log.w(TAG, "Could not stop the alarm watch", it) }
        }

        private fun preferences(context: Context) = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }
}
