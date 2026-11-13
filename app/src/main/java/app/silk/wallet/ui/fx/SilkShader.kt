package app.silk.wallet.ui.fx

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.IntSize
import app.silk.wallet.domain.CardEffect
import kotlin.math.roundToInt

/**
 * 液态丝绸卡面（AGSL，Android 13+）。从下往上五层：
 *
 * 1. 底色流场：三层正弦域扭曲推着三段渐变缓慢流动（周期几十秒，静看是“活的”）；
 * 2. 丝绸褶皱光照：同一流场生成一张高度场（两组斜向长波 + 一层细波），
 *    有限差分求法线，用一盏随设备倾斜 / 手指拖动移动的光源做漫反射 + 镜面高光——
 *    褶皱的峰谷有真实的明暗，峰顶滑过缎面般的锐利光泽；
 * 3. 薄膜虹彩：按法线与视线夹角取油膜干涉色，只渗进高光和雕刻纹里，随角度流转；
 * 4. 金属闪粉：每 2.5px 一格，部分格子里有一片朝向随机的微小金属片，
 *    只有法线恰好对准半程向量时才亮——倾斜时满卡此起彼伏地闪；
 * 5. 扭索纹雕刻 + 边缘辉光 + 暗角 + 横向拉丝颗粒 + 抖动去色带。
 */
