package app.silk.wallet.ui.home

import app.silk.wallet.ui.fx.LocalTilt
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.mutableStateOf
import app.silk.wallet.data.SortMode
import app.silk.wallet.ui.theme.LocalDarkTheme
import app.silk.wallet.ui.theme.glassStyle
import app.silk.wallet.ui.theme.pressScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

val DockHeight = 56.dp
private val DockGap = 10.dp

/**
 * 液态 Dock（仿系统相册的液态玻璃底栏）：
 *
 * - 分类胶囊 + 右侧「添加」圆钮共用一块玻璃。切到非「全部」分类时，圆钮滑向胶囊右端，
 *   两者之间用 metaball 曲线拉出一道液体“颈”，像两滴水银相融后被吸进胶囊；
 *   回到「全部」时反向从胶囊端头长出、拉丝、分离。胶囊同时平移回到视觉居中。
 * - 选中态不用色块：图标由线框渐变为实心、文字变黑，点按位置泛起一圈透镜涟漪；
 *   每次切换整块玻璃轻微弹性形变。
 * - 形状每帧由路径并集（胶囊 ∪ 圆 ∪ 液颈）生成，真模糊、描边、投影都跟着同一路径走。
 */
@Composable
fun LiquidDock(
    selected: CardFilter,
    expiringCount: Int,
    archivedCount: Int,
    onSelect: (CardFilter) -> Unit,
    onAdd: () -> Unit,
    sortMode: SortMode,
    onSortSelect: (SortMode) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val dark = LocalDarkTheme.current
    val entries = CardFilter.entries
    val showAdd = selected == CardFilter.ALL
    // 拖选手势只创建一次，回调里要读最新的选中项与回调（上次就是读了旧快照才出的 bug）
    val latestSelected by rememberUpdatedState(selected)
    val latestOnSelect by rememberUpdatedState(onSelect)

    // 0 = 圆钮完全分离，1 = 完全融入胶囊
    val merge = remember { Animatable(if (showAdd) 0f else 1f) }
    LaunchedEffect(showAdd) {
        merge.animateTo(
            if (showAdd) 0f else 1f,
            spring(dampingRatio = 0.78f, stiffness = 210f),
        )
    }

    // 选中透镜：位置以“第几项”为单位，弹簧滑动；滑得快时被拉长（速度越大越扁长）
    val lens = remember { Animatable(entries.indexOf(selected).toFloat()) }
    // 按住底栏左右拖选时透镜抬起放大
    val lift = remember { Animatable(0f) }
    var scrubbing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(selected) {
        if (!scrubbing) lens.animateTo(entries.indexOf(selected).toFloat(), spring(dampingRatio = 0.66f, stiffness = 330f))
    }
    // 排序方式变了，排序图标弹一下
    val sortBounce = remember { Animatable(1f) }
    var lastSort by remember { mutableStateOf(sortMode) }
    LaunchedEffect(sortMode) {
        if (sortMode == lastSort) return@LaunchedEffect
        lastSort = sortMode
        sortBounce.snapTo(0.55f)
        sortBounce.animateTo(1f, spring(dampingRatio = 0.32f, stiffness = 520f))
    }

    // 切换分类时整块玻璃弹一下
    val wobble = remember { Animatable(0f) }
    var lastIndex by remember { mutableIntStateOf(entries.indexOf(selected)) }
    val ripple = remember { Animatable(1f) }
    LaunchedEffect(selected) {
        val idx = entries.indexOf(selected)
        if (idx == lastIndex) return@LaunchedEffect
        val dir = if (idx > lastIndex) 1f else -1f
        lastIndex = idx
        coroutineScope {
            launch {
                wobble.snapTo(0f)
                wobble.animateTo(0f, spring(dampingRatio = 0.32f, stiffness = 380f), initialVelocity = 7f * dir)
            }
            launch {
                ripple.snapTo(0f)
                ripple.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
            }
        }
    }

    // 与页头圆钮同一种质感：近乎纯白的玻璃，靠柔和投影从白底上浮起来
    val baseStyle = glassStyle()
    val dockStyle = if (dark) baseStyle else baseStyle.copy(
        tints = listOf(dev.chrisbanes.haze.HazeTint(Color.White.copy(alpha = 0.90f))),
        fallbackTint = dev.chrisbanes.haze.HazeTint(Color.White),
    )
    val shadowWide = if (dark) Color.Black.copy(alpha = 0.45f) else Color.Black.copy(alpha = 0.10f)
    val shadowTight = if (dark) Color.Black.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.07f)
    val rimTop = if (dark) Color.White.copy(alpha = 0.30f) else Color.White
    val rimBottom = if (dark) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.35f)
    val hairline = if (dark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.10f)
    val rippleColor = MaterialTheme.colorScheme.onSurface
    val glint = if (dark) Color.White.copy(alpha = 0.55f) else Color(0xFFDDE6F5)
    val lensFill = if (dark) Color.White.copy(alpha = 0.11f) else Color.Black.copy(alpha = 0.055f)
    val lensRimTop = if (dark) Color.White.copy(alpha = 0.28f) else Color.White
    val lensRimBottom = if (dark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.06f)
    val tilt = LocalTilt.current

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(DockHeight)
    ) {
        val wPx = constraints.maxWidth.toFloat()
        val hPx = with(density) { DockHeight.toPx() }
        val gapPx = with(density) { DockGap.toPx() }
        // 胶囊恒定居中、宽度固定：圆钮融入 / 分离时分类项绝不在手指下移动
        val capsuleW = wPx - 2f * (gapPx + hPx)

        // 形状随 merge 变化：用它作为 key 重建 Shape，触发裁剪 / 阴影更新
        val m = merge.value
        val geo = dockGeometry(m, gapPx + hPx, capsuleW, hPx, gapPx)
        val shape = remember(m, capsuleW) {
            GenericShape { _, _ -> addPath(liquidPath(geo, hPx, gapPx)) }
        }
        val capsuleOffsetPx = geo.capsuleLeft

        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .graphicsLayer {
                    val wv = wobble.value
                    scaleX = 1f + 0.018f * abs(wv)
                    scaleY = 1f - 0.022f * abs(wv)
                    translationX = 3.dp.toPx() * wv
                }
                // 自绘投影：系统阴影对异形路径很弱，这里按液态路径画两层模糊阴影，
                // 一层大而淡（浮起感），一层小而实（贴地的接触阴影），随形状一起变形
                .drawBehind {
                    val path = liquidPath(geo, hPx, gapPx).asAndroidPath()
                    drawIntoCanvas { canvas ->
                        val nc = canvas.nativeCanvas
                        softShadow(nc, path, shadowWide, blur = 22.dp.toPx(), dy = 10.dp.toPx())
                        softShadow(nc, path, shadowTight, blur = 5.dp.toPx(), dy = 2.dp.toPx())
                    }
                }
                .clip(shape)
                .hazeChild(hazeState, dockStyle)
                .drawWithContent {
                    drawContent()
                    val path = liquidPath(geo, hPx, gapPx)
                    // 外圈极细暗线定轮廓，内圈顶亮底暗的折射高光（半边被裁剪，正好是内缘）
                    drawPath(path, hairline, style = Stroke(width = 1.dp.toPx()))
                    drawPath(
                        path,
                        Brush.verticalGradient(listOf(rimTop, rimBottom)),
                        style = Stroke(width = 3.dp.toPx()),
                    )
                    // 沿边游走的一点高光：随手机左右倾斜在玻璃边上滑动，像光在液面上走
                    val gx = size.width * (0.3f + tilt.value.x * 0.35f)
                    val gw = size.width * 0.22f
                    drawPath(
                        path,
                        Brush.horizontalGradient(
                            0f to Color.Transparent,
                            0.5f to glint,
                            1f to Color.Transparent,
                            startX = gx - gw,
                            endX = gx + gw,
                        ),
                        style = Stroke(width = 3.5.dp.toPx()),
                    )
                    // 透镜涟漪：从选中项中心扩散的一圈细环
                    val rp = ripple.value
                    if (rp < 1f) {
                        val itemW = (capsuleW - 12.dp.toPx()) / entries.size
                        val cx = geo.capsuleLeft + 6.dp.toPx() + itemW * (entries.indexOf(selected) + 0.5f)
                        val cy = size.height * 0.40f
                        val rad = 14.dp.toPx() + 14.dp.toPx() * rp
                        drawCircle(
                            rippleColor.copy(alpha = 0.18f * (1f - rp)),
                            radius = rad,
                            center = Offset(cx, cy),
                            style = Stroke(width = 1.5.dp.toPx()),
                        )
                        drawCircle(
                            rippleColor.copy(alpha = 0.05f * (1f - rp)),
                            radius = rad,
                            center = Offset(cx, cy),
                        )
                    }
                }
        ) {
            // 分类项：跟随胶囊平移
            Row(
                Modifier
                    .offset { IntOffset(capsuleOffsetPx.roundToInt(), 0) }
                    .width(with(density) { capsuleW.toDp() })
                    .fillMaxHeight()
                    .padding(horizontal = 6.dp)
                    .pointerInput(entries) {
                        val n = entries.size
                        fun at(x: Float) = (x / (size.width.toFloat() / n) - 0.5f).coerceIn(-0.3f, n - 0.7f)
                        var fingerX = 0f
                        var lastCell = -1
                        fun follow(x: Float) {
                            val t = at(x)
                            scope.launch { lens.animateTo(t, spring(dampingRatio = 0.82f, stiffness = 1300f)) }
                            val cell = t.roundToInt().coerceIn(0, n - 1)
                            if (cell != lastCell) {
                                if (lastCell != -1) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                lastCell = cell
                            }
                        }
                        fun finish() {
                            val idx = lens.targetValue.roundToInt().coerceIn(0, n - 1)
                            scrubbing = false
                            scope.launch { lift.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 420f)) }
                            if (entries[idx] != latestSelected) latestOnSelect(entries[idx])
                            else scope.launch { lens.animateTo(idx.toFloat(), spring(dampingRatio = 0.66f, stiffness = 330f)) }
                        }
                        detectHorizontalDragGestures(
                            onDragStart = { o ->
                                scrubbing = true
                                fingerX = o.x
                                lastCell = -1
                                scope.launch { lift.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 520f)) }
                                follow(fingerX)
                            },
                            onDragEnd = { finish() },
                            onDragCancel = { finish() },
                            onHorizontalDrag = { change, dx ->
                                change.consume()
                                fingerX += dx
                                follow(fingerX)
                            },
                        )
                    }
                    .drawBehind {
                        val n = entries.size
                        val itemW = size.width / n
                        val k = (abs(lens.velocity) * 0.05f).coerceAtMost(0.45f)
                        val up = lift.value
                        val w = (itemW - 2.dp.toPx()) * (1f + k) * (1f + 0.12f * up)
                        val h = (size.height - 10.dp.toPx()) * (1f - 0.16f * k) * (1f + 0.10f * up)
                        val cx = itemW * (lens.value + 0.5f)
                        val top = size.height / 2f - h / 2f
                        val left = cx - w / 2f
                        val radius = CornerRadius(h / 2f)
                        drawRoundRect(
                            lensFill.copy(alpha = lensFill.alpha * (1f + 0.7f * up)),
                            topLeft = Offset(left, top),
                            size = Size(w, h),
                            cornerRadius = radius,
                        )
                        drawRoundRect(
                            Brush.verticalGradient(listOf(lensRimTop, lensRimBottom), startY = top, endY = top + h),
                            topLeft = Offset(left, top),
                            size = Size(w, h),
                            cornerRadius = radius,
                            style = Stroke(width = 1.dp.toPx()),
                        )
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                entries.forEachIndexed { i, f ->
                    DockItem(
                        filter = f,
                        selected = f == selected,
                        count = when (f) {
                            CardFilter.EXPIRING -> expiringCount
                            CardFilter.ARCHIVED -> archivedCount
                            else -> 0
                        },
                        onClick = { onSelect(f) },
                        // 拖选时透镜经过的图标放大
                        hover = { (1f - abs(lens.value - i)).coerceIn(0f, 1f) * lift.value },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // 左：排序（点开排序菜单）；右：添加。融合时图标缩小淡出，过半后不再响应点击
            var sortMenu by remember { mutableStateOf(false) }
            DockCircle(cx = geo.leftCx, r = geo.circleR, h = hPx, merge = m, onClick = { sortMenu = true }) {
                Icon(
                    Icons.AutoMirrored.Rounded.Sort,
                    contentDescription = "排序方式",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .size(23.dp)
                        .graphicsLayer {
                            circleIconFade(m)
                            scaleY *= sortBounce.value
                            scaleX *= 2f - sortBounce.value
                        },
                )
                DropdownMenu(
                    expanded = sortMenu,
                    onDismissRequest = { sortMenu = false },
                    shape = RoundedCornerShape(20.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    SortMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(mode.label, fontWeight = if (mode == sortMode) FontWeight.SemiBold else FontWeight.Normal) },
                            leadingIcon = {
                                if (mode == sortMode) {
                                    Icon(Icons.Rounded.Check, contentDescription = null, Modifier.size(18.dp))
                                } else {
                                    Spacer(Modifier.size(18.dp))
                                }
                            },
                            onClick = {
                                onSortSelect(mode)
                                sortMenu = false
                            },
                        )
                    }
                }
            }
            DockCircle(cx = geo.circleCx, r = geo.circleR, h = hPx, merge = m, onClick = onAdd, pressSpin = 90f) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = "添加卡片",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .size(26.dp)
                        .graphicsLayer { circleIconFade(m) },
                )
            }
        }
    }
}

