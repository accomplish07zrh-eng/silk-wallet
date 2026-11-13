package app.silk.wallet.ui.card

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.silk.wallet.R
import app.silk.wallet.data.Card
import app.silk.wallet.data.CardKind
import app.silk.wallet.data.rememberCardGif
import app.silk.wallet.data.rememberCardImage
import app.silk.wallet.domain.CardBrand
import app.silk.wallet.domain.CardEffect
import app.silk.wallet.domain.CardMath
import app.silk.wallet.domain.CardStylePreset
import app.silk.wallet.domain.CardStyleSeries
import app.silk.wallet.ui.fx.LocalFlowClock
import app.silk.wallet.ui.fx.LocalTilt
import app.silk.wallet.ui.fx.flowingGradient
import app.silk.wallet.ui.fx.rememberFlowClock
import kotlin.math.roundToInt

/** 卡面统一圆角：卡片、阴影、扫光都按它裁 */
val CardShape = RoundedCornerShape(22.dp)

/** 卡面标准排版尺寸：缩略显示时按此尺寸排版再整体缩放，字号比例永远一致 */
val CardDesignWidth = 340.dp
const val CardRatio = 1.586f

/**
 * 卡面底层：着色器流动底色 / 照片 / 动图，其上叠光泽与倒角描边。
 * live = true（详情、编辑预览）时自带时钟；否则跟随页面共享时钟。
 */
@Composable
private fun Modifier.cardSurface(card: Card, style: CardStylePreset, back: Boolean, live: Boolean): Modifier {
    val photo = rememberCardImage(card.id)
    val gif = if (photo == null) rememberCardGif(card.id) else null
    val clock = if (live) rememberFlowClock() else LocalFlowClock.current
    val tilt = LocalTilt.current
    val light = style.darkText && photo == null && gif == null
    val texture = if (photo == null && gif == null) style.texture?.let { rememberTexture(it) } else null
    val colors = if (back) style.colors.reversed() else style.colors
    val paintedMyth = style.series == CardStyleSeries.SILICON_MYTHS && texture != null
    return this
        .clip(CardShape)
        .then(if (paintedMyth) Modifier else Modifier.laminate(tilt, light))
        .then(
            when {
                photo != null -> Modifier.flowingGradient(colors, false, clock, tilt, photo = photo).photoScrim(back)
                gif != null -> Modifier.gifLayer(gif, clock).photoScrim(back)
                paintedMyth -> if (style.effect == CardEffect.WHALE) {
                    Modifier.whaleSurface(texture, clock, tilt, back)
                } else {
                    Modifier.mythSurface(texture, style.effect, clock, tilt, back)
                }
                else -> Modifier.flowingGradient(colors, style.darkText, clock, tilt, style.effect, photo = texture)
            }
        )
        .gloss(light)
        .border(1.dp, edgeBrush(light), CardShape)
}

/** 皮肤贴图与缩略图分别解码、缓存，选择器滚动时无需加载全尺寸贴图。 */
private object TextureCache {
    private class BitmapCache(maxBytes: Int) : LruCache<Int, ImageBitmap>(maxBytes) {
        override fun sizeOf(key: Int, value: ImageBitmap): Int = value.width * value.height * 4
    }

    private val textures = BitmapCache(36 * 1024 * 1024)
    private val thumbnails = BitmapCache(4 * 1024 * 1024)

    fun get(res: android.content.res.Resources, id: Int, thumbnail: Boolean): ImageBitmap {
        val cache = if (thumbnail) thumbnails else textures
        return synchronized(cache) {
            cache.get(id) ?: run {
                val options = BitmapFactory.Options().apply {
                    inScaled = false
                    inSampleSize = if (thumbnail) 4 else 1
                }
                val src = BitmapFactory.decodeResource(res, id, options)
                val scale = 1280f / maxOf(src.width, src.height)
                val bitmap = if (scale < 1f) {
                    Bitmap.createScaledBitmap(src, (src.width * scale).toInt(), (src.height * scale).toInt(), true)
                } else src
                bitmap.asImageBitmap().also { cache.put(id, it) }
            }
        }
    }
}