private const val FLOW_AGSL = """
uniform float2 uSize;
uniform float uTime;
uniform float2 uTilt;
uniform float uLight;
uniform float uMode;
uniform float2 uFlow;
uniform shader uPhoto;
uniform float uPhotoOn;
uniform float2 uPhotoSize;
layout(color) uniform half4 uC0;
layout(color) uniform half4 uC1;
layout(color) uniform half4 uC2;

float hash(float2 p) {
    return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453);
}

float gaussian(float x) {
    return exp(-x * x);
}

float3 textureColor(float2 uv) {
    return float3(uPhoto.eval(clamp(uv, 0.0, 1.0) * uPhotoSize).rgb);
}

float3 ramp(float x) {
    x = clamp(x, 0.0, 1.0);
    float3 a = float3(uC0.rgb);
    float3 b = float3(uC1.rgb);
    float3 c = float3(uC2.rgb);
    return x < 0.5 ? mix(a, b, x * 2.0) : mix(b, c, x * 2.0 - 1.0);
}

// 绕灰轴旋转色相：保持明暗，只让颜色随角度流转
float3 hueShift(float3 c, float a) {
    float3 k = float3(0.57735);
    float ca = cos(a);
    return c * ca + cross(k, c) * sin(a) + k * dot(k, c) * (1.0 - ca);
}

// 极光帘幕：三层波动的光幕，竖向光柱在里面流动；颜色自下而上青绿 → 青蓝 → 紫
float3 auroraCurtains(float2 uv, float t, float aspect, float sway) {
    float3 acc = float3(0.0);
    for (int k = 0; k < 3; k++) {
        float fk = float(k);
        float x = uv.x * aspect;
        float base = 0.40 + fk * 0.07 + 0.06 * sin(x * 1.3 + t * 0.22 + fk * 2.0) + 0.03 * sin(x * 3.1 - t * 0.31 + fk)
            + sway * 0.05 * sin(x * 2.0 + fk);
        float dy = base - uv.y;
        // 光幕：底边清晰，向上柔和淡出
        float body = smoothstep(-0.008, 0.004, dy) * exp(-max(dy, 0.0) * (7.0 - fk * 1.2));
        // 细密光丝：多个频率叠在一起，避免整齐的梳齿
        float xw = x + 0.05 * sin(uv.y * 12.0 + t * 0.5 + fk);
        float rays = 0.45 * sin(xw * 70.0 + t * 0.6 + fk * 4.0) + 0.35 * sin(xw * 123.0 - t * 0.9 + fk) + 0.2 * sin(xw * 41.0 + t * 0.3);
        rays = 0.72 + 0.28 * rays;
        // 光幕沿水平方向一段亮一段暗（真实极光不会均匀铺满）
        float patch = smoothstep(0.02, 0.7, 0.5 + 0.5 * sin(x * 1.7 - t * 0.12 + fk * 2.4) * cos(x * 0.9 + t * 0.07 + fk));
        // 亮度脉冲沿光幕跑过
        float pulse = 0.6 + 0.4 * pow(0.5 + 0.5 * sin(x * 2.2 - t * 1.1 + fk * 2.1), 2.0);
        float3 c = mix(float3(0.2, 0.95, 0.6), float3(0.25, 0.75, 1.0), clamp(dy * 4.0, 0.0, 1.0));
        c = mix(c, float3(0.6, 0.4, 1.0), clamp(dy * 4.0 - 1.0, 0.0, 1.0));
        float fringe = gaussian((dy + 0.003) / 0.008);
        acc += (c * body * rays + float3(1.0, 0.3, 0.65) * fringe * 0.5) * patch * pulse * (0.62 - fk * 0.14);
    }
    return acc;
}

// 水银滴的环形波：xy 是高度场斜率，z 是反光的波脊。
float3 dropletRipples(float2 p, float t, float aspect) {
    float3 waves = float3(0.0);
    for (int k = 0; k < 3; k++) {
        float fk = float(k);
        float period = 4.2;
        float tt = t + fk * 1.4 + 0.33;
        float slot = floor(tt / period);
        float lt = tt - slot * period;
        float2 c = float2(0.18 + 0.64 * hash(float2(slot, fk + 1.0)), 0.30 + 0.32 * hash(float2(slot, fk + 7.0)));
        c.x *= aspect;
        float2 d = p - c;
        float r = length(d);
        float distance = r - lt * 0.23;
        float wave = distance * 103.0;
        float amp = exp(-lt * 1.30) * smoothstep(0.0, 0.09, lt) * (1.0 - smoothstep(2.8, period, lt));
        float band = gaussian(distance / 0.025) * amp;
        float slope = band * (103.0 * cos(wave) - distance * 3200.0 * sin(wave));
        waves.xy += d / max(r, 0.001) * slope * 0.24;
        waves.z += amp * (gaussian(distance / 0.0055) + 0.38 * gaussian((distance + 0.027) / 0.004));
    }
    return waves;
}

// 三组正弦波叠加的高度场斜率。
float2 chromeSlope(float2 p, float t) {
    float2 a = float2(5.0, 1.5);
    float2 b = float2(-2.3, 6.1);
    float2 c = float2(8.0, 8.0);
    return a * cos(dot(p, a) - t * 1.1) * 0.28
         + b * cos(dot(p, b) + t * 0.9) * 0.20
         + c * cos(dot(p, c) - t * 1.5) * 0.09;
}

float2 warp(float2 p, float t) {
    float2 q = p;
    q += 0.20 * float2(sin(q.y * 2.3 + t * 0.21 + uTilt.x * 0.9), cos(q.x * 1.9 - t * 0.17 + uTilt.y * 0.9));
    q += 0.10 * float2(sin(q.y * 4.1 - t * 0.29), cos(q.x * 3.7 + t * 0.23));
    q += 0.04 * float2(sin(q.y * 8.3 + t * 0.37), cos(q.x * 7.9 - t * 0.31));
    return q;
}

// 丝绸褶皱高度场
float silk(float2 q, float t) {
    return 0.55 * sin((q.x * 1.6 + q.y * 2.4) * 2.0 - t * 0.33)
         + 0.30 * sin((q.x * -2.1 + q.y * 1.3) * 2.6 + t * 0.19 + 1.3)
         + 0.08 * sin((q.x * 5.3 + q.y * 3.1) * 2.0 - t * 0.41);
}

half4 main(float2 fc) {
    float2 uv = fc / uSize;
    float aspect = uSize.x / uSize.y;
    float2 p = float2(uv.x * aspect, uv.y);
    float edge = min(min(uv.x, 1.0 - uv.x) * aspect, min(uv.y, 1.0 - uv.y));
    float edgeGlow = 1.0 - smoothstep(0.003, 0.033, edge);
    float t = uTime;
    bool lightCard = uLight > 0.5;
    int m = int(uMode + 0.5);

    // —— 1. 底色流场 ——
    // 底色流场的时间：普通卡面流快一些，否则看上去像静止
    float tf = t * (uMode < 0.5 ? 1.7 : 1.0);
    float2 q = warp(p, tf);
    float h = silk(q, tf);
    float diag = (q.x / aspect) * 0.58 + q.y * 0.42;
    float3 col = ramp(diag);
    col = mix(col, float3(uC0.rgb), smoothstep(0.1, 1.0, h) * 0.26);
    col = mix(col, float3(uC2.rgb), smoothstep(0.1, 1.0, -h) * 0.22);

    // —— 2. 褶皱法线 + 光照 ——
    float e = 1.5 / uSize.y;
    float hx = silk(warp(p + float2(e, 0.0), tf), tf);
    float hy = silk(warp(p + float2(0.0, e), tf), tf);
    float relief = 0.11;
    float3 N = normalize(float3(-(hx - h) / e * relief, -(hy - h) / e * relief, 1.0));
    bool photo = uPhotoOn > 0.5;
    if (photo && m == 0) {
        // 照片底：按褶皱法线轻微折射取样，像隔着一层流动的液态玻璃看照片
        float2 puv = clamp(uv + N.xy * 0.012, 0.0, 1.0);
        col = float3(uPhoto.eval(puv * uPhotoSize).rgb);
    }
    // 光从左上方来，随倾斜 / 拖动移动
    float3 L = normalize(float3(-0.35 - uTilt.x * 1.2, -0.55 - uTilt.y * 1.2, 1.0));
    float3 H = normalize(L + float3(0.0, 0.0, 1.0));
    float diffuse = dot(N, L);
    col *= photo ? (0.88 + 0.22 * diffuse) : (0.74 + 0.40 * diffuse);
    float ndh = max(dot(N, H), 0.0);
    float spec = pow(ndh, 90.0) * 0.58 + pow(ndh, 18.0) * 0.09;
    float3 sheen = mix(float3(1.0), min(float3(uC0.rgb) * 1.6, float3(1.0)), 0.30);
    col += spec * sheen * (lightCard ? 0.55 : (photo ? 0.6 : 1.0));

    // —— 3. 薄膜虹彩 ——
    float phase = N.x * 2.4 - N.y * 1.8 + uTilt.x * 1.5 - uTilt.y * 1.1 + h * 0.5;
    float3 irid = 0.5 + 0.5 * cos(6.2831 * (phase + float3(0.0, 0.33, 0.67)));

    // —— 5a. 扭索纹（右上一片，线宽约 1.6px），纹里透出虹彩 ——
    float w1 = p.y + 0.055 * sin(p.x * 8.0 + t * 0.12) + 0.025 * sin(p.x * 21.0 - t * 0.08);
    float w2 = p.y - 0.055 * sin(p.x * 8.0 + 1.4 - t * 0.10) - 0.025 * sin(p.x * 17.0 + t * 0.07);
    float lw = 1.6 * 22.0 / uSize.y;
    float l1 = abs(fract(w1 * 22.0) - 0.5) * 2.0;
    float l2 = abs(fract(w2 * 22.0) - 0.5) * 2.0;
    float lines = smoothstep(1.0 - lw, 1.0, l1) + smoothstep(1.0 - lw, 1.0, l2);
    float mask = 1.0 - smoothstep(0.15, 0.95, length((uv - float2(0.88, 0.22)) * float2(1.0, 1.35)));
    float engrave = clamp(lines, 0.0, 1.0) * mask;
    if (!photo) {
        col += engrave * (lightCard ? -0.05 : 0.06);
        col += engrave * (irid - 0.5) * (lightCard ? 0.18 : 0.30);
    }
    col += spec * (irid - 0.5) * 0.28;

    // —— 4. 金属闪粉 ——
    if (!photo || m == 0) {
        float2 cell = floor(fc / 2.5);
        float r = hash(cell);
        float3 fn = normalize(float3(hash(cell + 7.13) - 0.5, hash(cell + 3.71) - 0.5, 0.55));
        float flake = pow(max(dot(fn, H), 0.0), 360.0) * step(0.86, r);
        col += flake * (lightCard ? 0.6 : 1.1) * mix(float3(1.0), irid, 0.4);
    }

    // —— 6. 皮肤特效（uMode 见 CardEffect）——
    if (m == 1) {
        // 镭射全息：整卡彩虹衍射，随角度与褶皱流转；叠一层极细的衍射光栅
        float3 rb = 0.5 + 0.5 * cos(6.2831 * (diag * 1.6 + h * 0.25 + uTilt.x * 0.9 - uTilt.y * 0.7 + t * 0.02 + float3(0.0, 0.33, 0.67)));
        // 浅色镭射卡让彩虹融进自身底色（珍珠紫灰 / 玫瑰粉各有性格），深色卡保持满彩
        if (lightCard) rb = mix(rb, float3(uC1.rgb), 0.5);
        col = mix(col, rb * (lightCard ? 1.0 : 0.92), 0.40 + 0.35 * spec);
        float grate = 0.5 + 0.5 * sin(p.x * 140.0 + p.y * 46.0);
        col += (grate - 0.5) * 0.05;
        col += spec * 0.35;
    } else if (m == 2) {
        // 星河：斜穿卡面的银河带 + 星云 + 各自闪烁的星点
        float z = (uv.y - 0.45 - 0.25 * (uv.x - 0.5) + 0.06 * sin(uv.x * 6.0 + t * 0.1)) * 4.5;
        float band2 = exp(-z * z);
        float neb = 0.5 + 0.5 * sin(q.x * 5.0 + q.y * 3.0 + t * 0.15);
        col += band2 * mix(float3(0.25, 0.30, 0.70), float3(0.55, 0.30, 0.75), neb) * 0.38;
        float2 sc = floor(fc / 5.0);
        float sr = hash(sc + 1.7);
        float tw = 0.55 + 0.45 * sin(t * (1.5 + sr * 3.0) + sr * 40.0);
        float2 sp = fract(fc / 5.0) - 0.5;
        float star = step(0.975, sr) * (1.0 - smoothstep(0.0, 0.35, length(sp))) * tw;
        col += star * mix(float3(0.85, 0.9, 1.0), irid, 0.25) * 1.3;
        float2 sc2 = floor(fc / 2.0);
        float s2 = step(0.92, hash(sc2 + 9.1)) * band2 * (0.5 + 0.5 * sin(t * 2.0 + hash(sc2) * 30.0));
        col += s2 * 0.35;
    } else if (m == 3 && photo) {
        // 金脉按倾斜方向逐段亮起，金沙沿同一方向流向低处。
        float3 tex = textureColor(uv * 0.95 + 0.025 + uTilt * 0.014);
        float lumG = dot(tex, float3(0.299, 0.587, 0.114));
        float goldness = clamp((tex.r - tex.b) * 2.2, 0.0, 1.0);
        float vein = smoothstep(0.11, 0.48, lumG) * goldness;
        float2 g = float2(uTilt.x, 0.18 + uTilt.y);
        float strength = length(g);
        float2 gd = g / max(strength, 0.001);
        float2 across = float2(-gd.y, gd.x);
        float pull = smoothstep(0.13, 0.82, strength);
        float2 low = float2(0.5 * aspect, 0.5)
            + gd * (0.46 / max(max(abs(gd.x) / aspect, abs(gd.y)), 0.01));
        float downhill = dot(p - low, gd);
        float crosshill = dot(p - low, across);
        float pool = gaussian(downhill / 0.29) * gaussian(crosshill / 0.35) * pull;
        float routeWidth = mix(0.25, 0.79, smoothstep(0.0, 1.1, -downhill));
        float route = gaussian(crosshill / routeWidth);
        float wave = 0.5 + 0.5 * cos((downhill + crosshill * crosshill * 0.22) * 17.0 - t * (1.45 + pull));
        float neutral = 0.5 + 0.5 * sin(dot(p, float2(8.0, 5.0)) - t * 1.7);
        float flowLight = mix(pow(neutral, 4.0), pow(wave, 5.0) * (0.36 + 0.64 * route), pull);
        float3 gold = float3(1.0, 0.75, 0.28);
        col = tex * (0.87 + 0.18 * diffuse) + vein * gold * (flowLight * 0.75 + pool * 1.0);
        col += gold * pool * (0.10 + edgeGlow * 0.22);

        // 两层金沙顺着重力移动，离汇聚点越近越密。
        for (int k = 0; k < 2; k++) {
            float fk = float(k);
            float cell = 10.0 - fk * 3.0;
            float2 q = (fc - uFlow * (140.0 + fk * 90.0)) / cell;
            float2 id = floor(q);
            float2 f = fract(q) - 0.5;
            float rnd = hash(id + fk * 31.0);
            float2 jitter = float2(hash(id + 3.1), hash(id + 7.7)) - 0.5;
            // 沿流动方向拉长：看起来是一粒粒正在淌的金沙
            float2 rel = f - jitter * 0.6;
            float par = dot(rel, gd);
            float perp = dot(rel, across);
            float stretch = 1.0 + 3.0 * pull;
            float d2 = par * par / stretch + perp * perp * 1.6;
            float grain = step(0.98 - fk * 0.02 - pool * 0.12, rnd) * exp(-d2 * (32.0 + fk * 10.0));
            float tw = 0.6 + 0.4 * sin(t * 3.0 + rnd * 40.0);
            col += grain * tw * gold * (0.23 + pool * (0.65 + fk * 0.22)) * smoothstep(0.0, 0.08, strength);
        }
        col *= 1.0 - 0.43 * (1.0 - smoothstep(0.08, 0.34, uv.y)) - 0.33 * smoothstep(0.63, 0.95, uv.y);
    } else if (m == 3) {
        // 黑金流沙：流动的金色脉络，金沙沿脉络聚集、随角度闪烁
        float3 gold = float3(1.0, 0.80, 0.42);
        float vein = abs(sin(q.x * 5.0 + h * 2.4 - q.y * 2.0 + t * 0.15));
        float veinLine = pow(1.0 - vein, 10.0);
        col += gold * veinLine * (0.25 + 0.6 * spec);
        float2 gc = floor(fc / 2.0);
        float gr = hash(gc + 4.2);
        float3 gn = normalize(float3(hash(gc + 1.3) - 0.5, hash(gc + 8.8) - 0.5, 0.45));
        float glint = pow(max(dot(gn, H), 0.0), 90.0);
        float density = step(0.55, gr) * (0.25 + 0.75 * pow(1.0 - vein, 3.0));
        col += gold * glint * density * 1.4;
        col += gold * 0.06 * (1.0 - vein);
    } else if (m == 4 && photo) {
        // 北境极光（贴图）：三层极光帘幕在雪峰上空舞动（品红下沿、亮度脉冲沿幕跑过）；
        // 极光把下方的积雪染上颜色；冰湖倒映；每隔几秒一颗流星划过；倾斜时帘幕摆动、与山体分层视差
        float2 base = clamp(uv * 0.95 + 0.025 + uTilt * 0.01, 0.0, 1.0);
        float3 tex = float3(uPhoto.eval(base * uPhotoSize).rgb);
        col = tex * (0.88 + 0.15 * diffuse);
        float2 auv = uv + uTilt * float2(0.05, 0.035);
        float sway = uTilt.x;
        float3 au = auroraCurtains(auv, t, aspect, sway);
        float skyMask = 1.0 - smoothstep(0.5, 0.8, uv.y);
        // 行名一带的天空留暗，文字清楚
        float topQuiet = 1.0 - 0.55 * (1.0 - smoothstep(0.1, 0.3, uv.y));
        col += au * skyMask * topQuiet * (1.9 + edgeGlow * 0.65);
        // 极光照亮积雪：取正上方极光的颜色与亮度，染到亮处（雪）
        float lumA = dot(tex, float3(0.299, 0.587, 0.114));
        float3 above = auroraCurtains(float2(auv.x, 0.36), t, aspect, sway) + auroraCurtains(float2(auv.x, 0.44), t, aspect, sway);
        float snow = smoothstep(0.3, 0.7, lumA) * smoothstep(0.45, 0.6, uv.y);
        col += snow * above * 0.55;
        // —— 冰湖（冻住的湖面）——
        float shore = 0.82;
        float lake = smoothstep(shore - 0.004, shore + 0.006, uv.y);
        float depth = clamp((uv.y - shore) / (1.0 - shore), 0.0, 1.0);
        // 镜面倒影：冰面光滑，倒影清晰，只有极轻的冰面起伏
        float ripple = 0.0025 * sin(uv.x * 40.0 + uv.y * 90.0 - t * 0.45);
        float2 ruv = float2(auv.x + ripple, 0.55 - depth * 0.27 + uTilt.y * 0.025);
        float3 refl = auroraCurtains(ruv, t, aspect, sway);
        col += refl * lake * (0.47 - 0.28 * depth);
        // 冰裂纹里流光：贴图湖面上的亮线是裂纹，极光的光沿着它们一段段跑过去
        float crack = smoothstep(0.2, 0.42, lumA) * lake;
        float run = pow(0.5 + 0.5 * sin(uv.x * aspect * 7.0 + uv.y * 30.0 - t * 1.6), 6.0);
        float run2 = pow(0.5 + 0.5 * sin(uv.x * aspect * 4.0 - uv.y * 22.0 + t * 1.1 + 1.7), 8.0);
        float3 crackLight = refl * 1.6 + above * 0.4 + float3(0.12, 0.3, 0.35);
        col += crack * crackLight * (0.25 + 1.4 * max(run, run2));
        // 冰晶闪光路：一条光之路随倾斜在冰面上左右移动，路上的冰晶会闪
        float pathX = 0.5 + uTilt.x * 0.4;
        float path = gaussian((uv.x - pathX) * aspect / (0.1 + 0.25 * depth));
        float2 ic = floor(fc / 2.5);
        float spark = step(0.93, hash(ic + 6.6)) * pow(max(0.0, sin(hash(ic) * 60.0 + t * 3.0 + uTilt.y * 8.0)), 12.0);
        col += spark * path * lake * (refl * 2.0 + float3(0.6, 0.8, 1.0)) * 0.8;
        // 贴地吹雪：一缕缕细雪沫被风吹着贴冰面横向掠过，被极光染色
        float drift = 0.0;
        for (int k = 0; k < 3; k++) {
            float fk = float(k);
            float yy = shore + 0.012 + fk * 0.035;
            float band = gaussian((uv.y - yy) / (0.006 + fk * 0.003));
            float x = uv.x * aspect * (5.0 - fk) - t * (0.35 + fk * 0.12);
            float wisp = pow(0.5 + 0.5 * sin(x * 3.0 + sin(x * 1.3 + fk) * 2.0), 3.0);
            drift += band * wisp * (0.6 - fk * 0.15);
        }
        col += drift * (above * 0.3 + float3(0.35, 0.42, 0.5)) * 0.55;
        // 流星掠过雪峰上空，头部清晰，拖尾慢慢消失。
        float fly = fract(t / 8.6) * 8.6;
        if (fly < 1.2) {
            float2 head = float2(0.96, 0.09) + float2(-0.51, 0.22) * fly;
            float2 pos = (uv - head) * float2(aspect, 1.0);
            float2 axis = normalize(float2(0.51 * aspect, -0.22));
            float tail = dot(pos, axis);
            float across = dot(pos, float2(-axis.y, axis.x));
            float fade = smoothstep(0.0, 0.15, fly) * (1.0 - smoothstep(0.7, 1.2, fly));
            float streak = step(0.0, tail) * exp(-max(tail, 0.0) * 14.0) * gaussian(across / 0.003);
            float star = gaussian(length(pos) / 0.006);
            col += float3(0.58, 0.82, 1.0) * (streak * 0.75 + star * 1.15) * fade;
        }
    } else if (m == 4) {
        // 北境极光：两层飘动的光幕，自下而上由青绿渐变到紫，配稀疏星点
        float x = uv.x * 3.0 + 0.4 * sin(uv.y * 3.0 + t * 0.25) + uTilt.x * 0.6;
        float curtain = pow(0.5 + 0.5 * sin(x * 3.1 + t * 0.35), 6.0)
                      + 0.7 * pow(0.5 + 0.5 * sin(x * 5.3 - t * 0.27 + 1.7), 8.0);
        float vfade = (1.0 - smoothstep(0.15, 1.05, uv.y)) * smoothstep(-0.05, 0.35, uv.y);
        float3 ac = mix(float3(0.15, 0.95, 0.65), float3(0.55, 0.35, 1.0), smoothstep(0.2, 0.9, 1.0 - uv.y + 0.2 * sin(x)));
        col += ac * curtain * vfade * 0.75;
        float2 sc = floor(fc / 4.0);
        col += step(0.985, hash(sc)) * (0.5 + 0.5 * sin(t * 2.5 + hash(sc) * 50.0)) * 0.8 * (1.0 - vfade * 0.5);
    } else if (m == 5) {
        // 水墨山河：宣纸底 + 三层远山（近浓远淡）+ 山脚流动的雾 + 右下角朱印
        float3 paper = float3(0.955, 0.945, 0.915) + (hash(floor(fc / 1.5)) - 0.5) * 0.025;
        float3 ink = float3(0.10, 0.11, 0.13);
        float x = uv.x + uTilt.x * 0.02;
        float tex = 0.82 + 0.18 * sin(q.x * 9.0 + h * 3.0);
        float r1 = 0.46 + 0.10 * sin(x * 4.0 + 0.6) + 0.05 * sin(x * 11.0 + 1.3) + 0.02 * sin(x * 27.0);
        float r2 = 0.60 + 0.08 * sin(x * 5.5 + 2.1) + 0.04 * sin(x * 14.0 + 0.4) + 0.015 * sin(x * 33.0);
        float r3 = 0.76 + 0.06 * sin(x * 7.0 + 4.0) + 0.03 * sin(x * 17.0 + 2.2);
        float a1 = smoothstep(r1 - 0.004, r1 + 0.004, uv.y) * (1.0 - 0.85 * smoothstep(r1, r1 + 0.24, uv.y));
        float a2 = smoothstep(r2 - 0.004, r2 + 0.004, uv.y) * (1.0 - 0.85 * smoothstep(r2, r2 + 0.22, uv.y));
        float a3 = smoothstep(r3 - 0.004, r3 + 0.004, uv.y) * (1.0 - 0.6 * smoothstep(r3, r3 + 0.25, uv.y));
        col = paper;
        col = mix(col, ink, a1 * 0.22 * tex);
        col = mix(col, ink, a2 * 0.40 * tex);
        col = mix(col, ink, a3 * 0.68 * tex);
        float mist = 0.5 + 0.5 * sin(uv.x * 3.0 + t * 0.08 + uv.y * 2.0);
        col = mix(col, paper, mist * 0.22 * smoothstep(0.55, 0.9, uv.y));
        float2 sd = uv - float2(0.87, 0.80);
        float seal = step(abs(sd.x * aspect), 0.035) * step(abs(sd.y), 0.055);
        float grain = step(0.35, hash(floor(float2(sd.x * aspect, sd.y) * 70.0)));
        col = mix(col, float3(0.74, 0.12, 0.10), seal * (0.7 + 0.3 * grain));
        col += spec * 0.12;
    } else if (m == 6) {
        // 青瓷冰裂：Voronoi 双距离差勾出冰裂纹，釉面高光更锐
        float2 vp = float2(uv.x * aspect, uv.y) * 7.0;
        float2 ip = floor(vp);
        float2 fp = fract(vp);
        float f1 = 8.0;
        float f2 = 8.0;
        for (int yy = -1; yy <= 1; yy++) {
            for (int xx = -1; xx <= 1; xx++) {
                float2 g = float2(float(xx), float(yy));
                float2 o = float2(hash(ip + g), hash(ip + g + 17.3));
                float d = length(g + o - fp);
                if (d < f1) { f2 = f1; f1 = d; } else if (d < f2) { f2 = d; }
            }
        }
        float crack = 1.0 - smoothstep(0.0, 0.045, f2 - f1);
        col = mix(col, col * 0.6, crack * 0.8);
        col += spec * 0.35 + pow(ndh, 220.0) * 0.55;
    } else if (m == 7 && photo) {
        // 熔岩沿底图的裂缝涌动，火星从两个裂缝交汇点升起。
        float2 scene = uv * 0.96 + 0.02 + uTilt * 0.012;
        float3 tex = textureColor(scene);
        float seam = smoothstep(0.12, 0.65, tex.r - tex.b);
        float along = scene.x * 0.86 - scene.y * 0.51;
        float pulse = 0.5 + 0.5 * sin(along * 19.0 - t * 2.4);
        float heat = pow(pulse, 4.0);
        float focus = gaussian((along - (0.17 + uTilt.x * 0.40 - uTilt.y * 0.22)) / 0.18);
        float3 ember = float3(1.0, 0.26, 0.04);
        float3 molten = mix(ember, float3(1.0, 0.72, 0.24), heat);
        col = tex * (0.79 + 0.16 * diffuse + seam * heat * 0.24);
        col += seam * molten * (0.08 + heat * (0.55 + edgeGlow * 0.28) + focus * 0.48);
        col += ember * focus * smoothstep(0.02, 0.36, tex.r - tex.b) * 0.16;
        if (scene.y < 0.69) {
            for (int k = 0; k < 6; k++) {
                float fk = float(k);
                float rnd = hash(float2(fk, 3.2));
                float age = fract(t * (0.36 + 0.24 * rnd) + rnd);
                float2 start = k < 3 ? float2(0.28, 0.54) : float2(0.73, 0.29);
                float2 drift = float2((rnd - 0.5) * 0.32 + uTilt.x * 0.16, -0.18 - rnd * 0.16 + uTilt.y * 0.06);
                float2 d = (scene - start - drift * age) * float2(aspect, 1.0);
                float life = smoothstep(0.0, 0.14, age) * (1.0 - smoothstep(0.25, 1.0, age));
                float spark = gaussian(length(d * float2(1.0, 0.68)) / (0.0018 + 0.0015 * rnd + 0.5 / uSize.y));
                col += (float3(1.0, 0.75, 0.34) * spark * 1.3 + ember * gaussian(length(d) / 0.012) * 0.48) * life;
            }
        }
        col *= 1.0 - 0.32 * smoothstep(0.60, 0.85, uv.y);
    } else if (m == 8 && photo) {
        // 霓虹夜城：灯管呼吸，光沿湿地的透视车道疾驰。倾斜时近处倒影移动得更远。
        float2 scene = uv * 0.95 + 0.025 + uTilt * float2(0.016, 0.012);
        float depth = max(scene.y - 0.565, 0.0);
        float wet = smoothstep(0.0, 0.14, depth);
        float2 reflection = uTilt * float2(0.047, 0.022) * wet;
        reflection.x += 0.0028 * wet * sin(scene.y * 151.0 - t * 2.1);
        float2 road = scene + reflection;
        float3 tex = textureColor(road);
        float3 cyan = float3(0.11, 0.85, 1.0);
        float3 pink = float3(1.0, 0.13, 0.55);
        float neon = smoothstep(0.10, 0.62, max(tex.b - tex.r, tex.r - tex.g));
        float breath = 0.59 + 0.41 * sin(t * 1.75 + scene.x * 12.0);
        float3 glowColor = mix(pink, cyan, smoothstep(-0.18, 0.24, tex.b - tex.r));
        col = tex * (0.91 + neon * (0.22 + breath * 0.49));
        col += glowColor * neon * (0.06 + wet * 0.16 + edgeGlow * 0.14) * breath;
        float sun = gaussian(length((scene - float2(0.604, 0.495)) * float2(aspect, 1.0)) / 0.17);
        col += pink * sun * (0.06 + 0.05 * sin(t * 0.85 + uTilt.y * 1.8));
        if (depth > 0.0) {
            float distance = 0.85 / (depth + 0.055);
            float litRoad = smoothstep(0.005, 0.05, depth) * (1.0 - 0.82 * smoothstep(0.13, 0.27, depth));
            for (int k = 0; k < 4; k++) {
                float fk = float(k);
                float slope = k < 2 ? -6.7 + fk * 2.0 : 3.1 + (fk - 2.0) * 1.8;
                float x = road.x - 0.604 - slope * (road.y - 0.565);
                float wave = 0.5 + 0.5 * cos(distance * 1.65 + t * (3.1 + 0.5 * fk) + fk * 1.8);
                float run = pow(wave, 9.0);
                float line = gaussian(x / (0.003 + 0.011 * depth + 1.0 / uSize.x));
                float glow = gaussian(x / (0.016 + 0.024 * depth));
                float3 light = mix(cyan, pink, 0.12 + 0.25 * fk);
                col += light * (line * 0.92 + glow * 0.20) * run * litRoad;
            }
        }
        col *= 1.0 - 0.35 * smoothstep(0.63, 0.90, uv.y);
    } else if (m == 9 && photo) {
        // 花枝和花瓣分层视差；近处花瓣翻卷，迎着光时边缘透出浅粉。
        float2 scene = uv * 0.95 + 0.025 + uTilt * float2(0.021, 0.016);
        float3 tex = textureColor(scene);
        float lightX = 0.71 + uTilt.x * 0.26 + 0.07 * sin(t * 0.34);
        float backlight = gaussian(length((uv - float2(lightX, 0.28 + uTilt.y * 0.15)) * float2(aspect, 1.0)) / 0.42);
        col = tex * (0.9 + backlight * 0.31);
        col += float3(0.78, 0.27, 0.49) * backlight * (0.10 + edgeGlow * 0.08);
        for (int k = 0; k < 3; k++) {
            float fk = float(k);
            float sc = 9.0 - fk * 1.9;
            float speed = 0.08 + fk * 0.032;
            float sway = sin(p.y * 2.2 + t * 0.54 + fk * 1.8) * (0.018 + fk * 0.018);
            float2 drift = float2(-t * speed + sway, t * speed * 1.27);
            float2 parallax = uTilt * float2(0.075, 0.048) * (fk + 0.5);
            float2 gp = (p - drift + parallax) * sc;
            float2 id = floor(gp);
            float2 f = fract(gp) - 0.5;
            float rr = hash(id + fk * 13.1);
            if (rr < 0.38 - fk * 0.065) {
                f -= (float2(hash(id + 3.3), hash(id + 7.7)) - 0.5) * 0.45;
                float ang = rr * 65.0 + t * (0.65 + rr) + uTilt.x * 0.8;
                float2 pr = float2(f.x * cos(ang) - f.y * sin(ang), f.x * sin(ang) + f.y * cos(ang));
                float roll = 0.36 + 0.64 * abs(cos(t * 1.4 + rr * 73.0 + uTilt.y * 1.4));
                pr.y /= roll;
                float d = length(pr * float2(0.93, 1.48 + pr.x * 1.8));
                float notch = 1.0 - smoothstep(0.020, 0.047, length(pr - float2(0.181, 0.0)));
                float petal = (1.0 - smoothstep(0.133, 0.175, d)) * (1.0 - notch);
                float rim = smoothstep(0.093, 0.17, d) * (0.28 + 0.72 * (1.0 - roll));
                float3 rose = mix(float3(0.53, 0.16, 0.29), float3(1.0, 0.71, 0.82), 0.20 + 0.56 * backlight + 0.24 * rim);
                float fade = 1.0 - 0.60 * smoothstep(0.60, 0.89, uv.y);
                col = mix(col, rose, petal * fade * (0.27 + fk * 0.24));
            }
        }
    } else if (m == 10) {
        // 龙鳞：错位排列的鳞片，每片法线随机，倾斜时逐片闪出金红光泽
        float2 sp = float2(uv.x * aspect, uv.y) * 11.0;
        float row = floor(sp.y);
        sp.x += mod(row, 2.0) * 0.5;
        float2 id = float2(floor(sp.x), row);
        float2 f = fract(sp) - float2(0.5, 0.0);
        float d = length(f - float2(0.0, 0.05));
        float edgeS = smoothstep(0.48, 0.60, d);
        float rr = hash(id);
        float3 sn = normalize(float3((hash(id + 2.1) - 0.5) * 0.9 + f.x * 0.6, (hash(id + 5.3) - 0.5) * 0.9 - (f.y - 0.5) * 0.6, 1.0));
        float g = pow(max(dot(sn, H), 0.0), 28.0);
        float3 base = mix(float3(0.45, 0.06, 0.05), float3(0.85, 0.55, 0.12), rr * 0.6 + f.y * 0.4);
        col = base * (0.55 + 0.45 * (1.0 - f.y));
        col += g * mix(float3(1.0, 0.85, 0.45), irid, 0.35) * 1.2;
        col *= 1.0 - edgeS * 0.5;
    } else if (m == 11 && photo) {
        // 星璇（贴图）：底图不动，一对对数螺旋光臂绕核心无止境地旋转，
        // 经过之处把贴图自己的旋臂点亮，像光在星系里打转；七颗带拖尾的彗星绕核心公转；核心呼吸发光
        float2 core = float2(0.50, 0.15);
        float2 asp = float2(aspect, 1.0);
        float2 d = (uv - core) * asp;
        float r = length(d) + 0.0001;
        // 引力透镜：一颗看不见的“黑洞”悬在星系前，随倾斜缓缓滑动；
        // 光线经过时按 1/r 偏折（旋臂被拉弯），视界内全黑，外缘一圈爱因斯坦环
        float2 lc = core + uTilt * float2(0.035, 0.03);
        float2 ld = (uv - lc) * asp;
        float lr = length(ld) + 0.0001;
        float rs = 0.075;
        float2 bend = (ld / lr) * (rs * rs / lr);
        float2 base = uv * 0.94 + 0.03 + uTilt * 0.014 + N.xy * 0.004 - bend / asp * 0.94;
        base = clamp(base, 0.0, 1.0);
        col = float3(uPhoto.eval(base * uPhotoSize).rgb);
        float horizon = 1.0 - smoothstep(rs * 0.5, rs * 0.62, lr);
        float la = atan(ld.y, ld.x);
        // 光子环：极细、极亮；外面一圈柔和的吸积盘辉光；
        // 多普勒增亮：朝向我们转来的一侧更亮，亮斑绕环高速打转
        float einstein = gaussian((lr - rs) / 0.004);
        float swirl = 0.3 + 0.7 * pow(0.5 + 0.5 * cos(la - t * 2.2), 4.0);
        float doppler = 0.6 + 0.4 * cos(la + 0.6 + uTilt.x * 1.5);
        float glowRing = gaussian((lr - rs * 1.25) / 0.035) * 0.45;
        // 吸积盘：斜着看的一条亮带，穿过黑洞，前半在视界前面
        float diskY = ld.y - ld.x * (0.18 + uTilt.x * 0.23) - uTilt.y * 0.02;
        float disk = gaussian(diskY / 0.010) * (1.0 - smoothstep(rs * 1.05, rs * 3.2, lr)) * smoothstep(rs * 0.55, rs * 0.9, lr);
        float diskFlow = 0.55 + 0.45 * sin(ld.x * 120.0 - t * 4.0);
        col *= 0.94 + 0.12 * diffuse;
        float peak = max(col.r, max(col.g, col.b));
        col *= mix(1.0, 0.7, smoothstep(0.62, 1.0, peak));
        col *= 1.0 - 0.22 * (1.0 - smoothstep(0.0, 0.3, uv.y));
        col *= 1.0 - 0.35 * (1.0 - smoothstep(0.08, 0.32, uv.y)) * (1.0 - smoothstep(0.32, 0.46, uv.x));
        // 旋转的螺旋光：角度项随时间单调前进，只会一直转下去，不会回放
        float aa = atan(d.y, d.x);
        float arm = pow(0.5 + 0.5 * cos(2.0 * aa - log(r + 0.03) * 3.2 - t * 0.45), 6.0);
        float fall = exp(-r * 2.0);
        col *= 1.0 + arm * fall * 1.1;
        col += arm * fall * mix(float3(0.55, 0.45, 1.0), float3(1.0, 0.75, 0.85), fall) * (0.12 + edgeGlow * 0.14);
        // 透镜：视界吞掉一切光，外缘的光环与吸积盘发亮
        col *= 1.0 - horizon;
        float3 hot = float3(1.0, 0.82, 0.62);
        col += (einstein * 1.6 + glowRing) * swirl * doppler * hot;
        col += disk * diskFlow * doppler * hot * 1.4;
        // 彗星：椭圆轨道（像斜看的星盘），头亮尾长
        float2 e = float2(d.x, d.y - d.x * uTilt.x * 0.17) * float2(1.0, 1.9 + uTilt.y * 0.5);
        float re = length(e);
        float ae = atan(e.y, e.x);
        for (int k = 0; k < 7; k++) {
            float fk = float(k);
            float rk = 0.12 + fk * 0.085;
            float head = t * (0.42 / (rk + 0.18)) + hash(float2(fk, 3.0)) * 6.2831;
            float da = mod(head - ae, 6.2831);
            float w = 0.0035 + 1.0 / uSize.y;
            float dr2 = re - rk;
            float ring = gaussian(dr2 / w);
            float halo = gaussian(dr2 / (w * 5.0)) * 0.35;
            float3 cc = mix(float3(1.0, 0.86, 0.66), float3(0.78, 0.66, 1.0), hash(float2(fk, 9.0)));
            float tail = exp(-da * 5.0);
            float headGlow = exp(-da * 40.0);
            col += cc * (ring * (tail * 0.6 + headGlow * 1.4) + halo * (tail * 0.25 + headGlow * 1.2));
        }
        // 前景星点：随倾斜比星系移动得多，形成纵深
        float2 fp = fc + uTilt * float2(-46.0, -46.0);
        float2 sc = floor(fp / 4.0);
        float tw = 0.5 + 0.5 * sin(t * 2.0 + hash(sc) * 60.0);
        float2 sf = fract(fp / 4.0) - 0.5;
        col += step(0.9975, hash(sc + 4.4)) * exp(-dot(sf, sf) * 18.0) * tw * float3(0.92, 0.9, 1.0) * 0.9;
        // 核心横向镜头光：长度、亮度随角度变化
        float2 dc = (uv - core) * asp;
        float flare = exp(-abs(dc.y) * (220.0 - abs(uTilt.x) * 120.0)) * exp(-abs(dc.x) * (3.2 - abs(uTilt.y) * 1.2));
        col += flare * mix(float3(0.7, 0.75, 1.0), float3(1.0, 0.8, 0.9), 0.5 + 0.5 * uTilt.x) * 0.45;
        // 暗部保留淡紫星云，旋臂经过时才微微照亮。
        float darkMask = 1.0 - smoothstep(0.05, 0.3, dot(col, float3(0.299, 0.587, 0.114)));
        col += darkMask * float3(0.28, 0.20, 0.55) * fall * (0.045 + 0.12 * arm);
        col += spec * 0.18;
    } else if (m == 11) {
        // 星璇：对数螺旋双旋臂缓慢旋转，亮核 + 旋臂里闪烁的星尘
        float2 c = float2((uv.x - 0.62) * aspect, uv.y - 0.48);
        float r = length(c);
        float a = atan(c.y, c.x);
        float spin = t * 0.25 + uTilt.x * 0.5;
        float arms = pow(0.5 + 0.5 * cos(2.0 * a - log(r + 0.02) * 4.5 + spin * 2.0), 5.0);
        float disc = exp(-r * 3.2);
        float3 gc = mix(float3(0.95, 0.55, 0.95), float3(0.35, 0.45, 1.0), smoothstep(0.0, 0.6, r));
        col = col * 0.7 + gc * arms * disc * 1.1;
        col += float3(1.0, 0.9, 0.85) * exp(-r * 16.0) * 1.2;
        float2 sc = floor(fc / 3.0);
        col += step(0.97, hash(sc)) * (0.4 + 0.6 * arms) * (0.5 + 0.5 * sin(t * 3.0 + hash(sc) * 40.0)) * 0.9;
    } else if (m == 12 && photo) {
        // 液态铬：水银涟漪折射底图的波纹，灯带沿起伏滑动，倾斜时倒映冷暖色光。
        float2 base = uv * 0.94 + 0.03 + uTilt * 0.015;
        float3 drops = dropletRipples(p + uTilt * float2(0.065, 0.045), t, aspect);
        float2 rip = -(chromeSlope(p, t) + drops.xy) * 0.009;
        float2 tuv = base + rip * 0.33;
        float3 tex = textureColor(tuv);
        float3 lw = float3(0.299, 0.587, 0.114);
        float2 px = 9.0 / uPhotoSize;
        float hL = dot(textureColor(tuv - float2(px.x, 0.0)), lw);
        float hR = dot(textureColor(tuv + float2(px.x, 0.0)), lw);
        float hU = dot(textureColor(tuv - float2(0.0, px.y)), lw);
        float hD = dot(textureColor(tuv + float2(0.0, px.y)), lw);
        float3 Nt = normalize(float3((hL - hR) * 2.6 + rip.x * 3.0, (hU - hD) * 2.6 + rip.y * 3.0, 1.0));
        float ry = uv.y * 1.5 + uv.x * 0.22 + Nt.y * 1.25 + Nt.x * 0.35 + uTilt.y * 1.3 + uTilt.x * 0.53 - t * 0.16;
        float sv = fract(ry * 0.5);
        float strips = gaussian((sv - 0.34) / 0.036) + 0.50 * gaussian((sv - 0.72) / 0.073);
        float crest = max(smoothstep(0.06, 0.48, dot(tex, lw)), drops.z * 0.66);
        col = tex * (0.87 + 0.16 * dot(Nt, L));
        float gx = uv.x + Nt.x * 0.8 + uTilt.x * 0.9;
        float3 env = mix(float3(0.13, 0.46, 1.0), float3(0.82, 0.24, 0.52), smoothstep(0.1, 1.0, gx));
        float3 silver = mix(float3(0.91, 0.95, 1.0), env, 0.35);
        col += env * crest * 0.30;
        col += strips * silver * (0.035 + 0.93 * crest + edgeGlow * 0.10);
        col += pow(max(dot(Nt, H), 0.0), 64.0) * crest * silver * 0.40;
        col += drops.z * silver * (0.34 + 0.37 * strips);
        col *= 1.0 - 0.37 * smoothstep(0.57, 0.87, uv.y);
    } else if (m == 12) {
        // 液态铬：放大褶皱法线做镜面反射，天空 / 地面环境色 + 锐利的地平线高光
        float3 Nc = normalize(float3(N.xy * 3.0, 1.0));
        float3 R = reflect(float3(0.0, 0.0, -1.0), Nc);
        float ry = R.y * 1.6 + R.x * 0.3 + uTilt.y * 0.8 + uTilt.x * 0.3;
        float3 sky = mix(float3(0.96, 0.97, 1.0), float3(0.55, 0.62, 0.76), smoothstep(-0.8, 0.0, ry));
        float3 ground = mix(float3(0.22, 0.22, 0.25), float3(0.04, 0.04, 0.05), smoothstep(0.0, 0.8, ry));
        float3 env = ry < 0.0 ? sky : ground;
        env += exp(-abs(ry) * 36.0) * 0.7;
        col = env * mix(float3(1.0), min(float3(uC0.rgb) * 1.3 + 0.2, float3(1.2)), 0.18);
        col += pow(ndh, 140.0) * 0.9;
    } else if (m == 13 && photo) {
        // 鳞缘的局部明暗决定反光方向；倾斜让薄膜虹彩在相邻鳞片间流转。
        float2 scene = uv * 0.95 + 0.025 + uTilt * 0.018;
        float3 tex = textureColor(scene);
        float3 lw = float3(0.299, 0.587, 0.114);
        float lum = dot(tex, lw);
        float dx = dot(textureColor(scene + float2(0.003, 0.0)), lw) - lum;
        float dy = dot(textureColor(scene + float2(0.0, 0.004)), lw) - lum;
        float3 scaleN = normalize(float3(-dx * 11.0, -dy * 9.0, 1.0));
        float hue = uTilt.x * 1.55 - uTilt.y * 1.15 + sin(t * 0.17) * 0.25;
        float3 base = max(hueShift(tex, hue), float3(0.0));
        float phase = scene.x * 0.7 - scene.y * 0.48 + dot(scaleN.xy, float2(0.85, -0.64)) + hue * 0.43;
        float3 film = 0.56 + 0.44 * cos(6.2831 * (phase + float3(0.0, 0.32, 0.63)));
        float rim = smoothstep(0.10, 0.40, lum + length(float2(dx, dy)) * 0.42);
        float glint = pow(max(dot(scaleN, H), 0.0), 24.0);
        col = base * (0.85 + 0.22 * dot(scaleN, L));
        col += film * (rim * (0.22 + 0.54 * glint) + edgeGlow * 0.055);

        // 两股流金绕过鳞面，亮点沿细光带一路向前。
        float3 gold = float3(1.0, 0.78, 0.36);
        for (int k = 0; k < 2; k++) {
            float fk = float(k);
            float x = p.x;
            float y = 0.32 + fk * 0.27 + 0.13 * sin(x * 2.4 + t * 0.39 + fk * 2.2)
                + 0.042 * sin(x * 5.4 - t * 0.31 + fk) + uTilt.y * 0.11 + uTilt.x * (uv.x - 0.5) * 0.16;
            float d = uv.y - y;
            float core = gaussian(d / (0.0035 + 0.65 / uSize.y));
            float halo = gaussian(d / 0.028);
            float wave = 0.5 + 0.5 * sin(x * 5.0 - t * 1.64 + fk * 2.4);
            float pulse = 0.28 + 0.72 * pow(wave, 3.0);
            col += gold * (core * 0.98 + halo * 0.31) * pulse;
            col += film * halo * rim * 0.32;
            float2 dust = float2(x * 52.0 - t * 10.0, d * 90.0);
            float rnd = hash(floor(dust) + fk * 17.0);
            float2 spot = fract(dust) - 0.5;
            col += gold * step(0.91, rnd) * gaussian(length(spot) / 0.22) * halo * (0.45 + 0.55 * wave);
        }
        col *= 1.0 - 0.39 * smoothstep(0.59, 0.90, uv.y);
    }

    // —— 5b. 边缘辉光 + 暗角 ——
    col += (1.0 - smoothstep(0.0, 0.045, edge)) * (lightCard ? 0.04 : 0.07);
    float vig = length((uv - 0.5) * float2(1.0, 0.8));
    col *= 1.0 - 0.14 * vig * vig;

    // 横向拉丝 + 抖动
    float streak = hash(float2(floor(fc.y + fc.x * 0.04), 7.31)) - 0.5;
    col += streak * (photo ? 0.005 : 0.016);
    col += (hash(fc) - 0.5) * (2.0 / 255.0);
    return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""

/**
 * 列表共用的流动时钟：同屏所有卡读同一个时间源，只在绘制阶段读取，
 * 帧推进只触发重绘，不触发重组。页面离开组合时时钟随之停止。
 */
val LocalFlowClock = staticCompositionLocalOf<State<Float>?> { null }

/** 设置里的「卡面动效」开关：关闭时时钟不走、倾斜不响应，卡面静止 */
val LocalMotionEnabled = staticCompositionLocalOf { true }

@Composable
fun rememberFlowClock(): State<Float> {
    val time = remember { mutableFloatStateOf(0f) }
    val motion = LocalMotionEnabled.current
    LaunchedEffect(motion) {
        if (!motion) return@LaunchedEffect
        var startNanos = 0L
        while (true) {
            withFrameNanos { frame ->
                if (startNanos == 0L) startNanos = frame - (time.floatValue * 1_000_000_000.0).toLong()
                time.floatValue = ((frame - startNanos) / 1_000_000_000.0).toFloat()
            }
        }
    }
    return time
}

/** 卡面底色：Android 13+ 使用流动着色器，更低版本显示静态贴图或渐变。 */
@Composable
fun Modifier.flowingGradient(
    colors: List<Color>,
    lightSurface: Boolean,
    time: State<Float>?,
    tilt: State<Offset>,
    effect: Int = 0,
    /** 皮肤贴图或用户导入的背景照片 */
    photo: ImageBitmap? = null,
): Modifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && FlowShaderOk) {
    flowShader(colors, lightSurface, time, tilt, effect, photo)
} else if (photo != null) {
    drawBehind {
        drawImage(
            photo,
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            filterQuality = FilterQuality.High,
        )
    }
} else {
    background(Brush.linearGradient(colors))
}

/**
 * 着色器能否编译：在这台设备上试编译一次。万一失败（驱动差异或代码改错），
 * 卡面退回静态渐变，应用照常可用，而不是一打开就闪退。
 */
private val FlowShaderOk: Boolean by lazy {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        runCatching { RuntimeShader(FLOW_AGSL) }
            .onFailure { android.util.Log.e("Silk", "卡面着色器编译失败，退回静态渐变", it) }
            .isSuccess
}

/** 没有照片时塞给 uPhoto 的 1×1 占位（AGSL 子着色器必须绑定） */
private val EmptyPhotoShader by lazy {
    android.graphics.BitmapShader(
        android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888),
        android.graphics.Shader.TileMode.CLAMP,
        android.graphics.Shader.TileMode.CLAMP,
    )
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun Modifier.flowShader(
    colors: List<Color>,
    lightSurface: Boolean,
    time: State<Float>?,
    tilt: State<Offset>,
    effect: Int,
    photo: ImageBitmap?,
): Modifier {
    val shader = remember { RuntimeShader(FLOW_AGSL) }
    val photoShader = remember(photo) {
        photo?.let {
            android.graphics.BitmapShader(
                it.asAndroidBitmap(),
                android.graphics.Shader.TileMode.CLAMP,
                android.graphics.Shader.TileMode.CLAMP,
            ).apply { filterMode = android.graphics.BitmapShader.FILTER_MODE_LINEAR }
        }
    }
    val brush = remember(shader) { ShaderBrush(shader) }
    // 流沙位移：[x, y, 上一帧时间]；在绘制里按倾斜积分，不触发重组
    val flow = remember(effect) { if (effect == CardEffect.GOLD) floatArrayOf(0f, 0f, -1f) else null }
    val c0 = colors.first().toArgb()
    val c1 = colors.getOrElse(1) { colors.first() }.toArgb()
    val c2 = colors.last().toArgb()
    return drawBehind {
        shader.setFloatUniform("uSize", size.width, size.height)
        val now = time?.value ?: 0f
        shader.setFloatUniform("uTime", now)
        val t = tilt.value
        shader.setFloatUniform("uTilt", t.x, t.y)
        if (flow != null) {
            val dt = if (flow[2] < 0f) 0f else (now - flow[2]).coerceIn(0f, 0.1f)
            flow[2] = now
            flow[0] += t.x * dt
            flow[1] += (0.18f + t.y) * dt
            shader.setFloatUniform("uFlow", flow[0], flow[1])
        } else {
            shader.setFloatUniform("uFlow", 0f, 0f)
        }
        shader.setFloatUniform("uLight", if (lightSurface) 1f else 0f)
        shader.setFloatUniform("uMode", effect.toFloat())
        if (photo != null && photoShader != null) {
            shader.setInputShader("uPhoto", photoShader)
            shader.setFloatUniform("uPhotoOn", 1f)
            shader.setFloatUniform("uPhotoSize", photo.width.toFloat(), photo.height.toFloat())
        } else {
            shader.setInputShader("uPhoto", EmptyPhotoShader)
            shader.setFloatUniform("uPhotoOn", 0f)
            shader.setFloatUniform("uPhotoSize", 1f, 1f)
        }
        shader.setColorUniform("uC0", c0)
        shader.setColorUniform("uC1", c1)
        shader.setColorUniform("uC2", c2)
        drawRect(brush)
    }
}
