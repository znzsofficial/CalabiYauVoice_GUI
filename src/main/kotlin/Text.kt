import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import com.konyaco.fluent.component.Text

@Composable
fun TextWithLinks(text: String, modifier: Modifier = Modifier) {
    // URL 正则表达式
    val urlRegex = Regex("(https?://\\S+)")
    // 获取当前的 UriHandler，用于打开链接
    //val uriHandler = LocalUriHandler.current

    // 构建带 LinkAnnotation 的 AnnotatedString
    val annotatedString = buildAnnotatedString {
        var lastMatchEnd = 0
        urlRegex.findAll(text).forEach { matchResult ->
            val url = matchResult.value
            val startIndex = matchResult.range.first
            val endIndex = matchResult.range.last + 1

            // 添加 URL 前的普通文本
            if (startIndex > lastMatchEnd) {
                append(text.substring(lastMatchEnd, startIndex))
            }

            // --- 添加带 LinkAnnotation 和样式的 URL ---
            // 使用 pushLink 指定这是一个链接
            pushLink(LinkAnnotation.Url(url))

            withStyle(
                style = SpanStyle(
                    color = Color.Blue, // 链接颜色
                    textDecoration = TextDecoration.Underline // 下划线
                )
            ) {
                append(url) // 添加 URL 文本
            }

            pop() // 结束 LinkAnnotation 和样式

            lastMatchEnd = endIndex
        }
        // 添加最后一个 URL 后面的普通文本
        if (lastMatchEnd < text.length) {
            append(text.substring(lastMatchEnd))
        }
    }

    // 直接使用 Text Composable
    //    Text 组件会自动检测 LinkAnnotation 并处理点击事件
    Text(
        text = annotatedString,
        modifier = modifier
    )
}