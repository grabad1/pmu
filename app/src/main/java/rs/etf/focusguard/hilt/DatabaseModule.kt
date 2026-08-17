package rs.etf.focusguard.hilt

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import rs.etf.focusguard.data.room.FocusGuardDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun providesDatabase(@ApplicationContext context: Context) =
        FocusGuardDatabase.getDatabase(context)

    @Provides
    @Singleton
    fun providesSessionDao(database: FocusGuardDatabase) = database.sessionDao()

    @Provides
    @Singleton
    fun providesPauseDao(database: FocusGuardDatabase) = database.pauseDao()

    @Provides
    @Singleton
    fun providesSensorSampleDao(database: FocusGuardDatabase) = database.sensorSampleDao()
}
