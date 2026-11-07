package app.silk.wallet.ui.lock

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.silk.wallet.SilkApp
import app.silk.wallet.security.Biometric
import app.silk.wallet.security.PinVault
import app.silk.wallet.ui.theme.TitleSerif
import app.silk.wallet.ui.theme.WordmarkFont
import app.silk.wallet.ui.theme.enterRise
import app.silk.wallet.ui.theme.glassSurface
import app.silk.wallet.ui.theme.springClickable
import kotlinx.coroutines.delay

/**
 * 锁屏：能用生物识别时进来就自动调起（每 7 天强制一次 PIN 的那天除外）；
 * PIN 输满 6 位即校验，错了整排圆点抖一下并提示剩余次数，过多则倒计时锁定。
 */
@Composable
fun LockScreen(app: SilkApp) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val pins = app.pins
    val bioUsable = remember { pins.biometricOn && Biometric.available(context) }
    val pinRequired = remember { pins.pinDue() }
    var entry by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf(false) }
    var lockLeft by remember { mutableIntStateOf((pins.lockoutLeft() / 1000).toInt()) }
    val shake = remember { Animatable(0f) }
    var shakeTick by remember { mutableIntStateOf(0) }

    BackHandler { /* 锁定时不允许返回绕过 */ }

    fun bio() = Biometric.authenticate(context, "解锁 Silk", null, onSuccess = { app.lock.unlock() }, onFail = { msg -> msg?.let { message = it } })

    LaunchedEffect(Unit) { if (bioUsable && !(pins.pinSet && pinRequired)) bio() }
    LaunchedEffect(lockLeft) {
        if (lockLeft > 0) {
            message = "错误次数过多，$lockLeft 秒后再试"
            delay(1000)
            lockLeft -= 1
            if (lockLeft == 0) { message = null; error = false }
        }
    }
    LaunchedEffect(shakeTick) {
        if (shakeTick > 0) {
            shake.snapTo(0f)
            shake.animateTo(0f, spring(dampingRatio = 0.22f, stiffness = 900f), initialVelocity = 2600f)
        }
    }

    fun press(d: Char) {
        if (lockLeft > 0 || entry.length >= PinVault.LENGTH) return
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        entry += d
        error = false
        if (entry.length == PinVault.LENGTH) {
            when (val r = pins.verify(entry)) {
                PinVault.Result.Ok -> app.lock.unlock()
                is PinVault.Result.Wrong -> { message = "PIN 不对，还可以试 ${r.attemptsLeft} 次"; error = true; entry = ""; shakeTick++ }
                is PinVault.Result.LockedOut -> { lockLeft = (r.millis / 1000).toInt(); error = true; entry = ""; shakeTick++ }
            }
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Text("Silk", fontFamily = WordmarkFont, fontWeight = FontWeight.Bold, fontSize = 30.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.enterRise(0, true))
            Spacer(Modifier.height(18.dp))
            Box(Modifier.size(76.dp).enterRise(1, true).glassSurface(null, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.height(22.dp))
            Text(
                if (pins.pinSet) (if (pinRequired && bioUsable) "请输入 PIN（每 7 天一次）" else "输入 PIN 解锁") else "使用生物识别解锁",
                fontFamily = TitleSerif, fontWeight = FontWeight.Bold, fontSize = 24.sp, letterSpacing = 1.sp,
                modifier = Modifier.enterRise(2, true),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                message ?: " ", fontSize = 14.sp, textAlign = TextAlign.Center,
                color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (pins.pinSet) {
                Spacer(Modifier.height(28.dp))
                Row(Modifier.graphicsLayer { translationX = shake.value }, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    repeat(PinVault.LENGTH) { i -> Dot(i < entry.length) }
                }
            }
            Spacer(Modifier.weight(1f))
            if (pins.pinSet) {
                Pad(
                    onDigit = ::press,
                    onDelete = { if (entry.isNotEmpty()) entry = entry.dropLast(1) },
                    showBio = bioUsable && !pinRequired,
                    onBio = ::bio,
                    modifier = Modifier.enterRise(3, true),
                )
            } else if (bioUsable) {
                Box(
                    Modifier.size(84.dp).springClickable(pressedScale = 0.9f, onClick = ::bio).glassSurface(null, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Fingerprint, "生物识别", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(38.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun Dot(filled: Boolean) {
    val s by animateFloatAsState(if (filled) 1f else 0.82f, spring(dampingRatio = 0.45f, stiffness = 700f), label = "dot")
    val c by animateColorAsState(if (filled) MaterialTheme.colorScheme.onSurface else Color.Transparent, tween(120), label = "dotc")
    Box(
        Modifier.size(14.dp).graphicsLayer { scaleX = s; scaleY = s }.clip(CircleShape).background(c)
            .border(1.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f), CircleShape)
    )
}

private val KeySize = 76.dp

@Composable
private fun Pad(onDigit: (Char) -> Unit, onDelete: () -> Unit, showBio: Boolean, onBio: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        listOf("123", "456", "789").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) { row.forEach { c -> Key(c.toString()) { onDigit(c) } } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            if (showBio) IconKey(onBio) { Icon(Icons.Rounded.Fingerprint, "生物识别", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(30.dp)) }
            else Spacer(Modifier.size(KeySize))
            Key("0") { onDigit('0') }
            IconKey(onDelete) { Icon(Icons.AutoMirrored.Rounded.Backspace, "删除", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(26.dp)) }
        }
    }
}

@Composable
private fun Key(label: String, onClick: () -> Unit) {
    Box(
        Modifier.springClickable(pressedScale = 0.88f, onClick = onClick).size(KeySize).glassSurface(null, CircleShape, elevated = false),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 30.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun IconKey(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.size(KeySize).clip(CircleShape).springClickable(pressedScale = 0.85f, onClick = onClick), contentAlignment = Alignment.Center) { content() }
}