@Composable
internal fun rememberTexture(id: Int, thumbnail: Boolean = false): ImageBitmap {
    val res = LocalContext.current.resources
    return remember(res, id, thumbnail) { TextureCache.get(res, id, thumbnail) }
}

/** 有照片 / 动图时文字一律白色 */
@Composable
private fun inkFor(card: Card, style: CardStylePreset): Color {
    val hasPhoto = rememberCardImage(card.id) != null || rememberCardGif(card.id) != null
    return if (style.darkText && !hasPhoto) Color(0xE0151518) else Color.White
}

private fun edgeBrush(light: Boolean) = Brush.verticalGradient(
    listOf(
        Color.White.copy(alpha = if (light) 0.75f else 0.34f),
        Color.White.copy(alpha = 0.04f),
        Color.White.copy(alpha = if (light) 0.3f else 0.12f),
    )
)

/** 左上一团柔光 + 底部轻压暗，给平面渐变一点厚度 */
private fun Modifier.gloss(light: Boolean) = drawWithCache {
    val highlight = Brush.radialGradient(
        listOf(Color.White.copy(alpha = if (light) 0.26f else 0.14f), Color.Transparent),
        center = Offset(size.width * 0.15f, -size.height * 0.2f),
        radius = size.width * 0.95f,
    )
    val bottom = Brush.verticalGradient(
        0.55f to Color.Transparent,
        1f to Color.Black.copy(alpha = if (light) 0.05f else 0.16f),
    )
    onDrawBehind {
        drawRect(highlight)
        drawRect(bottom)
    }
}

/**
 * 覆膜反光：一条斜向柔光带压在整张卡（连同文字、芯片）之上，随手机倾斜滑过卡面，
 * 像实体卡表面的覆膜在灯下反光；静止时停在左上角，只留一抹。
 */
private fun Modifier.laminate(tilt: State<Offset>, light: Boolean) = drawWithContent {
    drawContent()
    val t = tilt.value
    val w = size.width
    val s = 0.2f + t.x * 0.85f - t.y * 0.45f
    val p = Offset(w * s, size.height * 0.5f)
    val dir = Offset(0.876f, 0.482f) * (w * 0.36f)
    val a = if (light) 0.26f else 0.15f
    drawRect(
        Brush.linearGradient(
            0f to Color.Transparent,
            0.38f to Color.White.copy(alpha = a * 0.25f),
            0.5f to Color.White.copy(alpha = a),
            0.62f to Color.White.copy(alpha = a * 0.25f),
            1f to Color.Transparent,
            start = p - dir,
            end = p + dir,
        )
    )
}

/** 照片上的暗化层：上下两端加深托住白字，背面整体再暗一些 */
private fun Modifier.photoScrim(back: Boolean) = drawBehind {
    drawRect(Color.Black.copy(alpha = if (back) 0.3f else 0.16f))
    drawRect(
        Brush.verticalGradient(
            0f to Color.Black.copy(alpha = if (back) 0.45f else 0.42f),
            0.34f to Color.Black.copy(alpha = if (back) 0.2f else 0.04f),
            0.56f to Color.Black.copy(alpha = if (back) 0.2f else 0.1f),
            1f to Color.Black.copy(alpha = if (back) 0.5f else 0.55f),
        )
    )
}

/** Keep the white wordmark legible as bright parts of the illustration pass behind it. */
private fun Modifier.visaCornerShade() = drawWithCache {
    val ink = Color(0xFF08101D)
    val shade = Brush.radialGradient(
        0f to ink.copy(alpha = 0.54f),
        0.48f to ink.copy(alpha = 0.42f),
        1f to Color.Transparent,
        center = Offset(size.width - 37.dp.toPx(), 32.dp.toPx()),
        radius = 56.dp.toPx(),
    )
    onDrawBehind { drawRect(shade) }
}

