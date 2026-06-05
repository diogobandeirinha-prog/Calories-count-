package com.caloriescount.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [FoodEntryEntity::class, FavoriteFoodEntity::class, WorkoutEntity::class],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun foodEntryDao(): FoodEntryDao
    abstract fun favoriteFoodDao(): FavoriteFoodDao
    abstract fun workoutDao(): WorkoutDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** v1 → v2: added carbs/fats totals (per-item macros live in the JSON column). */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE food_entries ADD COLUMN totalCarbsG REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE food_entries ADD COLUMN totalFatsG REAL NOT NULL DEFAULT 0")
            }
        }

        /** v2 → v3: offline-first sync metadata + favorite/frequent foods cache. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Existing rows are treated as already-synced so they don't flood the queue.
                db.execSQL("ALTER TABLE food_entries ADD COLUMN remoteId TEXT")
                db.execSQL("ALTER TABLE food_entries ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'SYNCED'")
                db.execSQL("ALTER TABLE food_entries ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE food_entries ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_food_entries_syncStatus ON food_entries(syncStatus)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_food_entries_timestamp ON food_entries(timestamp)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS favorite_foods (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        nameKey TEXT NOT NULL,
                        name TEXT NOT NULL,
                        quantity TEXT NOT NULL DEFAULT '',
                        weightGrams REAL NOT NULL DEFAULT 0,
                        calories REAL NOT NULL DEFAULT 0,
                        proteinG REAL NOT NULL DEFAULT 0,
                        carbsG REAL NOT NULL DEFAULT 0,
                        fatsG REAL NOT NULL DEFAULT 0,
                        useCount INTEGER NOT NULL DEFAULT 0,
                        lastUsedAt INTEGER NOT NULL DEFAULT 0,
                        pinned INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_favorite_foods_nameKey ON favorite_foods(nameKey)"
                )
            }
        }

        /** v3 → v4: workout log (drives dynamic daily goal recalibration). */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS workouts (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        timestamp INTEGER NOT NULL,
                        name TEXT NOT NULL,
                        durationMin INTEGER NOT NULL,
                        intensity TEXT NOT NULL,
                        caloriesBurned REAL NOT NULL,
                        proteinBonusG REAL NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_workouts_timestamp ON workouts(timestamp)")
            }
        }

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "calories_count.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()
                    .also { INSTANCE = it }
            }
    }
}
