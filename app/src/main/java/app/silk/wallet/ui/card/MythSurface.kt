package app.silk.wallet.ui.card

import android.content.res.Resources
import android.graphics.BitmapShader
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.annotation.RawRes
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import app.silk.wallet.R
import app.silk.wallet.domain.CardEffect
import app.silk.wallet.ui.fx.LocalMotionEnabled
import kotlin.math.roundToInt

private enum class MythScene(
    val effect: Int,
    @DrawableRes val mask: Int,
    val period: Float,
    val leftShade: Float,
    val footShade: Float,
    val topShade: Float,
    val numberShade: Float,
    val faceX: Float,
    val faceY: Float,
) {
    CLAUDE(CardEffect.CLAUDE, R.drawable.skin_claude_motion, 10f, 0.33f, 0.52f, 0.38f, 0.27f, 0.672f, 0.304f),
    GPT(CardEffect.GPT, R.drawable.skin_gpt_motion, 10f, 0.27f, 0.48f, 0.33f, 0.24f, 0.591f, 0.250f),
    GEMINI(CardEffect.GEMINI, R.drawable.skin_gemini_motion, 9f, 0.41f, 0.54f, 0.30f, 0.14f, 0.400f, 0.362f),
    GROK(CardEffect.GROK, R.drawable.skin_grok_motion, 12f, 0.29f, 0.38f, 0.27f, 0.07f, 0.677f, 0.284f),
    GLM(CardEffect.GLM, R.drawable.skin_glm_motion, 11f, 0.34f, 0.46f, 0.32f, 0.10f, 0.380f, 0.385f),
    ERNIE(CardEffect.ERNIE, R.drawable.skin_ernie_motion, 13f, 0.40f, 0.55f, 0.36f, 0.10f, 0.519f, 0.301f),
    QWEN(CardEffect.QWEN, R.drawable.skin_qwen_motion, 12f, 0.35f, 0.56f, 0.37f, 0.14f, 0.639f, 0.251f),
    KIMI(CardEffect.KIMI, R.drawable.skin_kimi_motion, 12f, 0.36f, 0.48f, 0.29f, 0.44f, 0.482f, 0.280f),
    MINIMAX(CardEffect.MINIMAX, R.drawable.skin_minimax_motion, 10f, 0.45f, 0.64f, 0.48f, 0.34f, 0.635f, 0.350f),
}

@Composable
internal fun Modifier.mythSurface(
    original: ImageBitmap,
    effect: Int,
    time: State<Float>?,
    tilt: State<Offset>,
    back: Boolean,
): Modifier {
    val scene = MythScene.entries.firstOrNull { it.effect == effect } ?: return stillMyth(original)
    val surface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && LocalMotionEnabled.current) {
        animatedMyth(original, scene, time, tilt)
    } else {
        stillMyth(original)
    }
    return surface.mythScrim(scene, back)
}

internal object SiliconProgram {
    private val sources = mutableMapOf<Int, String>()

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun shader(resources: Resources, @RawRes resource: Int, effect: Int): RuntimeShader {
        val text = synchronized(this) {
            sources.getOrPut(effect) {
                val scene = resources.openRawResource(resource).bufferedReader().use { it.readText() }
                val portrait = resources.openRawResource(R.raw.silicon_portrait).bufferedReader().use { it.readText() }
                // A fixed scene lets the compiler discard the other scenes' effects.
                scene.replace("// SILICON_PORTRAIT", portrait)
                    .replace("const int SCENE = 0;", "const int SCENE = $effect;")
            }
        }
        return RuntimeShader(text)
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun Modifier.animatedMyth(
    original: ImageBitmap,
    scene: MythScene,
    time: State<Float>?,
    tilt: State<Offset>,
): Modifier {
    val resources = LocalContext.current.resources
    val mask = rememberTexture(scene.mask)
    val shader = remember(original, scene, mask, resources) {
        runCatching {
            SiliconProgram.shader(resources, R.raw.silicon_myths, scene.effect).apply {
                setInputShader("uArt", original.asMythShader())
                setInputBuffer("uMask", mask.asMythShader())
                setFloatUniform("uArtSize", original.width.toFloat(), original.height.toFloat())
                setFloatUniform("uMaskSize", mask.width.toFloat(), mask.height.toFloat())
                setFloatUniform("uFace", scene.faceX, scene.faceY)
            }
        }.onFailure { Log.e("Silk", "硅基神话着色器初始化失败", it) }.getOrNull()
    } ?: return stillMyth(original)
    val brush = remember(shader) { ShaderBrush(shader) }
    val response = remember(shader) { SiliconTilt() }
    return drawBehind {
        if (size.width <= 0f || size.height <= 0f) return@drawBehind
        val seconds = time?.value
        response.update(seconds, tilt.value)
        val phase = ((seconds ?: 0f) % scene.period) / scene.period * (2f * Math.PI.toFloat())
        shader.setFloatUniform("uSize", size.width, size.height)
        shader.setFloatUniform("uTilt", response.angle.x, response.angle.y)
        shader.setFloatUniform("uMomentum", response.momentum.x, response.momentum.y)
        shader.setFloatUniform("uPhase", phase)
        drawRect(brush)
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun ImageBitmap.asMythShader(): BitmapShader =
    BitmapShader(asAndroidBitmap(), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
        filterMode = BitmapShader.FILTER_MODE_LINEAR
    }

private fun Modifier.stillMyth(original: ImageBitmap) = drawBehind {
    drawImage(
        original,
        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
        filterQuality = FilterQuality.High,
    )
}

/** Protect the card data while keeping the portrait and its brightest focal details visible. */
private fun Modifier.mythScrim(scene: MythScene, back: Boolean) = drawWithCache {
    val ink = Color(0xFF08101D)
    val foot = Brush.verticalGradient(
        0f to Color.Transparent,
        0.54f to Color.Transparent,
        0.74f to ink.copy(alpha = scene.footShade * 0.68f),
        1f to ink.copy(alpha = scene.footShade),
    )
    val left = Brush.radialGradient(
        0f to ink.copy(alpha = scene.leftShade),
        0.53f to ink.copy(alpha = scene.leftShade * 0.78f),
        1f to Color.Transparent,
        center = Offset(size.width * 0.085f, size.height * 0.64f),
        radius = size.width * 0.57f,
    )
    val top = Brush.verticalGradient(
        0f to ink.copy(alpha = scene.topShade),
        0.115f to ink.copy(alpha = scene.topShade * 0.72f),
        0.245f to Color.Transparent,
        1f to Color.Transparent,
    )
    val number = Brush.verticalGradient(
        0f to Color.Transparent,
        0.50f to Color.Transparent,
        0.625f to ink.copy(alpha = scene.numberShade),
        0.704f to ink.copy(alpha = scene.numberShade * 0.85f),
        0.84f to Color.Transparent,
        1f to Color.Transparent,
    )
    onDrawBehind {
        drawRect(foot)
        drawRect(left)
        drawRect(top)
        drawRect(number)
        if (back) drawRect(ink.copy(alpha = 0.31f))
    }
}
