package rs.etf.focusguard.data.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@TypeConverters(Converters::class)
@Database(
    entities = [Session::class, Pause::class, SensorSample::class],
    version = 1,
    exportSchema = false,
)
abstract class FocusGuardDatabase : RoomDatabase() {

    abstract fun sessionDao(): SessionDao
    abstract fun pauseDao(): PauseDao
    abstract fun sensorSampleDao(): SensorSampleDao

    companion object {
        private const val DATABASE_NAME = "focus_guard_database"

        @Volatile
        private var INSTANCE: FocusGuardDatabase? = null

        fun getDatabase(context: Context): FocusGuardDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    FocusGuardDatabase::class.java,
                    DATABASE_NAME,
                )
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
