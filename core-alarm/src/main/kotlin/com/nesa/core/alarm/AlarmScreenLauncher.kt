package com.nesa.core.alarm

import android.content.Context
import android.content.Intent

/**
 * Builds the intent that opens the ringing screen.
 *
 * The alarm layer must be able to launch a screen it knows nothing about, and
 * the screen lives above it. This interface keeps the dependency pointing the
 * right way: the application module supplies the implementation.
 */
interface AlarmScreenLauncher {
    fun ringingIntent(context: Context, alarmId: String): Intent
}
