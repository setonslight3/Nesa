package com.nesa.core.storage

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.nesa.core.model.Alarm
import com.nesa.core.model.RecurrenceFrequency

/**
 * Schema migrations.
 *
 * Destructive fallback is deliberately not enabled (see `StorageModule`), so
 * every schema change has to be written down here or the app refuses to open
 * the database at all. That is the intended trade: a loud failure at build time
 * rather than a user's plan and alarms quietly disappearing on an update.
 */
object NesaMigrations {

    /**
     * 1 → 2: alarms gained a per-alarm volume.
     *
     * The default matches [Alarm.DEFAULT_VOLUME_PERCENT] so that an alarm which
     * existed before this column did keeps ringing at the level the code always
     * assumed, rather than silently arriving at zero.
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE alarms ADD COLUMN volumePercent INTEGER NOT NULL " +
                    "DEFAULT ${Alarm.DEFAULT_VOLUME_PERCENT}"
            )
        }
    }

    /**
     * 2 → 3: activities gained a recurrence rule.
     *
     * Every existing row becomes NONE — a single occurrence, which is exactly
     * what it was before this column existed. Nothing a user already has starts
     * repeating behind their back.
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE activities ADD COLUMN recurrenceFrequency TEXT NOT NULL " +
                    "DEFAULT '${RecurrenceFrequency.NONE.name}'"
            )
            db.execSQL("ALTER TABLE activities ADD COLUMN recurrenceInterval INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE activities ADD COLUMN recurrenceDays TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE activities ADD COLUMN recurrenceStart TEXT")
            db.execSQL("ALTER TABLE activities ADD COLUMN recurrenceEnd TEXT")
        }
    }

    /** Every migration, in order, for the database builder. */
    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
}
