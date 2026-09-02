package com.nesa.core.settings.di

import android.content.Context
import com.nesa.core.model.repository.SettingsRepository
import com.nesa.core.settings.NesaSettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SettingsModule {

    @Provides
    @Singleton
    fun provideSettingsRepository(@ApplicationContext context: Context): SettingsRepository =
        NesaSettingsRepository(context)
}
