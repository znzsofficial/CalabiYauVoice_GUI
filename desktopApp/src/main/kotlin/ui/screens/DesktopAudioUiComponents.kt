package ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.composefluent.ExperimentalFluentApi
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.Button
import io.github.composefluent.component.Text

@OptIn(ExperimentalFluentApi::class)
@Composable
internal fun AudioHistoryTimeline(
    steps: List<AudioHistoryStep>,
    currentIndex: Int,
    isBusy: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSelect: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onUndo, disabled = isBusy || currentIndex <= 0) { Text("撤销") }
            Button(onClick = onRedo, disabled = isBusy || currentIndex !in 0 until steps.lastIndex) { Text("重做") }
            Text(
                text = if (currentIndex in steps.indices) "当前：${steps[currentIndex].label}" else "时间轴待生成",
                fontSize = 12.sp,
                color = FluentTheme.colors.text.text.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            steps.forEachIndexed { index, step ->
                val selected = index == currentIndex
                val color = if (selected) FluentTheme.colors.control.default else FluentTheme.colors.control.secondary
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(color)
                        .clickable(enabled = !isBusy) { onSelect(index) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text((index + 1).toString(), fontSize = 11.sp, color = FluentTheme.colors.text.text.secondary)
                    Text(step.label, fontSize = 12.sp, color = FluentTheme.colors.text.text.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
