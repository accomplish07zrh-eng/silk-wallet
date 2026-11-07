package app.silk.wallet.ui.theme

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.zIndex
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeChild

/** 磨砂玻璃材质：底层真模糊（API 31+），低版本退化为高不透明度的纯色磨砂 */
@Composable
fun glassStyle(): HazeStyle {
    val dark = LocalDarkTheme.current
    val bg = MaterialTheme.colorScheme.background
    return if (dark) {
        HazeStyle(
            backgroundColor = bg,
            tint = HazeTint(Color(0xFF1C1C22).copy(alpha = 0.50f)),
            blurRadius = 36.dp,
            noiseFactor = 0.06f,
            fallbackTint = HazeTint(Color(0xFF1C1C22).copy(alpha = 0.94f)),
        )
    } else {
        HazeStyle(
            backgroundColor = bg,
            // 重模糊 + 偏透的白：背后卡片的颜色会晕进玻璃里（系统相册那种奶白通透感）
            tint = HazeTint(Color.White.copy(alpha = 0.52f)),
            blurRadius = 36.dp,
            noiseFactor = 0.03f,
            fallbackTint = HazeTint(Color.White.copy(alpha = 0.95f)),
        )
    }
}

/**
 * 玻璃表面：柔和大投影 + 模糊背后内容 + 双层描边——
 * 外缘一道极细暗线定轮廓，内缘一圈顶亮底暗的折射高光，像厚玻璃的倒角。
 * hazeState 为空时（控件本身就在模糊源里，例如页头圆钮）用奶白实色兜底。
 */
@Composable
fun Modifier.glassSurface(hazeState: HazeState?, shape: Shape, elevated: Boolean = true): Modifier {
    val dark = LocalDarkTheme.current
    val rim = if (dark) {
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0.05f)))
    } else {
        Brush.verticalGradient(listOf(Color.White, Color.White.copy(alpha = 0.3f)))
    }
    val hairline = if (dark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.07f)
    val fallback = if (dark) Color(0xFF1C1C22).copy(alpha = 0.92f) else Color.White
    return this
        .then(
            if (elevated) Modifier.shadow(
                if (dark) 8.dp else 16.dp,
                shape,
                ambientColor = Color.Black.copy(alpha = 0.08f),
                spotColor = Color.Black.copy(alpha = 0.16f),
            ) else Modifier
        )
        .clip(shape)
        .then(if (hazeState != null) Modifier.hazeChild(hazeState, glassStyle()) else Modifier.background(fallback))
        .border(2.dp, rim, shape)
        .border(1.dp, hairline, shape)
}

/**
 * 按压回弹：按下时弹簧收缩、松手带一点过冲弹回，替代 Material 水波纹。
 * 缩放放在图层上，只重绘不重排；与同一个 interactionSource 的 clickable 搭配使用。
 */
@Composable
fun Modifier.pressScale(interaction: InteractionSource, pressedScale: Float = 0.9f): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = if (pressed) spring(dampingRatio = 1f, stiffness = 1400f)
        else spring(dampingRatio = 0.45f, stiffness = 520f),
        label = "pressScale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** 可点击 + 按压回弹，一步到位 */
@Composable
fun Modifier.springClickable(
    enabled: Boolean = true,
    pressedScale: Float = 0.9f,
    onClick: () -> Unit,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return pressScale(interaction, pressedScale)
        .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
}

/** 圆形玻璃按钮（右上角搜索 / 更多、详情关闭、页面返回） */
@Composable
fun GlassCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    size: Dp = 46.dp,
    iconSize: Dp = 22.dp,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .pressScale(interaction, 0.88f)
            .size(size)
            .glassSurface(hazeState, CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** 页面背景：纯色底（浅色为纯白），作为玻璃控件的模糊取样源 */
@Composable
fun AppBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        content = content,
    )
}

/** 大标题页头：左上玻璃返回钮 + 右侧操作，下方粗体大标题（设置页、编辑页共用） */
@Composable
fun LargeTitleBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val bg = MaterialTheme.colorScheme.background
    Column(
        modifier
            .zIndex(1f)
            .fillMaxWidth()
            // 标题栏下沿一段渐隐：滚动内容从标题下滑过时柔和消失，而不是被一刀切断
            .drawBehind {
                val fade = 22.dp.toPx()
                drawRect(
                    Brush.verticalGradient(listOf(bg, bg.copy(alpha = 0f)), startY = size.height, endY = size.height + fade),
                    topLeft = Offset(0f, size.height),
                    size = Size(size.width, fade),
                )
            }
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GlassCircleButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                onClick = onBack,
                size = 42.dp,
                iconSize = 20.dp,
            )
            Spacer(Modifier.weight(1f))
            actions()
        }
        Spacer(Modifier.height(14.dp))
        Text(
            title,
            fontFamily = TitleSerif,
            fontSize = 31.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

/** 列表分组卡片的统一外观：白色浮起卡片（见 floatingCard） */
@Composable
fun Modifier.groupedSurface(shape: Shape): Modifier = floatingCard(shape)
