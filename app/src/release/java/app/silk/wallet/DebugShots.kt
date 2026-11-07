package app.silk.wallet

import android.app.Activity

/** 正式包不带截图钩子（调试包里的同名对象才会真正截图） */
object DebugShots {
    @Suppress("UNUSED_PARAMETER")
    fun install(activity: Activity) = Unit
}
