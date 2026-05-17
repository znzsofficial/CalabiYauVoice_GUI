package com.nekolaska.calabiyau.core.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

// ════════════════════════════════════════════════════════
//  Common lightweight building blocks
// ════════════════════════════════════════════════════════

/**
 * Shared rounded back button used by Wiki/detail pages that own their own top-row navigation.
 */
@Composable
fun BackNavButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    contentDescription: String = "返回"
) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = modifier
            .padding(start = 8.dp, end = 8.dp)
            .size(40.dp),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Filled search field with loading indicator, clear action, and IME search callback.
 */
@Composable
fun SearchBar(
    modifier: Modifier = Modifier,
    keyword: String,
    onKeywordChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    isSearching: Boolean,
    placeholder: String = "搜索角色名称..."
) {
    TextField(
        value = keyword,
        onValueChange = onKeywordChange,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        shape = smoothCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            unfocusedIndicatorColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent
        ),
        placeholder = {
            Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        leadingIcon = {
            if (isSearching) {
                CircularProgressIndicator(
                    Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        trailingIcon = {
            if (keyword.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Close, "清空")
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() })
    )
}

/**
 * Minimal exposed dropdown for single-choice filters.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleDropdownSelector(
    label: String,
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember(options, selected) { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true),
            shape = smoothCornerShape(16.dp),
            singleLine = true
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * Horizontally scrollable filter chips for simple enum/category filters.
 */
@Composable
fun <T> HorizontalFilterChips(
    items: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    showCheckIcon: Boolean = false
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { item ->
            val isSelected = item == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelected(item) },
                shape = smoothCornerShape(12.dp),
                label = { Text(label(item), maxLines = 1) },
                leadingIcon = if (showCheckIcon && isSelected) {
                    { Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                } else null
            )
        }
    }
}

/**
 * Quality filter row with a nullable "all" selection and optional selected-color mapping.
 */
@Composable
fun QualityFilterChips(
    selectedLevel: Int?,
    levels: List<Pair<Int, String>>,
    onSelectedLevelChange: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    allLabel: String = "全部品质",
    colorForLevel: (@Composable (Int) -> Color)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedLevel == null,
            onClick = { onSelectedLevelChange(null) },
            shape = smoothCornerShape(12.dp),
            label = { Text(allLabel, maxLines = 1) }
        )
        levels.forEach { (level, label) ->
            FilterChip(
                selected = selectedLevel == level,
                onClick = { onSelectedLevelChange(if (selectedLevel == level) null else level) },
                shape = smoothCornerShape(12.dp),
                label = { Text(label, maxLines = 1) },
                colors = colorForLevel?.let { color ->
                    FilterChipDefaults.filterChipColors(selectedContainerColor = color(level).copy(alpha = 0.2f))
                } ?: FilterChipDefaults.filterChipColors()
            )
        }
    }
}

/**
 * Consistent refresh icon button for top bars and page action rows.
 */
@Composable
fun RefreshActionButton(
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
    }
}

/**
 * Opens the original Wiki URL when available; disabled for blank URLs.
 */
@Composable
fun OpenWikiActionButton(
    wikiUrl: String,
    onOpenWikiUrl: (String) -> Unit,
    contentDescription: String = "打开 Wiki"
) {
    FilledTonalIconButton(
        onClick = { if (wikiUrl.isNotBlank()) onOpenWikiUrl(wikiUrl) },
        enabled = wikiUrl.isNotBlank(),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Icon(Icons.Outlined.OpenInBrowser, contentDescription = contentDescription)
    }
}

/**
 * Small image/icon container used by list rows and detail rows.
 *
 * If [imageUrl] is blank, [fallbackIcon] is rendered instead. This intentionally keeps image
 * loading simple; screens that need preview, retry, or custom error fallback should compose their
 * own image block instead of using this helper.
 */
@Composable
fun WikiIconBox(
    imageUrl: String?,
    fallbackIcon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    shape: Shape = smoothCornerShape(16.dp),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    contentScale: ContentScale = ContentScale.Fit,
    imagePadding: Dp = 6.dp,
    contentDescription: String? = null
) {
    Surface(shape = shape, color = containerColor, modifier = modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) {
            if (!imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = contentDescription,
                    contentScale = contentScale,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(imagePadding)
                )
            } else {
                Icon(
                    fallbackIcon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(size * 0.48f)
                )
            }
        }
    }
}

/**
 * Generic label/value row for detail cards.
 *
 * Optional [imageUrl], [fallbackImageUrl], or [icon] adds a compact leading badge. Use a custom row
 * when the screen requires image-specific onError handling or richer fallback behavior.
 */
@Composable
fun DetailInfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    imageUrl: String? = null,
    fallbackImageUrl: String? = null,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    labelWidth: Dp? = null
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        if (icon != null || imageUrl != null || fallbackImageUrl != null) {
            WikiIconBox(
                imageUrl = imageUrl ?: fallbackImageUrl,
                fallbackIcon = icon ?: Icons.Outlined.Info,
                size = 36.dp,
                shape = CircleShape,
                imagePadding = 6.dp
            )
            Spacer(Modifier.width(12.dp))
        }
        Text(
            label,
            modifier = labelWidth?.let { Modifier.width(it) } ?: Modifier.width(96.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor
        )
    }
}

/**
 * Icon + title heading used inside cards and detail sections.
 */
@Composable
fun SectionTitle(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            shape = smoothCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.padding(8.dp).size(20.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

/**
 * Compact informational badge with caller-provided semantic colors.
 */
@Composable
fun InfoChip(
    label: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        shape = smoothCornerShape(12.dp),
        color = containerColor,
        contentColor = contentColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
        }
    }
}
