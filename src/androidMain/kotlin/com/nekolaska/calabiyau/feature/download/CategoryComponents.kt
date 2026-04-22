package com.nekolaska.calabiyau.feature.download

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.RemoveDone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nekolaska.calabiyau.core.ui.EmptyState
import com.nekolaska.calabiyau.core.ui.smoothCornerShape
import data.CharacterGroup

@Composable
fun CategoryGroupList(
    characterGroups: List<CharacterGroup>,
    characterAvatars: Map<String, String>,
    hasSearched: Boolean,
    favorites: Set<String> = emptySet(),
    searchError: String? = null,
    onRetry: (() -> Unit)? = null,
    onToggleFavorite: (String) -> Unit = {},
    onSelectGroup: (CharacterGroup) -> Unit
) {
    if (characterGroups.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.RecordVoiceOver,
            message = if (hasSearched) "未找到相关角色" else "输入关键词搜索角色语音",
            errorMessage = if (hasSearched) searchError else null,
            onRetry = onRetry
        )
        return
    }

    // 收藏的排前面
    val sorted = remember(characterGroups, favorites) {
        characterGroups.sortedByDescending { it.characterName in favorites }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(sorted) { group ->
            GroupCard(
                group = group,
                avatarUrl = characterAvatars[group.characterName],
                isFavorite = group.characterName in favorites,
                onToggleFavorite = { onToggleFavorite(group.characterName) },
                onClick = { onSelectGroup(group) }
            )
        }
    }
}

@Composable
fun GroupCard(
    group: CharacterGroup,
    avatarUrl: String?,
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = smoothCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (avatarUrl != null) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(smoothCornerShape(16.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = smoothCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = group.characterName.take(1),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = group.characterName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${group.subCategories.size} 个分类",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // 收藏按钮
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    if (isFavorite) Icons.Default.Star else Icons.Outlined.StarBorder,
                    contentDescription = if (isFavorite) "取消收藏" else "收藏",
                    modifier = Modifier.size(20.dp),
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
            Spacer(Modifier.width(4.dp))
            FilledTonalIconButton(
                onClick = onClick,
                modifier = Modifier.size(36.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack, null,
                    modifier = Modifier.size(18.dp).rotate(180f),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun CategoryDetailContent(
    group: CharacterGroup,
    subCategories: List<String>,
    checkedCategories: List<String>,
    isScanning: Boolean,
    isDownloading: Boolean,
    manualSelectionMap: Map<String, List<Pair<String, String>>>,
    onCheckAll: () -> Unit,
    onUncheckAll: () -> Unit,
    onCategoryChecked: (String, Boolean) -> Unit,
    onOpenFileDialog: (String) -> Unit,
    onDownload: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.75f)
            .padding(bottom = 16.dp)
    ) {
        // ── 标题区 ──
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp, top = 8.dp)
        ) {
            Surface(
                shape = smoothCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.RecordVoiceOver, null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = group.characterName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                if (subCategories.isNotEmpty()) {
                    Text(
                        text = "已选 ${checkedCategories.size} / ${subCategories.size} 个分类",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (isScanning) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        }

        if (!isScanning && subCategories.isNotEmpty()) {
            // ── 操作栏 ──
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            ) {
                FilterChip(
                    selected = checkedCategories.size == subCategories.size,
                    onClick = {
                        if (checkedCategories.size == subCategories.size) onUncheckAll()
                        else onCheckAll()
                    },
                    shape = smoothCornerShape(12.dp),
                    label = {
                        Text(
                            if (checkedCategories.size == subCategories.size) "取消全选" else "全选",
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    leadingIcon = {
                        Icon(
                            if (checkedCategories.size == subCategories.size) Icons.Default.RemoveDone
                            else Icons.Default.DoneAll,
                            null, modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 24.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // ── 分类列表 ──
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(subCategories) { cat ->
                    val isRoot = cat == group.rootCategory
                    val cleanName = cat.removePrefix("Category:").removePrefix("分类:")
                    val displayName = if (isRoot) "根分类" else cleanName.replace(group.characterName, "").trimStart('/', ' ', '-')
                    val manualFiles = manualSelectionMap[cat]

                    CategoryItem(
                        name = displayName.ifBlank { cleanName },
                        checked = cat in checkedCategories,
                        isRoot = isRoot,
                        manualCount = manualFiles?.size,
                        onCheckedChange = { onCategoryChecked(cat, it) },
                        onSelectFiles = { onOpenFileDialog(cat) }
                    )
                }
            }

            // ── 下载按钮 ──
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onDownload,
                enabled = !isDownloading && checkedCategories.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 24.dp),
                shape = smoothCornerShape(28.dp)
            ) {
                Icon(Icons.Default.Download, null)
                Spacer(Modifier.width(8.dp))
                Text(
                    "下载选中分类 (${checkedCategories.size})",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
fun CategoryItem(
    name: String,
    checked: Boolean,
    isRoot: Boolean,
    manualCount: Int?,
    onCheckedChange: (Boolean) -> Unit,
    onSelectFiles: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onCheckedChange(!checked) },
        shape = smoothCornerShape(14.dp),
        color = if (checked)
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        else
            MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.padding(start = 6.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isRoot) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isRoot) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                if (manualCount != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "已手动选择 $manualCount 个文件",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
            // 文件选择按钮
            FilledTonalIconButton(
                onClick = onSelectFiles,
                modifier = Modifier.size(34.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = if (checked)
                        MaterialTheme.colorScheme.secondaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Icon(
                    Icons.Outlined.Checklist, "选择文件",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
