package app.silk.wallet.nfc

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 读卡结果的暂存箱：锁屏时先放着，解锁后再交给添加卡片页；超过 2 分钟的结果作废，
 * 防止很久以前贴过的卡在某次解锁后突然弹出来。
 */
class NfcInbox {
    data class Pending(val draft: NfcDraft, val at: Long = System.currentTimeMillis()) {
        val fresh: Boolean get() = System.currentTimeMillis() - at < 120_000
    }

    private val _pending = MutableStateFlow<Pending?>(null)
    val pending: StateFlow<Pending?> = _pending.asStateFlow()

    fun post(draft: NfcDraft) {
        _pending.value = Pending(draft)
    }

    fun take(): Pending? = _pending.value.also { _pending.value = null }
}
