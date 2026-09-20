package com.simple.lxplayer.model

/**
 * 歌曲数据类
 */
data class Song(
    val id: String,          // 歌曲ID
    val name: String,        // 歌曲名
    val artist: String,      // 歌手
    val album: String? = null, // 专辑
    val cover: String? = null, // 封面图片URL
    val duration: Long = 0,  // 时长（毫秒）
    val source: String,      // 音源平台（kg/kw/mg/tx/wy）
    val quality: String = "320k", // 默认音质
    val songId: String? = null, // 对应音源平台的歌曲ID（hash/songmid等）
    val playUrl: String? = null // 播放地址（搜索接口可能直接返回）
)
