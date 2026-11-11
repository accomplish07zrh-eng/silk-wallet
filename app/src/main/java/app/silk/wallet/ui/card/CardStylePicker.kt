package app.silk.wallet.ui.card

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.silk.wallet.domain.CardStyles

private val SwatchShape = RoundedCornerShape(10.dp)

/**
 * 卡面样式选择条：首项「自动」（按发卡行 / 卡组织匹配），其后为全部预设。
 * 编辑页与卡片详情共用；详情里点选即保存，不必再进编辑页。
 */
@Composable
fun CardStylePicker(
    selectedId: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    ringColor: Color = MaterialTheme.colorScheme.onSurface,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    /** 当前是否使用背景图；为 true 时颜色预设都不显示选中 */
    hasImage: Boolean = false,
    /** 非空时在最前面显示「图片」选项（从相册导入背景图） */
    onPickImage: (() -> Unit)? = null,
) {
    // 皮肤全部开放，排在基础配色前面，按稀有度从高到低
    val allSkins = remember { CardStyles.skins.sortedByDescending { it.rarity.ordinal } }
    val listState = rememberLazyListState()
    // 打开时把已选样式滚到可见处，避免选中项藏在右侧
    LaunchedEffect(Unit) {
        val skinIndex = allSkins.indexOfFirst { it.id == selectedId }
        val presetIndex = CardStyles.presets.indexOfFirst { it.id == selectedId }
        val lead = if (onPickImage != null) 2 else 1
        val index = when {
            skinIndex >= 0 -> lead + skinIndex
            presetIndex >= 0 -> lead + allSkins.size + presetIndex
            else -> 0
        }
        if (index > 0) listState.scrollToItem(index)
    }
    LazyRow(
        state = listState,
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (onPickImage != null) {
            item(key = "image") {
                StyleSwatch(
                    label = if (hasImage) "更换图片" else "图片",
                    brush = Brush.linearGradient(listOf(Color(0xFFDCE3F5), Color(0xFFB9A6D8), Color(0xFFE9C1AE))),
                    selected = hasImage,
                    darkMark = true,
                    labelColor = labelColor,
                    ringColor = ringColor,
                    onClick = onPickImage,
                    photoIcon = true,
                )
            }
        }
        item(key = "auto") {
            StyleSwatch(
                label = "自动",
                brush = Brush.linearGradient(listOf(Color(0xFF8E9099), Color(0xFF4A4C54))),
                selected = !hasImage && selectedId == null,
                darkMark = false,
                labelColor = labelColor,
                ringColor = ringColor,
                onClick = { onSelect(null) },
                autoIcon = true,
            )
        }
        items(allSkins, key = { it.id }) { skin ->
            StyleSwatch(
                label = skin.label,
                brush = Brush.linearGradient(skin.colors),
                selected = !hasImage && selectedId == skin.id,
                darkMark = skin.darkText,
                labelColor = labelColor,
                ringColor = ringColor,
                onClick = { onSelect(skin.id) },
                rarityGlow = skin.rarity.glow,
            )
        }
        items(CardStyles.presets, key = { it.id }) { preset ->
            StyleSwatch(
                label = preset.label,
                brush = Brush.linearGradient(preset.colors),
                selected = !hasImage && selectedId == preset.id,
                darkMark = preset.darkText,
                labelColor = labelColor,
                ringColor = ringColor,
                onClick = { onSelect(preset.id) },
            )
        }
    }
}

@Composable
private fun StyleSwatch(
    label: String,
    brush: Brush,
    selected: Boolean,
    darkMark: Boolean,
    labelColor: Color,
    ringColor: Color,
    onClick: () -> Unit,
    autoIcon: Boolean = false,
    photoIcon: Boolean = false,
    /** 抽卡皮肤：色块描一圈稀有度光边，右上角一颗光点 */
    rarityGlow: Color? = null,
    /** 限时皮肤的剩余时间 */
    sublabel: String? = null,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // 选中：外圈留 2dp 空隙再描 2dp 环，像 iOS 的颜色选择
        Box(
            Modifier
                .border(2.dp, if (selected) ringColor else Color.Transparent, RoundedCornerShape(14.dp))
                .padding(4.dp)
                .size(width = 58.dp, height = 37.dp)
                .clip(SwatchShape)
                .background(brush)
                .border(
                    if (rarityGlow != null) 1.5.dp else 1.dp,
                    rarityGlow?.copy(alpha = 0.9f) ?: Color.White.copy(alpha = 0.18f),
                    SwatchShape,
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            val mark = if (darkMark) Color(0xCC1A1A1A) else Color.White
            when {
                selected -> Icon(Icons.Rounded.Check, contentDescription = null, tint = mark, modifier = Modifier.size(18.dp))
                autoIcon -> Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = mark.copy(alpha = 0.85f), modifier = Modifier.size(16.dp))
                photoIcon -> Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, tint = mark.copy(alpha = 0.85f), modifier = Modifier.size(18.dp))
            }
            if (rarityGlow != null) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(rarityGlow)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) ringColor else rarityGlow ?: labelColor,
        )
        if (sublabel != null) {
            Text(sublabel, fontSize = 9.sp, color = labelColor.copy(alpha = 0.8f))
        }
    }
}
