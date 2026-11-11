package app.silk.wallet.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.silk.wallet.ui.fx.LocalMotionEnabled
import app.silk.wallet.ui.fx.LocalTilt
import app.silk.wallet.ui.theme.LocalDarkTheme
import app.silk.wallet.ui.theme.enterRise
import app.silk.wallet.ui.theme.glassSurface
import app.silk.wallet.ui.theme.pressScale
import app.silk.wallet.ui.theme.WordmarkFont
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 页头随列表滚动的状态：
 * - collapse：0 = 大标题，1 = 紧凑标题。上滑先收起页头再滚列表；回到顶部继续下拉才展开；
 * - stretch：已在顶部还往下拉时的橡皮筋位移（像素），松手弹回；拉过一半触发一次字标扫光。
 */
@Stable
class HeaderMotion(
    private val scope: CoroutineScope,
    private val collapseRangePx: Float,
    val maxStretchPx: Float,
) {
    var collapse by mutableFloatStateOf(0f)
        private set
    var stretch by mutableFloatStateOf(0f)
        private set
    /** 每加一次触发一次字标扫光、星星闪一下 */
    var sweepTick by mutableIntStateOf(0)
        private set

    private var stretchJob: Job? = null
    private var collapseJob: Job? = null
    private var armed = false

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val dy = available.y
            if (source == NestedScrollSource.UserInput) {
                stretchJob?.cancel()
                collapseJob?.cancel()
            }
            if (dy >= 0f) return Offset.Zero
            var used = 0f
            if (stretch > 0f) {
                val u = min(stretch, -dy)
                stretch -= u
                used += u
            }
            val rest = -dy - used
            if (rest > 0f && collapse < 1f) {
                val c = min(1f - collapse, rest / collapseRangePx)
                collapse += c
                used += c * collapseRangePx
            }
            return Offset(0f, -used)
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            val dy = available.y
            if (dy <= 0f) return Offset.Zero
            var used = 0f
            if (collapse > 0f) {
                val c = min(collapse, dy / collapseRangePx)
                collapse -= c
                used += c * collapseRangePx
            }
            val rest = dy - used
            if (rest > 0f && source == NestedScrollSource.UserInput) {
                // 越拉越沉：阻尼随位移增大
                stretch = (stretch + rest * 0.55f * (1f - stretch / maxStretchPx)).coerceIn(0f, maxStretchPx)
                if (!armed && stretch > maxStretchPx * 0.5f) {
                    armed = true
                    sweepTick++
                }
                used = dy
            }
            return Offset(0f, used)
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            springBack()
            return Velocity.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            snapCollapse()
            return Velocity.Zero
        }
    }

    private fun springBack() {
        if (stretch <= 0f) {
            armed = false
            return
        }
        stretchJob?.cancel()
        stretchJob = scope.launch {
            animate(stretch, 0f, animationSpec = spring(dampingRatio = 0.62f, stiffness = 320f)) { v, _ -> stretch = v }
            armed = false
        }
    }

    /** 停在中间时就近吸附到展开 / 收起 */
    private fun snapCollapse() {
        if (collapse <= 0.001f || collapse >= 0.999f) return
        animateCollapse(if (collapse > 0.5f) 1f else 0f)
    }

    fun expand() = animateCollapse(0f)

    private fun animateCollapse(target: Float) {
        collapseJob?.cancel()
        collapseJob = scope.launch {
            animate(collapse, target, animationSpec = spring(dampingRatio = 0.86f, stiffness = 420f)) { v, _ -> collapse = v }
        }
    }
}

@Composable
fun rememberHeaderMotion(): HeaderMotion {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    return remember(density) {
        HeaderMotion(scope, with(density) { 96.dp.toPx() }, with(density) { 150.dp.toPx() })
    }
}

