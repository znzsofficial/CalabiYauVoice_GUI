import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import com.mayakapps.compose.windowstyler.WindowBackdrop

/**
 * 全局 UI 状态，通过 [LocalAppStore] 向下传递。
 *
 * @param darkMode       深色模式开关，true = 深色
 * @param backdropType   当前窗口 Backdrop；null 表示使用默认渐变背景
 * @param isWin11        是否运行于 Windows 11（Build ≥ 22000）
 * @param canUseNonWin11Backdrop 非 Win11 但 Skia 图层可用，可应用 Acrylic/Aero/Transparent
 */
data class AppState(
    val darkMode: MutableState<Boolean>,
    val backdropType: MutableState<WindowBackdrop?>,
    val isWin11: Boolean,
    val canUseNonWin11Backdrop: Boolean,
)

val LocalAppStore = compositionLocalOf {
    AppState(
        darkMode = mutableStateOf(false),
        backdropType = mutableStateOf(WindowBackdrop.Tabbed),
        isWin11 = false,
        canUseNonWin11Backdrop = false,
    )
}

