package jna.windows

import jna.windows.structure.MENUITEMINFO
import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.CallbackReference
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.WPARAM
import com.sun.jna.platform.win32.WinDef.LPARAM
import com.sun.jna.platform.win32.WinDef.LRESULT
import com.sun.jna.platform.win32.WinUser.WindowProc
import com.sun.jna.platform.win32.BaseTSD.LONG_PTR
import com.sun.jna.platform.win32.WinDef.HMENU
import com.sun.jna.platform.win32.WinDef.POINT
import com.sun.jna.platform.win32.WinDef.RECT
import com.sun.jna.platform.win32.WinDef.UINT
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.win32.W32APIOptions

@Suppress("FunctionName")
internal interface User32Extend : User32 {

    fun SetWindowLong(hWnd: HWND, nIndex: Int, wndProc: WindowProc): Int

    fun SetWindowLongPtr(hWnd: HWND, nIndex: Int, wndProc: WindowProc): LONG_PTR

    fun SetWindowLongPtr(hWnd: HWND, nIndex: Int, wndProc: LONG_PTR): LONG_PTR

    override fun GetWindowLongPtr(hWnd: HWND, nIndex: Int): LONG_PTR

    fun CallWindowProc(
        proc: LONG_PTR,
        hWnd: HWND,
        uMsg: Int,
        uParam: WPARAM,
        lParam: LPARAM
    ): LRESULT

    fun GetSystemMetricsForDpi(nIndex: Int, dpi: UINT): Int

    fun GetDpiForWindow(hWnd: HWND): UINT

    fun ScreenToClient(hWnd: HWND, lpPoint: POINT): Boolean

    fun GetSystemMenu(hWnd: HWND, bRevert: Boolean): HMENU?

    fun SetMenuItemInfo(hMenu: HMENU, uItem: Int, fByPosition: Boolean, lpmii: MENUITEMINFO): Boolean

    fun TrackPopupMenu(hMenu: HMENU, uFlags: Int, x: Int, y: Int, nReserved: Int, hWnd: HWND, prcRect: RECT?): Int

    fun SetMenuDefaultItem(hMenu: HMENU, uItem: Int, fByPos: Boolean): Boolean


    companion object {

        val instance by lazy {
            runCatching {
                Native.load(
                    "user32",
                    User32Extend::class.java,
                    W32APIOptions.DEFAULT_OPTIONS
                )
            }
                .onFailure { println("Could not load user32 library") }
                .getOrNull()
        }
    }
}

internal data class WindowProcedureHook(
    val original: LONG_PTR,
    val callbackAddress: Long
)

internal fun User32Extend.installWindowProcedure(hWnd: HWND, procedure: WindowProcedure): WindowProcedureHook? {
    Native.setLastError(0)
    val original = if (Platform.is64Bit()) {
        SetWindowLongPtr(hWnd, WinUser.GWL_WNDPROC, procedure)
    } else {
        LONG_PTR(SetWindowLong(hWnd, WinUser.GWL_WNDPROC, procedure).toLong())
    }
    if (original.toLong() == 0L) return null
    val callbackAddress = Pointer.nativeValue(CallbackReference.getFunctionPointer(procedure))
    return WindowProcedureHook(original, callbackAddress)
}

internal fun User32Extend.restoreWindowProcedure(hWnd: HWND, hook: WindowProcedureHook) {
    val current = if (Platform.is64Bit()) {
        GetWindowLongPtr(hWnd, WinUser.GWL_WNDPROC).toLong()
    } else {
        GetWindowLong(hWnd, WinUser.GWL_WNDPROC).toLong() and 0xffff_ffffL
    }
    val callbackAddress = if (Platform.is64Bit()) hook.callbackAddress else hook.callbackAddress and 0xffff_ffffL
    if (current != callbackAddress) return

    if (Platform.is64Bit()) SetWindowLongPtr(hWnd, WinUser.GWL_WNDPROC, hook.original)
    else SetWindowLong(hWnd, WinUser.GWL_WNDPROC, hook.original.toInt())
}

internal fun User32.isWindowInMaximized(hWnd: HWND): Boolean {
    val placement = WinUser.WINDOWPLACEMENT()
    val result = GetWindowPlacement(hWnd, placement)
        .booleanValue() && placement.showCmd == WinUser.SW_SHOWMAXIMIZED
    placement.clear()
    return result
}

internal fun User32.updateWindowStyle(hWnd: HWND, styleBlock: (oldStyle: Int) -> Int) {
    val oldStyle = GetWindowLong(hWnd, WinUser.GWL_STYLE)
    SetWindowLong(hWnd, WinUser.GWL_STYLE, styleBlock(oldStyle))
}

internal val Int.lowWord: Int
    get() = (this and 0xFFFF)

internal val Int.highWord: Int
    get() = (this shr 16) and 0xFFFF
