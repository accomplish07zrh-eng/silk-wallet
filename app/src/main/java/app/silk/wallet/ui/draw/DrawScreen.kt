package app.silk.wallet.ui.draw

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Style
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.silk.wallet.SilkApp
import app.silk.wallet.data.Settings
import app.silk.wallet.data.Card
import app.silk.wallet.data.SkinStore
import app.silk.wallet.data.SkinDraw
import app.silk.wallet.domain.CardBrand
import app.silk.wallet.domain.CardStylePreset
import app.silk.wallet.domain.CardStyles
import app.silk.wallet.domain.CardMath
import app.silk.wallet.domain.Rarity
import app.silk.wallet.ui.fx.LocalTilt
import app.silk.wallet.ui.fx.LocalFlowClock
import app.silk.wallet.ui.fx.flowingGradient
import app.silk.wallet.ui.fx.rememberFlowClock
import app.silk.wallet.ui.card.CardBack
import app.silk.wallet.ui.card.CardFront
import app.silk.wallet.ui.card.CardShape
import app.silk.wallet.ui.theme.GlassCircleButton
import app.silk.wallet.ui.theme.TitleSerif
import app.silk.wallet.ui.theme.WordmarkFont
import app.silk.wallet.ui.theme.springClickable
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.random.Random

private enum class DrawMode(val label: String) { CARD("今日用卡"), SKIN("抽皮肤") }
private enum class Phase { IDLE, SPINNING, REVEAL }

/** 轮盘上的一格：要么是用户自己的卡，要么是一张盖着的神秘皮肤卡 */
private sealed interface DeckItem {
    data class Real(val card: Card, val preset: CardStylePreset) : DeckItem
    data object Mystery : DeckItem
}

private val Ink = Color(0xFF111114)

/** 白底舞台上的光色：比稀有度原色更饱和，光束、冲击波、粒子在白底上才压得住 */
private fun vivid(rarity: Rarity?): Color = when (rarity) {
    Rarity.RARE -> Color(0xFF2F7BFF)
    Rarity.EPIC -> Color(0xFF8B45FF)
    Rarity.LEGENDARY -> Color(0xFFF2A100)
    else -> Color(0xFFE39B12)
}

/**
 * 抽卡舞台（白色）：
 * - 横向 3D 轮盘：正中最大，两侧沿弧线后退并朝中心偏转，越远越小越淡；
 * - 点「抽」：先往回蓄力，再猛转三圈以上、长减速落到预先抽好的那一格；
 *   每过一张一次“咔哒”触感，高速时卡片横向拉伸、舞台铺满光痕，临停时中心背光越聚越亮；
 * - 揭晓：其余卡向两侧滑出淡去，中心卡腾空 360° 翻转；两道冲击波、细密光束、
 *   四角星芒与粒子火花、镜头轻震，翻转结束一道镭射扫光掠过卡面，之后光尘缓缓上浮。
 * 所有变换只依赖两个连续的浮点数：轮盘位置 pos 与揭晓进度 reveal。
 */
