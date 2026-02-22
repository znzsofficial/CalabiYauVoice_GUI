package jna.windows.structure

import com.sun.jna.platform.win32.Kernel32

val windowsBuildNumber by lazy {
    val buildNumber = Kernel32.INSTANCE.GetVersion().high.toInt()
    buildNumber
}

fun isWindows11OrLater() = windowsBuildNumber >= 22000