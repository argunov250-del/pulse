package dev.pulse.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** База данных Pulse. Лежит только на телефоне, в закрытой папке приложения. */
@Database(
    entities = [
        HeartRateMinuteEntity::class,
        MeasurementEntity::class,
        HourlyTotalEntity::class,
        SleepSessionEntity::class,
        SleepStageEntity::class,
        WorkoutEntity::class,
        AppMetaEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class PulseDatabase : RoomDatabase() {
    abstract fun healthDao(): HealthDao
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PulseDatabase =
        Room.databaseBuilder(context, PulseDatabase::class.java, "pulse.db")
            // Пока приложение в разработке и структура базы меняется: при несовпадении версий
            // база пересоздаётся. Это кэш — данные можно заново получить из источников.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideHealthDao(database: PulseDatabase): HealthDao = database.healthDao()
}
