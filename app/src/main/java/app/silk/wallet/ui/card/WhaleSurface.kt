package app.silk.wallet.ui.card

import android.graphics.BitmapShader
import android.graphics.Shader
import android.os.Build
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

@Composable
internal fun Modifier.whaleSurface(
    original: ImageBitmap,
    time: State<Float>?,
    tilt: State<Offset>,
    back: Boolean,
): Modifier {
    val surface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && LocalMotionEnabled.current) {
        animatedWhaleSurface(original, time, tilt)
    } else {
        staticWhaleSurface(original)
    }
    return surface.drawWithCache {
        val scrim = Brush.verticalGradient(
            0f to Color(0x66061630),
            0.25f to Color.Transparent,
            0.54f to Color.Transparent,
            0.67f to Color(0x66061630),
            1f to Color(0x4D061630),
        )
        onDrawBehind {
            if (back) drawRect(Color(0x54061021))
            drawRect(scrim)
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun Modifier.animatedWhaleSurface(
    original: ImageBitmap,
    time: State<Float>?,
    tilt: State<Offset>,
): Modifier {
    val resources = LocalContext.current.resources
    val cleanPatch = rememberTexture(R.drawable.skin_whale_water)
    val whale = rememberTexture(R.drawable.skin_whale_swimmer)
    val masks = rememberTexture(R.drawable.skin_whale_motion)
    val shader = remember(resources, original, cleanPatch, whale, masks) {
        runCatching {
            SiliconProgram.shader(resources, R.raw.silicon_whale, CardEffect.WHALE).apply {
                setInputShader("uOriginal", original.toWhaleShader())
                setInputShader("uCleanPatch", cleanPatch.toWhaleShader())
                setInputShader("uWhale", whale.toWhaleShader())
                setInputBuffer("uMasks", masks.toWhaleShader())
                setFloatUniform("uArtSize", original.width.toFloat(), original.height.toFloat())
                setFloatUniform("uWhaleSize", whale.width.toFloat(), whale.height.toFloat())
                setFloatUniform("uMaskSize", masks.width.toFloat(), masks.height.toFloat())
            }
        }.onFailure { android.util.Log.e("Silk", "硅基神话-DeepSeek 着色器编译失败", it) }.getOrNull()
    } ?: return staticWhaleSurface(original)
    val brush = remember(shader) { ShaderBrush(shader) }
    return drawBehind {
        if (size.width <= 0f || size.height <= 0f) return@drawBehind
        shader.setFloatUniform("uSize", size.width, size.height)
        shader.setFloatUniform("uTime", time?.value ?: 0f)
        val angle = tilt.value
        shader.setFloatUniform("uTilt", angle.x, angle.y)
        drawRect(brush)
    }
}

private fun Modifier.staticWhaleSurface(original: ImageBitmap) = drawBehind {
    drawImage(original, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()), filterQuality = FilterQuality.High)
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun ImageBitmap.toWhaleShader(): BitmapShader =
    BitmapShader(asAndroidBitmap(), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
        filterMode = BitmapShader.FILTER_MODE_LINEAR
    }
