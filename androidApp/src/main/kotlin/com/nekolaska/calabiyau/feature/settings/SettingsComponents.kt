package com.nekolaska.calabiyau.feature.settings

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.nekolaska.calabiyau.core.preferences.AppPrefs
import com.nekolaska.calabiyau.core.ui.AppShapes
import com.nekolaska.calabiyau.core.ui.AppSizes
import com.nekolaska.calabiyau.core.ui.AppSpacing
import com.nekolaska.calabiyau.core.ui.AppTextStyles
import com.nekolaska.calabiyau.core.ui.rememberPlainTextClipboardCopier

/** No weighted body: even the title and actions remain reachable on a short dialog window. */
@Composable
internal fun SettingsDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    shape: Shape = AppShapes.dialog
) {
    BasicAlertDialog(onDismissRequest = onDismissRequest) {
        Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(AppSpacing.dialogContent),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sectionGap)
            ) {
                icon?.let {
                    Box(Modifier.align(Alignment.CenterHorizontally)) { it() }
                }
                ProvideTextStyle(AppTextStyles.dialogTitle, content = title)
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                    ProvideTextStyle(MaterialTheme.typography.bodyMedium, content = text)
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.itemGap, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.xSmall)
                ) {
                    dismissButton?.invoke()
                    confirmButton()
                }
            }
        }
    }
}

/** Choices apply immediately; the title, choices and close action share one scroll owner. */
@Composable
internal fun SettingsSingleChoiceDialog(
    title: String,
    options: List<Pair<Int, String>>,
    selectedValue: Int,
    onSelect: (Int) -> Unit,
    onDismissRequest: () -> Unit
) {
    SettingsDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(title, style = AppTextStyles.dialogTitle) },
        text = {
            Column(Modifier.selectableGroup()) {
                options.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(AppShapes.compactCard)
                            .selectable(
                                selected = selectedValue == value,
                                role = Role.RadioButton,
                                onClick = { onSelect(value) }
                            )
                            .minimumInteractiveComponentSize()
                            .padding(horizontal = AppSpacing.large, vertical = AppSpacing.large),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.iconGap)
                    ) {
                        RadioButton(selected = selectedValue == value, onClick = null)
                        Text(label, style = AppTextStyles.settingsItemTitle)
                    }
                }
            }
        },
        shape = AppShapes.dialog,
        confirmButton = {
            TextButton(onClick = onDismissRequest) { Text("关闭") }
        }
    )
}

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
            .clip(AppShapes.compactCard)
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
            .clip(AppShapes.compactCard)
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
    SettingsDialog(
        onDismissRequest = { showDebugMenu = false },
        icon = { Icon(Icons.Outlined.BugReport, null) },
        title = { Text("调试菜单") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xSmall)) {
                Surface(
                    shape = AppShapes.chip,
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
        SettingsDialog(
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
