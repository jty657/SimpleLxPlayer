package com.simple.lxplayer

import android.Manifest
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.lifecycleScope
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.simple.lxplayer.model.Song
import com.simple.lxplayer.player.MusicPlayerManager
import com.simple.lxplayer.ui.screens.MeScreen
import com.simple.lxplayer.ui.screens.PlayerScreen
import com.simple.lxplayer.ui.screens.SearchScreen
import kotlinx.coroutines.launch

/**
 * 主Activity
 */
class MainActivity : ComponentActivity() {
    private lateinit var jsRuntime: LxJsRuntime
    private lateinit var playerManager: MusicPlayerManager

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 初始化JS运行环境和播放器
        jsRuntime = LxJsRuntime.getInstance(this)
        jsRuntime.init()
        playerManager = MusicPlayerManager.getInstance(this)

        // 尝试加载已保存的JS文件
        val savedJsFile = getFileStreamPath("lx_source.js")
        if (savedJsFile.exists()) {
            lifecycleScope.launch {
                jsRuntime.loadJsFile(savedJsFile.absolutePath)
            }
        }

        setContent {
            // 请求必要权限
            RequestPermissions()

            // 底部导航页面
            val navigationItems = listOf(
                NavigationItem("搜索", Icons.Default.Home),
                NavigationItem("播放", Icons.Default.LibraryMusic),
                NavigationItem("我的", Icons.Default.Person)
            )

            var selectedItemIndex by remember { mutableIntStateOf(0) }
            val coroutineScope = rememberCoroutineScope()

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                bottomBar = {
                    NavigationBar {
                        navigationItems.forEachIndexed { index, item ->
                            NavigationBarItem(
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label) },
                                selected = selectedItemIndex == index,
                                onClick = { selectedItemIndex = index }
                            )
                        }
                    }
                }
            ) { innerPadding ->
                val modifier = Modifier.padding(innerPadding)

                when (selectedItemIndex) {
                    0 -> SearchScreen(
                        modifier = modifier,
                        onSongClick = { song ->
                            coroutineScope.launch {
                                playSong(song)
                            }
                        }
                    )
                    1 -> PlayerScreen(
                        modifier = modifier,
                        playerManager = playerManager
                    )
                    2 -> MeScreen(
                        modifier = modifier,
                        jsRuntime = jsRuntime
                    )
                }
            }
        }
    }

    /**
     * 播放歌曲
     */
    private suspend fun playSong(song: Song) {
        // 如果搜索结果已经有播放地址，直接播放
        if (!song.playUrl.isNullOrEmpty()) {
            playerManager.play(song, song.playUrl)
            Toast.makeText(this, "正在播放: ${song.name}", Toast.LENGTH_SHORT).show()
            return
        }

        // 否则调用JS引擎获取播放地址
        if (!jsRuntime.isInitialized()) {
            Toast.makeText(this, "请先导入音源文件", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "正在获取播放地址...", Toast.LENGTH_SHORT).show()
        
        val playUrl = jsRuntime.getMusicUrl(song)
        if (playUrl.isNullOrEmpty()) {
            Toast.makeText(this, "获取播放地址失败", Toast.LENGTH_SHORT).show()
            return
        }

        playerManager.play(song, playUrl)
        Toast.makeText(this, "正在播放: ${song.name}", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        jsRuntime.release()
        playerManager.release()
    }
}

/**
 * 底部导航项
 */
data class NavigationItem(
    val label: String,
    val icon: ImageVector
)

/**
 * 请求必要权限
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun RequestPermissions() {
    // 存储权限
    val storagePermissionState = rememberPermissionState(
        Manifest.permission.READ_EXTERNAL_STORAGE
    )
    // 通知权限
    val notificationPermissionState = rememberPermissionState(
        Manifest.permission.POST_NOTIFICATIONS
    )

    LaunchedEffect(Unit) {
        if (!storagePermissionState.status.isGranted) {
            storagePermissionState.launchPermissionRequest()
        }
        if (!notificationPermissionState.status.isGranted) {
            notificationPermissionState.launchPermissionRequest()
        }
    }
}
