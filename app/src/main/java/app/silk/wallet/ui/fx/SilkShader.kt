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
import kotlin.math.roundToInt

/**
 * 液态丝绸卡面（AGSL，Android 13+）。从下往上五层：
 *
 * 1. 底色流场：三层正弦域扭曲推着三段渐变缓慢流动（周期几十秒，静看是“活的”）；
 * 2. 丝绸褶皱光照：同一流场生成一张高度场（两组斜向长波 + 一层细波），
 *    有限差分求法线，用一盏随设备倾斜 / 手指拖动移动的光源做漫反射 + 镜面高光——
 *    褶皱的峰谷有真实的明暗，峰顶滑过缎面般的锐利光泽；
 * 3. 薄膜虹彩：按法线与视线夹角取油膜干涉色，只渗进高光和雕刻纹里，随角度流转；
 * 4. 金属闪粉：每 2.5px 一格，约三成格子里有一片朝向随机的微小金属片，
 *    只有法线恰好对准半程向量时才亮——倾斜时满卡此起彼伏地闪；
 * 5. 扭索纹雕刻 + 边缘辉光 + 暗角 + 横向拉丝颗粒 + 抖动去色带。
 */
private const val FLOW_AGSL = """
uniform float2 uSize;
uniform float uTime;
uniform float2 uTilt;
uniform float uLight;
uniform float uMode;
uniform float uTier;
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
        float fringe = exp(-pow((dy + 0.003) / 0.008, 2.0));
        acc += (c * body * rays + float3(1.0, 0.3, 0.65) * fringe * 0.5) * patch * pulse * (0.62 - fk * 0.14);
    }
    return acc;
}

// 水银滴落：三个错开节奏的落点，每滴在随机位置荡开一圈衰减的环形波；
// 换落点时上一滴已几乎平息，且新一滴从零渐起，所以不会跳
float drops(float2 p, float t, float aspect) {
    float h = 0.0;
    for (int k = 0; k < 3; k++) {
        float fk = float(k);
        float period = 3.6;
        float tt = t + fk * 1.2;
        float slot = floor(tt / period);
        float lt = tt - slot * period;
        float2 c = float2(0.1 + 0.8 * hash(float2(slot, fk + 1.0)), 0.15 + 0.7 * hash(float2(slot, fk + 7.0)));
        c.x *= aspect;
        float r = length(p - c);
        float radius = lt * 0.26;
        float amp = exp(-lt * 1.2) * smoothstep(0.0, 0.1, lt);
        float band = exp(-pow((r - radius) / 0.03, 2.0));
        h += sin((r - radius) * 110.0) * band * amp * 0.35;
        h += exp(-pow(r / 0.012, 2.0)) * exp(-lt * 8.0) * smoothstep(0.0, 0.04, lt) * 0.3;
    }
    return h;
}

// 液态金属表面的涟漪高度场：三组方向、频率不同的波叠加
float ripple(float2 p, float t) {
    return 0.5 * sin(p.x * 5.0 + p.y * 1.5 - t * 1.1)
         + 0.35 * sin(-p.x * 2.3 + p.y * 6.1 + t * 0.9)
         + 0.16 * sin((p.x + p.y) * 8.0 - t * 1.5);
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
    float t = uTime;
    bool lightCard = uLight > 0.5;

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
    if (photo) {
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
    float2 cell = floor(fc / 2.5);
    float r = hash(cell);
    float3 fn = normalize(float3(hash(cell + 7.13) - 0.5, hash(cell + 3.71) - 0.5, 0.55));
    float flake = pow(max(dot(fn, H), 0.0), 360.0) * step(uTier > 1.5 ? 0.76 : 0.86, r);
    col += flake * (lightCard ? 0.6 : 1.1) * mix(float3(1.0), irid, 0.4);

    // —— 6. 抽卡皮肤特效（uMode 见 CardEffect）——
    int m = int(uMode + 0.5);
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
        float star = step(0.975, sr) * smoothstep(0.35, 0.0, length(sp)) * tw;
        col += star * mix(float3(0.85, 0.9, 1.0), irid, 0.25) * 1.3;
        float2 sc2 = floor(fc / 2.0);
        float s2 = step(0.92, hash(sc2 + 9.1)) * band2 * (0.5 + 0.5 * sin(t * 2.0 + hash(sc2) * 30.0));
        col += s2 * 0.35;
    } else if (m == 3 && photo) {
        // 黑金流沙（贴图）：金脉里熔金流动；金沙顺着手机倾斜的方向流淌，
        // 在“低处”的卡边聚成一片暖光——怎么拿，沙就往哪走
        float2 base = clamp(uv * 0.95 + 0.025 + uTilt * 0.014, 0.0, 1.0);
        float3 tex = float3(uPhoto.eval(base * uPhotoSize).rgb);
        float lumG = dot(tex, float3(0.299, 0.587, 0.114));
        float goldness = clamp((tex.r - tex.b) * 2.2, 0.0, 1.0);
        float vein = smoothstep(0.12, 0.45, lumG) * goldness;
        col = tex * (0.9 + 0.2 * diffuse);
        // 托住文字：顶部行名、底部持卡人一带压暗（金脉也一起压）
        float textBand = 1.0 - 0.5 * (1.0 - smoothstep(0.08, 0.34, uv.y)) - 0.35 * smoothstep(0.74, 0.95, uv.y);
        col *= textBand;
        vein *= textBand;
        // 熔金沿金脉流动 + 随倾斜移动的金属高光
        float flowLight = pow(0.5 + 0.5 * sin(dot(uv, float2(aspect * 9.0, 6.0)) - t * 1.8), 3.0);
        float tiltLight = pow(0.5 + 0.5 * cos(uv.x * 5.0 + uv.y * 3.0 - uTilt.x * 5.0 + uTilt.y * 3.5), 4.0);
        float3 gold = float3(1.0, 0.78, 0.36);
        col += vein * gold * (flowLight * 0.45 + tiltLight * 0.6);
        // 重力方向（屏幕坐标，y 向下）：静止时缓缓下落
        float2 g = float2(uTilt.x, 0.35 + uTilt.y);
        float gl = max(length(g), 0.001);
        float2 gd = g / gl;
        // 两层金沙：近层颗粒大、走得快
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
            float perp = dot(rel, float2(-gd.y, gd.x));
            float stretch = 1.0 + 3.0 * smoothstep(0.05, 0.6, gl);
            float d2 = par * par / stretch + perp * perp * 1.6;
            float grain = step(0.965 - fk * 0.02, rnd) * exp(-d2 * (30.0 + fk * 10.0));
            float tw = 0.6 + 0.4 * sin(t * 3.0 + rnd * 40.0);
            // 金沙主要聚在低处，高处只零星几粒
            float lowSide = 0.25 + 0.75 * smoothstep(-0.25, 0.45, dot(uv - 0.5, gd));
            col += grain * tw * gold * (0.8 + fk * 0.6) * lowSide;
        }
        // 低处堆积的暖光
        float pile = smoothstep(0.15, 0.62, dot(uv - 0.5, gd) * 1.0 + 0.1) * smoothstep(0.05, 0.6, gl);
        col += pile * gold * 0.3;
        col += pile * vein * gold * 0.5;
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
        float skyMask = smoothstep(0.8, 0.5, uv.y);
        // 行名一带的天空留暗，文字清楚
        float topQuiet = 1.0 - 0.55 * (1.0 - smoothstep(0.1, 0.3, uv.y));
        col += au * skyMask * topQuiet * 1.9;
        // 极光照亮积雪：取正上方极光的颜色与亮度，染到亮处（雪）
        float lumA = dot(tex, float3(0.299, 0.587, 0.114));
        float3 above = auroraCurtains(float2(auv.x, 0.36), t, aspect, sway) + auroraCurtains(float2(auv.x, 0.44), t, aspect, sway);
        float snow = smoothstep(0.3, 0.7, lumA) * smoothstep(0.45, 0.6, uv.y);
        col += snow * above * 0.55;
        // —— 冰湖（冻住的湖面）——
        float shore = 0.868;
        float lake = smoothstep(shore - 0.004, shore + 0.006, uv.y);
        float depth = clamp((uv.y - shore) / (1.0 - shore), 0.0, 1.0);
        // 镜面倒影：冰面光滑，倒影清晰，只有极轻的冰面起伏
        float2 ruv = float2(auv.x + 0.0015 * sin(uv.x * 40.0 + uv.y * 90.0), shore - (uv.y - shore) * 1.6);
        float3 refl = auroraCurtains(ruv, t, aspect, sway);
        col += refl * lake * (0.6 - 0.2 * depth);
        // 冰裂纹里流光：贴图湖面上的亮线是裂纹，极光的光沿着它们一段段跑过去
        float crack = smoothstep(0.2, 0.42, lumA) * lake;
        float run = pow(0.5 + 0.5 * sin(uv.x * aspect * 7.0 + uv.y * 30.0 - t * 1.6), 6.0);
        float run2 = pow(0.5 + 0.5 * sin(uv.x * aspect * 4.0 - uv.y * 22.0 + t * 1.1 + 1.7), 8.0);
        float3 crackLight = refl * 1.6 + above * 0.4 + float3(0.12, 0.3, 0.35);
        col += crack * crackLight * (0.25 + 1.4 * max(run, run2));
        // 冰晶闪光路：一条光之路随倾斜在冰面上左右移动，路上的冰晶会闪
        float pathX = 0.5 + uTilt.x * 0.4;
        float path = exp(-pow((uv.x - pathX) * aspect / (0.1 + 0.25 * depth), 2.0));
        float2 ic = floor(fc / 2.5);
        float spark = step(0.93, hash(ic + 6.6)) * pow(max(0.0, sin(hash(ic) * 60.0 + t * 3.0 + uTilt.y * 8.0)), 12.0);
        col += spark * path * lake * (refl * 2.0 + float3(0.6, 0.8, 1.0)) * 0.8;
        // 贴地吹雪：一缕缕细雪沫被风吹着贴冰面横向掠过，被极光染色
        float drift = 0.0;
        for (int k = 0; k < 3; k++) {
            float fk = float(k);
            float yy = shore + 0.012 + fk * 0.035;
            float band = exp(-pow((uv.y - yy) / (0.006 + fk * 0.003), 2.0));
            float x = uv.x * aspect * (5.0 - fk) - t * (0.35 + fk * 0.12);
            float wisp = pow(0.5 + 0.5 * sin(x * 3.0 + sin(x * 1.3 + fk) * 2.0), 3.0);
            drift += band * wisp * (0.6 - fk * 0.15);
        }
        col += drift * (above * 0.3 + float3(0.35, 0.42, 0.5)) * 0.55;
    } else if (m == 4) {    } else if (m == 4) {
        // 北境极光：两层飘动的光幕，自下而上由青绿渐变到紫，配稀疏星点
        float x = uv.x * 3.0 + 0.4 * sin(uv.y * 3.0 + t * 0.25) + uTilt.x * 0.6;
        float curtain = pow(0.5 + 0.5 * sin(x * 3.1 + t * 0.35), 6.0)
                      + 0.7 * pow(0.5 + 0.5 * sin(x * 5.3 - t * 0.27 + 1.7), 8.0);
        float vfade = smoothstep(1.05, 0.15, uv.y) * smoothstep(-0.05, 0.35, uv.y);
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
    } else if (m == 7) {
        // 熔岩：暗色岩面下两层发光脉络缓慢流动、呼吸，火星向上飘
        float v1 = pow(1.0 - abs(sin(q.x * 3.6 + h * 2.6 - t * 0.12)), 12.0);
        float v2 = pow(1.0 - abs(sin(q.y * 4.2 - h * 2.0 + q.x * 1.3 + t * 0.09)), 16.0);
        float heat = clamp(v1 + v2 * 0.8, 0.0, 1.0) * (0.75 + 0.25 * sin(t * 1.7 + h * 3.0));
        float3 lavaC = mix(float3(0.55, 0.05, 0.02), float3(1.0, 0.45, 0.05), smoothstep(0.2, 0.7, heat));
        lavaC = mix(lavaC, float3(1.0, 0.92, 0.55), smoothstep(0.75, 1.0, heat));
        col = col * 0.55 + lavaC * heat * 1.1;
        float2 ec = floor(float2(fc.x / 6.0, fc.y / 6.0 + t * 4.0));
        float er = hash(ec);
        col += step(0.985, er) * float3(1.0, 0.6, 0.2) * (0.5 + 0.5 * sin(t * 6.0 + er * 50.0));
    } else if (m == 8) {
        // 霓虹：地平线落日 + 透视网格地面向前滚动 + 横扫激光
        float hz = 0.56;
        float3 nA = float3(1.0, 0.2, 0.75);
        float3 nB = float3(0.2, 0.9, 1.0);
        col = mix(float3(0.05, 0.02, 0.12), float3(0.16, 0.03, 0.22), uv.y);
        if (uv.y > hz) {
            float z = 0.35 / (uv.y - hz + 0.002);
            float gx = (uv.x - 0.5) * aspect * z;
            float gz = z + t * 0.9;
            float lx = smoothstep(0.90, 1.0, 1.0 - abs(fract(gx * 1.4) - 0.5) * 2.0);
            float lz = smoothstep(0.90, 1.0, 1.0 - abs(fract(gz * 0.8) - 0.5) * 2.0);
            float fade = smoothstep(hz, hz + 0.25, uv.y);
            col += mix(nA, nB, uv.x) * max(lx, lz) * fade * 0.9;
        }
        col += nA * exp(-abs(uv.y - hz) * 30.0) * 0.6;
        float sd = length((uv - float2(0.5, hz - 0.01)) * float2(aspect, 1.0));
        float sun = smoothstep(0.21, 0.20, sd) * step(uv.y, hz);
        float stripe = step(0.45, fract((hz - uv.y) * 26.0));
        sun *= mix(1.0, stripe, smoothstep(hz - 0.13, hz, uv.y));
        col += sun * mix(float3(1.0, 0.85, 0.30), nA, clamp((uv.y - (hz - 0.21)) / 0.21, 0.0, 1.0));
        float sx = (uv.x - (fract(t * 0.12) * 1.6 - 0.3)) * aspect * 22.0;
        col += nB * exp(-sx * sx) * 0.28;
    } else if (m == 9) {
        // 樱吹雪：三层花瓣，越近越大越实，各自旋转飘落
        float3 petalC = float3(1.0, 0.86, 0.90);
        for (int k = 0; k < 3; k++) {
            float fk = float(k);
            float sc = 5.0 + fk * 3.0;
            float2 gp = float2(uv.x * aspect, uv.y) * sc - float2(t * (0.25 + fk * 0.12), t * (0.45 + fk * 0.15));
            float2 id = floor(gp);
            float2 f = fract(gp) - 0.5;
            float rr = hash(id + fk * 13.1);
            if (rr < 0.45) {
                float2 off = float2(hash(id + 3.3), hash(id + 7.7)) - 0.5;
                f -= off * 0.5;
                float ang = rr * 40.0 + t * (0.6 + rr);
                float2 pr = float2(f.x * cos(ang) - f.y * sin(ang), f.x * sin(ang) + f.y * cos(ang));
                float d = length(pr * float2(1.0, 1.9));
                float notch = smoothstep(0.035, 0.0, length(pr - float2(0.13, 0.0)));
                float petal = smoothstep(0.17, 0.13, d) * (1.0 - notch);
                float shade = 0.86 + 0.14 * clamp(pr.x / 0.15, -1.0, 1.0);
                col = mix(col, petalC * shade, petal * (0.5 + fk * 0.22));
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
        float horizon = smoothstep(rs * 0.62, rs * 0.5, lr);
        float la = atan(ld.y, ld.x);
        // 光子环：极细、极亮；外面一圈柔和的吸积盘辉光；
        // 多普勒增亮：朝向我们转来的一侧更亮，亮斑绕环高速打转
        float einstein = exp(-pow((lr - rs) / 0.004, 2.0));
        float swirl = 0.3 + 0.7 * pow(0.5 + 0.5 * cos(la - t * 2.2), 4.0);
        float doppler = 0.6 + 0.4 * cos(la + 0.6 + uTilt.x * 1.5);
        float glowRing = exp(-pow((lr - rs * 1.25) / 0.035, 2.0)) * 0.45;
        // 吸积盘：斜着看的一条亮带，穿过黑洞，前半在视界前面
        float diskY = ld.y - ld.x * 0.18 - uTilt.y * 0.02;
        float disk = exp(-pow(diskY / 0.010, 2.0)) * smoothstep(rs * 3.2, rs * 1.05, lr) * smoothstep(rs * 0.55, rs * 0.9, lr);
        float diskFlow = 0.55 + 0.45 * sin(ld.x * 120.0 - t * 4.0);
        col = hueShift(col, uTilt.x * 0.5 - uTilt.y * 0.3 + sin(t * 0.1) * 0.12);
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
        col += arm * fall * mix(float3(0.55, 0.45, 1.0), float3(1.0, 0.75, 0.85), fall) * 0.12;
        // 透镜：视界吞掉一切光，外缘的光环与吸积盘发亮
        col *= 1.0 - horizon;
        float3 hot = float3(1.0, 0.82, 0.62);
        col += (einstein * 1.6 + glowRing) * swirl * doppler * hot;
        col += disk * diskFlow * doppler * hot * 1.4;
        // 彗星：椭圆轨道（像斜看的星盘），头亮尾长
        float2 e = d * float2(1.0, 1.9);
        float re = length(e);
        float ae = atan(e.y, e.x);
        for (int k = 0; k < 7; k++) {
            float fk = float(k);
            float rk = 0.12 + fk * 0.085;
            float head = t * (0.42 / (rk + 0.18)) + hash(float2(fk, 3.0)) * 6.2831;
            float da = mod(head - ae, 6.2831);
            float w = 0.0035 + 1.0 / uSize.y;
            float dr2 = re - rk;
            float ring = exp(-pow(dr2 / w, 2.0));
            float halo = exp(-pow(dr2 / (w * 5.0), 2.0)) * 0.35;
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
        // 暗部的全息虹彩：只在某些角度浮现
        float darkMask = 1.0 - smoothstep(0.05, 0.3, dot(col, float3(0.299, 0.587, 0.114)));
        float3 holo = 0.5 + 0.5 * cos(6.2831 * (diag * 1.3 + uTilt.x * 0.9 - uTilt.y * 0.6 + float3(0.0, 0.33, 0.67)));
        col += darkMask * holo * (0.05 + 0.07 * smoothstep(0.1, 0.6, length(uTilt))) * float3(0.8, 0.7, 1.0);
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
        // 液态铬（贴图）：黑镜般的铬面。起伏主要取自底图里真实的波形，再叠一层持续的细涟漪；
        // 两道摄影棚灯带的倒影横着滑过卡面，经过波峰时被拧弯、点亮，倾斜手机时跟着走
        float2 base = uv * 0.94 + 0.03 + uTilt * 0.012;
        float ee = 2.0 / uSize.y;
        float rh = ripple(p, t) * 0.5 + drops(p, t, aspect);
        float rx = ripple(p + float2(ee, 0.0), t) * 0.5 + drops(p + float2(ee, 0.0), t, aspect);
        float rz = ripple(p + float2(0.0, ee), t) * 0.5 + drops(p + float2(0.0, ee), t, aspect);
        float2 rip = float2(-(rx - rh) / ee, -(rz - rh) / ee) * 0.008;
        float2 tuv = clamp(base + rip * 0.45, 0.0, 1.0);
        float3 tex = float3(uPhoto.eval(tuv * uPhotoSize).rgb);
        float3 lw = float3(0.299, 0.587, 0.114);
        float2 px = float2(6.0 / uSize.x, 6.0 / uSize.y);
        float hL = dot(float3(uPhoto.eval(clamp(tuv - float2(px.x, 0.0), 0.0, 1.0) * uPhotoSize).rgb), lw);
        float hR = dot(float3(uPhoto.eval(clamp(tuv + float2(px.x, 0.0), 0.0, 1.0) * uPhotoSize).rgb), lw);
        float hU = dot(float3(uPhoto.eval(clamp(tuv - float2(0.0, px.y), 0.0, 1.0) * uPhotoSize).rgb), lw);
        float hD = dot(float3(uPhoto.eval(clamp(tuv + float2(0.0, px.y), 0.0, 1.0) * uPhotoSize).rgb), lw);
        float3 Nt = normalize(float3((hL - hR) * 2.6 + rip.x * 3.0, (hU - hD) * 2.6 + rip.y * 3.0, 1.0));
        // 涟漪本身也反光：环形波峰在黑面上亮起一圈细光
        float dropEdge = clamp(length(rip) * 6.0, 0.0, 1.0);
        // 灯带位置：随卡面纵向位置 + 法线偏折 + 时间单调前进（周期函数，绕回处亮度为零，无跳变）
        float ry = uv.y * 1.4 + Nt.y * 1.6 + Nt.x * 0.35 + uTilt.y * 2.4 + uTilt.x * 0.8 - t * 0.07;
        float sv = fract(ry * 0.5);
        float strips = exp(-pow((sv - 0.3) / 0.035, 2.0)) + 0.5 * exp(-pow((sv - 0.72) / 0.09, 2.0));
        float crest = max(smoothstep(0.08, 0.5, dot(tex, lw)), dropEdge * 0.8);
        col = tex * (0.9 + 0.2 * dot(Nt, L));
        col *= 1.0 - 0.25 * (1.0 - smoothstep(0.0, 0.3, uv.y));
        // 黑镜倒映摄影棚的彩色灯：深蓝从一侧、品红从另一侧，倾斜时整片颜色在镜面上移动
        float gx = uv.x + Nt.x * 0.8 + uTilt.x * 0.9;
        float gy = uv.y + Nt.y * 0.8 + uTilt.y * 0.9;
        float3 gelA = float3(0.10, 0.28, 0.75);
        float3 gelB = float3(0.70, 0.12, 0.55);
        float3 env = gelA * smoothstep(0.75, -0.1, gx) + gelB * smoothstep(0.35, 1.2, gx) * smoothstep(1.3, 0.2, gy);
        col += env * (0.04 + 0.32 * crest);
        float3 oil = 0.5 + 0.5 * cos(6.2831 * (ry * 0.9 + uTilt.x * 0.6 + float3(0.0, 0.33, 0.67)));
        float3 lightC = mix(float3(0.92, 0.95, 1.0), oil, 0.18);
        // 平静的黑面上灯带是一道柔和的反光，碰到波峰时变得锐利耀眼
        col += strips * lightC * (0.08 + 1.2 * crest);
        // 波峰高光带油膜虹彩，色相随角度变
        col += pow(max(dot(Nt, H), 0.0), 60.0) * crest * mix(float3(1.0), oil, 0.45) * 0.5;
        // 水银滴荡开的环：环脊亮起一道带虹彩的细光
        col += dropEdge * dropEdge * mix(float3(0.9, 0.95, 1.0), oil, 0.35) * 0.55;
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
    } else if (m == 13) {
        // 龙鳞 · 流金：贴图鳞片随倾斜视差 + 色相流转；鳞缘被一道斜光扫过；
        // 其上三条金色光带缓慢游动，光带周围飘着闪烁的星尘
        float2 tuv = clamp(uv * 0.94 + 0.03 + uTilt * 0.018 + N.xy * 0.006, 0.0, 1.0);
        float3 tex = float3(uPhoto.eval(tuv * uPhotoSize).rgb);
        float lum = dot(tex, float3(0.299, 0.587, 0.114));
        float hueA = uTilt.x * 1.3 - uTilt.y * 0.8 + sin(t * 0.13) * 0.35;
        col = hueShift(tex, hueA) * (0.78 + 0.26 * diffuse) * 0.98;
        // 鳞缘（贴图里最亮的细线）被扫光点亮
        float rim = smoothstep(0.28, 0.62, lum);
        float sweep = fract(t * 0.05) * 2.8 - 0.9 + uTilt.x * 0.35;
        float band = exp(-pow((diag - sweep) * 4.0, 2.0));
        col += rim * band * float3(1.0, 0.86, 0.52) * 0.9;
        col += rim * spec * 0.6;
        // 金色光带
        float3 gold = float3(1.0, 0.80, 0.40);
        for (int i = 0; i < 3; i++) {
            float fi = float(i);
            float x = uv.x * aspect;
            float yi = 0.5 + (fi - 1.0) * 0.27
                + 0.17 * sin(x * 1.7 + t * 0.32 + fi * 2.1)
                + 0.07 * sin(x * 4.1 - t * 0.21 + fi * 1.3);
            float dpx = abs(uv.y - yi) * uSize.y;
            float core = exp(-pow(dpx / 2.0, 2.0));
            float glow = exp(-dpx / 14.0) * 0.42 + exp(-dpx / 48.0) * 0.20 + exp(-dpx / 120.0) * 0.08;
            float3 tint = 0.5 + 0.5 * cos(6.2831 * (uv.x * 0.55 + fi * 0.31 + t * 0.025 + float3(0.0, 0.12, 0.24)));
            float3 rc = mix(gold, tint, 0.42);
            // 光带亮度沿自身起伏，像有光在里面流动
            float pulse = 0.65 + 0.35 * sin(x * 3.0 - t * 1.1 + fi * 1.7);
            col += rc * (core * 1.6 + glow) * pulse;
            float2 dc = floor(fc / 3.0) + fi * 17.0;
            float dr = hash(dc);
            float twinkle = 0.5 + 0.5 * sin(t * 2.6 + dr * 50.0);
            col += step(0.945, dr) * exp(-dpx / 38.0) * twinkle * gold * 1.25;
        }
        // 零星散落在鳞片上的金粉，很稀，只在角度合适时一闪
        float2 sc2 = floor(fc / 3.0);
        col += step(0.994, hash(sc2 + 9.1)) * (0.5 + 0.5 * sin(t * 2.0 + hash(sc2) * 60.0)) * gold * 0.5;
    }

    // —— 5b. 边缘辉光 + 暗角 ——
    float edge = min(min(uv.x, 1.0 - uv.x) * aspect, min(uv.y, 1.0 - uv.y));
    col += (1.0 - smoothstep(0.0, 0.045, edge)) * (lightCard ? 0.04 : 0.07);
    if (uTier > 2.5) {
        // 传说：一道光沿卡边不停绕圈
        float ang = atan(uv.y - 0.5, (uv.x - 0.5) * aspect);
        float spot = pow(0.5 + 0.5 * cos(ang - t * 1.3), 30.0);
        float rimZone = 1.0 - smoothstep(0.0, 0.03, edge);
        col += rimZone * (spot * float3(1.0, 0.86, 0.5) * 1.4 + 0.05);
    }
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

/** 卡面底色：Android 13+ 走流动着色器；更低版本退回静态对角渐变 */
@Composable
fun Modifier.flowingGradient(
    colors: List<Color>,
    lightSurface: Boolean,
    time: State<Float>?,
    tilt: State<Offset>,
    effect: Int = 0,
    /** 背景照片：非空时以照片为底色，叠加同一套光影 / 闪粉 / 虹彩 */
    photo: ImageBitmap? = null,
    /** 稀有度档位（0 普通 … 3 传说）：史诗起闪粉更密，传说卡边有绕圈流光 */
    tier: Int = 0,
): Modifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && FlowShaderOk) {
    flowShader(colors, lightSurface, time, tilt, effect, photo, tier)
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
    tier: Int,
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
    val flow = remember { floatArrayOf(0f, 0f, -1f) }
    val c0 = colors.first().toArgb()
    val c1 = colors.getOrElse(1) { colors.first() }.toArgb()
    val c2 = colors.last().toArgb()
    return drawBehind {
        shader.setFloatUniform("uSize", size.width, size.height)
        shader.setFloatUniform("uTime", time?.value ?: 0f)
        val t = tilt.value
        shader.setFloatUniform("uTilt", t.x, t.y)
        val now = time?.value ?: 0f
        val dt = if (flow[2] < 0f) 0f else (now - flow[2]).coerceIn(0f, 0.1f)
        flow[2] = now
        flow[0] += t.x * dt
        flow[1] += (0.35f + t.y) * dt
        shader.setFloatUniform("uFlow", flow[0], flow[1])
        shader.setFloatUniform("uLight", if (lightSurface) 1f else 0f)
        shader.setFloatUniform("uMode", effect.toFloat())
        shader.setFloatUniform("uTier", tier.toFloat())
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
