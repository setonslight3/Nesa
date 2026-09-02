package com.nesa.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.nesa.core.model.NesaSettings
import com.nesa.core.ui.theme.NesaTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * NESA's single main window.
 *
 * The first frame is not drawn until the stored settings arrive, because
 * starting in the wrong theme and correcting it a moment later is exactly the
 * kind of small ugliness a calm interface cannot afford.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val viewModel: MainViewModel = hiltViewModel()
            val settings by viewModel.settings.collectAsState(initial = null)

            NesaTheme(themeMode = settings?.themeMode ?: NesaSettings.Default.themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val loaded = settings
                    if (loaded == null) {
                        // Deliberately empty: a spinner for a single disk read
                        // flashes more than it reassures.
                        Box(Modifier.fillMaxSize())
                    } else {
                        // Asked after onboarding, not before it: a permission
                        // dialog on the welcome screen is a demand, not a
                        // request in context.
                        if (loaded.onboardingCompleted) RequestNotificationPermissionOnce()
                        NesaNavHost(startAtOnboarding = !loaded.onboardingCompleted)
                    }
                }
            }
        }
    }
}

/**
 * Asks for notifications once, in context, on the versions that require it.
 *
 * A refusal is not an error: reminders simply stop being delivered, the settings
 * screen says so, and everything else in NESA keeps working.
 */
@Composable
private fun RequestNotificationPermissionOnce() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* Either answer is acceptable; NESA degrades rather than insists. */ }

    LaunchedEffect(Unit) {
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
