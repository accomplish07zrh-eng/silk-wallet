package app.silk.wallet.ui.settings

import androidx.compose.material.icons.outlined.AutoAwesome
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.GppGood
import androidx.compose.material.icons.outlined.GppMaybe
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockClock
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.ScreenLockPortrait
import androidx.compose.material.icons.outlined.Screenshot
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.silk.wallet.BuildConfig
import app.silk.wallet.SilkApp
import app.silk.wallet.data.Prefs
import app.silk.wallet.data.Settings
import app.silk.wallet.net.Http
import app.silk.wallet.notify.ExpiryReminder
import app.silk.wallet.security.Biometric
import app.silk.wallet.security.PinVault
import app.silk.wallet.ui.theme.DialogField
import app.silk.wallet.ui.theme.DialogNote
import app.silk.wallet.ui.theme.LargeTitleBar
import app.silk.wallet.ui.theme.LocalDarkTheme
import app.silk.wallet.ui.theme.SilkDialog
import app.silk.wallet.ui.theme.SilkSwitch
import app.silk.wallet.ui.theme.enterRise
import app.silk.wallet.ui.theme.floatingCard
import app.silk.wallet.ui.theme.springClickable
import java.time.LocalDate

/** 设置：安全 / 隐私 / 提醒 / 网络 / 外观 / 数据，白色浮起分组依次浮入 */
@Composable
fun SettingsScreen(app: SilkApp, vm: SettingsViewModel, settings: Settings, onBack: () -> Unit) {
    val context = LocalContext.current
    val pins = app.pins
    var pinSet by remember { mutableStateOf(pins.pinSet) }
    var bioOn by remember { mutableStateOf(pins.biometricOn) }
    val bioAvailable = remember { Biometric.available(context) }
    var proxy by rememberSaveable { mutableStateOf(settings.proxy) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var pendingUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.setExpiryNotify(context, granted)
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) { pendingUri = uri; dialog = "export" }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { pendingUri = uri; dialog = "import" }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { LargeTitleBar(title = "设置", onBack = onBack) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
        ) {
            Section("安全", 0) {
                SecurityHeader(pinSet, bioOn)
                Item(
                    Icons.Outlined.Fingerprint, "生物识别解锁",
                    if (!bioAvailable) "设备不支持或尚未录入指纹 / 面容" else "验证通过后开启，可单独使用",
                    enabled = bioAvailable,
                ) {
                    SilkSwitch(bioOn && bioAvailable, enabled = bioAvailable, onCheckedChange = { want ->
                        if (want) Biometric.authenticate(context, "开启生物识别解锁", "验证一次即可启用", onSuccess = {
                            pins.setBiometric(true); bioOn = true
                        }) else { pins.setBiometric(false); bioOn = false }
                    })
                }
                Divider()
                Item(Icons.Outlined.Lock, "PIN 解锁", if (bioOn) "作为生物识别的备用方式，建议开启" else "打开应用时输入 6 位 PIN") {
                    SilkSwitch(pinSet, onCheckedChange = { want -> dialog = if (want) "pin_new" else "pin_off" })
                }
                Divider()
                Choice(
                    Icons.Outlined.Timer, "自动锁定", "离开应用超过所选时间后重新锁定",
                    listOf(0 to "立即", 30 to "30 秒", 60 to "1 分钟", 300 to "5 分钟", 900 to "15 分钟"),
                    settings.autoLockSeconds, enabled = pinSet || bioOn, onSelect = vm::setAutoLock,
                )
                Divider()
                Item(Icons.Outlined.ScreenLockPortrait, "息屏即锁", "屏幕熄灭时立即锁定", enabled = pinSet || bioOn) {
                    SilkSwitch(settings.lockOnScreenOff && (pinSet || bioOn), enabled = pinSet || bioOn, onCheckedChange = vm::setScreenOff)
                }
                if (pinSet || bioOn) {
                    Divider()
                    Item(Icons.Outlined.LockClock, "立即锁定", null, onClick = { app.lock.lockNow() }) { Chevron() }
                }
                if (pinSet) {
                    Divider()
                    Item(Icons.Outlined.Password, "修改 PIN", null, onClick = { dialog = "pin_change" }) { Chevron() }
                }
            }

            Section("隐私", 1) {
                Item(Icons.Outlined.CreditCard, "打码显示卡号", "列表只显示末四位") {
                    SilkSwitch(settings.maskNumbers, onCheckedChange = vm::setMask)
                }
                Divider()
                Choice(
                    Icons.Outlined.ContentPaste, "自动清空剪贴板", "复制的卡片信息在所选时间后清除",
                    listOf(0 to "不清除", 15 to "15 秒", 30 to "30 秒", 60 to "60 秒"),
                    settings.clipboardSeconds, onSelect = vm::setClipboard,
                )
                Divider()
                Item(Icons.Outlined.Screenshot, "阻止截屏", "同时隐藏最近任务里的预览") {
                    SilkSwitch(settings.secureScreen, onCheckedChange = vm::setSecure)
                }
            }

            Section("提醒", 2) {
                Item(Icons.Outlined.NotificationsActive, "到期通知", "每天在本机检查即将到期和已过期的卡") {
                    SilkSwitch(settings.expiryNotify, onCheckedChange = { want ->
                        if (want && !ExpiryReminder.canNotify(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else vm.setExpiryNotify(context, want)
                    })
                }
                Divider()
                Choice(
                    Icons.Outlined.Timer, "提前提醒", "到期前多少天算「即将到期」",
                    Prefs.NOTICE_OPTIONS.map { it to "$it 天" }, settings.noticeDays, onSelect = vm::setNoticeDays,
                )
            }

            Section("网络", 3) {
                Item(Icons.Outlined.CloudOff, "完全离线", "关闭在线查询、连通测试与检查更新") {
                    SilkSwitch(settings.offline, onCheckedChange = vm::setOffline)
                }
                Divider()
                Column(Modifier.padding(16.dp).alpha(if (settings.offline) 0.4f else 1f)) {
                    Text("代理", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
                    ProxyField(proxy, { proxy = it }, isError = !Http.isValidProxy(proxy))
                    Text(
                        if (!Http.isValidProxy(proxy)) "格式：socks5://host:port 或 http://host:port" else "留空即直连。只有在线查询、连通测试和检查更新会联网。",
                        fontSize = 12.sp, lineHeight = 16.sp,
                        color = if (!Http.isValidProxy(proxy)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 12.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Pill("保存", true, enabled = !settings.offline && Http.isValidProxy(proxy) && proxy.trim() != settings.proxy) { vm.saveProxy(proxy) }
                        Pill("测试连接", false, enabled = !settings.offline && Http.isValidProxy(proxy) && vm.proxyTest != Status.Working, busy = vm.proxyTest == Status.Working) { vm.testProxy(proxy) }
                    }
                    StatusLine(vm.proxyTest)
                }
            }

            Section("外观", 4) {
                Item(Icons.Outlined.AutoAwesome, "卡面动效", "流动光泽、皮肤特效与倾斜感应；关闭后卡面静止，更省电") {
                    SilkSwitch(settings.motion, onCheckedChange = vm::setMotion)
                }
                Divider()
                Choice(Icons.Outlined.Palette, "主题", null, listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色"), settings.theme, onSelect = vm::setTheme)
            }

            Section("数据", 5) {
                Item(Icons.Outlined.SystemUpdate, "检查更新", "从 GitHub 获取新版本", onClick = vm::checkUpdate) {
                    if (vm.update == UpdateUi.Checking) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Chevron()
                }
                Divider()
                Item(Icons.Outlined.FileDownload, "导出加密备份", "用备份密码加密保存全部卡片", onClick = {
                    app.lock.allowExternalOnce()
                    exportLauncher.launch("silk-backup-${LocalDate.now()}.silk")
                }) { Chevron() }
                Divider()
                Item(Icons.Outlined.FileUpload, "导入加密备份", "按卡号合并，相同卡号以备份为准", onClick = {
                    app.lock.allowExternalOnce()
                    importLauncher.launch(arrayOf("*/*"))
                }) { Chevron() }
                Box(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp)) { StatusLine(vm.backup) }
                Text(
                    "· 卡片只存在本机，整库 AES-256 加密，密钥由系统 Keystore 保护\n· 没有统计、广告或任何后台上报\n· 联网只发生在你主动发起的查询、测试与更新检查；查询只发卡号前 8 位",
                    fontSize = 12.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 16.dp),
                )
            }

            Text(
                "Silk ${BuildConfig.VERSION_NAME}",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 22.dp, bottom = 32.dp),
            )
        }
    }

    when (dialog) {
        "pin_new" -> PinSetupDialog(onDismiss = { dialog = null }) { pin ->
            pins.setPin(pin); pinSet = true; dialog = null
        }
        "pin_off" -> PinVerifyDialog(pins, "关闭 PIN 解锁", "关闭", onDismiss = { dialog = null }) {
            pins.clearPin(); pinSet = false; dialog = null
        }
        "pin_change" -> PinVerifyDialog(pins, "先验证当前 PIN", "下一步", onDismiss = { dialog = null }) { dialog = "pin_new" }
        "export", "import" -> BackupDialog(
            export = dialog == "export",
            onDismiss = { dialog = null; pendingUri = null },
        ) { password ->
            val uri = pendingUri ?: return@BackupDialog
            if (dialog == "export") vm.exportTo(context, uri, password) else vm.importFrom(context, uri, password)
            dialog = null
            pendingUri = null
        }
    }
    when (val u = vm.update) {
        is UpdateUi.Available, is UpdateUi.Downloading, is UpdateUi.Latest, is UpdateUi.Failed -> UpdateDialog(u, onDismiss = vm::dismissUpdate) { r ->
            vm.downloadAndInstall(context, r)
        }
        else -> Unit
    }
}

// —— 组件 ——

@Composable
private fun Section(title: String, index: Int, content: @Composable ColumnScope.() -> Unit) {
    Text(
        title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 6.dp, top = 22.dp, bottom = 8.dp).enterRise(index * 2, true),
    )
    Column(Modifier.fillMaxWidth().enterRise(index * 2 + 1, true).floatingCard(RoundedCornerShape(22.dp)), content = content)
}

