package app.silk.wallet.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

/** 在线识别发卡行（binlist.net），只上传卡号前 8 位 */
object BinLookup {

    data class Info(val bank: String?, val scheme: String?, val type: String?, val country: String?)

    suspend fun query(number: String, proxy: String?): Result<Info> = withContext(Dispatchers.IO) {
        runCatching {
            val bin = number.filter(Char::isDigit).take(8)
            require(bin.length >= 6) { "卡号太短，至少输入 6 位" }
            val req = Request.Builder()
                .url("https://lookup.binlist.net/$bin")
                .header("Accept-Version", "3")
                .build()
            Http.client(proxy).newCall(req).execute().use { resp ->
                when (resp.code) {
                    200 -> Unit
                    404 -> error("没有查到这个号段")
                    429 -> error("查询太频繁，稍后再试")
                    else -> error("查询失败：HTTP ${resp.code}")
                }
                val o = JSONObject(resp.body?.string().orEmpty())
                Info(
                    bank = o.optJSONObject("bank")?.optString("name")?.takeIf { it.isNotBlank() },
                    scheme = o.optString("scheme").takeIf { it.isNotBlank() },
                    type = o.optString("type").takeIf { it.isNotBlank() },
                    country = o.optJSONObject("country")?.optString("name")?.takeIf { it.isNotBlank() },
                )
            }
        }.recoverCatching { e -> throw IllegalStateException(if (e is IllegalStateException || e is IllegalArgumentException) e.message else Http.describe(e)) }
    }

    /** 代理连通性测试：请求一个轻量地址，返回耗时 */
    suspend fun ping(proxy: String?): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val start = System.nanoTime()
            Http.client(proxy, 10).newCall(Request.Builder().url("https://www.gstatic.com/generate_204").build())
                .execute().use { check(it.code in 200..399) { "HTTP ${it.code}" } }
            (System.nanoTime() - start) / 1_000_000
        }.recoverCatching { throw IllegalStateException(Http.describe(it)) }
    }
}
