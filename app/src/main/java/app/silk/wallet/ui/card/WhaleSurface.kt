package app.silk.wallet.ui.card

import android.graphics.BitmapShader
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.IntSize
import app.silk.wallet.R
import app.silk.wallet.ui.fx.LocalMotionEnabled
import kotlin.math.roundToInt

/** A layered illustration. The original supplies the static card and the foreground character. */
private const val WHALE_AGSL = """
uniform float2 uSize;
uniform float uTime;
uniform float2 uTilt;
uniform float2 uArtSize;
uniform float2 uWhaleSize;
uniform float2 uMaskSize;
uniform shader uOriginal;
uniform shader uCleanPatch;
uniform shader uWhale;
uniform shader uMasks;

const float TAU = 6.28318530718;

float3 maskAt(float2 uv) {
    // RGB stores the character's occlusion, loose hair, and fingertip masks.
    return float3(uMasks.eval(clamp(uv, 0.0, 1.0) * uMaskSize).rgb);
}

float softLine(float x) {
    return exp(-x * x);
}

float caustics(float2 uv, float phase) {
    float2 water = uv + uTilt * float2(0.086, 0.045);
    float first = sin(water.x * 24.0 + water.y * 13.0 + sin(water.y * 22.0 - phase) * 1.1 + phase * 2.0);
    float second = sin(water.x * 12.0 - water.y * 26.0 + cos(water.x * 19.0 + phase) * 1.4 - phase);
    float seams = softLine((first + second * 0.60) * 7.0);
    return seams * (0.36 + 0.64 * softLine(second * 2.2));
}

half4 main(float2 position) {
    float2 uv = position / uSize;
    float2 art = uv * uArtSize;
    half4 original = uOriginal.eval(art);
    half4 patch = uCleanPatch.eval(art);
    half3 col = original.rgb * (1.0 - patch.a) + patch.rgb;
    float3 masks = maskAt(uv);
    float progress = fract(uTime / 12.0 + 0.34);
    float phase = progress * TAU;
    float openWater = 1.0 - masks.r;
    float depth = smoothstep(0.01, 0.18, progress) * (1.0 - smoothstep(0.76, 0.99, progress));

    // The whale emerges behind the character, then glides left into darker water.
    float x = mix(0.58, -0.23, progress) + uTilt.x * 0.030;
    float y = 0.276 - 0.048 * sin(progress * 3.1415927) + 0.042 * progress + uTilt.y * 0.023;
    float scale = 0.88 + 0.16 * sin(progress * 3.1415927);
    float2 swimmer = (uv - float2(x, y)) / (float2(0.735, 0.356) * scale);
    float2 currentUV = swimmer;
    float bend = smoothstep(0.28, 1.0, swimmer.x);
    swimmer.y += bend * bend * sin(phase * 3.0 - swimmer.x * 4.2) * 0.225;
    swimmer.y += (swimmer.x - 0.24) * (0.14 + 0.085 * cos(phase));
    swimmer.x += (1.0 - smoothstep(0.2, 0.8, swimmer.x)) * sin(phase * 3.0 + swimmer.y * 5.0) * 0.012;
    if (swimmer.x >= 0.0 && swimmer.x <= 1.0 && swimmer.y >= 0.0 && swimmer.y <= 1.0) {
        half4 whale = uWhale.eval(swimmer * uWhaleSize);
        half3 blue = whale.rgb * half3(0.84, 1.03, 1.08) + half3(0.014, 0.07, 0.095) * whale.a;
        float backlight = 1.0 - smoothstep(0.06, 0.73, swimmer.y);
        float flash = softLine((swimmer.x - 0.32 - 0.33 * cos(phase * 2.0) - uTilt.x * 0.32) / 0.19);
        blue += half3(0.10, 0.34, 0.48) * whale.a * backlight * (0.38 + 0.62 * flash) *
            (0.32 + caustics(uv * 0.8, phase) * 0.68);
        col = mix(col, col * (1.0 - whale.a) + blue, openWater * depth * 0.82);
    }

    // The tail leaves a pair of dissolving wakes in its own coordinate space.
    if (currentUV.x > 0.73 && currentUV.x < 1.72 && openWater > 0.01) {
        float downstream = currentUV.x - 0.90;
        float center = 0.06 - sin(phase * 3.0 - currentUV.x * 4.2) * 0.20
            + 0.18 * downstream + 0.06 * sin(downstream * 9.0 + phase);
        float displacement = currentUV.y - center;
        float fade = smoothstep(0.73, 0.96, currentUV.x) * (1.0 - smoothstep(1.10, 1.72, currentUV.x));
        float tip = 0.35 + 0.65 * pow(0.5 + 0.5 * sin(phase * 3.0 - downstream * 13.0), 2.0);
        float wake = 0.62 * softLine(displacement / 0.095)
            + 0.28 * softLine((displacement - 0.18 - downstream * 0.15) / 0.14);
        col += half3(0.11, 0.35, 0.48) * wake * fade * tip * depth * openWater;
    }

    // Flow only through the loose locks. Near the shoulders the hair remains anchored.
    if (masks.g > 0.004) {
        float left = 1.0 - smoothstep(0.54, 0.68, uv.x);
        float right = smoothstep(0.86, 0.995, uv.x);
        float weight = masks.g * max(left, right);
        float wave = phase * 2.0 + uv.x * 16.0 - uv.y * 11.0;
        float2 current = float2(0.010 * sin(wave) + 0.005 * sin(wave * 2.0 + 0.7),
            0.020 * cos(wave) + 0.004 * sin(phase * 3.0 + uv.y * 22.0));
        float2 hairUV = clamp(uv + (current + uTilt * float2(0.012, 0.009)) * weight, 0.0, 1.0);
        half3 hair = uOriginal.eval(hairUV * uArtSize).rgb;
        half4 water = uCleanPatch.eval(hairUV * uArtSize);
        float foreground = maskAt(hairUV).r;
        hair = mix(hair, hair * (1.0 - water.a) + water.rgb, 1.0 - foreground);
        col = mix(col, hair, weight);
    }

    // Bend the tip about a point halfway down the index finger, with the knuckle fixed.
    if (uv.x > 0.735 && uv.x < 0.795 && uv.y > 0.455 && uv.y < 0.562) {
        float2 hinge = float2(0.750, 0.537);
        float2 finger = uv - hinge;
        float angle = 0.142 * sin(phase * 2.0 + 0.3) + 0.024 * uTilt.x;
        float2 fingerUV = hinge + float2(finger.x * cos(angle) - finger.y * sin(angle) * 0.631,
            finger.x * sin(angle) / 0.631 + finger.y * cos(angle));
        float fingerAlpha = maskAt(fingerUV).b;
        col = mix(col, uOriginal.eval(fingerUV * uArtSize).rgb, fingerAlpha);
    }

    // Light breaks at the surface and fans out through the open water.
    if (openWater > 0.008) {
        float2 source = float2(0.46 + uTilt.x * 0.085, -0.12 + uTilt.y * 0.055);
        float spread = (uv.x - source.x) / (uv.y - source.y + 0.12);
        float drift = 0.11 * sin(phase) + uTilt.x * 0.12 - uTilt.y * 0.08;
        float light = softLine((spread - drift + 0.40) / 0.11) * 0.61
            + softLine((spread - drift + 0.13) / 0.085) * 0.88
            + softLine((spread - drift - 0.14) / 0.072) * 0.40;
        float mist = 1.0 - smoothstep(0.05, 0.77, uv.y);
        float surface = 1.0 - smoothstep(0.04, 0.29, uv.y);
        col += half3(0.047, 0.17, 0.23) * openWater *
            (light * mist * (0.66 + 0.34 * cos(phase)) + caustics(uv, phase) * surface * 0.65);
    }

    // Bubbles at different depths rise at different speeds and catch the same light.
    if (uv.x < 0.71 && openWater > 0.01) {
        for (int i = 0; i < 11; i++) {
            float k = float(i);
            float near = fract(k * 0.618 + 0.39);
            float age = fract(phase / TAU * (i < 4 ? 1.0 : 2.0) + k * 0.371 + 0.16);
            float bx = 0.058 + fract(k * 0.733 + 0.24) * 0.54 + 0.022 * sin(age * TAU + k)
                + uTilt.x * (0.022 + near * 0.028);
            float by = 0.93 - age * (0.77 + 0.08 * near) + uTilt.y * 0.014;
            float2 bubble = (uv - float2(bx, by)) * float2(1.586, 1.0);
            float radius = 0.0026 + near * near * 0.011;
            if (max(abs(bubble.x), abs(bubble.y)) > radius * 1.9) continue;
            float r = length(bubble);
            float life = smoothstep(0.0, 0.19, age) * (1.0 - smoothstep(0.76, 1.0, age));
            float rim = softLine((r - radius) / (0.0012 + radius * 0.09));
            float glare = softLine(length(bubble + radius * float2(0.45, 0.52)) / (radius * 0.39));
            float edge = openWater * life * (1.0 - smoothstep(0.63, 0.71, uv.x));
            col *= 1.0 - softLine(r / (radius * 0.76)) * edge * 0.16;
            col += half3(0.46, 0.83, 1.0) * (0.29 * rim + 0.53 * glare) * edge;
        }
    }
    return half4(clamp(col, 0.0, 1.0), 1.0);
}
"""

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
    return surface.drawBehind {
        if (back) drawRect(Color(0x54061021))
        drawRect(
            Brush.verticalGradient(
                0f to Color(0x66061630),
                0.25f to Color.Transparent,
                0.54f to Color.Transparent,
                0.67f to Color(0x66061630),
                1f to Color(0x4D061630),
            )
        )
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun Modifier.animatedWhaleSurface(
    original: ImageBitmap,
    time: State<Float>?,
    tilt: State<Offset>,
): Modifier {
    val cleanPatch = rememberTexture(R.drawable.skin_whale_water)
    val whale = rememberTexture(R.drawable.skin_whale_swimmer)
    val masks = rememberTexture(R.drawable.skin_whale_motion)
    val shader = remember(original, cleanPatch, whale, masks) {
        runCatching {
            RuntimeShader(WHALE_AGSL).apply {
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