/** 动图按卡面比例居中裁切；读时钟让每帧重绘，动图逐帧推进 */
private fun Modifier.gifLayer(gif: Drawable, clock: State<Float>?) = drawBehind {
    clock?.value
    val iw = gif.intrinsicWidth.coerceAtLeast(1)
    val ih = gif.intrinsicHeight.coerceAtLeast(1)
    val s = maxOf(size.width / iw, size.height / ih)
    val l = (size.width - iw * s) / 2f
    val t = (size.height - ih * s) / 2f
    gif.setBounds(l.roundToInt(), t.roundToInt(), (l + iw * s).roundToInt(), (t + ih * s).roundToInt())
    drawIntoCanvas { gif.draw(it.nativeCanvas) }
}

/** 银箔：上亮、中线一道冷灰折痕、下沿回亮，像烫印的金属字 */
private val SilverFoil = Brush.verticalGradient(
    0f to Color.White,
    0.46f to Color(0xFFF3F5F9),
    0.56f to Color(0xFFC9CFDA),
    1f to Color(0xFFF4F6FA),
)

/** 凸印字：深色卡面用银箔 + 下投暗影，浅色卡面用深墨 + 下沿提亮 */
private fun emboss(ink: Color) = if (ink == Color.White) {
    TextStyle(brush = SilverFoil, shadow = Shadow(Color.Black.copy(alpha = 0.4f), Offset(0f, 2f), 3f))
} else {
    TextStyle(color = ink, shadow = Shadow(Color.White.copy(alpha = 0.7f), Offset(0f, 1.5f), 1.5f))
}

@Composable
fun CardFront(
    card: Card,
    style: CardStylePreset,
    masked: Boolean,
    modifier: Modifier = Modifier,
    live: Boolean = false,
    badge: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val ink = inkFor(card, style)
    val brand = CardBrand.of(card.brand)
    val visa = brand == CardBrand.VISA
    val cornerShade = if (visa && style.series == CardStyleSeries.SILICON_MYTHS) {
        Modifier.visaCornerShade()
    } else Modifier
    Box(
        modifier.cardSurface(card, style, back = false, live = live)
            .then(cornerShade)
            .padding(horizontal = 22.dp, vertical = 20.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    card.bank.ifBlank { "银行卡" },
                    style = emboss(ink),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.6.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (badge != null) {
                    badge()
                    Spacer(Modifier.width(8.dp))
                }
                if (!visa) BrandMark(brand, ink)
                if (trailing != null) {
                    if (!visa && brand != CardBrand.UNKNOWN) Spacer(Modifier.width(10.dp))
                    trailing()
                    if (visa) Spacer(Modifier.width(10.dp))
                }
                if (visa) BrandMark(brand, ink, Modifier.offset(x = 5.dp))
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Chip()
                Spacer(Modifier.width(12.dp))
                Contactless(ink.copy(alpha = 0.85f))
                CardKind.of(card.kind)?.let { k ->
                    Spacer(Modifier.weight(1f))
                    Text(
                        k.label,
                        color = ink.copy(alpha = 0.9f),
                        fontSize = 10.sp,
                        letterSpacing = 1.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(ink.copy(alpha = 0.14f))
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            FittedNumber(
                if (masked) CardMath.mask(card.number) else CardMath.format(card.number, brand),
                ink,
            )
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("持卡人", color = ink.copy(alpha = 0.55f), fontSize = 9.sp, letterSpacing = 1.5.sp)
                    Text(
                        card.holder.uppercase().ifBlank { "CARDHOLDER" },
                        style = emboss(ink),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.2.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("VALID THRU", color = ink.copy(alpha = 0.55f), fontSize = 8.sp, letterSpacing = 1.8.sp)
                    Text(
                        if (masked) "••/••" else CardMath.expiryText(card.expMonth, card.expYear),
                        style = emboss(ink),
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
fun CardBack(
    card: Card,
    style: CardStylePreset,
    masked: Boolean,
    modifier: Modifier = Modifier,
    live: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val ink = inkFor(card, style)
    Box(modifier.cardSurface(card, style, back = true, live = live)) {
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(22.dp))
            Box(Modifier.fillMaxWidth().height(40.dp).background(Color(0xE6080808)))
            Spacer(Modifier.height(18.dp))
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Brush.horizontalGradient(listOf(Color(0xFFF3F2EC), Color(0xFFE6E4DA)))),
                ) {
                    Text(
                        card.holder,
                        color = Color(0xFF8A8880),
                        fontSize = 12.sp,
                        fontStyle = FontStyle.Italic,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.CenterStart).padding(start = 12.dp, end = 64.dp),
                    )
                    Text(
                        if (masked) "•••" else card.cvv?.takeIf { it.isNotBlank() } ?: "•••",
                        color = Color(0xFF16161A),
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text("CVV", color = ink.copy(alpha = 0.7f), fontSize = 9.sp, letterSpacing = 1.sp)
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Silk · 仅限持卡人本人使用", color = ink.copy(alpha = 0.5f), fontSize = 9.sp, modifier = Modifier.weight(1f))
                if (trailing != null) {
                    trailing()
                    Spacer(Modifier.width(10.dp))
                }
                BrandMark(CardBrand.of(card.brand), ink)
            }
        }
    }
}

/** 点按翻面：绕 Y 轴弹簧翻转，转过 90° 后换成背面（背面反向再转 180° 抵消镜像） */
@Composable
fun FlipCard(
    card: Card,
    style: CardStylePreset,
    masked: Boolean,
    flipped: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    live: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    val haptics = LocalHapticFeedback.current
    val angle by animateFloatAsState(
        if (flipped) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 180f),
        label = "flip",
    )
    Box(
        modifier
            .graphicsLayer {
                rotationY = angle
                cameraDistance = 16f * density
            }
            .clickable(remember { MutableInteractionSource() }, indication = null) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onTap()
            }
    ) {
        if (angle <= 90f) {
            CardFront(card, style, masked, Modifier.fillMaxSize(), live = live, trailing = trailing)
        } else {
            CardBack(card, style, masked, Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }, live = live, trailing = trailing)
        }
    }
}

