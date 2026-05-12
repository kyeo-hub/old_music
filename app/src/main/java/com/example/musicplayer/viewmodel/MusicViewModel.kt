package com.example.musicplayer.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.example.musicplayer.data.Album
import com.example.musicplayer.data.MusicDatabase
import com.example.musicplayer.data.MusicSourceConfig
import com.example.musicplayer.data.MusicSourceType
import com.example.musicplayer.data.PlayerState
import com.example.musicplayer.data.Song
import com.example.musicplayer.network.MusicApiService
import com.example.musicplayer.service.MusicService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class MusicViewModel(application: Application) : AndroidViewModel(application) {
    val songs = MutableLiveData<List<Song>>(emptyList())
    val albums = MutableLiveData<List<Album>>(emptyList())
    val playerState = MutableLiveData<PlayerState>()
    val isLoading = MutableLiveData(false)
    val errorMessage = MutableLiveData<String?>()
    val musicSources = MutableLiveData<List<MusicSourceConfig>>(emptyList())

    private var musicService: MusicService? = null
    private var isServiceBound = false
    private lateinit var database: MusicDatabase

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as MusicService.LocalBinder
            musicService = binder.getService()
            isServiceBound = true
            musicService?.setCallback { state ->
                playerState.postValue(state)
            }
            playerState.postValue(musicService?.getCurrentState())
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            musicService = null
            isServiceBound = false
        }
    }

    init {
        database = Room.databaseBuilder(
            application,
            MusicDatabase::class.java, "music-db"
        ).build()
        
        loadMusicSources()
        bindService()
    }

    private fun bindService() {
        val intent = Intent(getApplication(), MusicService::class.java)
        getApplication<Application>().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        getApplication<Application>().startService(intent)
    }

    fun loadMusicSources() {
        viewModelScope.launch {
            val sources = database.musicSourceDao().getAllSources()
            musicSources.postValue(sources)
            
            if (sources.isEmpty()) {
                addDefaultSource()
            }
        }
    }

    private suspend fun addDefaultSource() {
        val defaultSource = MusicSourceConfig(
            id = UUID.randomUUID().toString(),
            name = "示例音乐源",
            type = MusicSourceType.NAS_HTTP,
            url = "http://your-nas-ip:port/music",
            enabled = true
        )
        database.musicSourceDao().insertSource(defaultSource)
        loadMusicSources()
    }

    fun addMusicSource(config: MusicSourceConfig) {
        viewModelScope.launch {
            database.musicSourceDao().insertSource(config.copy(id = UUID.randomUUID().toString()))
            loadMusicSources()
        }
    }

    fun updateMusicSource(config: MusicSourceConfig) {
        viewModelScope.launch {
            database.musicSourceDao().updateSource(config)
            loadMusicSources()
        }
    }

    fun deleteMusicSource(id: String) {
        viewModelScope.launch {
            database.musicSourceDao().deleteSource(id)
            loadMusicSources()
        }
    }

    fun fetchMusic() {
        viewModelScope.launch {
            isLoading.postValue(true)
            errorMessage.postValue(null)
            
            try {
                val enabledSources = database.musicSourceDao().getEnabledSources()
                val allSongs = mutableListOf<Song>()
                val allAlbums = mutableListOf<Album>()
                
                for (source in enabledSources) {
                    val apiService = MusicApiService(source)
                    val sourceSongs = apiService.fetchSongs()
                    val sourceAlbums = apiService.fetchAlbums()
                    allSongs.addAll(sourceSongs)
                    allAlbums.addAll(sourceAlbums)
                }
                
                songs.postValue(allSongs)
                albums.postValue(allAlbums)
                musicService?.setSongs(allSongs)
            } catch (e: Exception) {
                errorMessage.postValue("加载音乐失败: ${e.message}")
            } finally {
                isLoading.postValue(false)
            }
        }
    }

    fun playSong(index: Int) {
        musicService?.playSong(index)
    }

    fun play() {
        musicService?.play()
    }

    fun pause() {
        musicService?.pause()
    }

    fun playNext() {
        musicService?.playNext()
    }

    fun playPrevious() {
        musicService?.playPrevious()
    }

    fun seekTo(positionMs: Long) {
        musicService?.seekTo(positionMs)
    }

    fun setShuffleMode(enabled: Boolean) {
        musicService?.setShuffleMode(enabled)
    }

    fun setRepeatMode(mode: com.example.musicplayer.data.RepeatMode) {
        musicService?.setRepeatMode(mode)
    }

    fun searchSongs(query: String): List<Song> {
        return songs.value?.filter {
            it.title.contains(query, ignoreCase = true) ||
            it.artist.contains(query, ignoreCase = true) ||
            it.album.contains(query, ignoreCase = true)
        } ?: emptyList()
    }

    override fun onCleared() {
        super.onCleared()
        if (isServiceBound) {
            getApplication<Application>().unbindService(serviceConnection)
            isServiceBound = false
        }
    }
}