@Composable
fun DrawScreen(
    app: SilkApp,
    settings: Settings,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    SkinStore.ensureLoaded(context)

    var mode by remember { mutableStateOf(DrawMode.CARD) }
    var phase by remember { mutableStateOf(Phase.IDLE) }
    var resultIndex by remember { mutableStateOf(-1) }
    var resultSkin by remember { mutableStateOf<SkinDraw?>(null) }
    var showGallery by remember { mutableStateOf(false) }

    val allCards by app.cards.observe().collectAsState(initial = emptyList())
    val usable = remember(allCards) {
        allCards.filter {
            !it.archived && CardMath.expiry(it.expMonth, it.expYear, 0) != CardMath.Expiry.EXPIRED
        }
    }
    // 轮盘至少 7 格才转得开：卡少时循环铺满
    val deck: List<DeckItem> = remember(mode, usable) {
        when (mode) {
            DrawMode.SKIN -> List(9) { DeckItem.Mystery }
            DrawMode.CARD -> if (usable.isEmpty()) emptyList() else {
                val items = usable.map {
                    DeckItem.Real(it, CardStyles.resolve(it.styleId, it.bankCode, CardBrand.of(it.brand)))
                }
                val reps = ((7 + items.size - 1) / items.size).coerceAtLeast(1)
                List(reps) { items }.flatten()
            }
        }
    }

    val pos = remember { Animatable(0f) }
    val reveal = remember { Animatable(0f) }
    val speed = remember { mutableFloatStateOf(0f) }

    // 速度估计（驱动拉伸、光痕、临停蓄光）+ 每过一格的触感
    LaunchedEffect(Unit) {
        var lastPos = pos.value
        var lastT = 0L
        while (true) {
            withFrameNanos { t ->
                if (lastT != 0L) {
                    val dt = (t - lastT) / 1e9f
                    if (dt > 0f) speed.floatValue = speed.floatValue * 0.6f + abs(pos.value - lastPos) / dt * 0.4f
                }
                lastPos = pos.value
                lastT = t
            }
        }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { floor(pos.value + 0.5f).toInt() }
            .distinctUntilChanged()
            .collect { if (phase == Phase.SPINNING) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
    }

    fun switchMode(m: DrawMode) {
        if (phase == Phase.SPINNING || m == mode) return
        mode = m
        phase = Phase.IDLE
        resultIndex = -1
        resultSkin = null
        scope.launch {
            reveal.snapTo(0f)
            pos.snapTo(0f)
        }
    }

    val canDraw = deck.isNotEmpty() && phase != Phase.SPINNING &&
        (mode == DrawMode.CARD || SkinStore.canDrawSkin(context))

    fun spin() {
        if (!canDraw) return
        val n = deck.size
        // 先决定结果，再让轮盘“恰好”停在那一格
        val target = when (mode) {
            DrawMode.SKIN -> {
                resultSkin = SkinStore.drawSkin(context)
                Random.nextInt(n)
            }
            DrawMode.CARD -> {
                val today = SkinStore.todaysCard(context)
                val weights = deck.map { item -> if ((item as DeckItem.Real).card.id == today) 0.35f else 1f }
                var r = Random.nextFloat() * weights.sum()
                var idx = 0
                while (idx < n - 1 && r >= weights[idx]) { r -= weights[idx]; idx++ }
                idx
            }
        }
        scope.launch {
            // 先把上一轮的结果收回轮盘，再开转
            if (reveal.value > 0f) reveal.animateTo(0f, tween(260, easing = FastOutSlowInEasing))
            phase = Phase.SPINNING
            resultIndex = target
            val start = pos.value
            val baseIdx = floor(start + 0.5f).toInt()
            val cur = ((baseIdx % n) + n) % n
            val ahead = ((target - cur) % n + n) % n
            val end = (baseIdx + n * 3 + ahead).toFloat().coerceAtLeast(start + n * 3f)
            pos.animateTo(start - 0.35f, tween(280, easing = FastOutSlowInEasing))
            pos.animateTo(end, tween(4200, easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)))
            phase = Phase.REVEAL
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            if (mode == DrawMode.CARD) {
                (deck[target] as? DeckItem.Real)?.let { SkinStore.rememberCardDraw(context, it.card.id) }
            }
            reveal.animateTo(1f, tween(2400, easing = LinearEasing))
        }
    }

    BackHandler(enabled = !showGallery, onBack = onBack)

    val glow = vivid(if (mode == DrawMode.SKIN) resultSkin?.skin?.rarity else null)
    val legendary = mode == DrawMode.SKIN && resultSkin?.skin?.rarity == Rarity.LEGENDARY
    val flowClock = rememberFlowClock()

    CompositionLocalProvider(LocalFlowClock provides flowClock) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.White)
        ) {
            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    // 冲击瞬间镜头轻震，迅速衰减
                    .graphicsLayer {
                        val r = reveal.value
                        if (r in 0.001f..0.2f) {
                            val k = 1f - r / 0.2f
                            translationX = sin(r * 180f) * 2.5.dp.toPx() * k
                            translationY = cos(r * 150f) * 1.5.dp.toPx() * k
                        }
                    }
            ) {
                val density = LocalDensity.current
                val w = constraints.maxWidth.toFloat()
                val h = constraints.maxHeight.toFloat()
                val cardW = w - with(density) { 36.dp.toPx() }
                val cardH = cardW / 1.586f
                val centerY = h * 0.43f
                val center = Offset(w / 2f, centerY)

                // 背光：临停时越聚越亮（速度越慢越亮），揭晓时绽放
                Canvas(Modifier.fillMaxSize()) {
                    val sp = speed.floatValue
                    val charge = if (phase == Phase.SPINNING) (1f - (sp / 6f)).coerceIn(0f, 1f) else 0f
                    val r = reveal.value
                    val open = if (r <= 0f) 0f else if (r < 0.3f) r / 0.3f else 1f - 0.35f * ((r - 0.3f) / 0.7f)
                    val bloom = (charge * 0.45f + open * 0.75f).coerceIn(0f, 1f)
                    if (bloom > 0.01f) {
                        drawCircle(
                            Brush.radialGradient(
                                listOf(glow.copy(alpha = 0.30f * bloom), glow.copy(alpha = 0.08f * bloom), Color.Transparent),
                                center = center,
                                radius = size.width * (0.55f + 0.25f * r),
                            ),
                            radius = size.width * (0.55f + 0.25f * r),
                            center = center,
                        )
                    }
                }
                Shockwaves(progress = { reveal.value }, color = glow, center = center, modifier = Modifier.fillMaxSize())
                SpeedStreaks(speed = { speed.floatValue }, centerY = centerY, bandH = cardH * 1.3f, modifier = Modifier.fillMaxSize())

                val n = deck.size
                if (n > 0) {
                    deck.forEachIndexed { i, item ->
                        val isResult = i == resultIndex
                        CarouselSlot(
                            index = i,
                            n = n,
                            pos = { pos.value },
                            speed = { speed.floatValue },
                            reveal = { if (isResult && phase == Phase.REVEAL) reveal.value else 0f },
                            clear = { if (!isResult && resultIndex >= 0) reveal.value else 0f },
                            cardW = cardW,
                            cardH = cardH,
                            screenW = w,
                            centerY = centerY,
                            sweep = isResult,
                        ) { flipDeg ->
                            SlotFace(item, flipDeg, resultSkin?.skin.takeIf { isResult })
                        }
                    }
                } else {
                    Text(
                        "还没有可以抽的卡\n先去添加一张吧",
                        color = Ink.copy(alpha = 0.5f),
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                    )
                }

                Sparks(progress = { reveal.value }, color = glow, center = center, modifier = Modifier.fillMaxSize())
                Motes(progress = { reveal.value }, color = glow, center = center, rich = legendary, modifier = Modifier.fillMaxSize())
                with(density) {
                    Box(
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = (centerY + cardH * 0.58f).toDp())
                            .fillMaxWidth()
                    ) {
                        ResultPanel(
                            visible = phase == Phase.REVEAL && reveal.value > 0.6f,
                            mode = mode,
                            glow = glow,
                            card = (deck.getOrNull(resultIndex) as? DeckItem.Real)?.card,
                            skin = resultSkin,
                            onCopy = { card ->
                                app.clipboard.copy(context, "卡号", card.number, settings.clipboardSeconds)
                            },
                        )
                    }
                }
            }

            // 顶栏：返回 + 模式切换
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassCircleButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "返回",
                    onClick = onBack,
                    size = 42.dp,
                    iconSize = 20.dp,
                    tint = Ink,
                )
                Spacer(Modifier.weight(1f))
                ModeSwitch(mode = mode, onSelect = ::switchMode)
                Spacer(Modifier.weight(1f))
                GlassCircleButton(
                    icon = Icons.Rounded.Style,
                    contentDescription = "皮肤图鉴",
                    onClick = { showGallery = true },
                    size = 42.dp,
                    iconSize = 20.dp,
                    tint = Ink,
                )
            }

            // 底部：黑色主按钮
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                DrawButton(
                    text = when {
                        phase == Phase.SPINNING -> "· · ·"
                        mode == DrawMode.SKIN && !SkinStore.canDrawSkin(context) -> "今日已抽，明天再来"
                        phase == Phase.REVEAL -> "再抽一次"
                        mode == DrawMode.SKIN -> "抽一款皮肤"
                        else -> "抽一张"
                    },
                    enabled = canDraw,
                    glow = glow,
                    onClick = ::spin,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    if (mode == DrawMode.SKIN) "稀有 60% · 史诗 30% · 传说 10%" else "从未过期、未归档的卡里随机抽一张",
                    color = Ink.copy(alpha = 0.38f),
                    fontSize = 11.sp,
                )
            }

            AnimatedVisibility(
                visible = showGallery,
                enter = fadeIn(tween(200)) + slideInVertically(spring(dampingRatio = 0.86f, stiffness = 320f)) { it / 6 },
                exit = fadeOut(tween(160)),
            ) {
                SkinGallery(onClose = { showGallery = false })
            }
        }
    }
}

