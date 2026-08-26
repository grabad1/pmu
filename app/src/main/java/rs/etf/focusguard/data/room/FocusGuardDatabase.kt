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
    entities = [Session::class, Pause::class, SensorSample::class, Interruption::class],
    version = 3,
    exportSchema = false,
)
abstract class FocusGuardDatabase : RoomDatabase() {

    abstract fun sessionDao(): SessionDao
    abstract fun pauseDao(): PauseDao
    abstract fun sensorSampleDao(): SensorSampleDao
    abstract fun interruptionDao(): InterruptionDao

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

        /**
         * Adds session categories and topics, and the table interruptions will be recorded in.
         *
         * Both land in one migration on purpose. The interruptions table is not written to
         * until the call and notification tracking arrives, but a migration is the easiest
         * thing in the project to get wrong, and an empty table costs nothing — one careful
         * step beats two casual ones.
         *
         * Existing sessions get a null category and topic rather than being forced into
         * "Other": we genuinely do not know what they were, and pretending otherwise would
         * poison the per-topic averages this is all for.
         *
         * The table definition must match what Room generates for [Interruption] exactly, or
         * opening the database afterwards fails its own schema check.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN category TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE sessions ADD COLUMN topic TEXT DEFAULT NULL")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `interruptions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `sessionId` INTEGER NOT NULL,
                        `kind` TEXT NOT NULL,
                        `packageName` TEXT NOT NULL,
                        `appLabel` TEXT NOT NULL,
                        `occurredAt` INTEGER NOT NULL,
                        FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_interruptions_sessionId` " +
                        "ON `interruptions` (`sessionId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_interruptions_sessionId_packageName` " +
                        "ON `interruptions` (`sessionId`, `packageName`)"
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
