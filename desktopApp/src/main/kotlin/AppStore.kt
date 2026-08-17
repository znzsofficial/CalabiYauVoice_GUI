import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import dev.nucleusframework.application.NucleusApplicationScope

enum class AppBackdrop {
    None,
    Mica,
    MicaAlt,
    Acrylic,
}

data class AppState(
    val darkMode: MutableState<Boolean>,
    val backdropType: MutableState<AppBackdrop>,
    val isWindows: Boolean,
)

val LocalAppStore = compositionLocalOf {
    AppState(
        darkMode = mutableStateOf(false),
        backdropType = mutableStateOf(AppBackdrop.None),
        isWindows = false,
    )
}

val LocalNucleusAppScope = staticCompositionLocalOf<NucleusApplicationScope> {
    error("LocalNucleusAppScope is not provided")
}