/**
 * 皮肤图鉴：按传说 / 史诗 / 稀有分组的两列网格，每张都是实时渲染的卡面。
 * 已拥有的标剩余时间（稀有为永久）；未获得的整张磨砂模糊并加锁。
 */
@Composable
private fun SkinGallery(onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val expiries by SkinStore.expiries.collectAsState()
    val now = System.currentTimeMillis()
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, end = 18.dp, top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("图鉴", fontFamily = TitleSerif, fontWeight = FontWeight.Bold, fontSize = 31.sp, color = Ink, letterSpacing = 1.5.sp)
                val owned = CardStyles.skins.count { (expiries[it.id] ?: 0L) > now }
                Text("已拥有 $owned / ${CardStyles.skins.size}", fontSize = 13.sp, color = Ink.copy(alpha = 0.5f))
            }
            GlassCircleButton(
                icon = Icons.Rounded.Close,
                contentDescription = "关闭图鉴",
                onClick = onClose,
                size = 42.dp,
                iconSize = 20.dp,
                tint = Ink,
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            listOf(Rarity.LEGENDARY, Rarity.EPIC, Rarity.RARE).forEach { rarity ->
                item(span = { GridItemSpan(2) }, key = "h_" + rarity.name) {
                    Row(Modifier.padding(start = 4.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(vivid(rarity)))
                        Spacer(Modifier.size(8.dp))
                        Text(rarity.label, fontFamily = TitleSerif, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Ink)
                        Spacer(Modifier.size(8.dp))
                        Text(
                            rarity.days?.let { "抽到后可用 $it 天 · ${rarity.weight}%" } ?: "永久 · ${rarity.weight}%",
                            fontSize = 12.sp,
                            color = Ink.copy(alpha = 0.45f),
                        )
                    }
                }
                items(CardStyles.skins.filter { it.rarity == rarity }, key = { it.id }) { skin ->
                    GalleryItem(skin, expiries[skin.id] ?: 0L, now)
                }
            }
        }
    }
}

