package app.silk.wallet

import android.app.Application
import app.silk.wallet.data.CardRepository
import app.silk.wallet.data.Prefs
import app.silk.wallet.data.SilkDatabase
import app.silk.wallet.nfc.EmvReader
import app.silk.wallet.nfc.NfcInbox
import app.silk.wallet.security.AppLock
import app.silk.wallet.security.KeyVault
import app.silk.wallet.security.PinVault
import app.silk.wallet.security.SecureClipboard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** 全局依赖：数量少，手写单例比引入 DI 框架更直接 */
class SilkApp : Application() {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val prefs by lazy { Prefs(this) }
    val pins by lazy { PinVault(this) }
    val lock by lazy { AppLock(this, pins) }
    val clipboard by lazy { SecureClipboard(scope) }
    val nfcInbox = NfcInbox()
    val emv = EmvReader()

    private val database by lazy { SilkDatabase.open(this, KeyVault(this).databasePassphrase()) }
    val cards by lazy { CardRepository(database) }

    override fun onCreate() {
        super.onCreate()
    }
}
