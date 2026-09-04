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

    /**
     * 3 → 4: the fitness module's five tables.
     *
     * All new, so nothing existing is touched and there is no data to move.
     *
     * The DDL is written to match what Room generates, because Room validates
     * the migrated schema against its own and throws if they differ by so much
     * as a nullability. If a build fails here with an "expected/found" schema
     * dump, the fix is to make this DDL match the *expected* half — never to
     * relax the entity to match this.
     *
     * Note what `workout_sessions` deliberately does not have: a foreign key to
     * `workout_routines`. Deleting a routine must not erase the history of
     * having trained with it.
     */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `exercises` (" +
                    "`id` TEXT NOT NULL, `name` TEXT NOT NULL, `kind` TEXT NOT NULL, " +
                    "`notes` TEXT, PRIMARY KEY(`id`))"
            )

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `workout_routines` (" +
                    "`id` TEXT NOT NULL, `name` TEXT NOT NULL, `focus` TEXT, " +
                    "`createdAtEpochMillis` INTEGER NOT NULL, " +
                    "`updatedAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))"
            )

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `routine_exercises` (" +
                    "`id` TEXT NOT NULL, `routineId` TEXT NOT NULL, " +
                    "`exerciseId` TEXT NOT NULL, `position` INTEGER NOT NULL, " +
                    "`sets` INTEGER NOT NULL, `reps` INTEGER, `seconds` INTEGER, " +
                    "`weightKg` REAL, `restSeconds` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
                    "FOREIGN KEY(`routineId`) REFERENCES `workout_routines`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_routine_exercises_routineId` " +
                    "ON `routine_exercises` (`routineId`)"
            )

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `workout_sessions` (" +
                    "`id` TEXT NOT NULL, `routineId` TEXT, `blockId` TEXT, " +
                    "`date` TEXT NOT NULL, `durationMinutes` INTEGER NOT NULL, " +
                    "`effort` TEXT NOT NULL, `notes` TEXT, " +
                    "`recordedAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_workout_sessions_date` " +
                    "ON `workout_sessions` (`date`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_workout_sessions_routineId` " +
                    "ON `workout_sessions` (`routineId`)"
            )

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `set_logs` (" +
                    "`id` TEXT NOT NULL, `sessionId` TEXT NOT NULL, " +
                    "`exerciseId` TEXT NOT NULL, `setNumber` INTEGER NOT NULL, " +
                    "`reps` INTEGER, `seconds` INTEGER, `weightKg` REAL, " +
                    "`outcome` TEXT NOT NULL, PRIMARY KEY(`id`), " +
                    "FOREIGN KEY(`sessionId`) REFERENCES `workout_sessions`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_set_logs_sessionId` " +
                    "ON `set_logs` (`sessionId`)"
            )
        }
    }

    /**
     * 4 → 5: completion records remember the slot they are about.
     *
     * Nullable on purpose, and left null for every existing row. The adaptive
     * layer reads this history to work out which times of day actually work for
     * the user, and back-filling a guess — the time the record happened to be
     * written, say — would teach it something that was never true. A record
     * that cannot answer honestly is skipped instead.
     */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE completion_records ADD COLUMN scheduledStartMinute INTEGER")
        }
    }

    /** Every migration, in order, for the database builder. */
    val ALL: Array<Migration> = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5
    )
}
