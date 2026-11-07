package app.silk.wallet.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 卡面背景图：按卡片 id 存在应用私有目录 files/card_bg/<id>.jpg（静态图）或 <id>.gif（动图），
 * 不进数据库、不进备份。静态图导入时按卡面比例（1.586:1）居中裁切、缩到 1200px 宽再存；
 * 动图原样保存，绘制时再居中裁切。
 * version 每次增删自增，界面据此刷新缓存。
 */
object CardMedia {

    private const val CARD_RATIO = 1.586f
    private const val OUT_WIDTH = 1200

    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    // 解码后的位图缓存：键带上版本号，换图后旧缓存自然失效
    private val cache = object : LruCache<String, ImageBitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    private fun file(context: Context, cardId: Long) =
        File(File(context.filesDir, "card_bg"), "$cardId.jpg")

    private fun gifFile(context: Context, cardId: Long) =
        File(File(context.filesDir, "card_bg"), "$cardId.gif")

    private const val MAX_GIF_BYTES = 20L * 1024 * 1024

    fun has(context: Context, cardId: Long) =
        cardId > 0 && (file(context, cardId).exists() || gifFile(context, cardId).exists())

    /** 动图另存：原样拷贝（限 20MB），换图时删掉另一种格式的旧文件 */
    private fun importGif(context: Context, cardId: Long, uri: Uri): Boolean {
        val target = gifFile(context, cardId)
        target.parentFile?.mkdirs()
        val ok = context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > MAX_GIF_BYTES) return@use false
                    out.write(buf, 0, n)
                }
                true
            }
        } ?: false
        if (!ok) {
            target.delete()
            return false
        }
        file(context, cardId).delete()
        return true
    }

    internal fun gifPath(context: Context, cardId: Long): File? =
        gifFile(context, cardId).takeIf { cardId > 0 && it.exists() }

    suspend fun import(context: Context, cardId: Long, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val isGif = context.contentResolver.getType(uri) == "image/gif"
            if (isGif && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return@runCatching importGif(context, cardId, uri).also { if (it) _version.value++ }
            }
            gifFile(context, cardId).delete()
            val src = decode(context, uri) ?: return@runCatching false
            val cropped = centerCrop(src)
            val target = file(context, cardId)
            target.parentFile?.mkdirs()
            target.outputStream().use { cropped.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            if (cropped !== src) cropped.recycle()
            src.recycle()
            _version.value++
            true
        }.getOrDefault(false)
    }

    fun remove(context: Context, cardId: Long) {
        val a = file(context, cardId).delete()
        val b = gifFile(context, cardId).delete()
        if (a || b) _version.value++
    }

    /** 只查内存缓存，不碰磁盘：组合阶段同步取用，未命中再由协程到 IO 线程解码 */
    internal fun cached(cardId: Long, version: Int): ImageBitmap? = cache.get("$cardId@$version")

    internal fun load(context: Context, cardId: Long, version: Int): ImageBitmap? {
        val key = "$cardId@$version"
        cache.get(key)?.let { return it }
        val f = file(context, cardId)
        if (!f.exists()) return null
        val bmp = BitmapFactory.decodeFile(f.path)?.asImageBitmap() ?: return null
        cache.put(key, bmp)
        return bmp
    }

    /** 先按目标尺寸降采样解码，避免原图几千万像素直接进内存 */
    private fun decode(context: Context, uri: Uri): Bitmap? {
        val maxSide = OUT_WIDTH * 2
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val longest = max(info.size.width, info.size.height)
                if (longest > maxSide) {
                    val s = maxSide.toFloat() / longest
                    decoder.setTargetSize((info.size.width * s).roundToInt(), (info.size.height * s).roundToInt())
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        }
    }

    private fun centerCrop(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val (cw, ch) = if (w.toFloat() / h > CARD_RATIO) {
            (h * CARD_RATIO).roundToInt() to h
        } else {
            w to (w / CARD_RATIO).roundToInt()
        }
        val x = (w - cw) / 2
        val y = (h - ch) / 2
        val crop = Bitmap.createBitmap(src, x, y, cw, ch)
        val outH = (OUT_WIDTH / CARD_RATIO).roundToInt()
        return if (cw > OUT_WIDTH) {
            Bitmap.createScaledBitmap(crop, OUT_WIDTH, outH, true).also { if (it !== crop) crop.recycle() }
        } else {
            crop
        }
    }
}

/** 读取某张卡的背景图（无图返回 null）；增删图片后自动刷新 */
@Composable
fun rememberCardImage(cardId: Long): ImageBitmap? {
    if (cardId <= 0) return null
    val context = LocalContext.current
    val version by CardMedia.version.collectAsState()
    var image by remember(cardId, version) { mutableStateOf(CardMedia.cached(cardId, version)) }
    LaunchedEffect(cardId, version) {
        if (image == null) image = withContext(Dispatchers.IO) { CardMedia.load(context, cardId, version) }
    }
    return image
}

/** 读取某张卡的动图背景（API 28+，无动图返回 null）；组件离开时停止播放 */
@Composable
fun rememberCardGif(cardId: Long): android.graphics.drawable.Drawable? {
    if (cardId <= 0 || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
    val context = LocalContext.current
    val version by CardMedia.version.collectAsState()
    var drawable by remember(cardId, version) { mutableStateOf<android.graphics.drawable.Drawable?>(null) }
    LaunchedEffect(cardId, version) {
        drawable = withContext(Dispatchers.IO) {
            CardMedia.gifPath(context, cardId)?.let { f ->
                runCatching { ImageDecoder.decodeDrawable(ImageDecoder.createSource(f)) }.getOrNull()
            }
        }
        (drawable as? android.graphics.drawable.AnimatedImageDrawable)?.apply {
            repeatCount = android.graphics.drawable.AnimatedImageDrawable.REPEAT_INFINITE
            start()
        }
    }
    androidx.compose.runtime.DisposableEffect(drawable) {
        onDispose { (drawable as? android.graphics.drawable.AnimatedImageDrawable)?.stop() }
    }
    return drawable
}
