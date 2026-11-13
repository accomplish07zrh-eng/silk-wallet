package app.silk.wallet.ui.card

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
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
    val styles = remember { CardStyles.skins + CardStyles.presets }
    val listState = rememberLazyListState()
    val leadingItems = if (onPickImage != null) 2 else 1
    LaunchedEffect(selectedId, hasImage, leadingItems) {
        val index = if (hasImage) 0 else {
            val styleIndex = styles.indexOfFirst { it.id == selectedId }
            if (styleIndex >= 0) leadingItems + styleIndex else 0
        }
        if (listState.layoutInfo.visibleItemsInfo.none { it.index == index }) listState.scrollToItem(index)
    }
    LazyRow(
        state = listState,
        modifier = modifier.selectableGroup(),
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
        items(styles, key = { it.id }) { preset ->
            StyleSwatch(
                label = preset.label,
                brush = Brush.linearGradient(preset.colors),
                selected = !hasImage && selectedId == preset.id,
                darkMark = preset.darkText,
                labelColor = labelColor,
                ringColor = ringColor,
                onClick = { onSelect(preset.id) },
                artwork = preset.texture?.let { rememberTexture(it, thumbnail = true) },
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
    artwork: ImageBitmap? = null,
) {
    Column(
        modifier = Modifier.selectable(selected, role = Role.RadioButton, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 选中：外圈留 2dp 空隙再描 2dp 环，像 iOS 的颜色选择
        Box(
            Modifier
                .border(2.dp, if (selected) ringColor else Color.Transparent, RoundedCornerShape(14.dp))
                .padding(4.dp)
                .size(width = 58.dp, height = 37.dp)
                .clip(SwatchShape)
                .background(brush)
                .border(1.dp, Color.White.copy(alpha = 0.18f), SwatchShape),
            contentAlignment = Alignment.Center,
        ) {
            if (artwork != null) {
                Image(
                    bitmap = artwork,
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            val mark = if (darkMark) Color(0xCC1A1A1A) else Color.White
            when {
                selected -> Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)).padding(4.dp),
                )
                autoIcon -> Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = mark.copy(alpha = 0.85f), modifier = Modifier.size(16.dp))
                photoIcon -> Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, tint = mark.copy(alpha = 0.85f), modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) ringColor else labelColor,
        )
    }
}