@Composable
private fun GalleryItem(skin: CardStylePreset, expiresAt: Long, now: Long) {
    val owned = expiresAt > now
    Column {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.586f)
                .shadow(if (owned) 10.dp else 4.dp, CardShape)
                .clip(CardShape)
        ) {
            // 卡面按首页列表的原尺寸排版再整体缩小，字号比例与真卡一致
            val design = 340.dp
            val k = maxWidth / design
            Box(
                Modifier
                    .requiredSize(design, design / 1.586f)
                    .graphicsLayer {
                        scaleX = k
                        scaleY = k
                    }
                    .then(if (owned) Modifier else Modifier.blur(14.dp))
            ) {
                CardFront(skinSampleCard(skin), skin, masked = true, modifier = Modifier.fillMaxSize())
            }
            if (!owned) {
                Box(
                    Modifier
                        .matchParentSize()
                        .background(Color.White.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        androidx.compose.material3.Icon(
                            Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = Ink.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp),
                        )
                        Text("未获得", fontSize = 11.sp, color = Ink.copy(alpha = 0.6f))
                    }
                }
            }
        }
        Spacer(Modifier.size(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                skin.label,
                fontFamily = TitleSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = if (owned) Ink else Ink.copy(alpha = 0.45f),
            )
            Spacer(Modifier.weight(1f))
            if (owned) {
                Text(
                    SkinStore.remainingLabel(expiresAt, now) ?: "永久",
                    fontSize = 11.sp,
                    color = vivid(skin.rarity),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/** 轮盘中的一格：位置、旋转、缩放、明暗都由连续的 pos 计算；揭晓时其余卡被清场 */
@Composable
private fun CarouselSlot(
    index: Int,
    n: Int,
    pos: () -> Float,
    speed: () -> Float,
    reveal: () -> Float,
    clear: () -> Float,
    cardW: Float,
    cardH: Float,
    screenW: Float,
    centerY: Float,
    sweep: Boolean,
    content: @Composable (flipDeg: Float) -> Unit,
) {
    val density = LocalDensity.current
    fun dist(): Float {
        var d = index - pos()
        d -= n * floor((d + n / 2f) / n)
        return d
    }
    // 不在组合阶段按距离增删卡片：旋转中频繁进出会反复重建卡面着色器而掉帧，
    // 远处的卡只在图层里设为透明
    val d0 = dist()
    // reveal 是每次组合新建的 lambda（捕获了当时的 isResult），派生状态必须读最新的那个
    val latestReveal = rememberUpdatedState(reveal)
    val flip by remember { derivedStateOf { flipAngle(latestReveal.value()) } }
    Box(
        Modifier
            .zIndex(10f - abs(d0) + if (reveal() > 0f) 5f else 0f)
            .graphicsLayer {
                val d = dist()
                val ad = abs(d)
                val r = reveal()
                // 清场：其余卡向两侧滑出、缩小、淡去
                val c = clear().let { (it / 0.35f).coerceIn(0f, 1f) }
                val ce = c * c * (3f - 2f * c)
                val spread = cardW * 0.40f
                val x = d * spread * (1f - 0.10f * min(ad, 3f)) + sign(d) * ce * screenW * 0.65f
                translationX = screenW / 2f - cardW / 2f + x
                translationY = centerY - cardH / 2f + ad * ad * 6.dp.toPx() - r.liftCurve() * 34.dp.toPx()
                rotationY = (-d * 34f).coerceIn(-70f, 70f) + flipAngle(r)
                cameraDistance = 11f * this.density
                val stretch = (speed() / 30f).coerceIn(0f, 0.22f)
                val s = 0.74f * (1f - 0.13f * ad).coerceAtLeast(0.55f) * (1f + 0.18f * r.liftCurve()) * (1f - 0.15f * ce)
                scaleX = s * (1f + stretch)
                scaleY = s * (1f - stretch * 0.25f)
                alpha = if (ad > 3.6f) 0f else ((1f - 0.22f * ad) * (1f - ce)).coerceIn(0f, 1f)
                transformOrigin = TransformOrigin.Center
            }
            .size(with(density) { cardW.toDp() }, with(density) { cardH.toDp() })
            .shadow(if (abs(d0) < 0.5f) 30.dp else 12.dp, CardShape)
            .then(
                if (sweep) Modifier.drawWithContent {
                    drawContent()
                    // 镭射扫光：翻转结束后一道斜向光带掠过卡面
                    val r = reveal()
                    val t = ((r - 0.62f) / 0.22f)
                    if (t in 0f..1f) {
                        val x = -size.width * 0.5f + t * size.width * 2f
                        drawRect(
                            Brush.linearGradient(
                                listOf(
                                    Color.Transparent,
                                    Color(0x55FFFFFF),
                                    Color(0xCCFFFFFF),
                                    Color(0x66B9E2FF),
                                    Color(0x55FFD6F5),
                                    Color.Transparent,
                                ),
                                start = Offset(x - size.width * 0.25f, 0f),
                                end = Offset(x + size.width * 0.25f, size.height),
                            ),
                            blendMode = BlendMode.Plus,
                        )
                    }
                } else Modifier
            )
    ) {
        content(flip)
    }
}

/** 揭晓过程中的翻转角：前 60% 转满一圈，末段带一点过冲回弹 */
private fun flipAngle(r: Float): Float {
    if (r <= 0f) return 0f
    val t = (r / 0.6f).coerceIn(0f, 1f)
    val eased = 1f - (1f - t) * (1f - t) * (1f - t)
    val overshoot = sin(t * PI.toFloat()) * 0.06f
    return 360f * (eased + overshoot * (1f - t))
}

/** 腾空曲线：快速升起，翻转结束后缓缓落回一半高度 */
private fun Float.liftCurve(): Float {
    val r = this
    return when {
        r <= 0f -> 0f
        r < 0.22f -> r / 0.22f
        r < 0.62f -> 1f
        else -> 1f - 0.5f * ((r - 0.62f) / 0.38f).coerceIn(0f, 1f)
    }
}

/** 一格卡面：真卡（打码）或神秘卡背；翻到背面角度时换成对应的背面 */
@Composable
private fun SlotFace(item: DeckItem, flipDeg: Float, revealedSkin: CardStylePreset?) {
    val a = ((flipDeg % 360f) + 360f) % 360f
    val backHalf = a in 90f..270f
    when (item) {
        is DeckItem.Real -> {
            if (backHalf) {
                CardBack(
                    item.card, item.preset, masked = true,
                    modifier = Modifier.fillMaxSize().graphicsLayer { rotationY = 180f },
                )
            } else {
                CardFront(item.card, item.preset, masked = true, modifier = Modifier.fillMaxSize())
            }
        }
        DeckItem.Mystery -> {
            // 神秘卡：转过 90° 后露出抽中的皮肤
            val showSkin = revealedSkin != null && flipDeg >= 90f
            if (showSkin) {
                Box(Modifier.fillMaxSize().graphicsLayer { if (backHalf) rotationY = 180f }) {
                    CardFront(skinSampleCard(revealedSkin!!), revealedSkin, masked = true, modifier = Modifier.fillMaxSize())
                }
            } else {
                MysteryBack(Modifier.fillMaxSize().graphicsLayer { if (backHalf) rotationY = 180f })
            }
        }
    }
}

private fun skinSampleCard(skin: CardStylePreset) = Card(
    id = 0,
    holder = "SILK",
    number = "6200000000008888",
    brand = CardBrand.UNKNOWN.name,
    bank = skin.label,
    createdAt = 0,
    updatedAt = 0,
)

/** 神秘卡背：黑金流沙底 + 金色 Silk 字标 */
@Composable
private fun MysteryBack(modifier: Modifier = Modifier) {
    val tilt = LocalTilt.current
    val clock = LocalFlowClock.current
    Box(
        modifier
            .clip(CardShape)
            .flowingGradient(
                listOf(Color(0xFF26262E), Color(0xFF121218), Color(0xFF060609)),
                lightSurface = false, time = clock, tilt = tilt, effect = 3,
            )
            .border(1.dp, Brush.verticalGradient(listOf(Color(0x66FFE3A8), Color(0x10FFFFFF))), CardShape),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Silk",
                fontFamily = WordmarkFont,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.Bold,
                fontSize = 46.sp,
                style = TextStyle(
                    brush = Brush.linearGradient(listOf(Color(0xFFFFE9B0), Color(0xFFD9A441), Color(0xFFFFF4D6))),
                ),
            )
            Text("?  ?  ?", color = Color(0xFFFFE3A8).copy(alpha = 0.55f), fontSize = 13.sp, letterSpacing = 4.sp)
        }
    }
}

/** 冲击波：落定瞬间一道极细的光环轻轻扩散 */
@Composable
private fun Shockwaves(progress: () -> Float, color: Color, center: Offset, modifier: Modifier) {
    Canvas(modifier) {
        val p = progress()
        if (p <= 0f || p > 0.6f) return@Canvas
        listOf(0f).forEach { delay ->
            val t = ((p - delay) / 0.45f)
            if (t in 0f..1f) {
                val e = 1f - (1f - t) * (1f - t)
                val radius = size.width * (0.2f + 0.9f * e)
                drawCircle(
                    color.copy(alpha = 0.35f * (1f - t)),
                    radius = radius,
                    center = center,
                    style = Stroke(width = (2.5f * (1f - t) + 0.8f) * density),
                )
            }
        }
    }
}

/** 粒子：圆形火花 + 四角星芒，带重力下坠与衰减 */
@Composable
private fun Sparks(progress: () -> Float, color: Color, center: Offset, modifier: Modifier) {
    val particles = remember {
        val rnd = Random(7)
        List(70) {
            floatArrayOf(rnd.nextFloat() * 2f * PI.toFloat(), 0.3f + rnd.nextFloat() * 1.0f, rnd.nextFloat(), rnd.nextFloat())
        }
    }
    Canvas(modifier) {
        val p = progress()
        if (p <= 0.03f || p >= 1f) return@Canvas
        val t = (p - 0.03f) / 0.97f
        val reach = size.width * 0.62f
        particles.forEach { (ang, spd, seed, kind) ->
            val d = reach * spd * (1f - (1f - t) * (1f - t))
            val x = center.x + cos(ang) * d
            val y = center.y + sin(ang) * d + t * t * size.height * 0.22f
            val a = (1f - t) * (0.55f + 0.45f * seed)
            val c = if (seed > 0.7f) Color.White else color
            if (kind > 0.75f) {
                // 四角星芒：两道交叉的细光，随时间闪烁旋转
                val r = (4f + seed * 6f) * density * (1f - 0.4f * t) * (0.6f + 0.4f * sin(t * 30f + seed * 10f))
                val rot = t * 3f + seed * 6f
                for (k in 0 until 2) {
                    val ang2 = rot + k * (PI.toFloat() / 2f)
                    drawLine(
                        color.copy(alpha = a),
                        Offset(x - cos(ang2) * r, y - sin(ang2) * r),
                        Offset(x + cos(ang2) * r, y + sin(ang2) * r),
                        strokeWidth = 1.1f * density,
                    )
                }
                drawCircle(Color.White.copy(alpha = a), radius = 1.6f * density, center = Offset(x, y))
            } else {
                drawCircle(c.copy(alpha = a * 0.8f), radius = (0.8f + seed * 1.8f) * density * (1f - 0.5f * t), center = Offset(x, y))
            }
        }
    }
}

/** 光尘：揭晓后从卡片周围缓缓上浮的细小光点，传说级更多更亮 */
@Composable
private fun Motes(progress: () -> Float, color: Color, center: Offset, rich: Boolean, modifier: Modifier) {
    val motes = remember {
        val rnd = Random(11)
        List(60) { floatArrayOf(rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f, rnd.nextFloat(), rnd.nextFloat()) }
    }
    Canvas(modifier) {
        val p = progress()
        if (p < 0.3f || p >= 1f) return@Canvas
        val t = (p - 0.3f) / 0.7f
        val count = if (rich) motes.size else motes.size / 2
        for (i in 0 until count) {
            val (ox, oy, seed, ph) = motes[i]
            val x = center.x + ox * size.width * 0.95f + sin(t * 4f + ph * 9f) * 8f * density
            val y = center.y + oy * size.width * 0.7f - t * (40f + seed * 90f) * density
            val tw = 0.5f + 0.5f * sin(t * 12f + ph * 20f)
            // 浮起后在揭晓尾段逐渐淡去，动画结束时不留残点
            val fadeOut = 1f - ((t - 0.65f) / 0.35f).coerceIn(0f, 1f)
            val a = (if (t < 0.2f) t / 0.2f else 1f) * (0.25f + 0.55f * seed) * tw * fadeOut * fadeOut
            drawCircle(color.copy(alpha = a), radius = (1.2f + seed * 2.4f) * density, center = Offset(x, y))
        }
    }
}

/** 高速旋转时铺满舞台的横向光痕（白底上用淡墨色） */
@Composable
private fun SpeedStreaks(speed: () -> Float, centerY: Float, bandH: Float, modifier: Modifier) {
    val lines = remember {
        val rnd = Random(3)
        List(26) { Triple(rnd.nextFloat(), rnd.nextFloat(), rnd.nextFloat()) }
    }
    Canvas(modifier) {
        val k = (speed() / 25f).coerceIn(0f, 1f)
        if (k < 0.05f) return@Canvas
        lines.forEach { (yy, xx, ww) ->
            val y = centerY - bandH / 2f + yy * bandH
            val len = size.width * (0.25f + 0.6f * ww)
            val x = -len + (xx * (size.width + len))
            drawLine(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, Ink.copy(alpha = 0.12f * k), Color.Transparent),
                    startX = x, endX = x + len,
                ),
                start = Offset(x, y),
                end = Offset(x + len, y),
                strokeWidth = (0.6f + ww * 1.6f) * density,
            )
        }
    }
}

