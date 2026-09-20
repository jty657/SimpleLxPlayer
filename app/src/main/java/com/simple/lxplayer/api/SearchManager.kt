package com.simple.lxplayer.api

import com.simple.lxplayer.model.Song
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * 搜索管理类，统一调用各平台搜索接口
 */
object SearchManager {
    /**
     * 搜索所有平台的歌曲
     * @param keyword 搜索关键词
     */
    suspend fun searchAll(keyword: String): List<Song> = coroutineScope {
        val kuwoDeferred = async { KuwoApi.search(keyword) }
        val miguDeferred = async { MiguApi.search(keyword) }
        
        val kuwoResults = kuwoDeferred.await()
        val miguResults = miguDeferred.await()
        
        // 合并结果，酷我在前，咪咕在后
        return@coroutineScope kuwoResults + miguResults
    }

    /**
     * 按平台搜索
     * @param keyword 关键词
     * @param source 平台（kw/kg/mg/tx/wy）
     */
    suspend fun searchBySource(keyword: String, source: String): List<Song> {
        return when (source) {
            "kw" -> KuwoApi.search(keyword)
            "mg" -> MiguApi.search(keyword)
            else -> emptyList()
        }
    }
}
