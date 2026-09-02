package com.nesa.core.ui.theme

import androidx.compose.ui.unit.dp

/**
 * One spacing scale for the whole application.
 *
 * Consistent rhythm is most of what makes an interface feel calm, and a shared
 * scale is cheaper to hold in your head than a set of ad-hoc numbers.
 */
object NesaSpacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp

    /** Minimum touch target, per the Android accessibility guidance. */
    val touchTarget = 48.dp

    /** Standard screen edge inset. */
    val screen = 16.dp
}
