package com.nekolaska.calabiyau.feature.settings

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nekolaska.calabiyau.core.preferences.AppPrefs
import com.nekolaska.calabiyau.core.ui.AppShapes
import com.nekolaska.calabiyau.core.ui.AppSizes
import com.nekolaska.calabiyau.core.ui.AppSpacing
import com.nekolaska.calabiyau.core.ui.AppTextStyles
import com.nekolaska.calabiyau.core.ui.rememberPlainTextClipboardCopier
import com.nekolaska.calabiyau.core.ui.smoothCornerShape

// ════════════════════════════════════════════════════════
//  Direction B · Tactical Precision Design Tokens
// ════════════════════════════════════════════════════════

internal val SettingsCardRadius = 14.dp
internal val SettingsIconFrameRadius = 10.dp
internal val SettingsBadgeRadius = 8.dp
internal val SettingsAccentBarHeight = 2.dp
internal val SettingsIconFrameSize = 36.dp
internal val SettingsRowHorizontalPadding = AppSpacing.screen
internal val SettingsRowIconGap = 14.dp
internal val SettingsDividerStartIndent = SettingsRowHorizontalPadding + SettingsIconFrameSize + SettingsRowIconGap

internal val SettingsCardShape: Shape
    @Composable get() = smoothCornerShape(SettingsCardRadius)

internal val SettingsIconFrameShape: Shape
    @Composable get() = smoothCornerShape(SettingsIconFrameRadius)

internal val SettingsBadgeShape: Shape
    @Composable get() = smoothCornerShape(SettingsBadgeRadius)

@Composable
internal fun settingsCardBorder(highlighted: Boolean = false): BorderStroke = BorderStroke(
    width = 1.dp,
    color = if (highlighted) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    }
)

@Composable
internal fun settingsDividerColor(): Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f)

