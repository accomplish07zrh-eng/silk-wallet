package app.silk.wallet.ui.home

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.silk.wallet.SilkApp
import app.silk.wallet.data.Card
import app.silk.wallet.data.CardMedia
import app.silk.wallet.data.Settings
import app.silk.wallet.domain.CardBrand
import app.silk.wallet.domain.CardMath
import app.silk.wallet.domain.CardStyles
import app.silk.wallet.ui.card.CardRatio
import app.silk.wallet.ui.card.CardShape
import app.silk.wallet.ui.card.CardStylePicker
import app.silk.wallet.ui.card.FlipCard
import app.silk.wallet.ui.card.RevealToggle
import app.silk.wallet.ui.fx.LocalTilt
import app.silk.wallet.ui.theme.springClickable
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeChild
import kotlinx.coroutines.launch

/**
 * 卡片详情浮层：背后整屏磨砂虚化 + 压暗；卡片可点按翻面、可拖着 3D 倾斜（光泽跟随手指）；
 * 下方是卡面样式（含相册背景图）与可点按复制的信息。默认打码，点卡面右上角的眼睛临时显示。
 */
@Composable
fun CardDetail(
    app: SilkApp,
    card: Card,
    settings: Settings,
    haze: HazeState,
    onStyle: (String?) -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var flipped by remember(card.id) { mutableStateOf(false) }
    var revealed by remember(card.id) { mutableStateOf(false) }
    val brand = CardBrand.of(card.brand)
    val style = CardStyles.resolve(card.styleId, card.bankCode, brand)
    BackHandler(onBack = onDismiss)

    fun copy(label: String, value: String) = app.clipboard.copy(context, label, value, settings.clipboardSeconds)

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            if (!CardMedia.import(context, card.id, uri)) Toast.makeText(context, "图片读取失败，换一张试试", Toast.LENGTH_SHORT).show()
        }
    }
    val mediaVersion by CardMedia.version.collectAsState()
    val hasImage = remember(card.id, mediaVersion) { CardMedia.has(context, card.id) }

    val dismissTap = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxSize()
            .hazeChild(
                haze,
                HazeStyle(
                    backgroundColor = MaterialTheme.colorScheme.background,
                    tint = HazeTint(Color.Black.copy(alpha = 0.42f)),
                    blurRadius = 32.dp,
                    noiseFactor = 0.04f,
                    fallbackTint = HazeTint(Color.Black.copy(alpha = 0.72f)),
                ),
            )
            .clickable(dismissTap, indication = null, onClick = onDismiss)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 64.dp, bottom = 24.dp)
                .verticalScroll(rememberScrollState())
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // 拖着玩：拖动量同时驱动 3D 倾斜与光泽，松手弹簧回正
            val drag = remember(card.id) { Animatable(Offset.Zero, Offset.VectorConverter) }
            val device = LocalTilt.current
            val tilt = remember(device, drag) { derivedStateOf { device.value + Offset(drag.value.x / 420f, drag.value.y / 420f) } }
            CompositionLocalProvider(LocalTilt provides tilt) {
                FlipCard(
                    card = card,
                    style = style,
                    masked = !revealed,
                    flipped = flipped,
                    onTap = { flipped = !flipped },
                    trailing = { RevealToggle(!revealed) { revealed = !revealed } },
                    modifier = Modifier
                        .graphicsLayer {
                            val d = drag.value
                            rotationY = (d.x * 0.075f).coerceIn(-22f, 22f)
                            rotationX = (-d.y * 0.075f).coerceIn(-22f, 22f)
                            translationX = d.x * 0.28f
                            translationY = d.y * 0.28f
                            cameraDistance = 14f * density
                        }
                        .pointerInput(card.id) {
                            detectDragGestures(
                                onDragEnd = { scope.launch { drag.animateTo(Offset.Zero, spring(0.42f, 260f)) } },
                                onDragCancel = { scope.launch { drag.animateTo(Offset.Zero, spring(0.42f, 260f)) } },
                            ) { change, amount ->
                                change.consume()
                                scope.launch {
                                    val cur = drag.value
                                    drag.snapTo(cur + amount * (1f / (1f + cur.getDistance() / 600f)))
                                }
                            }
                        }
                        .fillMaxWidth()
                        .aspectRatio(CardRatio)
                        .shadow(24.dp, CardShape, ambientColor = style.colors.last(), spotColor = style.colors.last()),
                )
            }
            Spacer(Modifier.height(10.dp))
            Text("点按翻面看 CVV · 按住拖动看光泽", color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp)
            Spacer(Modifier.height(20.dp))

            Column(Modifier.fillMaxWidth().clickable(remember { MutableInteractionSource() }, indication = null) {}) {
                Text(
                    "卡面样式",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                )
                CardStylePicker(
                    selectedId = card.styleId,
                    onSelect = {
                        CardMedia.remove(context, card.id)
                        onStyle(it)
                    },
                    labelColor = Color.White.copy(alpha = 0.6f),
                    ringColor = Color.White,
                    hasImage = hasImage,
                    onPickImage = {
                        app.lock.allowExternalOnce()
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                )
            }
            Spacer(Modifier.height(18.dp))

            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable(remember { MutableInteractionSource() }, indication = null) {}
            ) {
                InfoRow("持卡人", card.holder) { copy("持卡人", card.holder) }
                Line()
                InfoRow("卡号", if (revealed) CardMath.format(card.number, brand) else CardMath.mask(card.number), mono = true) {
                    copy("卡号", card.number)
                }
                Line()
                InfoRow("有效期", if (revealed) CardMath.expiryText(card.expMonth, card.expYear) else "••/••") {
                    copy("有效期", CardMath.expiryText(card.expMonth, card.expYear))
                }
                if (!card.cvv.isNullOrBlank()) {
                    Line()
                    InfoRow("CVV", if (revealed) card.cvv else "•••") { copy("CVV", card.cvv) }
                }
                Line()
                InfoRow("发卡行", card.bank.ifBlank { "未填写" }, copyable = card.bank.isNotBlank()) { copy("发卡行", card.bank) }
                Line()
                InfoRow("卡组织", brand.displayName, copyable = false) {}
                if (card.note.isNotBlank()) {
                    Line()
                    InfoRow("备注", card.note) { copy("备注", card.note) }
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.14f))
                    .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape)
                    .springClickable(pressedScale = 0.92f, onClick = onEdit)
                    .padding(horizontal = 22.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Edit, null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("编辑卡片", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 8.dp, end = 20.dp)
                .size(42.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.16f))
                .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape)
                .springClickable(pressedScale = 0.86f, onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Close, "关闭", tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun Line() = HorizontalDivider(
    modifier = Modifier.padding(start = 18.dp),
    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
)

@Composable
private fun InfoRow(label: String, value: String, mono: Boolean = false, copyable: Boolean = true, onCopy: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = copyable, onClick = onCopy)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.width(64.dp))
        Text(
            value,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (copyable) {
            Icon(Icons.Rounded.ContentCopy, "复制$label", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
        }
    }
}
