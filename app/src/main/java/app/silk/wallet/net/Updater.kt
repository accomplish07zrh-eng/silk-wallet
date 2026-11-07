package app.silk.wallet.net

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import app.silk.wallet.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.File

/**
 * 应用内更新：读取本项目 GitHub 仓库的最新 Release，
 * 优先挑与本机 ABI 匹配的 APK，没有则用通用包；下载后调起系统安装器。
 */
object Updater {

    const val REPO = "accomplish07zrh-eng/silk"

    data class Release(val version: String, val notes: String, val apkUrl: String, val apkName: String)

    sealed interface Check {
        data class Available(val release: Release) : Check
        data class Latest(val version: String) : Check
    }

    suspend fun check(proxy: String?): Result<Check> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("https://api.github.com/repos/$REPO/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .build()
            Http.client(proxy).newCall(req).execute().use { resp ->
                if (resp.code == 404) error("还没有发布过版本")
                check(resp.isSuccessful) { "检查失败：HTTP ${resp.code}" }
                val o = JSONObject(resp.body?.string().orEmpty())
                val version = o.optString("tag_name").removePrefix("v")
                if (!newer(version, BuildConfig.VERSION_NAME)) return@runCatching Check.Latest(BuildConfig.VERSION_NAME)
                val assets = o.optJSONArray("assets")
                val apks = (0 until (assets?.length() ?: 0)).map { assets!!.getJSONObject(it) }
                    .filter { it.optString("name").endsWith(".apk") }
                val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
                val pick = apks.firstOrNull { it.optString("name").contains(abi) }
                    ?: apks.firstOrNull { it.optString("name").contains("universal") }
                    ?: apks.firstOrNull()
                    ?: error("这个版本没有附带安装包")
                Check.Available(Release(version, o.optString("body"), pick.getString("browser_download_url"), pick.getString("name")))
            }
        }.recoverCatching { e -> throw IllegalStateException(if (e is IllegalStateException) e.message else Http.describe(e)) }
    }

    /** 语义化版本比较：1.10.0 > 1.9.3 */
    fun newer(remote: String, local: String): Boolean {
        fun parts(v: String) = v.split('.', '-').mapNotNull { it.toIntOrNull() }
        val a = parts(remote)
        val b = parts(local)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    suspend fun download(context: Context, release: Release, proxy: String?, onProgress: (Float) -> Unit): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val target = File(dir, release.apkName)
                Http.client(proxy, 60).newCall(Request.Builder().url(release.apkUrl).build()).execute().use { resp ->
                    check(resp.isSuccessful) { "下载失败：HTTP ${resp.code}" }
                    val body = resp.body ?: error("下载内容为空")
                    val total = body.contentLength().coerceAtLeast(1)
                    body.byteStream().use { input ->
                        target.outputStream().use { out ->
                            val buf = ByteArray(64 * 1024)
                            var done = 0L
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                done += n
                                onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
                target
            }.recoverCatching { e -> throw IllegalStateException(if (e is IllegalStateException) e.message else Http.describe(e)) }
        }

    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
