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
 * 咪咕音乐API
 */
object MiguApi {
    private const val BASE_URL = "https://api.xcvts.cn/api/music/migu"
    private val gson = Gson()

    /**
     * 搜索歌曲
     * @param keyword 关键词
     * @param limit 返回数量
     */
    suspend fun search(keyword: String, limit: Int = 20): List<Song> = withContext(Dispatchers.IO) {
        val encodedKeyword = URLEncoder.encode(keyword, "UTF-8")
        val url = "$BASE_URL?gm=$encodedKeyword&n=1&num=$limit&type=json"
        
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            .build()
        
        HttpClient.instance.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext emptyList()
            
            val body = response.body?.string() ?: return@withContext emptyList()
            val result = gson.fromJson(body, MiguSearchResult::class.java)
            
            return@withContext if (result.code == 200 && result.data != null) {
                result.data.mapIndexed { index, item ->
                    Song(
                        id = "mg_${index}_${System.currentTimeMillis()}",
                        name = item.title ?: "未知歌曲",
                        artist = item.singer ?: "未知歌手",
                        cover = item.cover,
                        duration = 0, // 咪咕接口不返回时长
                        source = "mg",
                        songId = item.link?.substringAfterLast("/"),
                        playUrl = item.musicUrl
                    )
                }
            } else {
                emptyList()
            }
        }
    }

    // 咪咕搜索结果数据类
    private data class MiguSearchResult(
        val code: Int,
        val data: List<MiguSongItem>? = null
    )

    private data class MiguSongItem(
        val title: String? = null,
        val singer: String? = null,
        val cover: String? = null,
        val link: String? = null,
        @SerializedName("music_url") val musicUrl: String? = null,
        @SerializedName("lrc_url") val lrcUrl: String? = null
    )
}
