package app.silk.wallet.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore("silk_settings")

/** 用户设置快照 */
data class Settings(
    val theme: String = "system",
    val maskNumbers: Boolean = true,
    /** 卡面流动、光效与倾斜感应；关掉后卡面静止，更省电 */
    val motion: Boolean = true,
    val clipboardSeconds: Int = 30,
    val secureScreen: Boolean = false,
    val autoLockSeconds: Int = 60,
    val lockOnScreenOff: Boolean = false,
    val expiryNotify: Boolean = false,
    val noticeDays: Int = 30,
    val offline: Boolean = false,
    val proxy: String = "",
    val sort: SortMode = SortMode.MANUAL,
    /** 首页陈列：false = 叠放，true = 多列平铺 */
    val grid: Boolean = false,
    /** 平铺时的列数（2～4），也可以在首页双指捏合调整 */
    val gridColumns: Int = 2,
)

/** DataStore 封装；防截屏另存一份同步镜像，首帧前就要用 */
class Prefs(private val context: Context) {

    private object K {
        val theme = stringPreferencesKey("theme")
        val mask = booleanPreferencesKey("mask")
        val motion = booleanPreferencesKey("motion")
        val clip = intPreferencesKey("clip")
        val secure = booleanPreferencesKey("secure")
        val autoLock = intPreferencesKey("auto_lock")
        val screenOff = booleanPreferencesKey("screen_off")
        val notify = booleanPreferencesKey("notify")
        val notice = intPreferencesKey("notice")
        val offline = booleanPreferencesKey("offline")
        val proxy = stringPreferencesKey("proxy")
        val sort = stringPreferencesKey("sort")
        val grid = booleanPreferencesKey("grid")
        val gridCols = intPreferencesKey("grid_cols")
    }

    val settings: Flow<Settings> = context.store.data.map { p ->
        Settings(
            theme = p[K.theme] ?: "system",
            maskNumbers = p[K.mask] ?: true,
            motion = p[K.motion] ?: true,
            clipboardSeconds = p[K.clip] ?: 30,
            secureScreen = p[K.secure] ?: false,
            autoLockSeconds = p[K.autoLock] ?: 60,
            lockOnScreenOff = p[K.screenOff] ?: false,
            expiryNotify = p[K.notify] ?: false,
            noticeDays = p[K.notice] ?: 30,
            offline = p[K.offline] ?: false,
            proxy = p[K.proxy] ?: "",
            sort = SortMode.entries.firstOrNull { it.name == p[K.sort] } ?: SortMode.MANUAL,
            grid = p[K.grid] ?: false,
            gridColumns = (p[K.gridCols] ?: 2).coerceIn(2, 4),
        )
    }

    private suspend fun <T> put(key: Preferences.Key<T>, value: T) {
        context.store.edit { it[key] = value }
    }

    suspend fun setTheme(v: String) = put(K.theme, v)
    suspend fun setMask(v: Boolean) = put(K.mask, v)
    suspend fun setMotion(v: Boolean) = put(K.motion, v)
    suspend fun setClipboardSeconds(v: Int) = put(K.clip, v)
    suspend fun setAutoLock(v: Int) = put(K.autoLock, v)
    suspend fun setLockOnScreenOff(v: Boolean) = put(K.screenOff, v)
    suspend fun setExpiryNotify(v: Boolean) = put(K.notify, v)
    suspend fun setNoticeDays(v: Int) = put(K.notice, v)
    suspend fun setOffline(v: Boolean) = put(K.offline, v)
    suspend fun setProxy(v: String) = put(K.proxy, v.trim())
    suspend fun setSort(v: SortMode) = put(K.sort, v.name)
    suspend fun setGrid(v: Boolean) = put(K.grid, v)
    suspend fun setGridColumns(v: Int) = put(K.gridCols, v.coerceIn(2, 4))

    suspend fun setSecureScreen(v: Boolean) {
        mirror(context).edit().putBoolean("secure", v).apply()
        put(K.secure, v)
    }

    companion object {
        val NOTICE_OPTIONS = listOf(7, 15, 30, 60)
        private fun mirror(context: Context) = context.getSharedPreferences("silk_mirror", Context.MODE_PRIVATE)
        fun secureScreenNow(context: Context) = mirror(context).getBoolean("secure", false)
    }
}
