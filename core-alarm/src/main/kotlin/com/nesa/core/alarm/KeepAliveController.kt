package com.nesa.core.alarm

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Starts and stops [NesaKeepAliveService].
 *
 * A thin seam so screens can switch the service without holding a Context of
 * their own, and so the service stays the only thing that knows how it is built.
 */
@Singleton
class KeepAliveController @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun start() = NesaKeepAliveService.start(context)
    fun stop() = NesaKeepAliveService.stop(context)
}