/** 卡号按可用宽度自适应字号：从大到小试，第一档放得下的就用 */
@Composable
private fun FittedNumber(text: String, ink: Color) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val maxPx = constraints.maxWidth
        val sizes = listOf(21, 20, 19, 18, 17, 16, 15)
        val chosen = remember(text, maxPx) {
            sizes.firstOrNull { s ->
                measurer.measure(text, TextStyle(fontSize = s.sp, fontFamily = FontFamily.Monospace, letterSpacing = (s / 10f).sp)).size.width <= maxPx
            } ?: sizes.last()
        }
        Text(
            text,
            style = emboss(ink),
            fontSize = chosen.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            letterSpacing = (chosen / 10f).sp,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** 卡面内嵌的显示 / 隐藏切换钮 */
@Composable
fun RevealToggle(masked: Boolean, ink: Color = Color.White, onClick: () -> Unit) {
    Box(
        Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(ink.copy(alpha = 0.16f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (masked) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
            contentDescription = if (masked) "显示卡号" else "隐藏卡号",
            tint = ink,
            modifier = Modifier.size(17.dp),
        )
    }
}

/** IC 芯片：香槟金渐变 + 六块触点分区 + 倒角描边 */
@Composable
private fun Chip() {
    Canvas(Modifier.size(width = 42.dp, height = 31.dp)) {
        val r = CornerRadius(6.dp.toPx())
        drawRoundRect(
            Brush.linearGradient(listOf(Color(0xFFF2DFA8), Color(0xFFCBA65A), Color(0xFFEAD08E), Color(0xFFB58F47))),
            cornerRadius = r,
        )
        val line = Color(0x5C3E2A05)
        val sw = 1.2.dp.toPx()
        val w = size.width
        val h = size.height
        drawLine(line, Offset(0f, h * 0.36f), Offset(w * 0.33f, h * 0.36f), sw)
        drawLine(line, Offset(0f, h * 0.64f), Offset(w * 0.33f, h * 0.64f), sw)
        drawLine(line, Offset(w * 0.67f, h * 0.36f), Offset(w, h * 0.36f), sw)
        drawLine(line, Offset(w * 0.67f, h * 0.64f), Offset(w, h * 0.64f), sw)
        drawRoundRect(line, Offset(w * 0.33f, h * 0.2f), Size(w * 0.34f, h * 0.6f), CornerRadius(3.dp.toPx()), style = Stroke(sw))
        drawRoundRect(
            Brush.verticalGradient(listOf(Color(0xCCFFF6D8), Color(0x55704A10))),
            cornerRadius = r,
            style = Stroke(0.8.dp.toPx()),
        )
    }
}

/** 非接支付：三道同心弧 */
@Composable
private fun Contactless(color: Color) {
    Canvas(Modifier.size(22.dp)) {
        val stroke = Stroke(width = size.width * 0.09f, cap = StrokeCap.Round)
        val cx = size.width * 0.16f
        val cy = size.height / 2f
        for (i in 1..3) {
            val r = size.width * 0.22f * i
            drawArc(color, -48f, 96f, false, Offset(cx - r, cy - r), Size(r * 2, r * 2), style = stroke)
        }
    }
}

/** 卡组织标识，用于识别卡片所属网络。 */
@Composable
fun BrandMark(brand: CardBrand, ink: Color, modifier: Modifier = Modifier) {
    when (brand) {
        CardBrand.MASTERCARD -> Row {
            Box(Modifier.size(24.dp).clip(CircleShape).background(Color(0xEBEB001B)))
            Box(Modifier.offset(x = (-10).dp).size(24.dp).clip(CircleShape).background(Color(0xD9F79E1B)))
        }
        CardBrand.MAESTRO -> Row {
            Box(Modifier.size(22.dp).clip(CircleShape).background(Color(0xEB0099DF)))
            Box(Modifier.offset(x = (-9).dp).size(22.dp).clip(CircleShape).background(Color(0xD9ED0006)))
        }
        CardBrand.VISA -> Icon(
            // Visa 官方媒体素材字标，保留原始比例。
            painter = painterResource(R.drawable.visa_brand_mark),
            contentDescription = "Visa",
            tint = if (ink == Color.White) Color.White else Color(0xFF1434CB),
            modifier = modifier.size(width = 40.dp, height = 13.dp),
        )
        CardBrand.UNIONPAY -> UnionPayMark()
        CardBrand.AMEX -> Text(
            "AMEX",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp,
            modifier = Modifier.clip(RoundedCornerShape(3.dp)).background(Color(0xFF2E77BC)).padding(horizontal = 6.dp, vertical = 3.dp),
        )
        CardBrand.JCB -> Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            listOf(Color(0xFF0E4C96), Color(0xFFE21836), Color(0xFF007B40)).zip(listOf("J", "C", "B")).forEach { (c, l) ->
                Text(l, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black,
                    modifier = Modifier.clip(RoundedCornerShape(3.dp)).background(c).padding(horizontal = 4.dp, vertical = 1.dp))
            }
        }
        CardBrand.DISCOVER -> Text("DISCOVER", color = ink, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        CardBrand.DINERS -> Text("Diners Club", color = ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        CardBrand.MIR -> Text("МИР", color = ink, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic)
        CardBrand.UNKNOWN -> Unit
    }
}

/** 银联：红、蓝、青三块向右倾斜的圆角平行四边形叠放，上压白字 */
@Composable
private fun UnionPayMark() {
    Box(Modifier.size(width = 58.dp, height = 37.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            val r = 3.dp.toPx()
            val slant = h * 0.2f
            val bw = w * 0.44f
            val step = (w - bw - slant) / 2f
            listOf(Color(0xFFE21836), Color(0xFF00447C), Color(0xFF007B84)).forEachIndexed { i, c ->
                val x = i * step
                val p = Path().apply {
                    moveTo(x + slant + r, r)
                    lineTo(x + slant + bw - r, r)
                    lineTo(x + bw - r, h - r)
                    lineTo(x + r, h - r)
                    close()
                }
                drawPath(p, c)
                drawPath(p, c, style = Stroke(width = r * 2, join = StrokeJoin.Round))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("银联", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, lineHeight = 13.sp)
            Text("UnionPay", color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Bold, lineHeight = 8.sp)
        }
    }
}
