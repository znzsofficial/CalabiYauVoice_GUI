package ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.composefluent.component.Text

@Composable
fun TerminalOutputView(
    outputLines: List<String>,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // 自动滚动逻辑
    LaunchedEffect(outputLines.size) {
        if (outputLines.isNotEmpty()) {
            listState.animateScrollToItem(outputLines.size - 1)
        }
    }

    // 容器样式
    Box(
        modifier = modifier
            .background(Color(0xFF1E1E1E), RoundedCornerShape(8.dp)) // 更深邃的背景 + 圆角
            .border(1.dp, Color(0xFF333333), RoundedCornerShape(8.dp)) // 增加微弱边框
            .clip(RoundedCornerShape(8.dp)) // 裁剪内容以匹配圆角
    ) {
        // 允许文本选择复制
        SelectionContainer {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(vertical = 8.dp, horizontal = 12.dp),
                modifier = Modifier.fillMaxSize().padding(end = 12.dp) // 给滚动条留出空间
            ) {
                itemsIndexed(outputLines) { index, line ->
                    TerminalLineItem(index + 1, line)
                }
            }
        }

        // 自定义垂直滚动条
        VerticalScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            adapter = rememberScrollbarAdapter(listState),
            style = ScrollbarStyle(
                minimalHeight = 16.dp,
                thickness = 8.dp,
                shape = RoundedCornerShape(4.dp),
                hoverDurationMillis = 300,
                unhoverColor = Color(0xFF424242),
                hoverColor = Color(0xFF666666)
            )
        )
    }
}

@Composable
private fun TerminalLineItem(lineNumber: Int, line: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        // 1. 行号区域
        Text(
            text = "$lineNumber",
            style = TextStyle(
                color = Color(0xFF606366),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            ),
            modifier = Modifier.width(36.dp).padding(end = 8.dp),
        )

        // 2. 日志内容区域
        Text(
            text = buildAnnotatedString {
                // 根据内容解析颜色
                val color = getLogColor(line)
                withStyle(SpanStyle(color = color)) {
                    append(line)
                }
            },
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 20.sp // 增加行高，提升可读性
            ),
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 根据日志关键词返回对应的颜色
 */
private fun getLogColor(line: String): Color {
    val lower = line.lowercase()
    return when {
        // 错误/失败 -> 柔和的红色
        lower.contains("error") || lower.contains("错误") || lower.contains("失败") || lower.contains("exception") -> Color(
            0xFFF48771
        )
        // 警告 -> 橙色
        lower.contains("warning") || lower.contains("警告") || lower.contains("跳过") -> Color(0xFFCCA700)
        // 成功/完成 -> 绿色
        lower.contains("success") || lower.contains("完成") || lower.contains("ok") -> Color(0xFF89D185)
        // 进度/下载 -> 青色
        lower.contains("下载进度") || lower.contains("downloading") -> Color(0xFF61AFEF)
        // 关键信息 -> 蓝色/紫色
        lower.contains(">>>") || lower.contains("---") -> Color(0xFFC678DD)
        // 默认文本 -> 浅灰
        else -> Color(0xFFA9B7C6)
    }
}