package com.nesa.core.storage

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.nesa.core.model.Alarm

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

    /** Every migration, in order, for the database builder. */
    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