/** 模式切换：浅灰轨道 + 白色选中块（带投影） */
@Composable
private fun ModeSwitch(mode: DrawMode, onSelect: (DrawMode) -> Unit) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(Color(0xFFF0F0F3))
            .padding(4.dp),
    ) {
        DrawMode.entries.forEach { m ->
            val sel = m == mode
            val k by animateFloatAsState(if (sel) 1f else 0f, spring(dampingRatio = 0.8f, stiffness = 500f), label = "modeBg")
            Box(
                Modifier
                    .shadow((6 * k).dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.12f))
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = k))
                    .springClickable(pressedScale = 0.92f) { onSelect(m) }
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            ) {
                Text(
                    m.label,
                    fontSize = 14.sp,
                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (sel) Ink else Ink.copy(alpha = 0.5f),
                )
            }
        }
    }
}

/** 抽卡主按钮：黑色胶囊 + 稀有度色光晕投影 */
@Composable
private fun DrawButton(text: String, enabled: Boolean, glow: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .shadow(22.dp, CircleShape, ambientColor = glow, spotColor = glow)
            .clip(CircleShape)
            .background(Ink)
            .springClickable(enabled = enabled, pressedScale = 0.93f, onClick = onClick)
            .padding(horizontal = 48.dp, vertical = 17.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp)
    }
}

