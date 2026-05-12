package com.example.musicplayer.data

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val url: String,
    val coverUrl: String? = null,
    val trackNumber: Int = 0
)

data class Album(
    val id: String,
    val name: String,
    val artist: String,
    val coverUrl: String? = null,
    val songs: List<Song> = emptyList()
)

data class Artist(
    val id: String,
    val name: String,
    val albums: List<Album> = emptyList()
)

data class Playlist(
    val id: String,
    val name: String,
    val songs: List<Song> = emptyList()
)