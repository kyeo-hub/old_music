package com.example.musicplayer.service

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.annotation.RequiresApi
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.example.musicplayer.MainActivity
import com.example.musicplayer.data.PlayerState
import com.example.musicplayer.data.RepeatMode
import com.example.musicplayer.data.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MusicService : Service() {
    private lateinit var exoPlayer: ExoPlayer
    private lateinit var mediaSession: MediaSessionCompat
    private var audioFocusRequest: AudioFocusRequest? = null
    private var audioManager: AudioManager? = null

    private var songs: List<Song> = emptyList()
    private var currentIndex = 0
    private var shuffleMode = false
    private var repeatMode = RepeatMode.OFF
    private var isPlaying = false

    private val binder = LocalBinder()
    private var callback: ((PlayerState) -> Unit)? = null
    private var updateJob: Job? = null

    inner class LocalBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    override fun onCreate() {
        super.onCreate()
        initializePlayer()
        initializeMediaSession()
        requestAudioFocus()
    }

    private fun initializePlayer() {
        val dataSourceFactory = DefaultHttpDataSource.Factory()
        exoPlayer = ExoPlayer.Builder(this).build()
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                super.onPlaybackStateChanged(playbackState)
                isPlaying = playbackState == Player.STATE_READY && exoPlayer.isPlaying
                updateCallback()
                
                if (playbackState == Player.STATE_ENDED) {
                    handlePlaybackEnded()
                }
            }
        })
    }

    private fun initializeMediaSession() {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        mediaSession = MediaSessionCompat(this, "MusicPlayer")
        mediaSession.setSessionActivity(pendingIntent)
        mediaSession.isActive = true

        mediaSession.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() { play() }
            override fun onPause() { pause() }
            override fun onSkipToNext() { playNext() }
            override fun onSkipToPrevious() { playPrevious() }
            override fun onStop() { stop() }
            override fun onSeekTo(pos: Long) { seekTo(pos) }
        })
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createAudioFocusRequest(): AudioFocusRequest {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        return AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(audioAttributes)
            .setOnAudioFocusChangeListener { focusChange ->
                when (focusChange) {
                    AudioManager.AUDIOFOCUS_LOSS -> pause()
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pause()
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> exoPlayer.volume = 0.3f
                    AudioManager.AUDIOFOCUS_GAIN -> {
                        exoPlayer.volume = 1.0f
                        if (isPlaying) play()
                    }
                }
            }
            .build()
    }

    private fun requestAudioFocus() {
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = createAudioFocusRequest()
            audioManager?.requestAudioFocus(audioFocusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager?.requestAudioFocus(
                { focusChange ->
                    when (focusChange) {
                        AudioManager.AUDIOFOCUS_LOSS -> pause()
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pause()
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> exoPlayer.volume = 0.3f
                        AudioManager.AUDIOFOCUS_GAIN -> {
                            exoPlayer.volume = 1.0f
                            if (isPlaying) play()
                        }
                    }
                },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
    }

    private fun releaseAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager?.abandonAudioFocus(null)
        }
    }

    fun setSongs(newSongs: List<Song>) {
        songs = newSongs
    }

    fun setCallback(newCallback: (PlayerState) -> Unit) {
        callback = newCallback
        updateCallback()
    }

    fun playSong(index: Int) {
        if (index < 0 || index >= songs.size) return
        
        currentIndex = index
        val song = songs[index]
        
        val mediaItem = MediaItem.fromUri(song.url)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.play()
        
        startUpdateLoop()
    }

    fun play() {
        exoPlayer.play()
    }

    fun pause() {
        exoPlayer.pause()
    }

    fun stop() {
        exoPlayer.stop()
        updateJob?.cancel()
    }

    fun playNext() {
        if (songs.isEmpty()) return

        var nextIndex = when (repeatMode) {
            RepeatMode.ONE -> currentIndex
            RepeatMode.ALL -> {
                if (shuffleMode) {
                    (0 until songs.size).random()
                } else {
                    (currentIndex + 1) % songs.size
                }
            }
            else -> {
                if (shuffleMode) {
                    (0 until songs.size).random()
                } else {
                    val next = currentIndex + 1
                    if (next < songs.size) next else return
                }
            }
        }
        
        playSong(nextIndex)
    }

    fun playPrevious() {
        if (songs.isEmpty()) return
        
        var prevIndex = when (repeatMode) {
            RepeatMode.ONE -> currentIndex
            else -> {
                if (shuffleMode) {
                    (0 until songs.size).random()
                } else {
                    val prev = currentIndex - 1
                    if (prev >= 0) prev else songs.size - 1
                }
            }
        }
        
        playSong(prevIndex)
    }

    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
    }

    fun setShuffleMode(enabled: Boolean) {
        shuffleMode = enabled
        exoPlayer.shuffleModeEnabled = enabled
    }

    fun setRepeatMode(mode: RepeatMode) {
        repeatMode = mode
        when (mode) {
            RepeatMode.OFF -> exoPlayer.repeatMode = Player.REPEAT_MODE_OFF
            RepeatMode.ONE -> exoPlayer.repeatMode = Player.REPEAT_MODE_ONE
            RepeatMode.ALL -> exoPlayer.repeatMode = Player.REPEAT_MODE_ALL
        }
    }

    private fun handlePlaybackEnded() {
        when (repeatMode) {
            RepeatMode.OFF -> {
                if (currentIndex < songs.size - 1) {
                    playNext()
                }
            }
            RepeatMode.ONE -> playSong(currentIndex)
            RepeatMode.ALL -> playNext()
        }
    }

    private fun startUpdateLoop() {
        updateJob?.cancel()
        updateJob = CoroutineScope(Dispatchers.IO).launch {
            while (isPlaying) {
                delay(100)
                updateCallback()
            }
        }
    }

    private fun updateCallback() {
        val state = PlayerState(
            currentSong = if (songs.isNotEmpty()) songs[currentIndex] else null,
            playbackState = if (isPlaying) com.example.musicplayer.data.PlaybackState.PLAYING 
                           else com.example.musicplayer.data.PlaybackState.PAUSED,
            progress = exoPlayer.currentPosition,
            duration = exoPlayer.duration,
            shuffleMode = shuffleMode,
            repeatMode = repeatMode,
            queue = songs,
            currentIndex = currentIndex
        )
        callback?.invoke(state)
    }

    fun getCurrentState(): PlayerState {
        return PlayerState(
            currentSong = if (songs.isNotEmpty()) songs[currentIndex] else null,
            playbackState = if (isPlaying) com.example.musicplayer.data.PlaybackState.PLAYING 
                           else com.example.musicplayer.data.PlaybackState.PAUSED,
            progress = exoPlayer.currentPosition,
            duration = exoPlayer.duration,
            shuffleMode = shuffleMode,
            repeatMode = repeatMode,
            queue = songs,
            currentIndex = currentIndex
        )
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer.release()
        mediaSession.release()
        releaseAudioFocus()
        updateJob?.cancel()
    }
}