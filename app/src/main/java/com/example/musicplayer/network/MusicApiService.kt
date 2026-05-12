package com.example.musicplayer.network

import com.example.musicplayer.data.Album
import com.example.musicplayer.data.MusicSourceConfig
import com.example.musicplayer.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.Base64

class MusicApiService(private val config: MusicSourceConfig) {
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private fun createBasicAuth(username: String, password: String): String {
        val credentials = "$username:$password"
        return "Basic ${Base64.getEncoder().encodeToString(credentials.toByteArray())}"
    }

    suspend fun fetchSongs(): List<Song> = withContext(Dispatchers.IO) {
        when (config.type) {
            com.example.musicplayer.data.MusicSourceType.SUBSONIC -> fetchSubsonicSongs()
            com.example.musicplayer.data.MusicSourceType.NAS_HTTP -> fetchHttpSongs()
            else -> emptyList()
        }
    }

    suspend fun fetchAlbums(): List<Album> = withContext(Dispatchers.IO) {
        when (config.type) {
            com.example.musicplayer.data.MusicSourceType.SUBSONIC -> fetchSubsonicAlbums()
            else -> emptyList()
        }
    }

    private suspend fun fetchSubsonicSongs(): List<Song> {
        val url = "${config.url}/rest/getAllSongs.view?u=${config.username}&p=${config.password}&v=1.16.1&c=MusicPlayer&f=json"
        
        try {
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Unexpected code $response")
                
                val json = JSONObject(response.body?.string() ?: "")
                val subsonicResponse = json.getJSONObject("subsonic-response")
                
                if (subsonicResponse.getString("status") != "ok") {
                    return emptyList()
                }
                
                val songsArray = subsonicResponse.getJSONObject("songs").optJSONArray("song") ?: JSONArray()
                val songs = mutableListOf<Song>()
                
                for (i in 0 until songsArray.length()) {
                    val songObj = songsArray.getJSONObject(i)
                    songs.add(
                        Song(
                            id = songObj.getString("id"),
                            title = songObj.getString("title"),
                            artist = songObj.getString("artist"),
                            album = songObj.getString("album"),
                            duration = songObj.getLong("duration") * 1000,
                            url = "${config.url}/rest/stream.view?u=${config.username}&p=${config.password}&v=1.16.1&c=MusicPlayer&id=${songObj.getString("id")}",
                            coverUrl = "${config.url}/rest/getCoverArt.view?u=${config.username}&p=${config.password}&v=1.16.1&c=MusicPlayer&id=${songObj.getString("albumId")}",
                            trackNumber = songObj.optInt("track", 0)
                        )
                    )
                }
                return songs
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }
    }

    private suspend fun fetchSubsonicAlbums(): List<Album> {
        val url = "${config.url}/rest/getAlbumList2.view?u=${config.username}&p=${config.password}&v=1.16.1&c=MusicPlayer&f=json&type=alphabeticalByName"
        
        try {
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Unexpected code $response")
                
                val json = JSONObject(response.body?.string() ?: "")
                val subsonicResponse = json.getJSONObject("subsonic-response")
                
                if (subsonicResponse.getString("status") != "ok") {
                    return emptyList()
                }
                
                val albumsArray = subsonicResponse.getJSONObject("albumList2").optJSONArray("album") ?: JSONArray()
                val albums = mutableListOf<Album>()
                
                for (i in 0 until albumsArray.length()) {
                    val albumObj = albumsArray.getJSONObject(i)
                    albums.add(
                        Album(
                            id = albumObj.getString("id"),
                            name = albumObj.getString("name"),
                            artist = albumObj.getString("artist"),
                            coverUrl = "${config.url}/rest/getCoverArt.view?u=${config.username}&p=${config.password}&v=1.16.1&c=MusicPlayer&id=${albumObj.getString("coverArt")}"
                        )
                    )
                }
                return albums
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }
    }

    private suspend fun fetchHttpSongs(): List<Song> {
        val url = "${config.url}/songs.json"
        
        try {
            val requestBuilder = Request.Builder()
                .url(url)
                .get()
            
            if (config.username.isNotEmpty() && config.password.isNotEmpty()) {
                requestBuilder.header("Authorization", createBasicAuth(config.username, config.password))
            }
            
            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Unexpected code $response")
                
                val json = JSONObject(response.body?.string() ?: "")
                val songsArray = json.optJSONArray("songs") ?: JSONArray()
                val songs = mutableListOf<Song>()
                
                for (i in 0 until songsArray.length()) {
                    val songObj = songsArray.getJSONObject(i)
                    songs.add(
                        Song(
                            id = songObj.optString("id", i.toString()),
                            title = songObj.getString("title"),
                            artist = songObj.getString("artist"),
                            album = songObj.getString("album"),
                            duration = songObj.getLong("duration"),
                            url = "${config.url}/${songObj.getString("path")}",
                            coverUrl = songObj.optString("coverUrl", null)
                        )
                    )
                }
                return songs
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }
    }

    fun getStreamUrl(song: Song): String {
        return song.url
    }

    fun getCoverUrl(coverUrl: String?): String? {
        if (coverUrl.isNullOrEmpty()) return null
        
        val urlBuilder = StringBuilder(coverUrl)
        
        if (!coverUrl.contains("u=") && config.username.isNotEmpty()) {
            urlBuilder.append(if (coverUrl.contains("?")) "&" else "?")
            urlBuilder.append("u=${URLEncoder.encode(config.username, "UTF-8")}")
            urlBuilder.append("&p=${URLEncoder.encode(config.password, "UTF-8")}")
        }
        
        return urlBuilder.toString()
    }
}