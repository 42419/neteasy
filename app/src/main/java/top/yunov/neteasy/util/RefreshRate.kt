package top.yunov.neteasy.util

import android.app.Activity
import android.os.Build
import android.view.Display

/**
 * 高刷新率申请。
 *
 * 参考 AOSP《多刷新率》（docs/core/graphics/multiple-refresh-rate）：
 * 系统默认按省电策略在 60Hz / 高刷之间动态切换，App 滚动、动画稍一空闲
 * 就可能被降回 60Hz。应用层唯一可靠的「我要高刷」方式是给窗口指定
 * [android.view.WindowManager.LayoutParams.preferredDisplayModeId]——
 * 从 [Display.getSupportedModes] 里挑出与当前分辨率一致、刷新率最高的
 * 显示模式并写入窗口属性，SurfaceFlinger 会按各窗口的偏好投票决定显示模式。
 *
 * 注意：
 * - 只选分辨率相同的模式，避免触发非无缝切换（切分辨率会黑屏闪烁）；
 * - 部分 OEM 的省电策略（低电量模式等）仍可覆盖应用请求，属于系统行为；
 * - 需要 API 23+（本项目 minSdk 29，无需判断）。
 * - 拿 [Display] 不能走 `activity.window.decorView.display`：[applyHighest] 挂在
 *   `ActivityLifecycleCallbacks.onActivityCreated`，这个时机窗口还没 attach 到
 *   WindowManager（真正 attach 一般在 onResume 前后），View 没 attach 前
 *   `View.getDisplay()` 恒为 null，导致这行直接提前 return、后面全白跑——
 *   实测下来这个功能会「安安静静地什么都没做」。改用 `Context.getDisplay()`
 *   （API 30+）/ `WindowManager.getDefaultDisplay()`（minSdk 29 的兜底）
 *   直接从 Context/WindowManager 上取，不依赖 View 的 attach 状态，onCreate()
 *   阶段就能稳定拿到非空值。
 */
object RefreshRate {

    /** 让 [activity] 的窗口跑在当前分辨率下系统支持的最高刷新率。 */
    fun applyHighest(activity: Activity) {
        @Suppress("DEPRECATION")
        val display: Display =
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                activity.display
            } else {
                activity.windowManager.defaultDisplay
            }) ?: return
        val current = display.mode ?: return
        val best = display.supportedModes
            ?.filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            ?.maxByOrNull { it.refreshRate }
            ?: return
        if (best.modeId == current.modeId) return

        val lp = activity.window.attributes
        if (lp.preferredDisplayModeId != best.modeId) {
            lp.preferredDisplayModeId = best.modeId
            activity.window.attributes = lp
        }
    }
}
