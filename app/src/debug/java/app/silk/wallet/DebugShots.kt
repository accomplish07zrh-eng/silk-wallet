package app.silk.wallet

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Choreographer
import android.view.PixelCopy
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import app.silk.wallet.data.Card
import app.silk.wallet.data.SkinStore
import app.silk.wallet.domain.BankDirectory
import app.silk.wallet.domain.CardBrand
import java.io.File
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor

/**
 * 仅调试包：把应用自己的窗口（不含状态栏、通知图标和别的悬浮窗）截成图片，
 * 用来做 README 截图与动图。正式包里同名对象是空实现。
 *
 * adb shell am broadcast -a app.silk.wallet.debug.SHOT -p app.silk.wallet.debug --es name home
 * adb shell am broadcast -a app.silk.wallet.debug.SHOT -p app.silk.wallet.debug --es name open --ei burst 2500
 * 输出在 /sdcard/Android/data/app.silk.wallet.debug/files/shots/
 */
object DebugShots {
    private const val ACTION = "app.silk.wallet.debug.SHOT"
    private val io = Executors.newFixedThreadPool(2) as ThreadPoolExecutor
    private val main = Handler(Looper.getMainLooper())

    fun install(activity: Activity) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                if (i.getBooleanExtra("seed", false)) return seed(activity)
                // 演示锁屏：设一个测试 PIN 并立即上锁；unlockdemo 则清掉 PIN
                if (i.getBooleanExtra("lockdemo", false)) {
                    val app = activity.application as SilkApp
                    app.pins.setPin("246810")
                    app.lock.lockNow()
                    return
                }
                if (i.getBooleanExtra("unlockdemo", false)) {
                    val app = activity.application as SilkApp
                    app.pins.clearPin()
                    app.lock.unlock()
                    return
                }
                val name = i.getStringExtra("name") ?: "shot"
                val burst = i.getIntExtra("burst", 0)
                if (burst > 0) burst(activity, name, burst, i.getIntExtra("width", 540)) else single(activity, name)
            }
        }
        ContextCompat.registerReceiver(activity, receiver, IntentFilter(ACTION), ContextCompat.RECEIVER_EXPORTED)
        (activity as LifecycleOwner).lifecycle.addObserver(LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_DESTROY) activity.unregisterReceiver(receiver)
        })
    }

    /** 清空现有卡片，换成一组好看的演示卡（卡号均为虚构、仅通过 Luhn 校验） */
    private fun seed(a: Activity) {
        val app = a.application as SilkApp
        app.scope.launch {
            app.cards.all().forEach { app.cards.delete(it) }
            // 与真实抽到时一致：传说留 1 天、史诗留 3 天（这里各少一点，显示成刚抽到不久）
            val now0 = System.currentTimeMillis()
            listOf("skin_black_gold", "skin_borealis").forEach { SkinStore.grant(a, it, now0 + DAY - 40 * 60_000L) }
            listOf("skin_galaxy", "skin_lava", "skin_sakura").forEach { SkinStore.grant(a, it, now0 + 3 * DAY - 2 * 3_600_000L) }
            val demo = listOf(
                Demo("LIN QING", "622202", 11, 26, "debit", null, "工资卡"),
                Demo("SU WAN", "436742", 3, 28, "credit", null, ""),
                Demo("CHEN YU", "378282", 9, 29, "credit", "gold", "", "美国运通"),
                Demo("LIN QING", "621661", 5, 31, "debit", "skin_galaxy", ""),
                Demo("SU WAN", "552033", 7, 30, "credit", "skin_borealis", "旅行用", "汇丰银行"),
                Demo("LIN QING", "622588", 12, 29, "credit", "skin_black_gold", "日常消费"),
            )
            demo.forEachIndexed { n, d ->
                val number = luhn(d.prefix, if (d.prefix.startsWith("37")) 15 else 16, n)
                val bank = BankDirectory.find(number)
                val now = System.currentTimeMillis()
                app.cards.save(
                    Card(
                        holder = d.holder, number = number, expMonth = d.month, expYear = 2000 + d.year,
                        cvv = "123", brand = CardBrand.detect(number).name, bank = d.bank ?: bank?.name.orEmpty(),
                        bankCode = bank?.code, kind = d.kind, note = d.note, styleId = d.style,
                        createdAt = now, updatedAt = now,
                    )
                )
            }
        }
    }

    private class Demo(val holder: String, val prefix: String, val month: Int, val year: Int, val kind: String, val style: String?, val note: String, val bank: String? = null)

    private const val DAY = 24L * 60 * 60 * 1000

    private fun luhn(prefix: String, length: Int, salt: Int): String {
        val body = StringBuilder(prefix)
        var x = 7 + salt * 31
        while (body.length < length - 1) { x = (x * 1103 + 12345) % 10007; body.append(x % 10) }
        val digits = body.reversed().map { it - '0' }
        val sum = digits.mapIndexed { i, d -> if (i % 2 == 0) (d * 2).let { if (it > 9) it - 9 else it } else d }.sum()
        return body.append((10 - sum % 10) % 10).toString()
    }

    private fun dir(a: Activity) = File(a.getExternalFilesDir(null), "shots").apply { mkdirs() }

    private fun copy(a: Activity, done: (Bitmap?) -> Unit) {
        val v = a.window.decorView
        if (v.width == 0 || v.height == 0) return done(null)
        val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        PixelCopy.request(a.window, bmp, { r -> done(if (r == PixelCopy.SUCCESS) bmp else null) }, main)
    }

    private fun single(a: Activity, name: String) = copy(a) { bmp ->
        bmp ?: return@copy
        io.execute {
            // 先写临时文件再改名：外面轮询到 .png 时文件一定是完整的
            val tmp = File(dir(a), "$name.part")
            tmp.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            tmp.renameTo(File(dir(a), "$name.png"))
            bmp.recycle()
        }
    }

    /** 连拍：每一帧发一次 PixelCopy，文件名带上距开始的毫秒数，拼动图时按真实时间排帧 */
    private fun burst(a: Activity, name: String, durationMs: Int, width: Int) {
        val start = SystemClock.uptimeMillis()
        var index = 0
        var busy = false
        val cb = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                val t = SystemClock.uptimeMillis() - start
                if (t > durationMs) return
                if (!busy && io.queue.size < 4) {
                    busy = true
                    val n = index++
                    copy(a) { bmp ->
                        busy = false
                        bmp ?: return@copy
                        io.execute {
                            val h = (bmp.height * width.toFloat() / bmp.width).toInt()
                            val small = Bitmap.createScaledBitmap(bmp, width, h, true)
                            bmp.recycle()
                            File(dir(a), "%s_%04d_%05d.jpg".format(name, n, t)).outputStream().use {
                                small.compress(Bitmap.CompressFormat.JPEG, 94, it)
                            }
                            small.recycle()
                        }
                    }
                }
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
        Choreographer.getInstance().postFrameCallback(cb)
    }
}
