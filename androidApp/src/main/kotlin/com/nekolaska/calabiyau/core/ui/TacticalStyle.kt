package com.nekolaska.calabiyau.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 语域 B · 战术情报（Tactical Precision）共享 Compose 样式与组件原语。
 *
 * 适用于设置页、弦溯战线（搜打撤）、平衡数据、PC 游戏设置等以参数比对、
 * 开关控制、规格速查为核心任务的页面，也可作为图文详情页内部的数值规格表原语。
 */
object TacticalStyle {
    val cardRadius = 14.dp
    val compactCardRadius = 12.dp
    val tileRadius = 10.dp
    val iconFrameRadius = 10.dp
    val badgeRadius = 8.dp
    val pillRadius = 6.dp
    val accentBarHeight = 2.dp
    val iconFrameSize = 36.dp
    val compactIconFrameSize = 32.dp

    val cardShape: Shape
        @Composable get() = smoothCornerShape(cardRadius)

    val compactCardShape: Shape
        @Composable get() = smoothCornerShape(compactCardRadius)

    val tileShape: Shape
        @Composable get() = smoothCornerShape(tileRadius)

    val iconFrameShape: Shape
        @Composable get() = smoothCornerShape(iconFrameRadius)

    val badgeShape: Shape
        @Composable get() = smoothCornerShape(badgeRadius)

    val pillShape: Shape
        @Composable get() = smoothCornerShape(pillRadius)

    @Composable
    fun cardBorder(
        highlighted: Boolean = false,
        accentColor: Color = MaterialTheme.colorScheme.primary
    ): BorderStroke = BorderStroke(
        width = 1.dp,
        color = if (highlighted) {
            accentColor.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
        }
    )

    @Composable
    fun tileBorder(
        highlighted: Boolean = false,
        accentColor: Color = MaterialTheme.colorScheme.primary
    ): BorderStroke = BorderStroke(
        width = 1.dp,
        color = if (highlighted) {
            accentColor.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        }
    )

    @Composable
    fun dividerColor(): Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f)
}

/** 为数值文本启用等宽数字（`tnum`），保证列表与规格表纵向对齐。 */
fun TextStyle.tacticalTabular(): TextStyle = copy(fontFeatureSettings = "tnum")

/**
 * 战术情报卡片：支持 `12dp/14dp` 连续圆角、`1dp` 克制细框与可选 `2dp` 顶部强调色线。
 */
@Composable
fun TacticalCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    compact: Boolean = false,
    highlighted: Boolean = false,
    accentColor: Color? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    border: BorderStroke = TacticalStyle.cardBorder(
        highlighted = highlighted,
        accentColor = accentColor ?: MaterialTheme.colorScheme.primary
    ),
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = if (compact) TacticalStyle.compactCardShape else TacticalStyle.cardShape
    val body: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth()) {
            if (accentColor != null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(TacticalStyle.accentBarHeight)
                        .background(accentColor)
                )
            }
            content()
        }
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = containerColor,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = border,
            content = body
        )
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = containerColor,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = border,
            content = body
        )
    }
}

/**
 * 战术精框图标槽：默认 `36dp` 外框、`10dp` 圆角、`1dp` 细线边框与 `19dp` 图标。
 */
@Composable
fun TacticalFramedIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = TacticalStyle.iconFrameSize,
    iconSize: Dp = 19.dp,
    highlighted: Boolean = false,
    containerColor: Color = if (highlighted) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    },
    borderColor: Color = if (highlighted) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.40f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    },
    iconTint: Color = if (highlighted) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.primary
    }
) {
    Surface(
        modifier = modifier.size(size),
        shape = TacticalStyle.iconFrameShape,
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

/**
 * 战术状态／数值胶囊：带 `1dp` 细框，可选前置色点、主色高亮与 `tnum` 等宽数字。
 */
@Composable
fun TacticalBadge(
    text: String,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    tabular: Boolean = false,
    dotColor: Color? = null,
    containerColor: Color = if (highlighted) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    },
    contentColor: Color = if (highlighted) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    },
    borderColor: Color = if (highlighted) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    }
) {
    val textStyle = MaterialTheme.typography.labelSmall.copy(
        fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Medium,
        fontFeatureSettings = if (tabular) "tnum" else null
    )
    Surface(
        modifier = modifier,
        shape = TacticalStyle.badgeShape,
        color = containerColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (dotColor != null) {
                Box(
                    Modifier
                        .size(6.dp)
                        .background(dotColor, smoothCornerShape(2.dp))
                )
            }
            Text(
                text = text,
                style = textStyle,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 战术分区标题行：使用 `6dp` 圆角方点标记取代左侧粗竖条，支持右侧计数胶囊或自定义动作。
 */
@Composable
fun TacticalSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    countText: String? = null,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier
                .size(6.dp)
                .background(accentColor, smoothCornerShape(2.dp))
        )
        Text(
            text = title,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
            style = AppTextStyles.sectionTitle
        )
        if (countText != null) {
            TacticalBadge(text = countText, tabular = true)
        }
        trailing?.invoke()
    }
}

/**
 * 战术指标／规格瓦片：用于展示单项参数、平衡指标或参考表字段，数值默认开启 `tnum` 对齐。
 */
@Composable
fun TacticalSpecTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    tabular: Boolean = true,
    supportingText: String? = null,
    deltaText: String? = null,
    deltaColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val colors = MaterialTheme.colorScheme
    val valueStyle = MaterialTheme.typography.titleSmall.let {
        if (tabular) it.tacticalTabular() else it
    }
    val subStyle = MaterialTheme.typography.labelSmall.let {
        if (tabular) it.tacticalTabular() else it
    }
    Surface(
        modifier = modifier,
        shape = TacticalStyle.tileShape,
        color = if (highlighted) {
            colors.primaryContainer.copy(alpha = 0.28f)
        } else {
            colors.surfaceContainer
        },
        border = TacticalStyle.tileBorder(highlighted = highlighted)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (highlighted) colors.primary else colors.onSurfaceVariant,
                fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = valueStyle,
                fontWeight = if (highlighted) FontWeight.Bold else FontWeight.SemiBold,
                color = if (highlighted) colors.primary else colors.onSurface
            )
            if (supportingText != null) {
                Text(
                    text = supportingText,
                    style = subStyle,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (deltaText != null) {
                Text(
                    text = deltaText,
                    style = subStyle,
                    fontWeight = FontWeight.SemiBold,
                    color = deltaColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