@Composable
private fun Divider() = HorizontalDivider(
    modifier = Modifier.padding(start = 66.dp),
    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
)

@Composable
private fun Chevron() = Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun Item(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    val a = if (enabled) 1f else 0.45f
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.springClickable(enabled = enabled, pressedScale = 0.985f, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f * a), modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.5.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = a))
            if (subtitle != null) Text(subtitle, fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = a))
        }
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}

@Composable
private fun <T> Choice(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    options: List<Pair<T, String>>,
    current: T,
    enabled: Boolean = true,
    onSelect: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Item(icon, title, subtitle, enabled, onClick = { open = true }) {
        Box {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(options.firstOrNull { it.first == current }?.second ?: "$current", fontSize = 14.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(2.dp))
                Icon(Icons.Rounded.UnfoldMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
            }
            DropdownMenu(open, { open = false }, shape = RoundedCornerShape(18.dp), containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                options.forEach { (v, label) ->
                    DropdownMenuItem(
                        text = { Text(label, fontWeight = if (v == current) FontWeight.SemiBold else FontWeight.Normal) },
                        onClick = { onSelect(v); open = false },
                    )
                }
            }
        }
    }
}

/** 安全状态：克制的圆形徽记 + 文字；未开启时一颗琥珀色提示点 */
@Composable
private fun SecurityHeader(pinSet: Boolean, bioOn: Boolean) {
    val cs = MaterialTheme.colorScheme
    val on = pinSet || bioOn
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box {
            Box(Modifier.size(44.dp).clip(CircleShape).background(if (on) cs.onSurface else cs.onSurface.copy(alpha = 0.06f)), contentAlignment = Alignment.Center) {
                Icon(if (on) Icons.Outlined.GppGood else Icons.Outlined.GppMaybe, null, tint = if (on) cs.surfaceContainerHigh else cs.onSurface, modifier = Modifier.size(22.dp))
            }
            if (!on) Box(
                Modifier.align(Alignment.TopEnd).size(11.dp).clip(CircleShape).background(Color(0xFFFF9F0A))
                    .border(2.dp, if (LocalDarkTheme.current) cs.surface else Color.White, CircleShape)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                when { pinSet && bioOn -> "生物识别 + PIN 已开启"; bioOn -> "生物识别已开启"; pinSet -> "PIN 已开启"; else -> "应用锁未开启" },
                fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurface,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                when {
                    pinSet && bioOn -> "优先用生物识别解锁，PIN 作为备用"
                    bioOn -> "建议同时开启 PIN，以防生物识别不可用"
                    pinSet -> "可以再开启生物识别，解锁更快"
                    else -> "卡片已加密存储；开启下方任一方式后，打开应用需先解锁"
                },
                fontSize = 12.5.sp, lineHeight = 17.sp, color = cs.onSurfaceVariant,
            )
        }
    }
    Divider()
}