private fun softShadow(
    canvas: android.graphics.Canvas,
    path: android.graphics.Path,
    color: Color,
    blur: Float,
    dy: Float,
) {
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb()
        maskFilter = android.graphics.BlurMaskFilter(blur, android.graphics.BlurMaskFilter.Blur.NORMAL)
    }
    canvas.save()
    canvas.translate(0f, dy)
    canvas.drawPath(path, paint)
    canvas.restore()
}

/** 圆钮图标随融合进度缩小、淡出 */
private fun androidx.compose.ui.graphics.GraphicsLayerScope.circleIconFade(m: Float) {
    val k = (1f - m * 1.6f).coerceIn(0f, 1f)
    alpha = k
    scaleX = 0.4f + 0.6f * k
    scaleY = 0.4f + 0.6f * k
}

/** Dock 两侧的圆钮：只负责点击区域与图标，玻璃形状由整块液态路径统一绘制 */
@Composable
private fun DockCircle(
    cx: Float,
    r: Float,
    h: Float,
    merge: Float,
    onClick: () -> Unit,
    pressSpin: Float = 0f,
    content: @Composable () -> Unit,
) {
    if (r <= 1f) return
    val density = LocalDensity.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(
        if (pressed) 1f else 0f,
        spring(dampingRatio = 0.42f, stiffness = 480f),
        label = "circlePress",
    )
    Box(
        Modifier
            .offset { IntOffset((cx - r).roundToInt(), (h / 2f - r).roundToInt()) }
            .size(with(density) { (r * 2f).toDp() })
            .pressScale(interaction, 0.88f)
            .clip(CircleShape)
            // 融进胶囊后不再挂点击：哪怕 enabled=false 的 clickable 也会吞掉落在它上面的点按，
            // 让下面的「全部」「已归档」点不动
            .then(
                if (merge < 0.4f) Modifier.clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick,
                ) else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.graphicsLayer { rotationZ = pressSpin * press }, contentAlignment = Alignment.Center) { content() }
    }
}

