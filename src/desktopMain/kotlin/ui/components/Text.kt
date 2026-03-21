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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.composefluent.component.Text

@Composable
fun TerminalOutputView(
    outputLines: List<String>,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // 预计算每行颜色，只在列表内容变化时重算
    val lineColors = remember(outputLines) {
        outputLines.map { getLogColor(it) }
    }

    LaunchedEffect(outputLines.size) {
        if (outputLines.isNotEmpty()) {
            listState.animateScrollToItem(outputLines.size - 1)
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
    ) {
        SelectionContainer {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(vertical = 8.dp, horizontal = 12.dp),
                modifier = Modifier.fillMaxSize().padding(end = 12.dp)
            ) {
                itemsIndexed(outputLines) { index, line ->
                    TerminalLineItem(index + 1, line, lineColors[index])
                }
            }
        }

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
private fun TerminalLineItem(lineNumber: Int, line: String, color: Color) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "$lineNumber",
            style = TextStyle(
                color = Color(0xFF606366),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            ),
            modifier = Modifier.width(36.dp).padding(end = 8.dp),
        )
        Text(
            text = line,
            style = TextStyle(
                color = color,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 20.sp
            ),
            modifier = Modifier.weight(1f)
        )
    }
}

private val LOG_COLOR_RULES: List<Pair<(String) -> Boolean, Color>> = listOf(
    // 错误/失败/中断 → 红
    { s: String -> s.contains("error") || s.contains("错误") || s.contains("失败")
            || s.contains("exception") || s.contains("中断") } to Color(0xFFF48771),
    // 全部完成 → 亮绿（优先于"完成"的其他情况）
    { s: String -> s.contains("全部下载完成") || s.contains("转换完成")
            || s.contains("合并完成") || s.contains("success") } to Color(0xFF89D185),
    // 单步完成 → 淡绿
    { s: String -> s.contains("加载完成") || s.contains("获取完成")
            || s.contains("搜索完成") || s.contains("角色数据加载") } to Color(0xFF73C991),
    // 已合并/箭头 → 青
    { s: String -> s.contains("已合并") || s.contains("→") } to Color(0xFF56B6C2),
    // 统计/总量 → 天蓝
    { s: String -> (s.contains("找到") && (s.contains("mp3") || s.contains("wav")))
            || s.contains("将合并为") } to Color(0xFF4FC1E9),
    // 正在进行 → 蓝
    { s: String -> s.contains("开始") || s.contains("正在")
            || (s.contains("共") && s.contains("个文件")) } to Color(0xFF61AFEF),
    // 手动选择 → 紫
    { s: String -> s.contains("手动选择") || s.contains("使用手动") } to Color(0xFFC678DD),
    // 警告/跳过/未找到 → 橙
    { s: String -> s.contains("warning") || s.contains("警告") || s.contains("跳过")
            || s.contains("格式不一致") || s.contains("未找到") } to Color(0xFFCCA700),
    // 欢迎 → 灰白
    { s: String -> s.contains("欢迎") } to Color(0xFFBABABA),
)

private fun getLogColor(line: String): Color {
    val lower = line.lowercase()
    return LOG_COLOR_RULES.firstOrNull { (predicate, _) -> predicate(lower) }?.second
        ?: Color(0xFFA9B7C6)
}