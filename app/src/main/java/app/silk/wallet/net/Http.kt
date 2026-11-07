package app.silk.wallet.net

import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

/**
 * HTTP 客户端工厂：支持 socks5://host:port 与 http://host:port 代理。
 * SOCKS 用未解析地址，域名交给代理端解析，避免本地 DNS 泄露访问目标。
 */
object Http {

    private val proxyPattern = Regex("^(socks5|http)://([^:/\\s]+):(\\d{1,5})/?$", RegexOption.IGNORE_CASE)

    fun isValidProxy(text: String): Boolean = text.isBlank() || proxyPattern.matches(text.trim())

    private fun parse(text: String?): Proxy? {
        val m = text?.trim()?.let(proxyPattern::matchEntire) ?: return null
        val (scheme, host, port) = m.destructured
        val type = if (scheme.equals("socks5", true)) Proxy.Type.SOCKS else Proxy.Type.HTTP
        return Proxy(type, InetSocketAddress.createUnresolved(host, port.toInt()))
    }

    fun client(proxy: String?, readTimeoutSec: Long = 15): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSec, TimeUnit.SECONDS)
            .apply { parse(proxy)?.let(::proxy) }
            .build()

    /** 把网络异常翻成人话 */
    fun describe(e: Throwable): String = when (e) {
        is java.net.UnknownHostException -> "无法解析域名，请检查网络或代理"
        is java.net.SocketTimeoutException -> "连接超时"
        is java.net.ConnectException -> "连接被拒绝，代理可能未启动"
        is javax.net.ssl.SSLException -> "安全连接失败"
        else -> e.message ?: "网络错误"
    }
}