/** 揭晓结果：稀有度徽标 + 宋体标题 + 操作 */
@Composable
private fun ResultPanel(
    visible: Boolean,
    mode: DrawMode,
    glow: Color,
    card: Card?,
    skin: SkinDraw?,
    onCopy: (Card) -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(380)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 300f)) { it / 3 },
        exit = fadeOut(tween(150)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            when (mode) {
                DrawMode.CARD -> if (card != null) {
                    Text("今天就用它", color = Ink.copy(alpha = 0.45f), fontSize = 12.sp, letterSpacing = 4.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        card.bank.ifBlank { CardBrand.of(card.brand).displayName } + "  ·  " + card.number.takeLast(4),
                        fontFamily = TitleSerif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        color = Ink,
                    )
                    Spacer(Modifier.height(16.dp))
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .background(Ink.copy(alpha = 0.06f))
                            .springClickable(pressedScale = 0.92f) { onCopy(card) }
                            .padding(horizontal = 22.dp, vertical = 10.dp),
                    ) {
                        Text("复制卡号", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
                DrawMode.SKIN -> if (skin != null) {
                    val preset = skin.skin
                    Text(
                        "${preset.rarity.label} - ${preset.label}",
                        fontFamily = TitleSerif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 26.sp,
                        color = Ink,
                        letterSpacing = 2.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        when {
                            preset.rarity.days == null && skin.isNew -> "已永久收入卡面样式 · 在卡片详情里给任意卡穿上"
                            preset.rarity.days == null -> "已拥有的永久皮肤 · 这次是重复抽到"
                            skin.isNew -> "可使用 ${preset.rarity.days} 天 · 在卡片详情里给任意卡穿上"
                            else -> "已拥有 · 使用期已重新计满 ${preset.rarity.days} 天"
                        },
                        color = Ink.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}
