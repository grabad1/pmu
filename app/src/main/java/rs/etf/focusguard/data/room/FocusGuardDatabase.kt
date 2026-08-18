package rs.etf.focusguard.data.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@TypeConverters(Converters::class)
@Database(
    entities = [Session::class, Pause::class, SensorSample::class],
    version = 2,
    exportSchema = false,
)
abstract class FocusGuardDatabase : RoomDatabase() {

    abstract fun sessionDao(): SessionDao
    abstract fun pauseDao(): PauseDao
    abstract fun sensorSampleDao(): SensorSampleDao

    companion object {
        private const val DATABASE_NAME = "focus_guard_database"

        /**
         * Adds the time-away column.
         *
         * A migration rather than a destructive rebuild, because previous sessions are the
         * whole point of the app — losing someone's history to add a column would be a poor
         * trade. Existing rows get 0, which is honest: that time was never measured.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE sessions ADD COLUMN awaySeconds INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        @Volatile
        private var INSTANCE: FocusGuardDatabase? = null

        fun getDatabase(context: Context): FocusGuardDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    FocusGuardDatabase::class.java,
                    DATABASE_NAME,
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
