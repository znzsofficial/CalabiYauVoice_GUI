package com.nekolaska.calabiyau.core.ui

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.nekolaska.calabiyau.core.wiki.reading.GuideText

@Composable
internal fun WikiRichText(content: GuideText, onOpenLink: (String) -> Unit, modifier: Modifier = Modifier, heading: Boolean = false) {
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = remember(content, linkColor, onOpenLink, heading) {
        buildAnnotatedString {
            content.spans.forEach { span ->
                withStyle(SpanStyle(fontWeight = if (span.bold || heading) FontWeight.Bold else FontWeight.Normal)) {
                    if (span.url == null) append(span.text)
                    else withLink(LinkAnnotation.Url(span.url,
                        TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))) {
                        onOpenLink(span.url)
                    }) { append(span.text) }
                }
            }
        }
    }
    SelectionContainer(modifier) {
        Text(annotated, style = if (heading) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge)
    }
}
