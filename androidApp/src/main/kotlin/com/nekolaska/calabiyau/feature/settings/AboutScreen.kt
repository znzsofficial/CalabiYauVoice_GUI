package com.nekolaska.calabiyau.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.nekolaska.calabiyau.R
import com.nekolaska.calabiyau.core.ui.AppShapes
import com.nekolaska.calabiyau.core.ui.AppSizes
import com.nekolaska.calabiyau.core.ui.AppSpacing
import com.nekolaska.calabiyau.core.ui.AppTextStyles
import com.nekolaska.calabiyau.core.ui.rememberSnackbarLauncher

private data class AboutVersionInfo(
    val name: String,
    val code: Long
) {
    val label: String
        get() = if (code > 0L) "Version $name ($code)" else "Version $name"
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val versionInfo = remember(context) {
        try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            @Suppress("DEPRECATION")
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                info.versionCode.toLong()
            }
            AboutVersionInfo(
                name = info.versionName?.takeIf { it.isNotBlank() } ?: "2.2.0",
                code = code
            )
        } catch (_: Exception) {
            AboutVersionInfo(name = "2.2.0", code = 0L)
        }
    }

    val pageBackground = MaterialTheme.colorScheme.background
    AboutScaffold(onBack = onBack, versionLabel = versionInfo.label, pageBackground = pageBackground)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AboutScaffold(onBack: () -> Unit, versionLabel: String, pageBackground: Color) {
    val context = LocalContext.current
    val showSnack = rememberSnackbarLauncher()
    val scheme = MaterialTheme.colorScheme
    val topBarProtection = aboutTextProtection(pageBackground, scheme.onSurface)
    val backBtnProtection = aboutTextProtection(scheme.surfaceContainerHigh, scheme.onSurface)
    val cardProtection = aboutTextProtection(scheme.surfaceContainerLow, scheme.onSurface)
    val subtitleProtection = aboutTextProtection(scheme.surfaceContainerLow, scheme.onSurfaceVariant)
    val versionProtection = aboutTextProtection(scheme.secondaryContainer, scheme.onSecondaryContainer)
    val tagProtection = aboutTextProtection(scheme.surfaceContainerHigh, scheme.onSurfaceVariant)
    val iconProtection = aboutTextProtection(scheme.primary, Color.White)
    val sectionHeaderProtection = aboutTextProtection(pageBackground, scheme.primary)
    val footerProtection = aboutTextProtection(pageBackground, scheme.onSurfaceVariant)

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = AppSpacing.screen, vertical = AppSpacing.medium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(
                    onClick = onBack,
                    modifier = Modifier.size(AppSizes.iconButton),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = backBtnProtection.background,
                        contentColor = backBtnProtection.foreground
                    )
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", modifier = Modifier.size(AppSizes.iconMedium))
                }
                Spacer(Modifier.width(AppSpacing.iconGap))
                Text(
                    "关于",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = topBarProtection.foreground
                )
            }
        },
        containerColor = pageBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = AppSpacing.screen)
                .padding(top = AppSpacing.small, bottom = AppSpacing.xxxLarge),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Hero 卡片：网格渐变仅烘托顶部图标区并平滑淡入卡片底色，正文与标题无需零碎底框。
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppShapes.sheet,
                    color = cardProtection.background
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(168.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            AboutMeshBackdrop(
                                modifier = Modifier.fillMaxSize(),
                                fadeColor = cardProtection.background
                            )
                            Surface(
                                modifier = Modifier.size(104.dp),
                                shape = CircleShape,
                                color = cardProtection.background.copy(alpha = 0.74f),
                                border = BorderStroke(1.dp, scheme.surface.copy(alpha = 0.9f))
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Surface(
                                        modifier = Modifier.size(84.dp),
                                        shape = CircleShape,
                                        color = iconProtection.background
                                    ) {
                                        Image(
                                            painter = painterResource(R.drawable.ic_launcher_foreground),
                                            contentDescription = "应用图标",
                                            colorFilter = ColorFilter.tint(iconProtection.foreground),
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(8.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = AppSpacing.cardContent,
                                    end = AppSpacing.cardContent,
                                    bottom = AppSpacing.cardContent
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "卡拉彼丘 Wiki 助手",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = cardProtection.foreground,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(AppSpacing.medium))
                            Surface(
                                shape = AppShapes.capsule,
                                color = versionProtection.background
                            ) {
                                Text(
                                    text = versionLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = versionProtection.foreground,
                                    modifier = Modifier.padding(
                                        horizontal = AppSpacing.chipHorizontal,
                                        vertical = AppSpacing.xSmall
                                    )
                                )
                            }
                            Spacer(Modifier.height(AppSpacing.xLarge))
                            Text(
                                text = "面向《卡拉彼丘》玩家的开源社区工具，整合 Wiki 浏览、角色武器、弦溯掉落、设置装饰图鉴与语音资源下载。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = subtitleProtection.foreground,
                                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(AppSpacing.large))
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(AppSpacing.medium),
                                verticalArrangement = Arrangement.spacedBy(AppSpacing.small)
                            ) {
                                TechTag(label = "Kotlin Multiplatform", protection = tagProtection)
                                TechTag(label = "Material 3", protection = tagProtection)
                                TechTag(label = "Apache-2.0", protection = tagProtection)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(AppSpacing.sectionGap))

                Text(
                    text = "相关链接",
                    style = MaterialTheme.typography.labelLarge,
                    color = sectionHeaderProtection.foreground,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = AppSpacing.large, bottom = AppSpacing.medium)
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppShapes.card,
                    color = cardProtection.background
                ) {
                    val dividerColor = scheme.outlineVariant.copy(alpha = 0.5f)
                    Column {
                        LinkItem(
                            title = "发布页",
                            subtitle = "wiki.nekolaska.vip · 历史版本与安装说明",
                            icon = Icons.Outlined.Language,
                            onClick = {
                                openExternalUri(
                                    context = context,
                                    uri = "https://wiki.nekolaska.vip/".toUri(),
                                    onError = { showSnack("未找到可用的浏览器") }
                                )
                            }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = AppSpacing.cardContent),
                            color = dividerColor
                        )
                        LinkItem(
                            title = "开源仓库",
                            subtitle = "github.com/znzsofficial/CalabiYauVoice_GUI",
                            icon = Icons.Outlined.Code,
                            onClick = {
                                openExternalUri(
                                    context = context,
                                    uri = "https://github.com/znzsofficial/CalabiYauVoice_GUI".toUri(),
                                    onError = { showSnack("未找到可用的浏览器") }
                                )
                            }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = AppSpacing.cardContent),
                            color = dividerColor
                        )
                        LinkItem(
                            title = "问题反馈",
                            subtitle = "GitHub Issues",
                            icon = Icons.Outlined.BugReport,
                            onClick = {
                                openExternalUri(
                                    context = context,
                                    uri = "https://github.com/znzsofficial/CalabiYauVoice_GUI/issues".toUri(),
                                    onError = { showSnack("未找到可用的浏览器") }
                                )
                            }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = AppSpacing.cardContent),
                            color = dividerColor
                        )
                        LinkItem(
                            title = "作者 B 站",
                            subtitle = "NekoLaska",
                            icon = Icons.Outlined.Person,
                            onClick = {
                                // 优先尝试 bilibili 客户端 URI，打不开再用网页
                                val openedClient = openExternalUri(
                                    context = context,
                                    uri = "bilibili://space/15544900".toUri(),
                                    newTask = true
                                )
                                if (!openedClient) {
                                    openExternalUri(
                                        context = context,
                                        uri = "https://space.bilibili.com/15544900".toUri(),
                                        onError = { showSnack("未找到可用的浏览器") }
                                    )
                                }
                            }
                        )
                    }
                }

                Spacer(Modifier.height(AppSpacing.xxLarge))

                Text(
                    text = "非官方玩家社区工具 · 资料源自 BWiki\n© 2026 Developed by NekoLaska · Apache-2.0",
                    style = MaterialTheme.typography.bodySmall,
                    color = footerProtection.foreground,
                    textAlign = TextAlign.Center,
                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                    modifier = Modifier.padding(horizontal = AppSpacing.medium)
                )
            }
        }
    }
}

