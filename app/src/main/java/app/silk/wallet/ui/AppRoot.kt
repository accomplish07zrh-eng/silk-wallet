package app.silk.wallet.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.silk.wallet.SilkApp
import app.silk.wallet.data.Settings
import app.silk.wallet.ui.draw.DrawScreen
import app.silk.wallet.ui.edit.EditScreen
import app.silk.wallet.ui.edit.EditViewModel
import app.silk.wallet.ui.home.HomeScreen
import app.silk.wallet.ui.home.HomeViewModel
import app.silk.wallet.ui.lock.LockScreen
import app.silk.wallet.ui.settings.SettingsScreen
import app.silk.wallet.ui.settings.SettingsViewModel
import app.silk.wallet.ui.theme.EntranceGate
import app.silk.wallet.ui.theme.LocalEntrance
import app.silk.wallet.ui.theme.LocalEntranceDelay
import app.silk.wallet.ui.theme.Motion

/**
 * 根结构：导航（统一推入 / 返回动效）+ 覆盖式锁屏 + 冷启动字标。
 * 锁屏叠在导航之上而不是替换它，锁一次不会丢掉编辑到一半的表单。
 */
@Composable
fun AppRoot(app: SilkApp, settings: Settings) {
    val locked by app.lock.locked.collectAsState()
    val gate = remember { EntranceGate() }
    var intro by rememberSaveable { mutableStateOf(true) }
    val focus = LocalFocusManager.current
    LaunchedEffect(locked) { if (locked) focus.clearFocus(force = true) }

    CompositionLocalProvider(
        LocalEntrance provides gate,
        LocalEntranceDelay provides if (intro) INTRO_HANDOFF_MS else 0L,
    ) {
        Box(Modifier.fillMaxSize()) {
            Nav(app, settings, locked)
            AnimatedVisibility(
                visible = locked,
                enter = fadeIn(tween(160)),
                exit = fadeOut(tween(260)) + scaleOut(spring(dampingRatio = 0.9f, stiffness = 300f), targetScale = 1.06f),
            ) {
                LockScreen(app)
            }
            if (intro) Intro { intro = false }
        }
    }
}

@Composable
private fun Nav(app: SilkApp, settings: Settings, locked: Boolean) {
    val nav = rememberNavController()
    val context = LocalContext.current

    // NFC 读卡：锁屏时先暂存，解锁后再打开添加页；超过 2 分钟的作废
    val pending by app.nfcInbox.pending.collectAsState()
    LaunchedEffect(pending, locked) {
        val p = pending ?: return@LaunchedEffect
        if (locked) return@LaunchedEffect
        if (!p.fresh) { app.nfcInbox.take(); return@LaunchedEffect }
        Toast.makeText(context, "已读取卡片", Toast.LENGTH_SHORT).show()
        nav.navigate("edit/-1?nfc=true") { launchSingleTop = true }
    }

    NavHost(
        navController = nav,
        startDestination = "home",
        enterTransition = Motion.push,
        exitTransition = Motion.pushOut,
        popEnterTransition = Motion.pop,
        popExitTransition = Motion.popOut,
    ) {
        composable("home") {
            val vm: HomeViewModel = viewModel { HomeViewModel(app.cards, app.prefs) }
            HomeScreen(
                app = app,
                vm = vm,
                settings = settings,
                onAdd = { nav.navigate("edit/-1") },
                onEdit = { nav.navigate("edit/$it") },
                onSettings = { nav.navigate("settings") },
                onDraw = { nav.navigate("draw") },
            )
        }
        composable(
            "edit/{id}?nfc={nfc}",
            arguments = listOf(
                navArgument("id") { type = NavType.LongType },
                navArgument("nfc") { type = NavType.BoolType; defaultValue = false },
            ),
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: -1L
            val fromNfc = entry.arguments?.getBoolean("nfc") ?: false
            val vm: EditViewModel = viewModel {
                EditViewModel(app.cards, app.prefs, id, if (fromNfc) app.nfcInbox.take()?.draft else null)
            }
            EditScreen(vm = vm, isNew = id <= 0, onBack = { nav.popBackStack() })
        }
        composable("settings") {
            val vm: SettingsViewModel = viewModel { SettingsViewModel(app) }
            SettingsScreen(app, vm, settings, onBack = { nav.popBackStack() })
        }
        composable("draw") {
            DrawScreen(app = app, settings = settings, onBack = { nav.popBackStack() })
        }
    }
}
