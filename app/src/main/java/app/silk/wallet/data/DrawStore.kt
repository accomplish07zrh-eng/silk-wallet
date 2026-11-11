package app.silk.wallet.data

import android.content.Context

/**
 * 抽卡的本地记录：只存上一次抽到的卡片 id（不含卡号等敏感信息，所以用普通 SharedPreferences）。
 * 皮肤已全部开放，不再记录使用期；抽卡也不限次数。
 */
object DrawStore {

    private const val PREFS = "silk_draw"
    private const val KEY_CARD_ID = "card_draw_id"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun rememberCardDraw(context: Context, cardId: Long) {
        prefs(context).edit().putLong(KEY_CARD_ID, cardId).apply()
    }

    /** 上一次抽到的卡（没有则 null），用来在下一次抽卡时降低它的权重 */
    fun lastCard(context: Context): Long? =
        prefs(context).getLong(KEY_CARD_ID, -1L).takeIf { it >= 0L }
}
