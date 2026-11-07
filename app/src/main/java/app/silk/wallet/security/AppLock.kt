package app.silk.wallet.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 应用锁状态机：冷启动即锁（若开启了锁）；退后台超过设定时长回来再锁；
 * 可选息屏即锁。App 主动拉起系统选择器（相册、文件）时那一次离开不计时。
 */
class AppLock(context: Context, private val pins: PinVault) {

    private val _locked = MutableStateFlow(pins.lockEnabled)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private var leftAt = -1L
    private var skipNext = false
    var lockOnScreenOff = false

    init {
        ContextCompat.registerReceiver(
            context.applicationContext,
            object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    if (lockOnScreenOff) lockNow()
                }
            },
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    fun unlock() {
        _locked.value = false
    }

    fun lockNow() {
        if (pins.lockEnabled) _locked.value = true
    }

    fun allowExternalOnce() {
        skipNext = true
    }

    fun onBackground() {
        if (skipNext) {
            skipNext = false
            leftAt = -1L
            return
        }
        leftAt = SystemClock.elapsedRealtime()
    }

    fun onForeground(autoLockSeconds: Int) {
        if (!pins.lockEnabled || _locked.value || leftAt < 0) return
        if (SystemClock.elapsedRealtime() - leftAt >= autoLockSeconds * 1000L) _locked.value = true
        leftAt = -1L
    }
}
