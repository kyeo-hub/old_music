package com.example.musicplayer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface MusicSourceDao {
    @Query("SELECT * FROM MusicSourceConfig")
    suspend fun getAllSources(): List<MusicSourceConfig>

    @Query("SELECT * FROM MusicSourceConfig WHERE enabled = 1")
    suspend fun getEnabledSources(): List<MusicSourceConfig>

    @Query("SELECT * FROM MusicSourceConfig WHERE id = :id")
    suspend fun getSourceById(id: String): MusicSourceConfig?

    @Insert
    suspend fun insertSource(source: MusicSourceConfig)

    @Update
    suspend fun updateSource(source: MusicSourceConfig)

    @Query("DELETE FROM MusicSourceConfig WHERE id = :id")
    suspend fun deleteSource(id: String)
}