@Composable
internal fun SettingsFramedIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = SettingsIconFrameSize,
    iconSize: Dp = 19.dp,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
    iconTint: Color = MaterialTheme.colorScheme.primary
) {
    Surface(
        modifier = modifier.size(size),
        shape = SettingsIconFrameShape,
        color = containerColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

@Composable
internal fun SettingsValueBadge(
    text: String,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    tabular: Boolean = false
) {
    val colors = MaterialTheme.colorScheme
    val containerColor = if (highlighted) colors.primaryContainer else colors.surfaceContainerHigh
    val contentColor = if (highlighted) colors.onPrimaryContainer else colors.onSurfaceVariant
    val borderColor = if (highlighted) {
        colors.primary.copy(alpha = 0.35f)
    } else {
        colors.outlineVariant.copy(alpha = 0.45f)
    }
    val textStyle = MaterialTheme.typography.labelSmall.copy(
        fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Medium,
        fontFeatureSettings = if (tabular) "tnum" else null
    )
    Surface(
        modifier = modifier,
        shape = SettingsBadgeShape,
        color = containerColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Text(
            text = text,
            style = textStyle,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
internal fun SettingsNavigationChevron(modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
        modifier = modifier.size(AppSizes.iconSmall)
    )
}

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
        Surface(
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
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
            Column(
                modifier = Modifier.selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.xSmall)
            ) {
                options.forEach { (value, label) ->
                    val selected = selectedValue == value
                    val colors = MaterialTheme.colorScheme
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(SettingsIconFrameShape)
                            .border(
                                width = if (selected) 1.5.dp else 1.dp,
                                color = if (selected) colors.primary else colors.outlineVariant.copy(alpha = 0.35f),
                                shape = SettingsIconFrameShape
                            )
                            .selectable(
                                selected = selected,
                                role = Role.RadioButton,
                                onClick = { onSelect(value) }
                            )
                            .minimumInteractiveComponentSize()
                            .padding(horizontal = AppSpacing.large, vertical = AppSpacing.medium),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.iconGap)
                    ) {
                        RadioButton(selected = selected, onClick = null)
                        Text(
                            text = label,
                            style = AppTextStyles.settingsItemTitle,
                            color = if (selected) colors.primary else colors.onSurface
                        )
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
internal fun SettingsCard(
    modifier: Modifier = Modifier,
    insetHorizontal: Boolean = true,
    highlighted: Boolean = false,
    accentTopBar: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val cardModifier = if (insetHorizontal) {
        modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.screen)
    } else {
        modifier.fillMaxWidth()
    }
    Surface(
        modifier = cardModifier,
        shape = SettingsCardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = settingsCardBorder(highlighted = highlighted)
    ) {
        Column(Modifier.fillMaxWidth()) {
            if (accentTopBar) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(SettingsAccentBarHeight)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
            content()
        }
    }
}

@Composable
internal fun SettingsDivider(
    startIndent: Dp = SettingsDividerStartIndent,
    endIndent: Dp = SettingsRowHorizontalPadding
) {
    HorizontalDivider(
        modifier = Modifier.padding(start = startIndent, end = endIndent),
        thickness = 1.dp,
        color = settingsDividerColor()
    )
}

@Composable
internal fun SettingsGroupHeader(
    title: String,
    modifier: Modifier = Modifier,
    insetHorizontal: Boolean = true,
    topPadding: Dp = AppSpacing.xxLarge
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.6.sp
        ),
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .padding(
                start = if (insetHorizontal) AppSpacing.screen else 0.dp,
                end = if (insetHorizontal) AppSpacing.screen else 0.dp,
                top = topPadding,
                bottom = AppSpacing.medium
            )
            .semantics { heading() }
    )
}

@Composable
internal fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    value: String? = null,
    compactSubtitle: String? = null,
    tabularSubtitle: Boolean = false,
    tabularValue: Boolean = false,
    enabled: Boolean = true,
    showChevron: Boolean = true,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val titleStyle = AppTextStyles.settingsItemTitle
    val titleColor = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = 0.55f)
    val secondaryColor = if (enabled) colors.onSurfaceVariant else colors.onSurfaceVariant.copy(alpha = 0.55f)
    val subtitleStyle = MaterialTheme.typography.bodySmall.let { base ->
        if (tabularSubtitle) base.copy(fontFeatureSettings = "tnum") else base
    }
    val valueStyle = MaterialTheme.typography.bodySmall.copy(
        fontWeight = FontWeight.Medium,
        fontFeatureSettings = if (tabularValue) "tnum" else null
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = SettingsRowHorizontalPadding, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsFramedIcon(
            icon = icon,
            iconTint = if (enabled) iconTint else iconTint.copy(alpha = 0.45f)
        )
        Spacer(Modifier.width(SettingsRowIconGap))

        when {
            subtitle != null -> {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.tiny)
                ) {
                    Text(
                        text = title,
                        style = titleStyle,
                        color = titleColor
                    )
                    if (compactSubtitle == null) {
                        Text(
                            text = subtitle,
                            style = subtitleStyle,
                            color = secondaryColor,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        BoxWithConstraints {
                            val width = rememberTextMeasurer()
                                .measure(subtitle, subtitleStyle, softWrap = false, maxLines = 1)
                                .size.width
                            Text(
                                text = if (width <= constraints.maxWidth) subtitle else compactSubtitle,
                                modifier = Modifier.clearAndSetSemantics { text = AnnotatedString(subtitle) },
                                style = subtitleStyle,
                                color = secondaryColor,
                                maxLines = 1,
                                overflow = TextOverflow.StartEllipsis
                            )
                        }
                    }
                }
                if (value != null) {
                    Spacer(Modifier.width(AppSpacing.large))
                    Text(
                        text = value,
                        style = valueStyle,
                        color = secondaryColor,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            value != null -> {
                BoxWithConstraints(Modifier.weight(1f)) {
                    val measurer = rememberTextMeasurer()
                    val gapPx = with(LocalDensity.current) { AppSpacing.large.roundToPx() }
                    val titleWidth = measurer.measure(title, titleStyle, softWrap = false, maxLines = 1).size.width
                    val valueWidth = measurer.measure(value, valueStyle, softWrap = false, maxLines = 1).size.width
                    if (titleWidth + gapPx + valueWidth <= constraints.maxWidth) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = title,
                                style = titleStyle,
                                color = titleColor,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(AppSpacing.large))
                            Text(
                                text = value,
                                style = valueStyle,
                                color = secondaryColor,
                                textAlign = TextAlign.End,
                                maxLines = 1
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(AppSpacing.tiny)
                        ) {
                            Text(
                                text = title,
                                style = titleStyle,
                                color = titleColor
                            )
                            Text(
                                text = value,
                                style = valueStyle,
                                color = secondaryColor,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            else -> {
                Text(
                    text = title,
                    style = titleStyle,
                    color = titleColor,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (trailing != null) {
            Spacer(Modifier.width(AppSpacing.medium))
            trailing()
        } else if (showChevron) {
            Spacer(Modifier.width(AppSpacing.small))
            SettingsNavigationChevron()
        }
    }
}

@Composable
internal fun SettingsToggleItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    tabularSubtitle: Boolean = false,
    onCheckedChange: (Boolean) -> Unit
) {
    val subtitleStyle = MaterialTheme.typography.bodySmall.let { base ->
        if (tabularSubtitle) base.copy(fontFeatureSettings = "tnum") else base
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .heightIn(min = 56.dp)
            .padding(horizontal = SettingsRowHorizontalPadding, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsFramedIcon(icon = icon)
        Spacer(Modifier.width(SettingsRowIconGap))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.tiny)
        ) {
            Text(
                text = title,
                style = AppTextStyles.settingsItemTitle,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = subtitleStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(AppSpacing.medium))
        Switch(
            checked = checked,
            onCheckedChange = null
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
        tabularSubtitle = true,
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
                    shape = SettingsIconFrameShape,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = settingsCardBorder(),
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
