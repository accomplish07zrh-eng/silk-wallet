package app.silk.wallet.ui.home

import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.geometry.Rect
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.silk.wallet.SilkApp
import app.silk.wallet.data.SortMode
import app.silk.wallet.data.Card
import app.silk.wallet.data.Settings
import app.silk.wallet.ui.fx.LocalFlowClock
import app.silk.wallet.ui.fx.rememberFlowClock
import app.silk.wallet.ui.theme.AppBackground
import app.silk.wallet.ui.theme.GlassCircleButton
import app.silk.wallet.ui.theme.LocalEntrance
import app.silk.wallet.ui.theme.TitleSerif
import app.silk.wallet.ui.theme.WordmarkFont
import app.silk.wallet.ui.theme.enterRise
import app.silk.wallet.ui.theme.glassSurface
import app.silk.wallet.ui.theme.springClickable
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import kotlinx.coroutines.launch

/**
 * 首页：粗体衬线字标页头 + 叠放卡片 + 底部液态 Dock。
 * 首次进入时页头、卡片、Dock 依次浮入；之后从其他页返回不再重播。
 */
@Composable
fun HomeScreen(
    app: SilkApp,
    vm: HomeViewModel,
    settings: Settings,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onSettings: () -> Unit,
    onDraw: () -> Unit,
) {
    val state by vm.state.collectAsState()
    val filter by vm.filter.collectAsState()
    var query by rememberSaveable { mutableStateOf(vm.query.value) }
    var searching by rememberSaveable { mutableStateOf(query.isNotEmpty()) }
    var detail by remember { mutableStateOf<Card?>(null) }
    var detailFrom by remember { mutableStateOf<Rect?>(null) }
    val haptics = LocalHapticFeedback.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haze = remember { HazeState() }
    val gate = LocalEntrance.current
    val play = remember { gate.firstTime("home") }

    fun setQuery(q: String) {
        query = q
        vm.query.value = q
    }
    BackHandler(enabled = searching && detail == null) {
        setQuery("")
        searching = false
    }

    fun deleteWithUndo(card: Card) {
        vm.delete(card)
        scope.launch {
            val r = snackbar.showSnackbar(
                "已删除「${card.bank.ifBlank { "银行卡" }} ${card.number.takeLast(4)}」",
                actionLabel = "撤销",
                duration = SnackbarDuration.Long,
            )
            if (r == SnackbarResult.ActionPerformed) vm.restore(card)
        }
    }

    fun toggleArchive(card: Card) {
        vm.setArchived(card, !card.archived)
        scope.launch { snackbar.showSnackbar(if (card.archived) "已移出归档" else "已归档，可在「已归档」里找到") }
    }

    val headerMotion = rememberHeaderMotion()
    // 换分类、进出搜索时页头回到大标题
    LaunchedEffect(filter, searching) { headerMotion.expand() }

    val clock = rememberFlowClock()
    CompositionLocalProvider(LocalFlowClock provides clock) {
        Box(Modifier.fillMaxSize()) {
            AppBackground(Modifier.haze(haze)) {
                Column(Modifier.fillMaxSize().nestedScroll(headerMotion.connection)) {
                    AnimatedContent(
                        targetState = searching,
                        transitionSpec = {
                            val slide = spring<IntOffset>(dampingRatio = 0.86f, stiffness = 360f)
                            if (targetState) {
                                (slideInHorizontally(slide) { it / 2 } + fadeIn(tween(220))) togetherWith
                                    (slideOutHorizontally(slide) { -it / 5 } + fadeOut(tween(150)) + scaleOut(targetScale = 0.96f))
                            } else {
                                (slideInHorizontally(slide) { -it / 5 } + fadeIn(tween(220)) + scaleIn(initialScale = 0.96f)) togetherWith
                                    (slideOutHorizontally(slide) { it / 2 } + fadeOut(tween(150)))
                            }.using(SizeTransform(clip = false))
                        },
                        label = "header",
                    ) { open ->
                        if (open) {
                            SearchBar(query, ::setQuery) {
                                setQuery("")
                                searching = false
                            }
                        } else {
                            HomeHeader(
                                subtitle = when {
                                    state.loading -> " "
                                    query.isNotBlank() -> "找到 ${state.cards.size} 张"
                                    filter != CardFilter.ALL -> "${filter.label} · ${state.cards.size} 张"
                                    state.expiring > 0 -> "共 ${state.total} 张 · ${state.expiring} 张即将到期"
                                    else -> "共 ${state.total} 张卡片"
                                },
                                play = play,
                                motion = headerMotion,
                                onDraw = onDraw,
                                onSearch = { searching = true },
                                onSettings = onSettings,
                            )
                        }
                    }
                    when {
                        state.loading -> Unit
                        state.cards.isEmpty() -> EmptyState(filter, query, state.noticeDays, onAdd)
                        settings.grid -> Box(Modifier.fillMaxSize().graphicsLayer { translationY = headerMotion.stretch }) { CardGrid(
                            cards = state.cards,
                            filter = filter,
                            query = query,
                            masked = settings.maskNumbers,
                            noticeDays = state.noticeDays,
                            play = play,
                            hiddenId = detail?.id,
                            columns = settings.gridColumns,
                            onColumnsChange = { scope.launch { app.prefs.setGridColumns(it) } },
                            onOpen = { card, from ->
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                detailFrom = from
                                detail = card
                            },
                            onEdit = { onEdit(it.id) },
                            onArchive = ::toggleArchive,
                            onDelete = ::deleteWithUndo,
                        ) }
                        else -> Box(Modifier.fillMaxSize().graphicsLayer { translationY = headerMotion.stretch }) { CardStack(
                            cards = state.cards,
                            filter = filter,
                            query = query,
                            canReorder = filter == CardFilter.ALL && query.isBlank(),
                            manualSort = state.sort == SortMode.MANUAL,
                            masked = settings.maskNumbers,
                            noticeDays = state.noticeDays,
                            play = play,
                            hiddenId = detail?.id,
                            onOpen = { card, from ->
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                detailFrom = from
                                detail = card
                            },
                            onEdit = { onEdit(it.id) },
                            onArchive = ::toggleArchive,
                            onDelete = ::deleteWithUndo,
                            onReorder = { ids, switched ->
                                if (switched) {
                                    vm.setSort(SortMode.MANUAL)
                                    scope.launch { snackbar.showSnackbar("已切换为手动排序") }
                                }
                                vm.reorder(ids)
                            },
                        ) }
                    }
                }
            }

            LiquidDock(
                selected = filter,
                expiringCount = state.expiring,
                archivedCount = state.archived,
                onSelect = {
                    if (it != filter) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    vm.filter.value = it
                },
                onAdd = onAdd,
                sortMode = state.sort,
                onSortSelect = vm::setSort,
                grid = settings.grid,
                columns = settings.gridColumns,
                onLayoutChange = { g, cols ->
                    scope.launch {
                        app.prefs.setGrid(g)
                        if (g) app.prefs.setGridColumns(cols)
                    }
                },
                hazeState = haze,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
                    .enterRise(index = 4, play = play, rise = 90.dp, baseDelayMs = 120),
            )

            SnackbarHost(
                snackbar,
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = DockHeight + 26.dp),
            ) { data ->
                Snackbar(
                    data,
                    shape = RoundedCornerShape(18.dp),
                    containerColor = MaterialTheme.colorScheme.onSurface,
                    contentColor = MaterialTheme.colorScheme.surface,
                    actionColor = Color(0xFFFFC857),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            // 详情：卡片从堆叠里的位置飞出、放大到位；关闭时原路飞回，落地后才把堆叠里那张显示出来
            detail?.let { d ->
                // 详情里的卡始终取最新版本（换样式、编辑后即时刷新）
                val live = state.cards.firstOrNull { it.id == d.id } ?: d
                CardDetail(
                    app = app,
                    card = live,
                    settings = settings,
                    haze = haze,
                    from = detailFrom,
                    onStyle = { vm.setStyle(live, it) },
                    onEdit = {
                        detail = null
                        onEdit(live.id)
                    },
                    onClosed = { detail = null },
                )
            }
        }
    }
}

@Composable
private fun SearchBar(value: String, onChange: (String) -> Unit, onCancel: () -> Unit) {
    val focus = remember { FocusRequester() }
    val fm = LocalFocusManager.current
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 18.dp, end = 8.dp, top = 14.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .height(48.dp)
                .glassSurface(null, RoundedCornerShape(24.dp))
                .padding(start = 16.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text("持卡人、银行、卡号、备注", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), fontSize = 14.sp, maxLines = 1)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onChange,
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { fm.clearFocus() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            if (value.isNotEmpty()) {
                Box(
                    Modifier.size(32.dp).clip(CircleShape).springClickable(pressedScale = 0.85f) { onChange("") },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Close, "清空", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                }
            }
        }
        Text(
            "取消",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .springClickable(pressedScale = 0.9f, onClick = onCancel)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun EmptyState(filter: CardFilter, query: String, noticeDays: Int, onAdd: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp)
            .padding(bottom = DockHeight),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(88.dp).glassSurface(null, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.CreditCard, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(
            when {
                query.isNotBlank() -> "没有匹配「$query」的卡片"
                filter == CardFilter.EXPIRING -> "没有 $noticeDays 天内到期的卡片"
                filter == CardFilter.EXPIRED -> "没有已过期的卡片"
                filter == CardFilter.ARCHIVED -> "没有已归档的卡片"
                else -> "还没有卡片"
            },
            fontFamily = TitleSerif,
            fontWeight = FontWeight.Bold,
            fontSize = 19.sp,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (query.isBlank() && filter == CardFilter.ALL) {
            Spacer(Modifier.height(20.dp))
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface)
                    .springClickable(pressedScale = 0.94f, onClick = onAdd)
                    .padding(horizontal = 22.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.surface, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("添加第一张卡", color = MaterialTheme.colorScheme.surface, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "也可以把实体卡贴在手机背面\n通过 NFC 自动读取卡号",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
