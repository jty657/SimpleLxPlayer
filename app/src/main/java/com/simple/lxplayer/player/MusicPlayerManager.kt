package com.simple.lxplayer.player

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.simple.lxplayer.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 音乐播放器管理类
 * 使用Media3 ExoPlayer实现
 */
class MusicPlayerManager private constructor(private val context: Context) {
    companion object {
        private const val TAG = "MusicPlayerManager"
        private var instance: MusicPlayerManager? = null

        fun getInstance(context: Context): MusicPlayerManager {
            return instance ?: synchronized(this) {
                instance ?: MusicPlayerManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val exoPlayer: ExoPlayer by lazy {
        ExoPlayer.Builder(context).build().apply {
            addListener(playerListener)
        }
    }

    // 播放状态
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    // 当前播放歌曲
    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    // 播放进度
    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    // 总时长
    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    // 播放列表
    private val playlist = mutableListOf<Song>()
    private var currentIndex = 0

    /**
     * 播放歌曲
     * @param song 要播放的歌曲
     * @param playUrl 播放地址
     */
    fun play(song: Song, playUrl: String) {
        Log.d(TAG, "Playing: ${song.name} - ${song.artist}, url: $playUrl")
        
        // 构建MediaItem
        val mediaItem = MediaItem.fromUri(playUrl)
        
        exoPlayer.apply {
            setMediaItem(mediaItem)
            prepare()
            play()
        }

        _currentSong.value = song
        _duration.value = song.duration.takeIf { it > 0 } ?: exoPlayer.duration
        
        // 添加到播放列表
        if (playlist.isEmpty() || playlist.getOrNull(currentIndex)?.id != song.id) {
            playlist.add(currentIndex + 1, song)
            currentIndex++
        }
    }

    /**
     * 暂停播放
     */
    fun pause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
            _isPlaying.value = false
        }
    }

    /**
     * 继续播放
     */
    fun resume() {
        if (!exoPlayer.isPlaying && _currentSong.value != null) {
            exoPlayer.play()
            _isPlaying.value = true
        }
    }

    /**
     * 停止播放
     */
    fun stop() {
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        _isPlaying.value = false
        _currentSong.value = null
        _currentPosition.value = 0
        _duration.value = 0
    }

    /**
     * 上一首
     */
    fun previous() {
        if (currentIndex > 0) {
            currentIndex--
            val song = playlist[currentIndex]
            // 这里需要重新获取播放地址，暂时先空实现
            Log.d(TAG, "Previous song: ${song.name}")
        }
    }

    /**
     * 下一首
     */
    fun next() {
        if (currentIndex < playlist.size - 1) {
            currentIndex++
            val song = playlist[currentIndex]
            // 这里需要重新获取播放地址，暂时先空实现
            Log.d(TAG, "Next song: ${song.name}")
        }
    }

    /**
     * 拖动进度
     * @param position 目标位置（毫秒）
     */
    fun seekTo(position: Long) {
        exoPlayer.seekTo(position)
        _currentPosition.value = position
    }

    /**
     * 释放资源
     */
    fun release() {
        exoPlayer.release()
        instance = null
    }

    /**
     * 获取当前播放进度
     */
    fun getCurrentPosition(): Long = exoPlayer.currentPosition

    /**
     * 获取总时长
     */
    fun getDuration(): Long = exoPlayer.duration.takeIf { it > 0 } ?: _duration.value

    // 播放器监听器
    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            super.onIsPlayingChanged(isPlaying)
            _isPlaying.value = isPlaying
            Log.d(TAG, "Playing state changed: $isPlaying")
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            super.onPlaybackStateChanged(playbackState)
            when (playbackState) {
                Player.STATE_READY -> {
                    _duration.value = exoPlayer.duration
                    Log.d(TAG, "Player ready, duration: ${exoPlayer.duration}")
                }
                Player.STATE_ENDED -> {
                    Log.d(TAG, "Playback ended")
                    // 自动播放下一首
                    next()
                }
                Player.STATE_BUFFERING -> {
                    Log.d(TAG, "Buffering...")
                }
                Player.STATE_IDLE -> {
                    _isPlaying.value = false
                }
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            super.onMediaItemTransition(mediaItem, reason)
            // 更新当前歌曲信息
        }
    }
}
