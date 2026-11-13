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
import androidx.compose.ui.draw.drawWithCache
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

float2 metric(float2 delta) {
    return delta * float2(1.586, 1.0);
}

float3 maskAt(float2 uv) {
    // RGB stores the character's occlusion, loose hair, and fingertip masks.
    return float3(uMasks.eval(clamp(uv, 0.0, 1.0) * uMaskSize).rgb);
}

float softLine(float x) {
    return exp(-x * x);
}

float life(float t) {
    return smoothstep(0.0, 0.19, t) * (1.0 - smoothstep(0.76, 1.0, t));
}

float caustics(float2 uv, float phase) {
    float2 water = uv + uTilt * float2(0.086, 0.045);
    float first = sin(water.x * 24.0 + water.y * 13.0 + sin(water.y * 22.0 - phase) * 1.1 + phase * 2.0);
    float second = sin(water.x * 12.0 - water.y * 26.0 + cos(water.x * 19.0 + phase) * 1.4 - phase);
    float seam = first + second * 0.60;
    float narrow = softLine(seam * 7.0);
    float crossing = softLine(second * 2.2);
    return narrow * (0.36 + 0.64 * crossing) + 0.18 * softLine((seam - 0.21) * 4.0) * crossing;
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
    float depth = smoothstep(0.01, 0.18, progress) * (1.0 - smoothstep(0.78, 0.998, progress));

    // The whale emerges behind the character, then glides left into darker water.
    float dive = smoothstep(0.50, 0.97, progress);
    float stroke = phase * 3.0 - 0.8;
    float x = mix(0.58, -0.23, progress) + 0.014 * sin(stroke) + uTilt.x * 0.048;
    float y = 0.275 - 0.060 * sin(progress * 3.1415927) + 0.075 * dive + 0.009 * cos(stroke) + uTilt.y * 0.033;
    float scale = 0.88 + 0.17 * sin(progress * 3.1415927) - dive * 0.060;
    float2 swimmer = (uv - float2(x, y)) / (float2(0.735, 0.356) * scale);
    float2 currentUV = swimmer;
    float bend = smoothstep(0.28, 1.0, swimmer.x);
    swimmer.y += bend * bend * sin(stroke - swimmer.x * 4.2 + 0.8) * 0.27;
    swimmer.y += (swimmer.x - 0.24) * (0.12 + 0.085 * cos(phase) - dive * 0.11);
    swimmer.x += (1.0 - smoothstep(0.2, 0.8, swimmer.x)) * sin(phase * 3.0 + swimmer.y * 5.0) * 0.012;
    // The long pectoral fin rolls independently. Its root stays attached to the body.
    float fin = softLine((swimmer.x - 0.32 - (swimmer.y - 0.62) * 0.28) / 0.18)
        * smoothstep(0.48, 0.94, swimmer.y);
    swimmer.x += fin * (0.052 * sin(stroke - 1.3) + uTilt.x * 0.026);
    swimmer.y += fin * (0.062 * cos(stroke - 1.3) + uTilt.y * 0.028);
    if (swimmer.x >= 0.0 && swimmer.x <= 1.0 && swimmer.y >= 0.0 && swimmer.y <= 1.0) {
        half4 whale = uWhale.eval(swimmer * uWhaleSize);
        half3 blue = whale.rgb * mix(half3(0.86, 1.03, 1.10), half3(0.48, 0.72, 0.90), dive * 0.75)
            + half3(0.012, 0.053, 0.079) * whale.a;
        float backlight = 1.0 - smoothstep(0.06, 0.73, swimmer.y);
        float flash = softLine((swimmer.x - 0.32 - 0.36 * cos(phase * 2.0) - uTilt.x * 0.36) / 0.20);
        float reflected = caustics(uv * 0.82, phase);
        blue += half3(0.12, 0.37, 0.50) * whale.a * backlight * (0.26 + 0.74 * flash) *
            (0.24 + reflected * 0.76) * (1.0 - dive * 0.56);
        col = mix(col, col * (1.0 - whale.a) + blue, openWater * depth * 0.82);
    }

    // The wake lags the fin beat, opening into a wider, darker ribbon as it disperses.
    if (currentUV.x > 0.76 && currentUV.x < 1.79 && openWater > 0.01) {
        float downstream = currentUV.x - 0.90;
        float age = max(0.0, downstream);
        float center = 0.10 - sin(stroke - currentUV.x * 4.2 + 0.8 - age * 5.2) * 0.22
            + 0.16 * downstream + 0.065 * sin(downstream * 8.0 + phase);
        float displacement = currentUV.y - center;
        float fade = smoothstep(0.76, 1.00, currentUV.x) * (1.0 - smoothstep(1.11, 1.79, currentUV.x));
        float tip = 0.32 + 0.68 * pow(0.5 + 0.5 * sin(stroke - downstream * 14.0), 2.0);
        float spread = 0.041 + age * 0.19;
        float wake = 0.65 * softLine((displacement + spread) / (0.041 + age * 0.09))
            + 0.37 * softLine((displacement - spread) / (0.055 + age * 0.09));
        float flow = fade * tip * depth * openWater;
        col *= 1.0 - softLine(displacement / (0.090 + age * 0.15)) * flow * 0.12;
        col += half3(0.105, 0.32, 0.42) * wake * flow;
    }

    // Flow only through the loose locks. Near the shoulders the hair remains anchored.
    if (masks.g > 0.004) {
        float left = 1.0 - smoothstep(0.54, 0.68, uv.x);
        float right = smoothstep(0.86, 0.995, uv.x);
        float weight = masks.g * max(left, right);
        float wave = phase * 2.0 + uv.x * 15.0 - uv.y * 12.0;
        float recoil = sin(phase - uv.y * 5.0);
        float2 current = float2(0.014 * sin(wave) + 0.006 * sin(wave * 2.0 + 0.7) + 0.008 * recoil,
            0.023 * cos(wave) + 0.006 * sin(phase * 3.0 + uv.y * 23.0));
        float2 hairUV = clamp(uv + (current + uTilt * float2(0.018, 0.013)) * weight, 0.0, 1.0);
        half3 hair = uOriginal.eval(hairUV * uArtSize).rgb;
        half4 water = uCleanPatch.eval(hairUV * uArtSize);
        float foreground = maskAt(hairUV).r;
        hair = mix(hair, hair * (1.0 - water.a) + water.rgb, 1.0 - foreground);
        float strands = smoothstep(0.28, 0.68, hair.b) * foreground;
        hair += half3(0.035, 0.10, 0.15) * strands * softLine(sin(wave + uTilt.x * 2.8) * 2.5);
        col = mix(col, hair, weight);
    }

    // Bend the tip about a point halfway down the index finger, with the knuckle fixed.
    if (uv.x > 0.735 && uv.x < 0.795 && uv.y > 0.455 && uv.y < 0.562) {
        float2 hinge = float2(0.750, 0.537);
        float2 finger = uv - hinge;
        float angle = 0.131 * sin(phase * 2.0 + 0.3) + 0.028 * sin(phase * 3.0 - 0.7) + 0.032 * uTilt.x;
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
        float horizon = light * mist * (0.68 + 0.32 * cos(phase));
        float scatter = caustics(uv, phase);
        col += half3(0.042, 0.148, 0.207) * openWater * (horizon + scatter * surface * 0.72);
        col += half3(0.076, 0.20, 0.24) * openWater * scatter * horizon * 0.23;
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
            float2 bubble = metric(uv - float2(bx, by));
            float radius = 0.0026 + near * near * 0.011;
            if (max(abs(bubble.x), abs(bubble.y)) > radius * 1.9) continue;
            bubble.y *= 1.0 + 0.16 * sin(phase * 3.0 + k);
            float r = length(bubble);
            float rim = softLine((r - radius) / (0.0012 + radius * 0.09));
            float2 lightSide = float2(0.45 + 0.26 * uTilt.x, 0.52 + 0.22 * uTilt.y);
            float glare = softLine(length(bubble + radius * lightSide) / (radius * 0.33));
            float bounce = softLine(length(bubble - radius * lightSide) / (radius * 0.39));
            float edge = openWater * life(age) * (1.0 - smoothstep(0.63, 0.71, uv.x));
            col *= 1.0 - softLine(r / (radius * 0.76)) * edge * (0.13 + near * 0.13);
            col += half3(0.38, 0.74, 0.96) * (rim * (0.19 + near * 0.15) + glare * 0.58 + bounce * 0.18) * edge;
        }
    }

    // A compact shoal turns as the whale passes. Its silver flanks flash in sequence.
    if (uv.x < 0.62 && uv.y > 0.29 && uv.y < 0.59 && openWater > 0.02) {
        float schoolAge = fract(uTime / 12.0 + 0.86);
        float2 school = float2(0.04 + schoolAge * 0.52, 0.53 - schoolAge * 0.22)
            + uTilt * float2(0.032, 0.025);
        float2 d = metric(uv - school);
        if (abs(d.x) < 0.13 && abs(d.y) < 0.069) {
            float strength = life(schoolAge) * openWater * (1.0 - smoothstep(0.55, 0.62, uv.x))
                * smoothstep(0.29, 0.32, uv.y) * (1.0 - smoothstep(0.55, 0.59, uv.y));
            for (int i = 0; i < 9; i++) {
                float k = float(i);
                float2 fish = d - float2((fract(k * 0.61 + 0.20) - 0.5) * 0.20,
                    (fract(k * 0.38 + 0.13) - 0.5) * 0.083 + 0.014 * sin(stroke + k * 0.7));
                if (abs(fish.x) > 0.016 || abs(fish.y) > 0.012) continue;
                float turning = sin(stroke + k * 0.47 + uTilt.x * 2.1);
                fish.y += fish.x * (0.26 + 0.30 * turning);
                float flank = softLine(length(fish * float2(0.38, 1.0)) / 0.0028);
                float tail = softLine((fish.x + 0.008) / 0.0028) * softLine((abs(fish.y) - 0.0021) / 0.0017);
                float glint = 0.18 + 0.82 * turning * turning;
                col += half3(0.27, 0.61, 0.78) * (flank * 0.62 + tail * 0.24) * glint * strength;
            }
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