@Composable
private fun ProxyField(value: String, onChange: (String) -> Unit, isError: Boolean) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier.fillMaxWidth().height(48.dp).clip(shape)
            .background(cs.onSurface.copy(alpha = if (focused) 0.07f else 0.045f))
            .border(1.dp, if (isError) cs.error.copy(alpha = 0.6f) else Color.Transparent, shape)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) Text("socks5://127.0.0.1:10808", fontSize = 15.sp, fontFamily = FontFamily.Monospace, color = cs.onSurfaceVariant.copy(alpha = 0.5f))
        BasicTextField(
            value, onChange, singleLine = true, interactionSource = interaction,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            textStyle = LocalTextStyle.current.copy(fontSize = 15.sp, fontFamily = FontFamily.Monospace, color = cs.onSurface),
            cursorBrush = SolidColor(cs.onSurface), modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Pill(text: String, primary: Boolean, enabled: Boolean, busy: Boolean = false, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val fg = if (primary) cs.surfaceContainerHigh else cs.onSurface
    Row(
        Modifier.alpha(if (enabled || busy) 1f else 0.35f).height(40.dp).clip(CircleShape)
            .background(if (primary) cs.onSurface else cs.onSurface.copy(alpha = 0.06f))
            .springClickable(enabled = enabled, pressedScale = 0.94f, onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) { CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = fg); Spacer(Modifier.width(8.dp)) }
        Text(text, color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StatusLine(s: Status) {
    val (text, color) = when (s) {
        is Status.Ok -> s.message to Color(0xFF34C759)
        is Status.Failed -> s.message to MaterialTheme.colorScheme.error
        else -> return
    }
    Row(Modifier.padding(top = 10.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(7.dp))
        Text(text, color = color, fontSize = 13.sp)
    }
}

// —— 弹窗 ——

@Composable
private fun PinSetupDialog(onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    val mismatch = b.length == PinVault.LENGTH && a != b
    SilkDialog("设置 6 位 PIN", onDismiss, "确定", { onDone(a) }, confirmEnabled = a.length == PinVault.LENGTH && a == b) {
        DialogField(a, { a = it.filter(Char::isDigit).take(PinVault.LENGTH) }, "输入 PIN", secret = true, keyboard = KeyboardType.NumberPassword)
        Spacer(Modifier.height(10.dp))
        DialogField(b, { b = it.filter(Char::isDigit).take(PinVault.LENGTH) }, "再输一次", secret = true, keyboard = KeyboardType.NumberPassword, isError = mismatch)
        if (mismatch) DialogNote("两次输入不一致", error = true)
    }
}

@Composable
private fun PinVerifyDialog(pins: PinVault, title: String, confirm: String, onDismiss: () -> Unit, onOk: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    SilkDialog(title, onDismiss, confirm, {
        when (val r = pins.verify(pin)) {
            PinVault.Result.Ok -> onOk()
            is PinVault.Result.Wrong -> { err = "PIN 不对，还可以试 ${r.attemptsLeft} 次"; pin = "" }
            is PinVault.Result.LockedOut -> { err = "错误次数过多，请 ${r.millis / 1000 + 1} 秒后再试"; pin = "" }
        }
    }, confirmEnabled = pin.length == PinVault.LENGTH, destructive = confirm == "关闭") {
        DialogField(pin, { pin = it.filter(Char::isDigit).take(PinVault.LENGTH) }, "当前 PIN", secret = true, keyboard = KeyboardType.NumberPassword, isError = err != null)
        err?.let { DialogNote(it, error = true) }
    }
}

@Composable
private fun BackupDialog(export: Boolean, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    val ok = if (export) a.length >= 6 && a == b else a.isNotEmpty()
    SilkDialog(if (export) "设置备份密码" else "输入备份密码", onDismiss, if (export) "导出" else "导入", { onConfirm(a) }, confirmEnabled = ok) {
        DialogField(a, { a = it }, "备份密码", secret = true)
        if (export) {
            Spacer(Modifier.height(10.dp))
            DialogField(b, { b = it }, "再输一次", secret = true, isError = b.isNotEmpty() && a != b)
            DialogNote("至少 6 位。备份密码与 PIN 无关，忘记后无法恢复备份。")
        }
    }
}

@Composable
private fun UpdateDialog(u: UpdateUi, onDismiss: () -> Unit, onDownload: (app.silk.wallet.net.Updater.Release) -> Unit) {
    when (u) {
        is UpdateUi.Latest -> SilkDialog("已是最新版本", onDismiss, "好", onDismiss) { DialogNote("当前版本 ${u.version}") }
        is UpdateUi.Failed -> SilkDialog("检查更新失败", onDismiss, "好", onDismiss) { DialogNote(u.message, error = true) }
        is UpdateUi.Available -> SilkDialog("发现新版本 ${u.release.version}", onDismiss, "下载并安装", { onDownload(u.release) }) {
            Text(u.release.notes.ifBlank { "没有更新说明" }, fontSize = 13.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 12)
        }
        is UpdateUi.Downloading -> SilkDialog("正在下载 ${u.release.version}", onDismiss, "下载中", {}, busy = true) {
            LinearProgressIndicator(progress = { u.progress }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape), color = MaterialTheme.colorScheme.onSurface)
            DialogNote("${(u.progress * 100).toInt()}%")
        }
        else -> Unit
    }
}
