package com.example.flixtv.di

import android.content.Context
import androidx.room.Room
import com.example.flixtv.data.local.FlixDatabase
import com.example.flixtv.data.local.MediaDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FlixDatabase {
        return Room.databaseBuilder(
            context,
            FlixDatabase::class.java,
            "flix_db"
        )
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()
    }

    @Provides
    @Singleton
    fun provideMediaDao(db: FlixDatabase): MediaDao {
        return db.mediaDao
    }
}