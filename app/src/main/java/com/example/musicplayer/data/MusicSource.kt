package com.example.musicplayer.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MusicSourceType {
    LOCAL,
    NAS_HTTP,
    SUBSONIC
}

@Entity(tableName = "MusicSourceConfig")
data class MusicSourceConfig(
    @PrimaryKey val id: String,
    val name: String,
    val type: MusicSourceType,
    val url: String,
    val username: String = "",
    val password: String = "",
    val enabled: Boolean = true
)