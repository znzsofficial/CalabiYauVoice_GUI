package ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.composefluent.ExperimentalFluentApi
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.ComboBoxItem
import io.github.composefluent.component.DropDownButton
import io.github.composefluent.component.DropdownMenu
import io.github.composefluent.component.Text

@OptIn(ExperimentalFluentApi::class)
@Composable
fun ComboBox(
    modifier: Modifier = Modifier,
    header: String? = null,
    placeholder: String? = null,
    disabled: Boolean = false,
    items: List<String>,
    selected: Int?,
    onSelectionChange: (index: Int, item: String) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current

    Column(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (header != null) {
                Text(text = header)
            }

            DropDownButton(
                modifier = Modifier
                    .defaultMinSize(128.dp)
                    .onSizeChanged { size = it },
                onClick = { open = true },
                disabled = disabled,
                contentArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val text = selected?.let(items::get) ?: placeholder.orEmpty()
                    Text(
                        modifier = Modifier.padding(end = 8.dp),
                        text = text,
                        color = if (selected != null) {
                            FluentTheme.colors.text.text.primary
                        } else {
                            FluentTheme.colors.text.text.secondary
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        DropdownMenu(
            modifier = Modifier.width(with(density) { size.width.toDp() + 4.dp }),
            expanded = open,
            onDismissRequest = { open = false },
            offset = DpOffset(
                x = 0.dp,
                y = with(density) { -(size.height.toDp() + 6.dp) }
            )
        ) {
            items.forEachIndexed { index, item ->
                ComboBoxItem(
                    selected = index == selected,
                    label = item,
                    onClick = {
                        onSelectionChange(index, item)
                        open = false
                    }
                )
            }
        }
    }
}