/** 单个分类：线框 / 实心两层图标交叉淡化，选中时轻弹 */
@Composable
private fun DockItem(
    filter: CardFilter,
    selected: Boolean,
    count: Int,
    onClick: () -> Unit,
    hover: () -> Float,
    modifier: Modifier = Modifier,
) {
    val badgePop = remember { Animatable(1f) }
    var lastCount by remember { mutableIntStateOf(count) }
    LaunchedEffect(count) {
        if (count == lastCount) return@LaunchedEffect
        lastCount = count
        badgePop.snapTo(1.45f)
        badgePop.animateTo(1f, spring(dampingRatio = 0.32f, stiffness = 520f))
    }
    val sel by animateFloatAsState(
        if (selected) 1f else 0f,
        animationSpec = tween(260),
        label = "dockSel",
    )
    val pop by animateFloatAsState(
        if (selected) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.42f, stiffness = 500f),
        label = "dockPop",
    )
    val tint by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(260),
        label = "dockTint",
    )
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier
            .fillMaxHeight()
            .pressScale(interaction, 0.86f)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.graphicsLayer {
                // pop 是欠阻尼弹簧、sel 是线性渐变：稳态相互抵消为 1，过程中先鼓后收
                val s = (1f + 0.10f * pop - 0.10f * sel) * (1f + 0.2f * hover())
                scaleX = s
                scaleY = s
                translationY = -3.dp.toPx() * hover()
            }
        ) {
            Icon(
                filter.outlineIcon(),
                contentDescription = null,
                tint = tint,
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer { alpha = 1f - sel },
            )
            Icon(
                filter.filledIcon(),
                contentDescription = null,
                tint = tint,
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer { alpha = sel },
            )
            if (count > 0) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 9.dp, y = (-4).dp)
                        .graphicsLayer {
                            scaleX = badgePop.value
                            scaleY = badgePop.value
                        }
                        .height(15.dp)
                        .widthIn(min = 15.dp)
                        .clip(CircleShape)
                        .background(
                            if (filter == CardFilter.EXPIRING) Color(0xFFFF7A1A)
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (count > 99) "99+" else count.toString(),
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 9.sp,
                    )
                }
            }
        }
        Spacer(Modifier.height(1.dp))
        Text(
            filter.label,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = tint,
            maxLines = 1,
        )
    }
}

