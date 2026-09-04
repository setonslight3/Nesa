package com.nesa.core.storage

import androidx.room.Database
import androidx.room.RoomDatabase
import com.nesa.core.storage.dao.ActivityDao
import com.nesa.core.storage.dao.AlarmDao
import com.nesa.core.storage.dao.GoalDao
import com.nesa.core.storage.dao.HistoryDao
import com.nesa.core.storage.entity.ActivityEntity
import com.nesa.core.storage.entity.AlarmEntity
import com.nesa.core.storage.entity.CompletionRecordEntity
import com.nesa.core.storage.entity.GoalEntity
import com.nesa.core.storage.entity.ScheduleBlockEntity
import com.nesa.core.storage.entity.WakeChallengeResultEntity

/**
 * The local database. It is the source of truth for the whole application:
 * every screen renders from here, and nothing in the core waits on a network.
 *
 * Schemas are exported to `core-storage/schemas` so that a future migration is
 * reviewable in a diff. Destructive fallback is deliberately not enabled —
 * losing a user's plan silently would be worse than a loud failure.
 */
@Database(
    entities = [
        ActivityEntity::class,
        ScheduleBlockEntity::class,
        GoalEntity::class,
        AlarmEntity::class,
        CompletionRecordEntity::class,
        WakeChallengeResultEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class NesaDatabase : RoomDatabase() {
    abstract fun activityDao(): ActivityDao
    abstract fun goalDao(): GoalDao
    abstract fun alarmDao(): AlarmDao
    abstract fun historyDao(): HistoryDao

    companion object {
        const val NAME: String = "nesa.db"
    }
}
