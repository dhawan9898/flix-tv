package com.example.flixtv.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [MediaEntity::class],
    version = 2,
    exportSchema = false
)
abstract class FlixDatabase : RoomDatabase() {
    abstract val mediaDao: MediaDao
}