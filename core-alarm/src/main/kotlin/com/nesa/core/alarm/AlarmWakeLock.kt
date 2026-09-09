package com.nesa.core.alarm

import android.content.Context
import android.os.PowerManager
import android.util.Log
import androidx.core.content.getSystemService

/**
 * Ensures CPU wakefulness between AlarmManager delivery and AlarmRingerService startup.
 *
 * AlarmManager only holds a broadcast wakelock for the duration of onReceive().
 * Starting a foreground service is asynchronous, so without acquiring a partial wakelock
 * here, the CPU can fall back to sleep immediately in Doze mode before the service
 * or audio player gets CPU execution time.
 */
object AlarmWakeLock {
    private const val TAG = "AlarmWakeLock"
    private const val WAKE_LOCK_TAG = "nesa:alarm-delivery"
    private var wakeLock: PowerManager.WakeLock? = null
    private val lock = Any()

    fun acquire(context: Context, timeoutMs: Long = 60_000L) {
        synchronized(lock) {
            try {
                val power = context.getSystemService<PowerManager>() ?: return
                if (wakeLock == null) {
                    wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
                        setReferenceCounted(false)
                    }
                }
                wakeLock?.acquire(timeoutMs)
                Log.d(TAG, "WakeLock acquired for ${timeoutMs}ms")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to acquire WakeLock", e)
            }
        }
    }

    fun release() {
        synchronized(lock) {
            try {
                wakeLock?.let {
                    if (it.isHeld) {
                        it.release()
                        Log.d(TAG, "WakeLock released")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to release WakeLock", e)
            }
        }
    }
}
