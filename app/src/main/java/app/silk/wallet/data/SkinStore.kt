package app.silk.wallet.data

import android.content.Context
import app.silk.wallet.BuildConfig
import app.silk.wallet.domain.CardStylePreset
import app.silk.wallet.domain.CardStyles
import app.silk.wallet.domain.SkinAccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import kotlin.random.Random

/** 一次抽皮肤的结果：皮肤、是否新获得（此前没有或已过期）、新的到期时间 */
data class SkinDraw(val skin: CardStylePreset, val isNew: Boolean, val expiresAt: Long)

/**
 * 抽卡的本地状态：皮肤使用权（id → 到期时间）、上次抽皮肤的日期、上次“今日用卡”的结果。
 * 只存皮肤 id 和卡片 id，不含任何卡号等敏感信息，所以用普通 SharedPreferences。
 *
 * 使用期按稀有度：稀有永久，史诗 3 天，传说 1 天；重复抽到限时皮肤时从此刻重新计满。
 * 抽皮肤每天一次；调试包不限次数，方便试玩与调动效。
 */
object SkinStore {

    private const val PREFS = "silk_draw"
    private const val KEY_SKINS = "skin_expiry"
    private const val KEY_SKIN_DAY = "skin_draw_day"
    private const val KEY_CARD_ID = "card_draw_id"
    private const val KEY_CARD_DAY = "card_draw_day"
    private const val DAY_MS = 24L * 60 * 60 * 1000

    private val _expiries = MutableStateFlow<Map<String, Long>>(emptyMap())
    /** 全部记录（含已过期），界面按当前时间过滤 */
    val expiries: StateFlow<Map<String, Long>> = _expiries.asStateFlow()
    private var loaded = false

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun ensureLoaded(context: Context) {
        if (loaded) return
        val map = prefs(context).getStringSet(KEY_SKINS, emptySet()).orEmpty().mapNotNull { entry ->
            val i = entry.lastIndexOf('|')
            if (i <= 0) null else entry.substring(0, i) to (entry.substring(i + 1).toLongOrNull() ?: return@mapNotNull null)
        }.toMap()
        publish(map)
        loaded = true
    }

    private fun publish(map: Map<String, Long>) {
        _expiries.value = map
        SkinAccess.expiries = map
    }

    private fun save(context: Context, map: Map<String, Long>) {
        prefs(context).edit()
            .putStringSet(KEY_SKINS, map.map { (id, exp) -> "$id|$exp" }.toSet())
            .apply()
        publish(map)
    }

    fun isActive(id: String, now: Long = System.currentTimeMillis()) = (_expiries.value[id] ?: 0L) > now

    private fun today() = LocalDate.now().toEpochDay()

    fun canDrawSkin(context: Context): Boolean =
        BuildConfig.DEBUG || prefs(context).getLong(KEY_SKIN_DAY, -1L) != today()

    /**
     * 抽一款皮肤：先按稀有度权重掷出稀有度，再在该档里优先给当前不可用的（没有或已过期）。
     * 永久皮肤重复抽到不变；限时皮肤重复抽到则从此刻重新计满使用期。
     */
    fun drawSkin(context: Context, random: Random = Random.Default): SkinDraw {
        ensureLoaded(context)
        val now = System.currentTimeMillis()
        val current = _expiries.value
        val byRarity = CardStyles.skins.groupBy { it.rarity }
        val rarities = byRarity.keys.toList()
        var roll = random.nextInt(rarities.sumOf { it.weight })
        val rarity = rarities.first { r -> (roll < r.weight).also { if (!it) roll -= r.weight } }
        val pool = byRarity.getValue(rarity)
        val fresh = pool.filter { (current[it.id] ?: 0L) <= now }
        val skin = fresh.ifEmpty { pool }.random(random)
        val isNew = (current[skin.id] ?: 0L) <= now
        val expiresAt = rarity.days?.let { now + it * DAY_MS } ?: Long.MAX_VALUE
        val next = current + (skin.id to maxOf(current[skin.id] ?: 0L, expiresAt))
        prefs(context).edit().putLong(KEY_SKIN_DAY, today()).apply()
        save(context, next)
        return SkinDraw(skin, isNew, next.getValue(skin.id))
    }

    fun rememberCardDraw(context: Context, cardId: Long) {
        prefs(context).edit().putLong(KEY_CARD_ID, cardId).putLong(KEY_CARD_DAY, today()).apply()
    }

    /** 今天已经抽过的卡（没有则 null），用来在下一次抽卡时降低它的权重 */
    fun todaysCard(context: Context): Long? {
        val p = prefs(context)
        return if (p.getLong(KEY_CARD_DAY, -1L) == today()) p.getLong(KEY_CARD_ID, -1L) else null
    }

    /** 剩余时间的简短描述：永久返回 null */
    fun remainingLabel(expiresAt: Long, now: Long = System.currentTimeMillis()): String? {
        if (expiresAt == Long.MAX_VALUE) return null
        val left = expiresAt - now
        if (left <= 0) return "已过期"
        val hours = left / (60 * 60 * 1000)
        return if (hours >= 24) "剩 ${(left + DAY_MS - 1) / DAY_MS} 天" else "剩 ${hours.coerceAtLeast(1)} 小时"
    }
}