@Composable
private fun TechTag(label: String, protection: AboutTextProtection) {
    Surface(
        shape = AppShapes.chip,
        color = protection.background
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = protection.foreground,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun LinkItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val titleProtection = aboutTextProtection(scheme.surfaceContainerLow, scheme.onSurface)
    val subtitleProtection = aboutTextProtection(scheme.surfaceContainerLow, scheme.onSurfaceVariant)
    val iconProtection = aboutTextProtection(scheme.surfaceContainerHigh, scheme.onSurfaceVariant)
    Surface(
        onClick = onClick,
        color = titleProtection.background
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.cardContent, vertical = AppSpacing.xLarge),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(AppSizes.iconButton),
                shape = CircleShape,
                color = iconProtection.background
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconProtection.foreground,
                        modifier = Modifier.size(AppSizes.iconMedium)
                    )
                }
            }
            Spacer(Modifier.width(AppSpacing.sectionGap))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = AppTextStyles.settingsItemTitle,
                    color = titleProtection.foreground
                )
                Text(
                    text = subtitle,
                    style = AppTextStyles.settingsItemSubtitle,
                    color = subtitleProtection.foreground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(AppSpacing.medium))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                tint = subtitleProtection.foreground,
                modifier = Modifier.size(AppSizes.iconSmall)
            )
        }
    }
}

private fun openExternalUri(
    context: Context,
    uri: Uri,
    newTask: Boolean = false,
    onError: (() -> Unit)? = null
): Boolean = try {
    context.startActivity(
        Intent(Intent.ACTION_VIEW, uri).apply {
            if (newTask) flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    )
    true
} catch (_: Exception) {
    onError?.invoke()
    false
}

internal data class AboutTextProtection(val background: Color, val foreground: Color)

/** Preserve the theme's surface, including AMOLED, but never let mesh colors bleed under text. */
internal fun aboutTextProtection(background: Color, foreground: Color): AboutTextProtection {
    val opaqueBackground = background.copy(alpha = 1f)
    val opaqueForeground = foreground.copy(alpha = 1f)
    val backgroundLuminance = opaqueBackground.luminance()
    val foregroundLuminance = opaqueForeground.luminance()
    val contrast = (maxOf(backgroundLuminance, foregroundLuminance) + 0.05f) /
        (minOf(backgroundLuminance, foregroundLuminance) + 0.05f)
    // Custom/reduced-contrast palettes (also mid-theme transitions) may not supply an AA pair.
    val readableForeground = if (contrast >= 4.5f) opaqueForeground else {
        if ((backgroundLuminance + 0.05f) / 0.05f >= 1.05f / (backgroundLuminance + 0.05f)) {
            Color.Black
        } else {
            Color.White
        }
    }
    return AboutTextProtection(opaqueBackground, readableForeground)
}
