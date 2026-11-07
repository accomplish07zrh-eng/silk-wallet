package app.silk.wallet

import android.nfc.NfcAdapter
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import app.silk.wallet.data.Prefs
import app.silk.wallet.data.Settings
import app.silk.wallet.ui.AppRoot
import app.silk.wallet.ui.fx.LocalTilt
import app.silk.wallet.ui.fx.rememberTilt
import app.silk.wallet.ui.theme.SilkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** BiometricPrompt 需要 FragmentActivity，所以继承 AppCompatActivity */
class MainActivity : AppCompatActivity() {

    private val app get() = application as SilkApp
    private var nfc: NfcAdapter? = null
    private var autoLockSeconds = 60

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nfc = NfcAdapter.getDefaultAdapter(this)
        // 防截屏必须在首帧前生效：DataStore 是异步的，这里读同步镜像
        if (Prefs.secureScreenNow(this)) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        setContent {
            val settings by app.prefs.settings.collectAsState(initial = Settings())
            LaunchedEffect(settings.secureScreen) {
                if (settings.secureScreen) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            LaunchedEffect(settings.lockOnScreenOff, settings.autoLockSeconds) {
                app.lock.lockOnScreenOff = settings.lockOnScreenOff
                autoLockSeconds = settings.autoLockSeconds
            }
            SilkTheme(themeMode = settings.theme) {
                CompositionLocalProvider(LocalTilt provides rememberTilt()) {
                    AppRoot(app, settings)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        app.lock.onForeground(autoLockSeconds)
    }

    override fun onStop() {
        super.onStop()
        app.lock.onBackground()
    }

    override fun onResume() {
        super.onResume()
        // 只在前台读卡；跳过 NDEF 检查以便直接走 ISO-DEP 的 EMV 流程
        nfc?.enableReaderMode(
            this,
            { tag ->
                lifecycleScope.launch(Dispatchers.IO) {
                    runCatching { app.emv.read(tag) }.getOrNull()?.let(app.nfcInbox::post)
                }
            },
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            null,
        )
    }

    override fun onPause() {
        super.onPause()
        nfc?.disableReaderMode(this)
    }
}
