package dev.pulse.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
        JournalEntryEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class PulseDatabase : RoomDatabase() {
    abstract fun healthDao(): HealthDao
}

/** Версия 2: добавлена таблица журнала. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `journal_entries` (`cycleStart` INTEGER NOT NULL, `tag` TEXT NOT NULL, " +
                "PRIMARY KEY(`cycleStart`, `tag`))",
        )
    }
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
            // При обновлении приложения данные сохраняются: новые таблицы добавляются «миграциями».
            .addMigrations(MIGRATION_1_2)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideHealthDao(database: PulseDatabase): HealthDao = database.healthDao()
}