private fun CardFilter.outlineIcon(): ImageVector = when (this) {
    CardFilter.ALL -> Icons.Outlined.AccountBalanceWallet
    CardFilter.EXPIRING -> Icons.Outlined.HourglassEmpty
    CardFilter.EXPIRED -> Icons.Outlined.EventBusy
    CardFilter.ARCHIVED -> Icons.Outlined.Inventory2
}

private fun CardFilter.filledIcon(): ImageVector = when (this) {
    CardFilter.ALL -> Icons.Rounded.AccountBalanceWallet
    CardFilter.EXPIRING -> Icons.Rounded.HourglassBottom
    CardFilter.EXPIRED -> Icons.Rounded.EventBusy
    CardFilter.ARCHIVED -> Icons.Rounded.Inventory2
}

// —— 几何：胶囊 + 圆钮 + 液颈 ——

private data class DockGeo(
    val capsuleLeft: Float,
    val capsuleW: Float,
    /** 右圆（添加）圆心 */
    val circleCx: Float,
    val circleR: Float,
    /** 左圆（排序）圆心，与右圆镜像对称 */
    val leftCx: Float,
)

/**
 * merge = 0：两颗圆钮分列胶囊左右，间隔 gap；
 * merge = 1：圆钮先“够”到胶囊拉出液颈，再缩小滑入两端被吸收。胶囊位置始终不变。
 */
