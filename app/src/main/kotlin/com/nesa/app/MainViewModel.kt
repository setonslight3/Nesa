package com.nesa.app

import androidx.lifecycle.ViewModel
import com.nesa.core.model.NesaSettings
import com.nesa.core.model.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Holds the small amount of state the shell itself needs: the theme, and
 * whether onboarding has been completed.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    settings: SettingsRepository
) : ViewModel() {
    val settings: Flow<NesaSettings> = settings.settings
}
