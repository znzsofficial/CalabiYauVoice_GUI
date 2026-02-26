import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.composefluent.FluentTheme
import io.github.composefluent.background.Mica
import io.github.composefluent.component.HyperlinkButton
import io.github.composefluent.component.Text
import io.github.composefluent.darkColors
import io.github.composefluent.lightColors

@Composable
fun AboutContent() {
    val darkMode = LocalThemeState.current
    FluentTheme(colors = if (darkMode.value) darkColors() else lightColors()) {
        Mica(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. 标题和版本
                Text(
                    text = "卡拉彼丘 Wiki 语音下载器",
                    style = TextStyle(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = FluentTheme.colors.text.text.primary
                    )
                )
                Text(
                    text = "Version 1.2.1",
                    style = TextStyle(
                        fontSize = 12.sp,
                        color = FluentTheme.colors.text.text.secondary
                    )
                )

                Spacer(Modifier.height(8.dp))

                // 2. 软件介绍文案
                Text(
                    text = "一款基于 Kotlin Compose Desktop 开发的现代化工具，采用 Fluent Design 设计风格。旨在为卡拉彼丘玩家提供便捷、流畅的 Wiki 语音资源提取与下载体验。",
                    style = TextStyle(
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Center,
                        color = FluentTheme.colors.text.text.primary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.weight(1f)) // 把链接推到底部

                // 3. 链接区域
                Text(
                    text = "相关链接",
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = FluentTheme.colors.text.text.secondary
                    )
                )

                // 链接横向排列
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HyperlinkButton(navigateUri = "https://github.com/znzsofficial/CalabiyauWikiVoice") {
                        Text("核心脚本")
                    }
                    Text("|", color = FluentTheme.colors.text.text.disabled)
                    HyperlinkButton(navigateUri = "https://github.com/znzsofficial/CalabiYauVoice_GUI") {
                        Text("开源仓库")
                    }
                    // --- 新增部分 ---
                    Text("|", color = FluentTheme.colors.text.text.disabled)
                    HyperlinkButton(navigateUri = "https://space.bilibili.com/15544900") {
                        Text("作者B站")
                    }
                    // ----------------
                }

                // 4. 版权/落款
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "© 2025 Developed by NekoLaska",
                    style = TextStyle(
                        fontSize = 10.sp,
                        color = FluentTheme.colors.text.text.disabled
                    )
                )
            }
        }
    }
}