/** 首页页头：箔金光泽字标 + 翻动副标题 + 三枚带小动效的玻璃圆钮，随滚动收成紧凑标题 */
@Composable
fun HomeHeader(
    subtitle: String,
    play: Boolean,
    motion: HeaderMotion,
    onDraw: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
) {
    val dark = LocalDarkTheme.current
    val hairline = if (dark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f)
    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                val c = motion.collapse
                if (c > 0.01f) {
                    drawLine(
                        hairline.copy(alpha = hairline.alpha * c),
                        Offset(0f, size.height - 0.5f),
                        Offset(size.width, size.height - 0.5f),
                        strokeWidth = 1f,
                    )
                }
            }
            .graphicsLayer { translationY = motion.stretch * 0.32f }
            .statusBarsPadding()
            .layout { m, cons ->
                // 收起时上下留白一起收紧
                val c = motion.collapse
                val top = (14.dp.toPx() - 6.dp.toPx() * c).roundToInt()
                val bottom = (14.dp.toPx() - 6.dp.toPx() * c).roundToInt()
                val inner = if (cons.hasBoundedHeight) cons.copy(
                    minHeight = 0,
                    maxHeight = (cons.maxHeight - top - bottom).coerceAtLeast(0),
                ) else cons.copy(minHeight = 0)
                val p = m.measure(inner)
                layout(p.width, p.height + top + bottom) { p.place(0, top) }
            }
            .padding(start = 22.dp, end = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Wordmark(play, motion)
            // 数字变化时副标题上下翻动；收起时向上淡出、让出高度
            AnimatedContent(
                targetState = subtitle,
                transitionSpec = {
                    val up = spring<IntOffset>(dampingRatio = 0.8f, stiffness = 420f)
                    (slideInVertically(up) { it / 2 } + fadeIn(tween(180))) togetherWith
                        (slideOutVertically(up) { -it / 2 } + fadeOut(tween(120)))
                },
                modifier = Modifier
                    .enterRise(1, play)
                    .layout { m, cons ->
                        val p = m.measure(cons)
                        val h = (p.height * (1f - motion.collapse)).roundToInt()
                        layout(p.width, h) {
                            p.placeWithLayer(0, h - p.height) {
                                alpha = (1f - motion.collapse * 1.8f).coerceIn(0f, 1f)
                            }
                        }
                    },
                label = "subtitle",
            ) { text ->
                Text(
                    text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        HeaderButton(Icons.Rounded.Style, "抽卡", IconFx.Twinkle, motion, onDraw, Modifier.enterRise(1, play))
        Spacer(Modifier.width(10.dp))
        HeaderButton(Icons.Rounded.Search, "搜索", IconFx.Peek, motion, onSearch, Modifier.enterRise(2, play))
        Spacer(Modifier.width(10.dp))
        HeaderButton(Icons.Outlined.Settings, "设置", IconFx.Spin, motion, onSettings, Modifier.enterRise(3, play))
    }
}

/**
 * 「Silk」字标：
 * - 收起时缩到 0.66 倍，下拉时放大（以左侧为原点）；
 * - 字面盖一层箔金光泽（SrcAtop 只着色在字形上）：一道淡香槟光随手机左右倾斜游走，
 *   每隔一阵整道金光从左扫到右，下拉过半也会扫一次。
 */
@Composable
private fun Wordmark(play: Boolean, motion: HeaderMotion) {
    val tilt = LocalTilt.current
    val motionOn = LocalMotionEnabled.current
    val dark = LocalDarkTheme.current
    val sweep = remember { Animatable(1f) }
    LaunchedEffect(motionOn) {
        if (!motionOn) return@LaunchedEffect
        delay(2600)
        while (true) {
            sweep.snapTo(0f)
            sweep.animateTo(1f, tween(1500, easing = FastOutSlowInEasing))
            delay(8000)
        }
    }
    LaunchedEffect(motion.sweepTick) {
        if (motion.sweepTick == 0) return@LaunchedEffect
        sweep.snapTo(0f)
        sweep.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
    }
    val gold = Color(0xFFD4A24C)
    val pale = if (dark) Color(0xFFFFF4D6) else Color(0xFFF6DFA4)
    val champagne = if (dark) Color(0x44FFE9B8) else Color(0x24C9A15A)

    Text(
        "Silk",
        fontFamily = WordmarkFont,
        fontStyle = FontStyle.Italic,
        fontWeight = FontWeight.Bold,
        fontSize = 44.sp,
        letterSpacing = 0.2.sp,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier
            .enterRise(0, play)
            .layout { m, cons ->
                val p = m.measure(cons)
                val s = 1f - 0.34f * motion.collapse
                val h = (p.height * s).roundToInt()
                layout(p.width, h) {
                    p.placeWithLayer(0, (h - p.height) / 2) {
                        val pull = motion.stretch / motion.maxStretchPx
                        val k = s * (1f + 0.2f * pull)
                        scaleX = k
                        scaleY = k
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    }
                }
            }
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val w = size.width
                // 倾斜游走的淡光
                val tx = w * (0.5f + tilt.value.x * 0.55f)
                drawRect(
                    Brush.linearGradient(
                        0f to Color.Transparent,
                        0.5f to champagne,
                        1f to Color.Transparent,
                        start = Offset(tx - w * 0.35f, 0f),
                        end = Offset(tx + w * 0.35f, size.height),
                    ),
                    blendMode = BlendMode.SrcAtop,
                )
                // 周期扫过的金光
                val p = sweep.value
                if (p < 1f) {
                    val cx = -w * 0.4f + w * 1.8f * p
                    val bw = w * 0.32f
                    drawRect(
                        Brush.linearGradient(
                            0f to Color.Transparent,
                            0.35f to gold,
                            0.5f to pale,
                            0.65f to gold,
                            1f to Color.Transparent,
                            start = Offset(cx - bw, 0f),
                            end = Offset(cx + bw, size.height),
                        ),
                        blendMode = BlendMode.SrcAtop,
                    )
                }
            },
    )
}

