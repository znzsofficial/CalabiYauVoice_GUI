package com.nekolaska.calabiyau.feature.settings

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nekolaska.calabiyau.core.preferences.AppPrefs
import com.nekolaska.calabiyau.core.ui.AppSizes

@Composable
internal fun SettingsUpdateItem(isActive: () -> Boolean) {
    val version = rememberAppVersion()
    val updates = LocalUpdateCheckSession.current
    val pageIsActive by rememberUpdatedState(isActive)
    val active = pageIsActive()
    var status by remember { mutableStateOf<String?>(null) }
    var discoveredVersion by remember { mutableStateOf<String?>(null) }
    // The Home composition can outlive its active visit under a drawer or during a transition.
    var request by remember(active) { mutableStateOf<(suspend () -> UpdateApi.Result)?>(null) }
    val checking = request != null
    val newVersionName = updates.available?.versionName ?: discoveredVersion

    SettingsItem(
        icon = Icons.Outlined.SystemUpdate,
        title = "检查更新",
        subtitle = when {
            checking -> "正在检查…"
            status != null -> status
            newVersionName != null -> "发现新版本：$newVersionName"
            else -> "点击检查新版本"
        },
        tabularSubtitle = true,
        enabled = active && !checking,
        showChevron = false,
        trailing = {
            when {
                checking -> CircularProgressIndicator(
                    modifier = Modifier.size(AppSizes.iconSmall),
                    strokeWidth = 2.dp
                )
                newVersionName != null -> SettingsValueBadge(
                    text = "新 ${formatVersionTag(newVersionName)}",
                    highlighted = true,
                    tabular = true
                )
                else -> SettingsValueBadge(
                    text = formatVersionTag(version.name),
                    tabular = true
                )
            }
        },
        onClick = {
            if (request == null && pageIsActive()) {
                request = updates.manualRequest { UpdateApi.checkUpdate(version.name, version.code) }
            }
        }
    )

    request?.let { read ->
        LaunchedEffect(read) {
            try {
                val result = read()
                if (!pageIsActive()) return@LaunchedEffect
                when (result) {
                    is UpdateApi.Result.NewVersion -> {
                        discoveredVersion = result.info.versionName
                        status = "发现新版本：${result.info.versionName}"
                        updates.available = result.info
                        AppPrefs.lastUpdateCheck = System.currentTimeMillis()
                    }
                    UpdateApi.Result.AlreadyLatest -> {
                        discoveredVersion = null
                        status = "已是最新版本"
                        AppPrefs.lastUpdateCheck = System.currentTimeMillis()
                    }
                    is UpdateApi.Result.Error -> status = result.message
                }
            } finally {
                request = null
            }
        }
    }
}

private fun formatVersionTag(versionName: String): String {
    val trimmed = versionName.trim()
    return if (trimmed.startsWith("v", ignoreCase = true)) trimmed else "v$trimmed"
}
