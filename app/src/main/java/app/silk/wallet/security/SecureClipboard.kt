package app.silk.wallet.security

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 复制敏感字段：标记为敏感内容（系统不在剪贴板预览里显示），并在设定秒数后自动清空 */
class SecureClipboard(private val scope: CoroutineScope) {

    private var clearJob: Job? = null

    fun copy(context: Context, label: String, text: String, clearAfterSeconds: Int) {
        val cm = context.getSystemService(ClipboardManager::class.java) ?: return
        val clip = ClipData.newPlainText(label, text).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
            }
        }
        cm.setPrimaryClip(clip)
        // Android 13+ 系统自己会弹“已复制”，低版本补一个提示
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, "已复制$label", Toast.LENGTH_SHORT).show()
        }
        clearJob?.cancel()
        if (clearAfterSeconds > 0) {
            clearJob = scope.launch {
                delay(clearAfterSeconds * 1000L)
                val current = cm.primaryClip
                if (current?.description?.label == label) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) cm.clearPrimaryClip()
                    else cm.setPrimaryClip(ClipData.newPlainText("", ""))
                }
            }
        }
    }
}
