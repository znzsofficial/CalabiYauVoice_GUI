package com.nekolaska.calabiyau.feature.settings

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.nekolaska.calabiyau.R
import com.nekolaska.calabiyau.core.ui.smoothCornerShape

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val versionName = remember(context) {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "2.1.25"
        } catch (_: Exception) {
            "2.1.25"
        }
    }

    val pageBackground = MaterialTheme.colorScheme.background
    Box(
        Modifier
            .fillMaxSize()
            .background(pageBackground)
    ) {
        AboutMeshBackdrop(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.72f)
        )
        AboutScaffold(onBack, versionName, pageBackground)
    }
}

@Composable
private fun AboutScaffold(onBack: () -> Unit, versionName: String, pageBackground: Color) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val textProtection = aboutTextProtection(scheme.surface, scheme.onSurface)
    val cardProtection = aboutTextProtection(scheme.surfaceContainerLow, scheme.onSurface)
    val versionProtection = aboutTextProtection(scheme.secondaryContainer, scheme.onSecondaryContainer)
    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(
                    onClick = onBack,
                    modifier = Modifier.size(40.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = textProtection.background,
                        contentColor = textProtection.foreground
                    )
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Surface(
                    shape = smoothCornerShape(16.dp),
                    color = textProtection.background
                ) {
                    Text(
                        "关于",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = textProtection.foreground,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))

            Surface(
                modifier = Modifier.size(108.dp),
                shape = CircleShape,
                color = pageBackground.copy(alpha = 0.72f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Surface(
                        modifier = Modifier.size(88.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_launcher_foreground),
                            contentDescription = "应用图标",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // 文字拥有不透明的主题底层；网格即使经过 90% primary 节点也不会改变对比度。
            Surface(
                modifier = Modifier.padding(horizontal = 20.dp),
                shape = smoothCornerShape(16.dp),
                color = textProtection.background
            ) {
                Text(
                    text = "卡拉彼丘 Wiki 助手",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = textProtection.foreground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }

            Spacer(Modifier.height(6.dp))

            Surface(
                shape = smoothCornerShape(12.dp),
                color = versionProtection.background
            ) {
                Text(
                    text = "Version $versionName",
                    style = MaterialTheme.typography.labelMedium,
                    color = versionProtection.foreground,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }

            Spacer(Modifier.height(24.dp))

            // 软件介绍
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = smoothCornerShape(24.dp),
                color = cardProtection.background
            ) {
                Text(
                    text = "一款基于 Kotlin Multiplatform 开发的现代化工具，" +
                            "Android 端采用 Material Design 3 设计风格。" +
                            "集 Wiki 资源浏览、角色图鉴、画廊预览与资源下载于一体，" +
                            "为卡拉彼丘玩家提供便捷、流畅的一站式 Wiki 体验。",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = cardProtection.foreground,
                    lineHeight = MaterialTheme.typography.bodyLarge.lineHeight,
                    modifier = Modifier.padding(24.dp)
                )
            }

            Spacer(Modifier.height(28.dp))

            // 链接卡片
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = smoothCornerShape(24.dp),
                color = cardProtection.background
            ) {
                Column {
                    Text(
                        text = "相关链接",
                        style = MaterialTheme.typography.labelLarge,
                        color = aboutTextProtection(cardProtection.background, scheme.primary).foreground,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp)
                    )
                    LinkItem(
                        title = "发布页",
                        subtitle = "wiki.nekolaska.vip",
                        icon = Icons.Outlined.Language,
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, "https://wiki.nekolaska.vip/".toUri())
                            )
                        }
                    )

                    LinkItem(
                        title = "开源仓库",
                        subtitle = "CalabiYauVoice_GUI",
                        icon = Icons.Outlined.Code,
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, "https://github.com/znzsofficial/CalabiYauVoice_GUI".toUri())
                            )
                        }
                    )

                    LinkItem(
                        title = "作者B站",
                        subtitle = "NekoLaska",
                        icon = Icons.Outlined.Person,
                        onClick = {
                            // 优先尝试 bilibili 客户端 URI，打不开再用网页
                            try {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, "bilibili://space/15544900".toUri()).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                )
                            } catch (_: Exception) {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, "https://space.bilibili.com/15544900".toUri())
                                )
                            }
                        }
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            // 版权声明
            Surface(
                modifier = Modifier.padding(horizontal = 20.dp),
                shape = smoothCornerShape(12.dp),
                color = textProtection.background
            ) {
                Text(
                    text = "© 2025 Developed by NekoLaska",
                    style = MaterialTheme.typography.bodySmall,
                    color = aboutTextProtection(textProtection.background, scheme.onSurfaceVariant).foreground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun LinkItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val titleProtection = aboutTextProtection(scheme.surfaceContainerLow, scheme.primary)
    val subtitleProtection = aboutTextProtection(scheme.surfaceContainerLow, scheme.onSurfaceVariant)
    val iconProtection = aboutTextProtection(scheme.surfaceContainerHigh, scheme.onSurfaceVariant)
    Surface(
        onClick = onClick,
        color = titleProtection.background,
        shape = smoothCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = iconProtection.background
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconProtection.foreground,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = titleProtection.foreground
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = subtitleProtection.foreground
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                tint = subtitleProtection.foreground,
                modifier = Modifier.size(18.dp)
            )
        }
    }
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
