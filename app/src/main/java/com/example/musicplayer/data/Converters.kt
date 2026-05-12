package com.example.musicplayer.data

import androidx.room.TypeConverter
import com.example.musicplayer.data.MusicSourceType

class Converters {
    @TypeConverter
    fun fromMusicSourceType(type: MusicSourceType): String {
        return type.name
    }

    @TypeConverter
    fun toMusicSourceType(value: String): MusicSourceType {
        return MusicSourceType.valueOf(value)
    }
}