package com.nesa.core.storage.di

import android.content.Context
import androidx.room.Room
import com.nesa.core.model.repository.ActivityRepository
import com.nesa.core.model.repository.AlarmRepository
import com.nesa.core.model.repository.FitnessRepository
import com.nesa.core.model.repository.GoalRepository
import com.nesa.core.model.repository.LifeScheduleRepository
import com.nesa.core.model.repository.HistoryRepository
import com.nesa.core.storage.NesaDatabase
import com.nesa.core.storage.NesaMigrations
import com.nesa.core.storage.dao.ActivityDao
import com.nesa.core.storage.dao.AlarmDao
import com.nesa.core.storage.dao.FitnessDao
import com.nesa.core.storage.dao.GoalDao
import com.nesa.core.storage.dao.LifeScheduleDao
import com.nesa.core.storage.dao.HistoryDao
import com.nesa.core.storage.repository.RoomActivityRepository
import com.nesa.core.storage.repository.RoomAlarmRepository
import com.nesa.core.storage.repository.RoomFitnessRepository
import com.nesa.core.storage.repository.RoomGoalRepository
import com.nesa.core.storage.repository.RoomLifeScheduleRepository
import com.nesa.core.storage.repository.RoomHistoryRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NesaDatabase =
        Room.databaseBuilder(context, NesaDatabase::class.java, NesaDatabase.NAME)
            // No destructive migration: a schema change must be handled
            // explicitly rather than quietly erasing the user's plan.
            .addMigrations(*NesaMigrations.ALL)
            .build()

    @Provides
    fun provideActivityDao(database: NesaDatabase): ActivityDao = database.activityDao()

    @Provides
    fun provideGoalDao(database: NesaDatabase): GoalDao = database.goalDao()

    @Provides
    fun provideAlarmDao(database: NesaDatabase): AlarmDao = database.alarmDao()

    @Provides
    fun provideHistoryDao(database: NesaDatabase): HistoryDao = database.historyDao()

    @Provides
    fun provideFitnessDao(database: NesaDatabase): FitnessDao = database.fitnessDao()

    @Provides
    fun provideLifeScheduleDao(database: NesaDatabase): LifeScheduleDao = database.lifeScheduleDao()
}

/** Binds the domain's repository contracts to their Room implementations. */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindActivityRepository(impl: RoomActivityRepository): ActivityRepository

    @Binds
    @Singleton
    abstract fun bindGoalRepository(impl: RoomGoalRepository): GoalRepository

    @Binds
    @Singleton
    abstract fun bindAlarmRepository(impl: RoomAlarmRepository): AlarmRepository

    @Binds
    @Singleton
    abstract fun bindHistoryRepository(impl: RoomHistoryRepository): HistoryRepository

    @Binds
    @Singleton
    abstract fun bindFitnessRepository(impl: RoomFitnessRepository): FitnessRepository

    @Binds
    @Singleton
    abstract fun bindLifeScheduleRepository(
        impl: RoomLifeScheduleRepository
    ): LifeScheduleRepository
}
