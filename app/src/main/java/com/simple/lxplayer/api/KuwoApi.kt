package com.simple.lxplayer.api

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.simple.lxplayer.model.Song
import com.simple.lxplayer.utils.HttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder

/**
 * 酷我音乐API
 */
object KuwoApi {
    private const val BASE_URL = "https://api.mmp.cc/api/kuwo"
    private val gson = Gson()

    /**
     * 搜索歌曲
     * @param keyword 关键词
     * @param pageSize 返回数量
     */
    suspend fun search(keyword: String, pageSize: Int = 20): List<Song> = withContext(Dispatchers.IO) {
        val encodedKeyword = URLEncoder.encode(keyword, "UTF-8")
        val url = "$BASE_URL?action=search_song&msg=$encodedKeyword&pageSize=$pageSize&br=320"
        
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            .build()
        
        HttpClient.instance.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext emptyList()
            
            val body = response.body?.string() ?: return@withContext emptyList()
            val result = gson.fromJson(body, KuwoSearchResult::class.java)
            
            return@withContext result.data?.map { item ->
                Song(
                    id = "kw_${item.songId}",
                    name = item.name ?: "未知歌曲",
                    artist = item.artist ?: "未知歌手",
                    album = item.album,
                    cover = item.cover,
                    duration = parseDuration(item.duration),
                    source = "kw",
                    songId = item.songId,
                    playUrl = item.playUrl
                )
            } ?: emptyList()
        }
    }

    /**
     * 解析时长字符串为毫秒
     */
    private fun parseDuration(durationStr: String?): Long {
        if (durationStr.isNullOrEmpty()) return 0
        return try {
            val parts = durationStr.split(":")
            if (parts.size == 2) {
                val minutes = parts[0].toLong()
                val seconds = parts[1].toLong()
                (minutes * 60 + seconds) * 1000
            } else {
                0
            }
        } catch (e: Exception) {
            0
        }
    }

    // 酷我搜索结果数据类
    private data class KuwoSearchResult(
        val code: Int,
        val data: List<KuwoSongItem>? = null
    )

    private data class KuwoSongItem(
        @SerializedName("song_id") val songId: String? = null,
        val name: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val duration: String? = null,
        val cover: String? = null,
        @SerializedName("play_url") val playUrl: String? = null
    )
}
