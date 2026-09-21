package com.nekolaska.calabiyau.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 应用级协程作用域：页面销毁不应中断的后台工作（如 WebView 文件落盘）。
 * 使用方需自行保证完成后与 UI 的交互可安全跳过（组合期 scope 已取消时静默）。
 */
internal object AppScopes {
    val io: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