private fun dockGeometry(merge: Float, capsuleLeft: Float, capsuleW: Float, h: Float, gap: Float): DockGeo {
    val r = h / 2f
    val capsuleRight = capsuleLeft + capsuleW
    val sepCx = capsuleRight + gap + r
    val mergedCx = capsuleRight - r * 0.9f
    val cx = sepCx + (mergedCx - sepCx) * merge
    // 前半程保持大小只做位移（先“够”到胶囊拉出液颈），后半程收缩被吸收
    val shrink = ((merge - 0.35f) / 0.65f).coerceIn(0f, 1f)
    val cr = r * (1f - 0.55f * shrink)
    val leftSep = capsuleLeft - gap - r
    val leftMerged = capsuleLeft + r * 0.9f
    val lx = leftSep + (leftMerged - leftSep) * merge
    return DockGeo(capsuleLeft, capsuleW, cx, cr, lx)
}

private fun liquidPath(g: DockGeo, h: Float, gap: Float): Path {
    val r = h / 2f
    var result = Path().apply {
        addRoundRect(RoundRect(g.capsuleLeft, 0f, g.capsuleLeft + g.capsuleW, h, r, r))
    }
    fun union(other: Path) {
        result = Path().apply { op(result, other, PathOperation.Union) }
    }
    // 胶囊两端的半圆各视作一个球，分别与左右圆钮做 metaball 连接；
    // 静止分离时（间距 = gap）不连，圆钮靠近到一半间距内才拉出液颈
    val ends = listOf(
        g.circleCx to Offset(g.capsuleLeft + g.capsuleW - r, r),
        g.leftCx to Offset(g.capsuleLeft + r, r),
    )
    for ((cx, capEnd) in ends) {
        union(Path().apply {
            addOval(androidx.compose.ui.geometry.Rect(cx - g.circleR, r - g.circleR, cx + g.circleR, r + g.circleR))
        })
        metaballBridge(Offset(cx, r), g.circleR, capEnd, r, maxDist = g.circleR + r + gap * 0.55f)
            ?.let(::union)
    }
    return result
}

