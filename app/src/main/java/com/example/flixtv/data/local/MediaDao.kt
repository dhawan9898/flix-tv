package com.example.flixtv.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    @Query("SELECT * FROM media_items ORDER BY lastUpdated DESC")
    fun getAllMedia(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media_items WHERE category = :category ORDER BY lastUpdated DESC")
    fun getMediaByCategory(category: String): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media_items WHERE isFeatured = 1 ORDER BY lastUpdated DESC")
    fun getFeaturedMedia(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media_items WHERE title LIKE '%' || :query || '%' OR synopsis LIKE '%' || :query || '%'")
    fun searchLocalMedia(query: String): Flow<List<MediaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedia(media: MediaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(media: List<MediaEntity>)

    @Query("DELETE FROM media_items WHERE id = :id")
    suspend fun deleteById(id: String)
}