package com.simple.lxplayer.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.simple.lxplayer.LxJsRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * 我的页面
 */
@Composable
fun MeScreen(
    modifier: Modifier = Modifier,
    jsRuntime: LxJsRuntime
) {
    val context = LocalContext.current
    var isJsLoaded by remember { mutableStateOf(jsRuntime.isInitialized()) }

    // 文件选择器
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = PickFileContract(),
        onResult = { uri ->
            uri?.let {
                // 处理选中的JS文件
                importJsFile(context, uri, jsRuntime) { success ->
                    isJsLoaded = success
                    if (success) {
                        Toast.makeText(context, "音源导入成功", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "音源导入失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = "我的音源",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        // 音源状态
        Text(
            text = if (isJsLoaded) "✓ 音源已导入" else "✗ 未导入音源",
            style = MaterialTheme.typography.titleMedium,
            color = if (isJsLoaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // 导入按钮
        Button(
            onClick = { filePickerLauncher.launch("*/*") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("导入JS音源文件")
        }

        // 说明文字
        Text(
            text = "请导入LX Music格式的音源JS文件，导入后即可使用搜索功能播放音乐",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 32.dp)
        )
    }
}

/**
 * 导入JS文件到应用私有目录并加载
 */
private fun importJsFile(
    context: Context,
    uri: Uri,
    jsRuntime: LxJsRuntime,
    onResult: (Boolean) -> Unit
) {
    kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
        try {
            // 从URI读取文件
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            inputStream?.use { input ->
                // 保存到应用私有目录
                val outputFile = File(context.filesDir, "lx_source.js")
                FileOutputStream(outputFile).use { output ->
                    input.copyTo(output)
                }

                // 加载JS文件
                val success = jsRuntime.loadJsFile(outputFile.absolutePath)
                withContext(Dispatchers.Main) {
                    onResult(success)
                }
            } ?: run {
                withContext(Dispatchers.Main) {
                    onResult(false)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                onResult(false)
            }
        }
    }
}

/**
 * 文件选择器Contract
 */
class PickFileContract : ActivityResultContract<String, Uri?>() {
    override fun createIntent(context: Context, input: String): Intent {
        return Intent(Intent.ACTION_GET_CONTENT).apply {
            type = input
            addCategory(Intent.CATEGORY_OPENABLE)
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        if (resultCode != Activity.RESULT_OK) return null
        return intent?.data
    }
}
