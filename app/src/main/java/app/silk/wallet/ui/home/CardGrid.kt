package app.silk.wallet.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import app.silk.wallet.data.Card
import app.silk.wallet.domain.CardBrand
import app.silk.wallet.domain.CardStyles
import app.silk.wallet.ui.card.CardFront
import app.silk.wallet.ui.card.CardRatio
import app.silk.wallet.ui.theme.enterRise
import app.silk.wallet.ui.theme.pressScale
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first

/** 圆角随格子宽度等比缩放（与 340dp 宽的大卡 22dp 圆角同比例），列数多时不会圆成胶囊 */
private val GridCardShape = GenericShape { size, _ ->
    val r = size.width * (22f / 340f)
    addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r)))
}

/** 卡面按叠放列表里的原尺寸排版，再整体缩小放进格子：字号比例与真卡一致 */
private val DesignWidth = 340.dp

/**
 * 多列平铺：每张卡完整摊开，一眼看全。
 * - 点卡片：和叠放一样从格子里飞出到详情；
 * - 长按：弹出编辑 / 归档 / 删除。
 * - 两指捏合在 2～4 列之间切换，也可以在排序菜单里直接选。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CardGrid(
    cards: List<Card>,
    filter: CardFilter,
    query: String,
    masked: Boolean,
    noticeDays: Int,
    play: Boolean,
    hiddenId: Long?,
    columns: Int,
    onColumnsChange: (Int) -> Unit,
    onOpen: (Card, Rect) -> Unit,
    onEdit: (Card) -> Unit,
    onArchive: (Card) -> Unit,
    onDelete: (Card) -> Unit,
) {
    val state = rememberLazyGridState()
    val haptics = LocalHapticFeedback.current
    val latestCards by rememberUpdatedState(cards)
    val latestColumns by rememberUpdatedState(columns)
    val latestOnColumns by rememberUpdatedState(onColumnsChange)
    // 列数越多，间距、投影越小
    val gap = when (columns) { 2 -> 12.dp; 3 -> 9.dp; else -> 7.dp }
    val elevation = when (columns) { 2 -> 8.dp; 3 -> 6.dp; else -> 4.dp }
    // 换筛选 / 搜索后回到顶部（等新数据落地后再回一次）
    LaunchedEffect(filter, query) {
        state.scrollToItem(0)
        snapshotFlow { latestCards }.drop(1).first()
        state.scrollToItem(0)
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = state,
        modifier = Modifier
            .fillMaxSize()
            // 双指捏合改列数：张开 → 列数变少（卡变大），捏小 → 列数变多。
            // 在 Initial 阶段拦下双指事件，免得列表同时跟着滚
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    var acc = 1f
                    do {
                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                        if (ev.changes.count { it.pressed } >= 2) {
                            acc *= ev.calculateZoom()
                            val cur = latestColumns
                            val next = when {
                                acc > 1.28f && cur > 2 -> cur - 1
                                acc < 0.78f && cur < 4 -> cur + 1
                                else -> cur
                            }
                            if (next != cur) {
                                latestOnColumns(next)
                                acc = 1f
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            ev.changes.forEach { it.consume() }
                        }
                    } while (ev.changes.any { it.pressed })
                }
            },
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 160.dp),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalArrangement = Arrangement.spacedBy(gap + 2.dp),
    ) {
        itemsIndexed(cards, key = { _, c -> c.id }) { index, card ->
            val style = remember(card.styleId, card.bankCode, card.brand) {
                CardStyles.resolve(card.styleId, card.bankCode, CardBrand.of(card.brand))
            }
            val bounds = remember(card.id) { arrayOf(Rect.Zero) }
            val press = remember(card.id) { MutableInteractionSource() }
            var menu by remember(card.id) { mutableStateOf(false) }
            Box(
                Modifier
                    .animateItem(
                        fadeInSpec = null,
                        fadeOutSpec = null,
                        placementSpec = spring(dampingRatio = 0.82f, stiffness = 380f, visibilityThreshold = IntOffset.VisibilityThreshold),
                    )
                    .enterRise(index = 5 + index.coerceAtMost(8), play = play, rise = 40.dp)
            ) {
                BoxWithConstraints(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(CardRatio)
                        .onGloballyPositioned { bounds[0] = it.boundsInRoot() }
                        // 正在详情里展示的那张先藏起来，看起来就是同一张卡被拿了出去
                        .graphicsLayer { alpha = if (hiddenId == card.id) 0f else 1f }
                        .pressScale(press, 0.95f)
                        .shadow(elevation, GridCardShape, ambientColor = style.colors.last(), spotColor = style.colors.last())
                        .clip(GridCardShape)
                        .combinedClickable(
                            interactionSource = press,
                            indication = null,
                            onLongClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                menu = true
                            },
                            onClick = { onOpen(card, bounds[0]) },
                        ),
                ) {
                    val k = maxWidth / DesignWidth
                    Box(
                        Modifier
                            .requiredSize(DesignWidth, DesignWidth / CardRatio)
                            .graphicsLayer {
                                scaleX = k
                                scaleY = k
                            }
                    ) {
                        CardFront(
                            card = card,
                            style = style,
                            masked = masked,
                            badge = { ExpiryBadge(card, noticeDays) },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                DropdownMenu(
                    expanded = menu,
                    onDismissRequest = { menu = false },
                    shape = RoundedCornerShape(20.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    DropdownMenuItem(
                        text = { Text("编辑") },
                        leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                        onClick = { menu = false; onEdit(card) },
                    )
                    DropdownMenuItem(
                        text = { Text(if (card.archived) "移出归档" else "归档") },
                        leadingIcon = { Icon(if (card.archived) Icons.Rounded.Unarchive else Icons.Rounded.Archive, null) },
                        onClick = { menu = false; onArchive(card) },
                    )
                    DropdownMenuItem(
                        text = { Text("删除", color = Color(0xFFFF3B30)) },
                        leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = Color(0xFFFF3B30)) },
                        onClick = { menu = false; onDelete(card) },
                    )
                }
            }
        }
    }
}
