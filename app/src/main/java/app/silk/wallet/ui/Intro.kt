package app.silk.wallet.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.silk.wallet.ui.theme.WordmarkFont
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 冷启动字标动画的总时长；首页进入动效在字标开始淡出时接力 */
const val INTRO_HANDOFF_MS = 720L

/**
 * 冷启动：「Silk」字标从略小、模糊透明处浮现，一道丝绸色扫光掠过字面，
 * 随后整层上移淡出，露出正在依次浮入的首页。
 */
@Composable
fun Intro(onDone: () -> Unit) {
    val show = remember { Animatable(0f) }
    val sweep = remember { Animatable(0f) }
    val leave = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        coroutineScope {
            launch { show.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 140f)) }
            launch {
                delay(160)
                sweep.animateTo(1f, tween(620, easing = FastOutSlowInEasing))
            }
        }
        delay(INTRO_HANDOFF_MS - 620 - 160 + 80)
        leave.animateTo(1f, tween(380, easing = LinearEasing))
        onDone()
    }
    val bg = MaterialTheme.colorScheme.background
    val ink = MaterialTheme.colorScheme.onBackground
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - leave.value }
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Silk",
            fontFamily = WordmarkFont,
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.Bold,
            fontSize = 64.sp,
            color = ink,
            modifier = Modifier
                .graphicsLayer {
                    val v = show.value
                    alpha = v.coerceIn(0f, 1f)
                    val s = 0.86f + 0.14f * v - 0.04f * leave.value
                    scaleX = s
                    scaleY = s
                    translationY = (1f - v) * 18.dp.toPx() - leave.value * 26.dp.toPx()
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawWithContent {
                    drawContent()
                    // 扫光只染在字形上（SrcAtop），从左下掠到右上
                    val x = -size.width + sweep.value * size.width * 2.6f
                    drawRect(
                        Brush.linearGradient(
                            listOf(Color.Transparent, Color(0xFF7FA8FF), Color(0xFFC79BFF), Color(0xFFFFC9A8), Color.Transparent),
                            start = Offset(x, size.height),
                            end = Offset(x + size.width * 0.8f, 0f),
                        ),
                        blendMode = BlendMode.SrcAtop,
                    )
                },
        )
    }
}
