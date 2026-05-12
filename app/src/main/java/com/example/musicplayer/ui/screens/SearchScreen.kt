package com.example.musicplayer.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.musicplayer.data.Song
import com.example.musicplayer.ui.components.SongList

@Composable
fun SearchScreen(
    allSongs: List<Song>,
    currentPlayingId: String?,
    onSongClick: (Int) -> Unit
) {
    var query by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("搜索歌曲、歌手或专辑") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            placeholder = { Text("输入关键词搜索") }
        )

        val filteredSongs = if (query.isNotEmpty()) {
            allSongs.filter {
                it.title.contains(query, ignoreCase = true) ||
                it.artist.contains(query, ignoreCase = true) ||
                it.album.contains(query, ignoreCase = true)
            }
        } else {
            allSongs
        }

        SongList(
            songs = filteredSongs,
            currentPlayingId = currentPlayingId,
            onSongClick = { index ->
                val actualIndex = allSongs.indexOf(filteredSongs[index])
                if (actualIndex != -1) {
                    onSongClick(actualIndex)
                }
            }
        )
    }
}