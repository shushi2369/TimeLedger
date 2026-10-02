package com.ivy.timetrack.di

import android.content.Context
import androidx.room.Room
import com.ivy.timetrack.data.TodoDao
import com.ivy.timetrack.data.TimeActivityDao
import com.ivy.timetrack.data.TimeEntryDao
import com.ivy.timetrack.data.TimeTrackDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TimeTrackDiModule {
    @Provides
    @Singleton
    fun provideTimeTrackDatabase(@ApplicationContext context: Context): TimeTrackDatabase =
        Room.databaseBuilder(context, TimeTrackDatabase::class.java, TimeTrackDatabase.DB_NAME)
            .addMigrations(TimeTrackDatabase.MIGRATION_1_2, TimeTrackDatabase.MIGRATION_2_3)
            .build()

    @Provides
    fun provideTimeActivityDao(db: TimeTrackDatabase): TimeActivityDao = db.timeActivityDao()

    @Provides
    fun provideTimeEntryDao(db: TimeTrackDatabase): TimeEntryDao = db.timeEntryDao()

    @Provides
    fun provideTodoDao(db: TimeTrackDatabase): TodoDao = db.todoDao()
}
