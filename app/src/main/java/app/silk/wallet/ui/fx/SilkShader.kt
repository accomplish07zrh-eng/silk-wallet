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
    float2 q = warp(p, t);
    float h = silk(q, t);
    float diag = (q.x / aspect) * 0.58 + q.y * 0.42;
    float3 col = ramp(diag);
    col = mix(col, float3(uC0.rgb), smoothstep(0.1, 1.0, h) * 0.26);
    col = mix(col, float3(uC2.rgb), smoothstep(0.1, 1.0, -h) * 0.22);

    // —— 2. 褶皱法线 + 光照 ——
    float e = 1.5 / uSize.y;
    float hx = silk(warp(p + float2(e, 0.0), t), t);
    float hy = silk(warp(p + float2(0.0, e), t), t);
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
    } else if (m == 4) {
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
        float sx = (uv.x - fract(t * 0.15)) * aspect * 22.0;
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
    col += streak * 0.016;
    col += (hash(fc) - 0.5) * (2.0 / 255.0);
    return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""

/**
 * 列表共用的流动时钟：同屏所有卡读同一个时间源，只在绘制阶段读取，
 * 帧推进只触发重绘，不触发重组。页面离开组合时时钟随之停止。
 */
val LocalFlowClock = staticCompositionLocalOf<State<Float>?> { null }

@Composable
fun rememberFlowClock(): State<Float> {
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var startNanos = 0L
        while (true) {
            withFrameNanos { frame ->
                if (startNanos == 0L) startNanos = frame
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
): Modifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
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
    val c0 = colors.first().toArgb()
    val c1 = colors.getOrElse(1) { colors.first() }.toArgb()
    val c2 = colors.last().toArgb()
    return drawBehind {
        shader.setFloatUniform("uSize", size.width, size.height)
        shader.setFloatUniform("uTime", time?.value ?: 0f)
        val t = tilt.value
        shader.setFloatUniform("uTilt", t.x, t.y)
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
