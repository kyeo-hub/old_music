package com.example.musicplayer.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [MusicSourceConfig::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun musicSourceDao(): MusicSourceDao
}