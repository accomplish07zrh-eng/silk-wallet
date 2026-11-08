package app.silk.wallet.ui.home

import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Rect
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.silk.wallet.data.Card
import app.silk.wallet.domain.CardBrand
import app.silk.wallet.domain.CardMath
import app.silk.wallet.domain.CardStyles
import app.silk.wallet.ui.card.CardFront
import app.silk.wallet.ui.card.CardRatio
import app.silk.wallet.ui.card.CardShape
import app.silk.wallet.ui.theme.enterRise
import app.silk.wallet.ui.theme.floatingCard
import app.silk.wallet.ui.theme.pressScale
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private enum class Swipe { Closed, Open }

/** 卡片叠放：相邻两张露出的高度 = 卡高 - Overlap */
private val Overlap = 136.dp
private val TrayWidth = 170.dp

/**
 * 叠放卡片列表：
 * - 长按拎起拖动排序：记住起点下标与累计位移，目标位置 = 起点 + round(位移 / 步长)，
 *   被拖的卡只渲染“剩余位移”，看起来始终粘在手指下；靠近上下边缘自动滚动；
 * - 左滑露出白色玻璃托盘（编辑 / 归档 / 删除），同一时间只展开一张；
 * - 卡片滑出视口顶端时轻微缩小变淡，像塞回卡包。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CardStack(
    cards: List<Card>,
    filter: CardFilter,
    query: String,
    canReorder: Boolean,
    manualSort: Boolean,
    masked: Boolean,
    noticeDays: Int,
    play: Boolean,
    hiddenId: Long?,
    onOpen: (Card, Rect) -> Unit,
    onEdit: (Card) -> Unit,
    onArchive: (Card) -> Unit,
    onDelete: (Card) -> Unit,
    onReorder: (ids: List<Long>, switchedToManual: Boolean) -> Unit,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()
    val overlapPx = with(density) { Overlap.toPx() }
    val trayPx = with(density) { TrayWidth.toPx() }
    // 底栏占掉的高度：导航条 + 底栏外边距 14dp + 底栏 + 一点余量
    val dockClearPx = WindowInsets.navigationBars.getBottom(density) + with(density) { (14.dp + DockHeight + 8.dp).toPx() }

    // 手势回调只创建一次，必须读最新的列表与设置
    val latestCards by rememberUpdatedState(cards)
    val latestManual by rememberUpdatedState(manualSort)

    var itemHeight by remember { mutableIntStateOf(0) }
    val step = (itemHeight - overlapPx).coerceAtLeast(1f)

    // —— 拖动排序状态 ——
    var dragId by remember { mutableStateOf<Long?>(null) }
    var liftedId by remember { mutableStateOf<Long?>(null) }
    var baseOrder by remember { mutableStateOf<List<Long>>(emptyList()) }
    var startIndex by remember { mutableIntStateOf(0) }
    val total = remember { mutableFloatStateOf(0f) }
    var pointerY by remember { mutableFloatStateOf(0f) }
    val settle = remember { Animatable(0f) }
    var settlingId by remember { mutableStateOf<Long?>(null) }

    fun targetIndex(): Int =
        (startIndex + (total.floatValue / step).roundToInt()).coerceIn(0, (baseOrder.size - 1).coerceAtLeast(0))

    // 被拖的卡相对槽位的剩余位移；拖过最顶 / 最底时加橡皮筋阻尼，不让卡冲出列表被裁成半截
    fun residual(): Float {
        val t = targetIndex()
        val raw = total.floatValue - (t - startIndex) * step
        val beyond = (t == 0 && raw < 0f) || (t == baseOrder.lastIndex && raw > 0f)
        if (!beyond) return raw
        val limit = step * 0.45f
        return kotlin.math.sign(raw) * limit * (1f - kotlin.math.exp(-kotlin.math.abs(raw) / limit))
    }

    // 松手后数据库写入要一会儿：这段时间继续按松手时的顺序排，避免先弹回旧顺序再跳到新顺序
    var pendingOrder by remember { mutableStateOf<List<Long>?>(null) }
    val cardIds = cards.map { it.id }
    val pending = pendingOrder?.takeIf { p -> p != cardIds && p.size == cardIds.size && p.toSet() == cardIds.toSet() }
    LaunchedEffect(pendingOrder, pending == null) {
        if (pendingOrder == null) return@LaunchedEffect
        if (pending == null) { pendingOrder = null; return@LaunchedEffect }
        delay(1500)
        pendingOrder = null
    }

    // 手势回调是在拖动开始前创建的，必须现算顺序，不能读组合时的快照
    fun dragOrder(): List<Long>? = if (dragId != null && baseOrder.isNotEmpty()) {
        val ids = baseOrder.toMutableList()
        val id = ids.removeAt(startIndex)
        ids.add(targetIndex(), id)
        ids
    } else null
    val order: List<Long>? = dragOrder() ?: pending

    // 顺序变化时按“下标”而不是“首张卡的 key”保持滚动位置：
    // 否则把卡拖到最顶上时，列表会跟着原来的首张卡走，新的首张卡被顶出屏幕只露半截
    fun pinScroll() = list.requestScrollToItem(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset)
    // 拖动中按本地顺序排，卡片内容始终取最新数据
    val shown = order?.mapNotNull { id -> cards.firstOrNull { it.id == id } } ?: cards

    fun finishDrag() {
        val id = dragId ?: return
        val final = dragOrder() ?: return
        liftedId = null
        if (final != latestCards.map { it.id }) {
            pendingOrder = final
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onReorder(final, !latestManual)
        }
        // 松手后把“剩余位移”弹簧收回槽位
        val rest = residual()
        scope.launch {
            settle.snapTo(rest)
            settlingId = id
            dragId = null
            settle.animateTo(0f, spring(dampingRatio = 0.75f, stiffness = 420f))
            settlingId = null
        }
    }

    // 拖到上下边缘时自动滚动，滚动量计入位移，卡片继续粘在手指下
    LaunchedEffect(dragId) {
        if (dragId == null) return@LaunchedEffect
        var last = 0L
        while (dragId != null) {
            val now = withFrameNanos { it }
            val dt = if (last == 0L) 0f else (now - last) / 1e9f
            last = now
            val info = list.layoutInfo
            val edge = with(density) { 90.dp.toPx() }
            val top = info.viewportStartOffset + edge
            val bottom = info.viewportEndOffset - edge
            // 最后一张已经完整露在底栏上方就不再往下滚，免得把顶上的卡白白滚出屏幕
            val lastItem = info.visibleItemsInfo.lastOrNull()
            val endShown = lastItem != null && lastItem.index == info.totalItemsCount - 1 &&
                lastItem.offset + lastItem.size <= info.viewportEndOffset - dockClearPx
            val v = when {
                pointerY < top -> -((top - pointerY) / edge).coerceAtMost(1f)
                pointerY > bottom && !endShown -> ((pointerY - bottom) / edge).coerceAtMost(1f)
                else -> 0f
            } * with(density) { 520.dp.toPx() }
            // 重排时 requestScrollToItem 会打断正在进行的滚动，这一帧当作没滚，下一帧接着滚
            if (v != 0f && dt > 0f) total.floatValue += try { list.scrollBy(v * dt) } catch (e: CancellationException) {
                if (!isActive) throw e
                0f
            }
        }
    }

    // —— 左滑：同一时间只展开一张 ——
    var openId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(list) {
        snapshotFlow { list.isScrollInProgress }.collect { if (it && dragId == null) openId = null }
    }
    // 换筛选 / 搜索后回到顶部（等新数据落地后再回一次，防止锚点把内容顶走）
    LaunchedEffect(filter, query) {
        openId = null
        list.scrollToItem(0)
        snapshotFlow { latestCards }.drop(1).first()
        list.scrollToItem(0)
    }

    // 卡片专用手势参数：左滑要更明确的位移，长按更快拎起
    val baseConfig = LocalViewConfiguration.current
    val cardConfig = remember(baseConfig) {
        object : ViewConfiguration by baseConfig {
            override val touchSlop: Float = baseConfig.touchSlop * 2.5f
            override val longPressTimeoutMillis: Long = 320L
        }
    }

    LazyColumn(
        state = list,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 160.dp),
        verticalArrangement = Arrangement.spacedBy(-Overlap),
    ) {
        itemsIndexed(shown, key = { _, c -> c.id }) { index, card ->
            val style = remember(card.styleId, card.bankCode, card.brand) {
                CardStyles.resolve(card.styleId, card.bankCode, CardBrand.of(card.brand))
            }
            val dragging = dragId == card.id
            val lifted = liftedId == card.id
            val lift by animateFloatAsState(if (lifted) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = 600f), label = "lift")
            val elevation by animateDpAsState(if (lifted) 28.dp else 8.dp, spring(dampingRatio = 0.9f, stiffness = 480f), label = "elev")

            val swipe = remember(card.id) {
                AnchoredDraggableState(
                    initialValue = Swipe.Closed,
                    anchors = DraggableAnchors {
                        Swipe.Closed at 0f
                        Swipe.Open at -(trayPx + with(density) { 10.dp.toPx() })
                    },
                    positionalThreshold = { it * 0.45f },
                    velocityThreshold = { with(density) { 120.dp.toPx() } },
                    snapAnimationSpec = spring(dampingRatio = 0.85f, stiffness = 380f),
                    decayAnimationSpec = exponentialDecay(),
                )
            }
            LaunchedEffect(swipe) {
                snapshotFlow { swipe.currentValue }.collect {
                    if (it == Swipe.Open) openId = card.id else if (openId == card.id) openId = null
                }
            }
            LaunchedEffect(openId) {
                if (openId != card.id && swipe.currentValue == Swipe.Open) swipe.animateTo(Swipe.Closed)
            }
            val exposed by remember(swipe) { derivedStateOf { swipe.offset < -0.5f } }
            val press = remember(card.id) { MutableInteractionSource() }
            // 卡片在屏幕上的位置：点开详情时从这里飞出去，关闭时飞回来
            val bounds = remember(card.id) { arrayOf(Rect.Zero) }

            androidx.compose.runtime.CompositionLocalProvider(LocalViewConfiguration provides cardConfig) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .zIndex(if (lifted) 1f else 0f)
                        .then(
                            if (dragging) Modifier
                            else Modifier.animateItem(
                                fadeInSpec = null,
                                fadeOutSpec = null,
                                placementSpec = spring(dampingRatio = 0.82f, stiffness = 380f, visibilityThreshold = IntOffset.VisibilityThreshold),
                            )
                        )
                        .onSizeChanged { itemHeight = it.height }
                        .enterRise(index = 5 + index.coerceAtMost(6), play = play, rise = 60.dp)
                        .then(
                            if (!canReorder) Modifier else Modifier.pointerInput(card.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { at ->
                                        openId = null
                                        baseOrder = latestCards.map { it.id }
                                        startIndex = baseOrder.indexOf(card.id).coerceAtLeast(0)
                                        total.floatValue = 0f
                                        val item = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == card.id }
                                        pointerY = (item?.offset ?: 0) + at.y
                                        dragId = card.id
                                        liftedId = card.id
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        val before = targetIndex()
                                        total.floatValue += amount.y
                                        pointerY += amount.y
                                        if (targetIndex() != before) {
                                            pinScroll()
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    },
                                    onDragEnd = { finishDrag() },
                                    onDragCancel = { finishDrag() },
                                )
                            }
                        )
                        .graphicsLayer {
                            // 被拖的卡只渲染剩余位移；松手后由 settle 收回
                            val manual = when {
                                dragging -> residual()
                                settlingId == card.id -> settle.value
                                else -> 0f
                            }
                            translationY = manual
                            // 顶部收纳：滑出视口上缘时轻微缩小、变淡
                            val info = list.layoutInfo
                            val item = info.visibleItemsInfo.firstOrNull { it.key == card.id }
                            val tuck = if (item == null) 0f
                            else ((info.viewportStartOffset - (item.offset + manual)) / (step * 0.6f)).coerceIn(0f, 1f)
                            transformOrigin = TransformOrigin(0.5f, 0f)
                            val s = (1f + 0.04f * lift) * (1f - 0.05f * tuck)
                            scaleX = s
                            scaleY = s
                            alpha = 1f - 0.18f * tuck * tuck
                        }
                ) {
                    if (exposed) {
                        val isLast = index == shown.lastIndex
                        val band = with(density) { if (isLast || itemHeight == 0) itemHeight.toDp() else (itemHeight.toDp() - Overlap) }
                        Tray(
                            archived = card.archived,
                            height = band.coerceAtLeast(60.dp),
                            progress = { (-swipe.offset / trayPx).coerceIn(0f, 1f) },
                            onEdit = { openId = null; onEdit(card) },
                            onArchive = { openId = null; onArchive(card) },
                            onDelete = { openId = null; onDelete(card) },
                            modifier = Modifier.align(Alignment.TopEnd),
                        )
                    }
                    Box(
                        Modifier
                            .offset { IntOffset(swipe.offset.roundToInt(), 0) }
                            .onGloballyPositioned { bounds[0] = it.boundsInRoot() }
                            // 正在详情里展示的那张先藏起来，看起来就是同一张卡被拿了出去
                            .graphicsLayer { alpha = if (hiddenId == card.id) 0f else 1f }
                            .pressScale(press, 0.97f)
                            .shadow(elevation, CardShape, ambientColor = style.colors.last(), spotColor = style.colors.last())
                            .anchoredDraggable(swipe, Orientation.Horizontal, enabled = dragId == null)
                            .clickable(press, indication = null) {
                                if (openId != null || exposed) {
                                    openId = null
                                    scope.launch { swipe.animateTo(Swipe.Closed) }
                                } else onOpen(card, bounds[0])
                            }
                            .semantics {
                                customActions = listOf(
                                    CustomAccessibilityAction("编辑") { onEdit(card); true },
                                    CustomAccessibilityAction(if (card.archived) "移出归档" else "归档") { onArchive(card); true },
                                    CustomAccessibilityAction("删除") { onDelete(card); true },
                                )
                            }
                    ) {
                        CardFront(
                            card = card,
                            style = style,
                            masked = masked,
                            badge = { ExpiryBadge(card, noticeDays) },
                            modifier = Modifier.fillMaxWidth().aspectRatio(CardRatio),
                        )
                    }
                }
            }
        }
    }
}

/** 到期角标：半透明暗底 + 彩色圆点 */
@Composable
private fun ExpiryBadge(card: Card, noticeDays: Int) {
    val (text, dot) = when (CardMath.expiry(card.expMonth, card.expYear, noticeDays)) {
        CardMath.Expiry.EXPIRED -> "已过期" to Color(0xFFFF5A52)
        CardMath.Expiry.SOON -> "${CardMath.daysLeft(card.expMonth, card.expYear)} 天后到期" to Color(0xFFFF9F0A)
        else -> return
    }
    Row(
        Modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.28f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(5.dp))
        Text(text, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** 左滑托盘：白色浮起玻璃上的三枚圆钮，随滑动进度淡入放大 */
@Composable
private fun Tray(
    archived: Boolean,
    height: androidx.compose.ui.unit.Dp,
    progress: () -> Float,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .height(height)
            .graphicsLayer {
                val p = progress()
                alpha = p
                val s = 0.8f + 0.2f * p
                scaleX = s
                scaleY = s
                transformOrigin = TransformOrigin(1f, 0.5f)
            },
        contentAlignment = Alignment.CenterEnd,
    ) {
        Row(
            Modifier
                .width(TrayWidth)
                .height(64.dp)
                .floatingCard(RoundedCornerShape(32.dp))
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrayButton(Icons.Rounded.Edit, "编辑", MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f), MaterialTheme.colorScheme.onSurface, onEdit)
            TrayButton(
                if (archived) Icons.Rounded.Unarchive else Icons.Rounded.Archive,
                if (archived) "移出归档" else "归档",
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                MaterialTheme.colorScheme.onSurface,
                onArchive,
            )
            TrayButton(Icons.Rounded.Delete, "删除", Color(0xFFFF3B30), Color.White, onDelete)
        }
    }
}

@Composable
private fun TrayButton(icon: ImageVector, label: String, bg: Color, fg: Color, onClick: () -> Unit) {
    val press = remember { MutableInteractionSource() }
    Box(
        Modifier
            .pressScale(press, 0.86f)
            .size(42.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(press, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = fg, modifier = Modifier.size(20.dp))
    }
}
