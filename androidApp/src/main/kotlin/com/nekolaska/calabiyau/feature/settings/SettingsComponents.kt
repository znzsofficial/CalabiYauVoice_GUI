package com.nekolaska.calabiyau.feature.settings

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.nekolaska.calabiyau.core.preferences.AppPrefs
import com.nekolaska.calabiyau.core.ui.AppShapes
import com.nekolaska.calabiyau.core.ui.AppSizes
import com.nekolaska.calabiyau.core.ui.AppSpacing
import com.nekolaska.calabiyau.core.ui.AppTextStyles
import com.nekolaska.calabiyau.core.ui.rememberPlainTextClipboardCopier
import com.nekolaska.calabiyau.core.ui.smoothCornerShape

@Composable
internal fun SettingsGroupHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = AppSpacing.xxxLarge, top = AppSpacing.cardContent, bottom = AppSpacing.large)
    )
}

@Composable
internal fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(smoothCornerShape(AppSpacing.xLarge))
            .clickable(onClick = onClick)
            .padding(horizontal = AppSpacing.cardContent, vertical = AppSpacing.sectionGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(AppSizes.iconButton),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(AppSizes.iconMedium)
                )
            }
        }
        Spacer(Modifier.width(AppSpacing.sectionGap))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = AppTextStyles.settingsItemTitle
            )
            Text(
                text = subtitle,
                style = AppTextStyles.settingsItemSubtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(AppSizes.iconMedium)
        )
    }
}

@Composable
internal fun SettingsToggleItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(smoothCornerShape(AppSpacing.xLarge))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = AppSpacing.cardContent, vertical = AppSpacing.sectionGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(AppSizes.iconButton),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(AppSizes.iconMedium)
                )
            }
        }
        Spacer(Modifier.width(AppSpacing.xLarge))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(AppSpacing.medium))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
internal fun SettingsDebugItem(
    currentVersion: String,
    showSnack: (String) -> Unit
) {
    var showDebugMenu by remember { mutableStateOf(false) }
    val copyText = rememberPlainTextClipboardCopier { showSnack("已复制") }
    SettingsItem(
        icon = Icons.Outlined.BugReport,
        title = "调试信息",
        subtitle = "版本 $currentVersion · API ${Build.VERSION.SDK_INT}",
        onClick = { showDebugMenu = true }
    )

    if (!showDebugMenu) return

    var showCrashConfirm by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { showDebugMenu = false },
        icon = { Icon(Icons.Outlined.BugReport, null) },
        title = { Text("调试菜单") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xSmall)) {
                Surface(
                    shape = smoothCornerShape(AppShapes.chipRadius),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        buildString {
                            appendLine("版本: $currentVersion (${Build.VERSION.SDK_INT})")
                            appendLine("设备: ${Build.MANUFACTURER} ${Build.MODEL}")
                            appendLine("系统: Android ${Build.VERSION.RELEASE}")
                            append("保存路径: ${AppPrefs.savePath}")
                        },
                        style = AppTextStyles.codeBlock,
                        modifier = Modifier.padding(AppSpacing.large)
                    )
                }

                OutlinedButton(
                    onClick = {
                        val info = buildString {
                            appendLine("版本: $currentVersion")
                            appendLine("设备: ${Build.MANUFACTURER} ${Build.MODEL}")
                            appendLine("系统: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                            append("保存路径: ${AppPrefs.savePath}")
                        }
                        copyText("设备信息", info)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.ContentCopy, null, Modifier.size(AppSizes.iconSmall))
                    Spacer(Modifier.width(AppSpacing.itemGap))
                    Text("复制设备信息")
                }

                FilledTonalButton(
                    onClick = { showCrashConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Icon(Icons.Outlined.Warning, null, Modifier.size(AppSizes.iconSmall))
                    Spacer(Modifier.width(AppSpacing.itemGap))
                    Text("触发测试崩溃")
                }
            }
        },
        shape = AppShapes.dialog,
        confirmButton = {
            TextButton(onClick = { showDebugMenu = false }) { Text("关闭") }
        }
    )

    if (showCrashConfirm) {
        AlertDialog(
            onDismissRequest = { showCrashConfirm = false },
            icon = {
                Icon(
                    Icons.Outlined.Warning,
                    null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("确认触发崩溃？") },
            text = { Text("应用会立即关闭并显示崩溃日志页面。") },
            shape = AppShapes.dialog,
            confirmButton = {
                FilledTonalButton(
                    onClick = { throw RuntimeException("手动触发的测试崩溃 - CrashHandler 功能验证") },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = { showCrashConfirm = false }) { Text("取消") }
            }
        )
    }
}
