package com.nekolaska.calabiyau

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nekolaska.calabiyau.data.AppPrefs
import com.nekolaska.calabiyau.data.WikiEngine
import com.nekolaska.calabiyau.ui.MainScreen
import data.PortraitRepository

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppPrefs.init(this)
        PortraitRepository.init(
            fetchFilesInCategory = { cat, audio -> WikiEngine.fetchFilesInCategory(cat, audio) },
            searchFilesFn = { kw, audio -> WikiEngine.searchFiles(kw, audio) },
            getAllCharacterNames = { WikiEngine.getAllCharacterNames() }
        )

        setContent {
            AppTheme {
                val vm: MainViewModel = viewModel()
                MainScreen(vm)
            }
        }
    }
}

/** 全局主题模式状态，可从任何 Composable 中读取/修改 */
val LocalThemeMode = staticCompositionLocalOf { mutableIntStateOf(AppPrefs.themeMode) }

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val themeMode = remember { mutableIntStateOf(AppPrefs.themeMode) }

    val darkTheme = when (themeMode.intValue) {
        AppPrefs.THEME_LIGHT -> false
        AppPrefs.THEME_DARK -> true
        else -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }

    CompositionLocalProvider(LocalThemeMode provides themeMode) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}
