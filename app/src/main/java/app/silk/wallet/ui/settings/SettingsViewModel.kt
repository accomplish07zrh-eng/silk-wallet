package app.silk.wallet.ui.settings

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.silk.wallet.SilkApp
import app.silk.wallet.data.Backup
import app.silk.wallet.net.BinLookup
import app.silk.wallet.net.Updater
import app.silk.wallet.notify.ExpiryReminder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Status {
    data object Idle : Status
    data object Working : Status
    data class Ok(val message: String) : Status
    data class Failed(val message: String) : Status
}

sealed interface UpdateUi {
    data object Idle : UpdateUi
    data object Checking : UpdateUi
    data class Latest(val version: String) : UpdateUi
    data class Available(val release: Updater.Release) : UpdateUi
    data class Downloading(val release: Updater.Release, val progress: Float) : UpdateUi
    data class Failed(val message: String) : UpdateUi
}

class SettingsViewModel(private val app: SilkApp) : ViewModel() {

    private val prefs = app.prefs

    var proxyTest by mutableStateOf<Status>(Status.Idle)
        private set
    var backup by mutableStateOf<Status>(Status.Idle)
        private set
    var update by mutableStateOf<UpdateUi>(UpdateUi.Idle)
        private set

    fun setTheme(v: String) = viewModelScope.launch { prefs.setTheme(v) }
    fun setMask(v: Boolean) = viewModelScope.launch { prefs.setMask(v) }
    fun setMotion(v: Boolean) = viewModelScope.launch { prefs.setMotion(v) }
    fun setClipboard(v: Int) = viewModelScope.launch { prefs.setClipboardSeconds(v) }
    fun setSecure(v: Boolean) = viewModelScope.launch { prefs.setSecureScreen(v) }
    fun setAutoLock(v: Int) = viewModelScope.launch { prefs.setAutoLock(v) }
    fun setScreenOff(v: Boolean) = viewModelScope.launch { prefs.setLockOnScreenOff(v) }
    fun setNoticeDays(v: Int) = viewModelScope.launch { prefs.setNoticeDays(v) }
    fun setOffline(v: Boolean) = viewModelScope.launch { prefs.setOffline(v) }

    fun setExpiryNotify(context: Context, v: Boolean) = viewModelScope.launch {
        prefs.setExpiryNotify(v)
        ExpiryReminder.schedule(context, v)
    }

    fun saveProxy(v: String) = viewModelScope.launch {
        prefs.setProxy(v)
        proxyTest = Status.Ok("已保存")
    }

    fun testProxy(v: String) = viewModelScope.launch {
        proxyTest = Status.Working
        proxyTest = BinLookup.ping(v.ifBlank { null }).fold(
            { Status.Ok("连接正常 · ${it} ms") },
            { Status.Failed(it.message ?: "连接失败") },
        )
    }

    fun checkUpdate() = viewModelScope.launch {
        val s = prefs.settings.first()
        if (s.offline) {
            update = UpdateUi.Failed("已开启完全离线，检查更新不可用")
            return@launch
        }
        update = UpdateUi.Checking
        update = Updater.check(s.proxy.ifBlank { null }).fold(
            {
                when (it) {
                    is Updater.Check.Available -> UpdateUi.Available(it.release)
                    is Updater.Check.Latest -> UpdateUi.Latest(it.version)
                }
            },
            { UpdateUi.Failed(it.message ?: "检查失败") },
        )
    }

    fun downloadAndInstall(context: Context, release: Updater.Release) = viewModelScope.launch {
        val proxy = prefs.settings.first().proxy.ifBlank { null }
        update = UpdateUi.Downloading(release, 0f)
        Updater.download(context, release, proxy) { p ->
            viewModelScope.launch(Dispatchers.Main) { update = UpdateUi.Downloading(release, p) }
        }.onSuccess {
            update = UpdateUi.Available(release)
            app.lock.allowExternalOnce()
            Updater.install(context, it)
        }.onFailure { update = UpdateUi.Failed(it.message ?: "下载失败") }
    }

    fun dismissUpdate() {
        if (update !is UpdateUi.Downloading) update = UpdateUi.Idle
    }

    fun exportTo(context: Context, uri: Uri, password: String) = viewModelScope.launch {
        backup = Status.Working
        backup = runCatching {
            val cards = app.cards.all()
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { Backup.write(cards, password, it) } ?: error("无法写入文件")
            }
            cards.size
        }.fold({ Status.Ok("已导出 $it 张卡片") }, { Status.Failed(it.message ?: "导出失败") })
    }

    fun importFrom(context: Context, uri: Uri, password: String) = viewModelScope.launch {
        backup = Status.Working
        backup = runCatching {
            val cards = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { Backup.read(it, password) } ?: error("无法读取文件")
            }
            app.cards.merge(cards)
        }.fold({ (added, updated) -> Status.Ok("新增 $added 张，更新 $updated 张") }, { Status.Failed(it.message ?: "导入失败") })
    }

    fun clearBackupStatus() {
        backup = Status.Idle
    }
}
