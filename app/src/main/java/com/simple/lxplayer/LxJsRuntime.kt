package com.simple.lxplayer

import android.content.Context
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.google.gson.Gson
import com.simple.lxplayer.model.Song
import com.simple.lxplayer.utils.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileReader
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * LX音源JS运行环境
 * 模拟LX Music的全局环境，运行JS音源插件
 * 使用WebView实现，稳定支持JS与安卓双向调用
 */
class LxJsRuntime private constructor(private val context: Context) {
    companion object {
        private const val TAG = "LxJsRuntime"
        private var instance: LxJsRuntime? = null

        fun getInstance(context: Context): LxJsRuntime {
            return instance ?: synchronized(this) {
                instance ?: LxJsRuntime(context.applicationContext).also { instance = it }
            }
        }
    }

    private lateinit var webView: WebView
    private val gson = Gson()
    private val mainScope = CoroutineScope(Dispatchers.Main)
    private val ioScope = CoroutineScope(Dispatchers.IO)
    private var isInitialized = false
    private val pendingRequests = mutableMapOf<Int, (Result<String>) -> Unit>()
    private var requestIdCounter = 0

    /**
     * 初始化JS运行环境
     */
    fun init() {
        mainScope.launch {
            webView = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true
                settings.allowContentAccess = true
                addJavascriptInterface(LxJavascriptInterface(), "AndroidBridge")
            }

            // 加载空白页面
            webView.loadDataWithBaseURL(null, "<html></html>", "text/html", "UTF-8", null)
            
            // 注入全局LX环境
            injectLxGlobal()
        }
    }

    /**
     * 加载JS音源文件
     * @param jsFilePath JS文件路径
     */
    suspend fun loadJsFile(jsFilePath: String): Boolean = suspendCancellableCoroutine { cont ->
        if (!::webView.isInitialized) {
            init()
            // 等待WebView初始化
            Thread.sleep(500)
        }

        mainScope.launch {
            try {
                // 读取JS文件内容
                val jsFile = File(jsFilePath)
                if (!jsFile.exists() || !jsFile.isFile) {
                    Log.e(TAG, "JS file not found: $jsFilePath")
                    cont.resume(false)
                    return@launch
                }

                val jsContent = FileReader(jsFile).use { it.readText() }
                
                // 执行JS代码
                webView.evaluateJavascript(jsContent) { result ->
                    Log.d(TAG, "JS file loaded, result: $result")
                    isInitialized = true
                    cont.resume(true)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load JS file", e)
                cont.resumeWithException(e)
            }
        }
    }

    /**
     * 获取音乐播放地址
     * @param song 歌曲信息
     * @param quality 音质（128k/320k/flac等）
     */
    suspend fun getMusicUrl(song: Song, quality: String = "320k"): String? = suspendCancellableCoroutine { cont ->
        if (!isInitialized) {
            Log.e(TAG, "JS runtime not initialized")
            cont.resume(null)
            return@suspendCancellableCoroutine
        }

        mainScope.launch {
            try {
                // 构造musicInfo对象，根据不同平台设置对应的ID字段
                val musicInfo = when (song.source) {
                    "kg" -> mapOf("hash" to song.songId)
                    "tx" -> mapOf("songmid" to song.songId)
                    else -> mapOf("id" to song.songId)
                }

                // 调用JS中的handleGetMusicUrl方法
                val jsCode = """
                    (async () => {
                        try {
                            const url = await handleGetMusicUrl(${gson.toJson(song.source)}, ${gson.toJson(musicInfo)}, ${gson.toJson(quality)});
                            return JSON.stringify({success: true, url: url});
                        } catch (e) {
                            return JSON.stringify({success: false, error: e.message});
                        }
                    })()
                """.trimIndent()

                webView.evaluateJavascript(jsCode) { result ->
                    Log.d(TAG, "getMusicUrl result: $result")
                    try {
                        // 解析结果
                        val cleanedResult = result?.removeSurrounding("\"")?.replace("\\\"", "\"")
                        val map = gson.fromJson(cleanedResult, Map::class.java)
                        if (map["success"] == true) {
                            cont.resume(map["url"] as? String)
                        } else {
                            Log.e(TAG, "JS method error: ${map["error"]}")
                            cont.resume(null)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse result", e)
                        cont.resume(null)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get music url", e)
                cont.resumeWithException(e)
            }
        }
    }

    /**
     * 释放资源
     */
    fun release() {
        mainScope.launch {
            webView.destroy()
            instance = null
            isInitialized = false
        }
    }

    /**
     * 注入全局LX环境
     */
    private fun injectLxGlobal() {
        val lxGlobalJs = """
            // 全局LX对象
            globalThis.lx = {
                EVENT_NAMES: {
                    request: "request",
                    updateAlert: "updateAlert",
                    inited: "inited"
                },
                env: "android",
                version: "1.0.0",
                request: function(url, options, callback) {
                    // 调用安卓端的网络请求
                    const requestId = ${++requestIdCounter};
                    window.lxPendingCallbacks[requestId] = callback;
                    AndroidBridge.executeRequest(requestId, url, JSON.stringify(options));
                },
                on: function(event, callback) {
                    if (!window.lxEventCallbacks[event]) {
                        window.lxEventCallbacks[event] = [];
                    }
                    window.lxEventCallbacks[event].push(callback);
                },
                send: function(event, data) {
                    // 调用安卓端的事件处理
                    AndroidBridge.handleEvent(event, JSON.stringify(data));
                }
            };
            
            // 存储回调
            window.lxPendingCallbacks = {};
            window.lxEventCallbacks = {};
            
            // 安卓端回调JS的方法
            window.onRequestComplete = function(requestId, error, responseJson) {
                const callback = window.lxPendingCallbacks[requestId];
                if (callback) {
                    callback(error ? new Error(error) : null, JSON.parse(responseJson));
                    delete window.lxPendingCallbacks[requestId];
                }
            };
        """.trimIndent()

        mainScope.launch {
            webView.evaluateJavascript(lxGlobalJs, null)
            Log.i(TAG, "LX global environment injected")
        }
    }

    /**
     * JS调用安卓的接口
     */
    inner class LxJavascriptInterface {
        @JavascriptInterface
        fun executeRequest(requestId: Int, url: String, optionsJson: String) {
            ioScope.launch {
                try {
                    Log.d(TAG, "JS request: $url, options: $optionsJson")
                    
                    // 解析请求选项
                    val options = gson.fromJson(optionsJson, Map::class.java)
                    val method = (options["method"] as? String) ?: "GET"
                    val headers = (options["headers"] as? Map<String, String>) ?: emptyMap()
                    val timeout = (options["timeout"] as? Int) ?: 15000
                    
                    // 构建请求
                    val requestBuilder = Request.Builder()
                        .url(url)
                    
                    // 添加头
                    headers.forEach { (key, value) ->
                        requestBuilder.addHeader(key, value)
                    }
                    
                    // 设置方法和请求体
                    if (method.equals("POST", ignoreCase = true)) {
                        val body = (options["body"] as? String) ?: ""
                        requestBuilder.post(body.toRequestBody())
                    }
                    
                    // 执行请求
                    val response = HttpClient.instance.newCall(requestBuilder.build()).execute()
                    val responseBody = response.body?.string() ?: ""
                    
                    // 构建响应对象
                    val responseObj = mapOf(
                        "statusCode" to response.code,
                        "status" to response.code,
                        "body" to responseBody,
                        "headers" to response.headers.toMultimap()
                    )
                    
                    // 回调JS
                    val responseJson = gson.toJson(responseObj)
                    mainScope.launch {
                        webView.evaluateJavascript(
                            "window.onRequestComplete($requestId, null, ${gson.toJson(responseJson)})",
                            null
                        )
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Request failed", e)
                    // 回调错误
                    mainScope.launch {
                        webView.evaluateJavascript(
                            "window.onRequestComplete($requestId, ${gson.toJson(e.message)}, '{}')",
                            null
                        )
                    }
                }
            }
        }

        @JavascriptInterface
        fun handleEvent(event: String, dataJson: String) {
            Log.d(TAG, "Received event from JS: $event, data: $dataJson")
            // 处理事件，比如初始化完成、更新提示等
            if (event == "inited") {
                Log.i(TAG, "JS plugin initialized successfully")
            }
        }
    }

    /**
     * 检查是否已初始化
     */
    fun isInitialized(): Boolean = isInitialized
}
