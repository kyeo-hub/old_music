package com.example.musicplayer.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.musicplayer.data.Album
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
    
    private val sharedPrefs: SharedPreferences = application.getSharedPreferences(
        "music_player_prefs", Context.MODE_PRIVATE
    )

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
            val sources = loadSourcesFromPrefs()
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
        saveSourcesToPrefs(listOf(defaultSource))
        loadMusicSources()
    }

    fun addMusicSource(config: MusicSourceConfig) {
        viewModelScope.launch {
            val currentSources = loadSourcesFromPrefs().toMutableList()
            currentSources.add(config.copy(id = UUID.randomUUID().toString()))
            saveSourcesToPrefs(currentSources)
            loadMusicSources()
        }
    }

    fun updateMusicSource(config: MusicSourceConfig) {
        viewModelScope.launch {
            val currentSources = loadSourcesFromPrefs().toMutableList()
            val index = currentSources.indexOfFirst { it.id == config.id }
            if (index != -1) {
                currentSources[index] = config
            }
            saveSourcesToPrefs(currentSources)
            loadMusicSources()
        }
    }

    fun deleteMusicSource(id: String) {
        viewModelScope.launch {
            val currentSources = loadSourcesFromPrefs().toMutableList()
            currentSources.removeAll { it.id == id }
            saveSourcesToPrefs(currentSources)
            loadMusicSources()
        }
    }

    private fun loadSourcesFromPrefs(): List<MusicSourceConfig> {
        val json = sharedPrefs.getString("music_sources", "[]") ?: "[]"
        return try {
            val sources = mutableListOf<MusicSourceConfig>()
            if (json != "[]") {
                val items = json.drop(1).dropLast(1).split("},{")
                for (item in items) {
                    val parts = item.split("\"")
                    var id = ""
                    var name = ""
                    var type = MusicSourceType.NAS_HTTP
                    var url = ""
                    var username = ""
                    var password = ""
                    var enabled = true
                    
                    var i = 0
                    while (i < parts.size) {
                        if (parts[i] == "id") id = parts[i + 2]
                        if (parts[i] == "name") name = parts[i + 2]
                        if (parts[i] == "type") type = when (parts[i + 2]) {
                            "SUBSONIC" -> MusicSourceType.SUBSONIC
                            else -> MusicSourceType.NAS_HTTP
                        }
                        if (parts[i] == "url") url = parts[i + 2]
                        if (parts[i] == "username") username = parts[i + 2]
                        if (parts[i] == "password") password = parts[i + 2]
                        if (parts[i] == "enabled") enabled = parts[i + 2] == "true"
                        i++
                    }
                    if (id.isNotEmpty()) {
                        sources.add(MusicSourceConfig(id, name, type, url, username, password, enabled))
                    }
                }
            }
            sources
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveSourcesToPrefs(sources: List<MusicSourceConfig>) {
        val json = sources.joinToString(",", "[", "]") { 
            """{"id":"${it.id}","name":"${it.name}","type":"${it.type}","url":"${it.url}","username":"${it.username}","password":"${it.password}","enabled":${it.enabled}}"""
        }
        sharedPrefs.edit().putString("music_sources", json).apply()
    }

    fun fetchMusic() {
        viewModelScope.launch {
            isLoading.postValue(true)
            errorMessage.postValue(null)
            
            try {
                val enabledSources = loadSourcesFromPrefs().filter { it.enabled }
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