/**
 * 两圆之间的 metaball 液颈（Hiroyuki Sato 的经典构造）：
 * 在两圆上各取一对切点，用三次贝塞尔连成腰身；距离越近腰越粗，超过 maxDist 断开。
 */
private fun metaballBridge(
    c1: Offset,
    r1: Float,
    c2: Offset,
    r2: Float,
    maxDist: Float,
    v: Float = 0.5f,
    handleRate: Float = 2.4f,
): Path? {
    val d = (c2 - c1).getDistance()
    if (r1 <= 0f || r2 <= 0f || d > maxDist || d <= abs(r1 - r2)) return null
    var u1 = 0f
    var u2 = 0f
    if (d < r1 + r2) {
        u1 = acos(((r1 * r1 + d * d - r2 * r2) / (2 * r1 * d)).coerceIn(-1f, 1f))
        u2 = acos(((r2 * r2 + d * d - r1 * r1) / (2 * r2 * d)).coerceIn(-1f, 1f))
    }
    val angle = atan2(c2.y - c1.y, c2.x - c1.x)
    val maxSpread = acos(((r1 - r2) / d).coerceIn(-1f, 1f))
    val pi = PI.toFloat()
    val a1 = angle + u1 + (maxSpread - u1) * v
    val a2 = angle - u1 - (maxSpread - u1) * v
    val a3 = angle + pi - u2 - (pi - u2 - maxSpread) * v
    val a4 = angle - pi + u2 + (pi - u2 - maxSpread) * v
    fun dir(a: Float) = Offset(cos(a), sin(a))
    val p1 = c1 + dir(a1) * r1
    val p2 = c1 + dir(a2) * r1
    val p3 = c2 + dir(a3) * r2
    val p4 = c2 + dir(a4) * r2
    val total = r1 + r2
    val d2 = min(v * handleRate, (p1 - p3).getDistance() / total) * min(1f, d * 2f / total)
    val h1 = r1 * d2
    val h2 = r2 * d2
    val half = pi / 2f
    return Path().apply {
        moveTo(p1.x, p1.y)
        val q1 = p1 + dir(a1 - half) * h1
        val q2 = p3 + dir(a3 + half) * h2
        cubicTo(q1.x, q1.y, q2.x, q2.y, p3.x, p3.y)
        lineTo(p4.x, p4.y)
        val q3 = p4 + dir(a4 - half) * h2
        val q4 = p2 + dir(a2 + half) * h1
        cubicTo(q3.x, q3.y, q4.x, q4.y, p2.x, p2.y)
        close()
    }
}