private enum class IconFx { Twinkle, Peek, Spin }

/**
 * 页头玻璃圆钮，各有一个小动效：
 * - 抽卡（叠放的卡片图标）：偶尔一闪（放大 + 轻转），按下时转一点；
 * - 搜索：按下时放大镜歪头凑近；
 * - 设置：每点一次齿轮弹性转 90°。
 */
@Composable
private fun HeaderButton(
    icon: ImageVector,
    contentDescription: String,
    fx: IconFx,
    motion: HeaderMotion,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(
        if (pressed) 1f else 0f,
        spring(dampingRatio = 0.45f, stiffness = 520f),
        label = "iconPress",
    )
    val spin = remember { Animatable(0f) }
    val twinkle = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val motionOn = LocalMotionEnabled.current
    if (fx == IconFx.Twinkle) {
        LaunchedEffect(motionOn) {
            if (!motionOn) return@LaunchedEffect
            delay(4200)
            while (true) {
                twinkle.snapTo(0f)
                twinkle.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
                delay(7000)
            }
        }
        LaunchedEffect(motion.sweepTick) {
            if (motion.sweepTick == 0) return@LaunchedEffect
            twinkle.snapTo(0f)
            twinkle.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        }
    }
    Box(
        modifier
            .graphicsLayer {
                val s = 1f - 0.1f * motion.collapse
                scaleX = s
                scaleY = s
            }
            .pressScale(interaction, 0.88f)
            .size(46.dp)
            .glassSurface(null, CircleShape)
            .clickable(interactionSource = interaction, indication = null) {
                if (fx == IconFx.Spin) scope.launch {
                    spin.animateTo(spin.targetValue + 1f, spring(dampingRatio = 0.5f, stiffness = 240f))
                }
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .size(22.dp)
                .graphicsLayer {
                    when (fx) {
                        IconFx.Twinkle -> {
                            val bump = sin(twinkle.value * PI).toFloat()
                            val s = 1f + 0.24f * bump + 0.06f * press
                            scaleX = s
                            scaleY = s
                            rotationZ = 22f * bump + 18f * press
                        }
                        IconFx.Peek -> {
                            val s = 1f + 0.12f * press
                            scaleX = s
                            scaleY = s
                            rotationZ = -16f * press
                            translationX = -1.5.dp.toPx() * press
                            translationY = -1.5.dp.toPx() * press
                        }
                        IconFx.Spin -> rotationZ = 90f * spin.value + 20f * press
                    }
                },
        )
    }
}
