package com.example.musicplayer.data

enum class PlaybackState {
    PLAYING,
    PAUSED,
    STOPPED
}

enum class RepeatMode {
    OFF,
    ONE,
    ALL
}

data class PlayerState(
    val currentSong: Song?,
    val playbackState: PlaybackState,
    val progress: Long,
    val duration: Long,
    val shuffleMode: Boolean,
    val repeatMode: RepeatMode,
    val queue: List<Song>,
    val currentIndex: Int
)