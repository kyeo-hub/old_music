package com.example.musicplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.example.musicplayer.ui.components.PlayerControls
import com.example.musicplayer.ui.screens.HomeScreen
import com.example.musicplayer.ui.screens.SearchScreen
import com.example.musicplayer.ui.screens.SettingsScreen
import com.example.musicplayer.viewmodel.MusicViewModel

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: MusicViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        viewModel = ViewModelProvider(this)[MusicViewModel::class.java]

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainApp(viewModel)
                }
            }
        }
    }
}

enum class Screen {
    HOME,
    SEARCH,
    LIBRARY,
    SETTINGS
}

@Composable
fun MainApp(viewModel: MusicViewModel) {
    var currentScreen by remember { mutableStateOf(Screen.HOME) }
    
    val songs = viewModel.songs.value ?: emptyList()
    val albums = viewModel.albums.value ?: emptyList()
    val playerState = viewModel.playerState.value
    val isLoading = viewModel.isLoading.value ?: false
    val sources = viewModel.musicSources.value ?: emptyList()

    LaunchedEffect(Unit) {
        viewModel.fetchMusic()
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = "首页") },
                    label = { Text("首页") },
                    selected = currentScreen == Screen.HOME,
                    onClick = { currentScreen = Screen.HOME }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Search, contentDescription = "搜索") },
                    label = { Text("搜索") },
                    selected = currentScreen == Screen.SEARCH,
                    onClick = { currentScreen = Screen.SEARCH }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.LibraryMusic, contentDescription = "音乐库") },
                    label = { Text("音乐库") },
                    selected = currentScreen == Screen.LIBRARY,
                    onClick = { currentScreen = Screen.LIBRARY }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = "设置") },
                    label = { Text("设置") },
                    selected = currentScreen == Screen.SETTINGS,
                    onClick = { currentScreen = Screen.SETTINGS }
                )
            }
        },
        content = { innerPadding ->
            Column(modifier = Modifier.padding(innerPadding)) {
                Box(modifier = Modifier.weight(1f)) {
                    when (currentScreen) {
                        Screen.HOME -> HomeScreen(
                            albums = albums,
                            isLoading = isLoading,
                            onRefresh = { viewModel.fetchMusic() }
                        )
                        Screen.SEARCH -> SearchScreen(
                            allSongs = songs,
                            currentPlayingId = playerState?.currentSong?.id,
                            onSongClick = { viewModel.playSong(it) }
                        )
                        Screen.LIBRARY -> com.example.musicplayer.ui.components.SongList(
                            songs = songs,
                            currentPlayingId = playerState?.currentSong?.id,
                            onSongClick = { viewModel.playSong(it) }
                        )
                        Screen.SETTINGS -> SettingsScreen(
                            sources = sources,
                            onAddSource = { viewModel.addMusicSource(it) },
                            onUpdateSource = { viewModel.updateMusicSource(it) },
                            onDeleteSource = { viewModel.deleteMusicSource(it) },
                            onBack = { currentScreen = Screen.HOME }
                        )
                    }
                }
                
                playerState?.let { state ->
                    PlayerControls(
                        playerState = state,
                        onPlayPause = {
                            if (state.playbackState == com.example.musicplayer.data.PlaybackState.PLAYING) {
                                viewModel.pause()
                            } else {
                                viewModel.play()
                            }
                        },
                        onNext = { viewModel.playNext() },
                        onPrevious = { viewModel.playPrevious() },
                        onSeek = { viewModel.seekTo(it) },
                        onShuffleToggle = { viewModel.setShuffleMode(!state.shuffleMode) },
                        onRepeatToggle = {
                            val nextMode = when (state.repeatMode) {
                                com.example.musicplayer.data.RepeatMode.OFF -> com.example.musicplayer.data.RepeatMode.ALL
                                com.example.musicplayer.data.RepeatMode.ALL -> com.example.musicplayer.data.RepeatMode.ONE
                                com.example.musicplayer.data.RepeatMode.ONE -> com.example.musicplayer.data.RepeatMode.OFF
                            }
                            viewModel.setRepeatMode(nextMode)
                        }
                    )
                }
            }
        }
    )
}
