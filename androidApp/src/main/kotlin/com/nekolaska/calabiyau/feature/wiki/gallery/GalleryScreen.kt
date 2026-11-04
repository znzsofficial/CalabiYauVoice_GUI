package com.nekolaska.calabiyau.feature.wiki.gallery

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.nekolaska.calabiyau.core.ui.ApiResourceContent
import com.nekolaska.calabiyau.core.ui.BackNavButton
import com.nekolaska.calabiyau.core.ui.ImagePreviewDialog
import com.nekolaska.calabiyau.core.ui.RefreshActionButton
import com.nekolaska.calabiyau.core.ui.ShimmerBox
import com.nekolaska.calabiyau.core.ui.rememberLoadState
import com.nekolaska.calabiyau.core.ui.smoothCornerShape
import com.nekolaska.calabiyau.core.ui.ConfirmImageSaveDialog
import com.nekolaska.calabiyau.core.ui.rememberSectionSelection
import com.nekolaska.calabiyau.core.ui.WikiSelectionSheet
import com.nekolaska.calabiyau.core.ui.rememberSnackbarLauncher
import com.nekolaska.calabiyau.feature.wiki.gallery.api.GalleryApi
import com.nekolaska.calabiyau.feature.wiki.gallery.model.GalleryImage
import com.nekolaska.calabiyau.feature.wiki.gallery.model.GallerySection

// ════════════════════════════════════════════════════════
//  画廊页 —— 壁纸 / 表情包 / 四格漫画 通用
// ════════════════════════════════════════════════════════

@Composable
fun GalleryScreen(
    title: String,
    pageName: String,
    onBack: () -> Unit
) {
    val showMessage = rememberSnackbarLauncher()
    val state = rememberLoadState(
        initial = emptyList<GallerySection>(),
        key = pageName,
        cachedPrefetchDelayMs = 300L,
        cachedFetch = { GalleryApi.fetchGallery(pageName, cacheOnly = true) }
    ) { force ->
        GalleryApi.fetchGallery(pageName = pageName, forceRefresh = force, allowMemoryCache = false)
    }
    val sectionKeys = remember(state.data) { com.nekolaska.calabiyau.core.ui.catalogKeys(state.data) { it.title } }
    val sectionSelection = rememberSectionSelection(pageName, sectionKeys)
    val selectedSectionIndex = sectionKeys.indexOf(sectionSelection.selectedKey).coerceAtLeast(0)

    // 全屏预览
    var previewImage by remember { mutableStateOf<GalleryImage?>(null) }
    // 长按保存
    var saveTargetImage by remember { mutableStateOf<GalleryImage?>(null) }


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    BackNavButton(onClick = onBack)
                },
                actions = {
                    if (state.data.isNotEmpty()) {
                        RefreshActionButton(onClick = { state.reload(forceRefresh = true) })
                    }
                }
            )
        }
    ) { innerPadding ->
        ApiResourceContent(
            state = state,
            modifier = Modifier.padding(innerPadding),
            loading = { mod -> GallerySkeleton(mod) }
        ) { sections ->
            Column(Modifier.fillMaxSize()) {
                val safeSelectedSectionIndex = selectedSectionIndex.coerceIn(0, sections.lastIndex.coerceAtLeast(0))

                // ── Section 选择器 ──
                if (sections.size > 1) {
                    GallerySectionSelector(
                        sections = sections,
                        selectedIndex = safeSelectedSectionIndex,
                        onSelectedIndexChange = { index -> sectionKeys.getOrNull(index)?.let(sectionSelection::select) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                // ── 图片网格 ──
                val currentSection = sections.getOrNull(safeSelectedSectionIndex)
                if (currentSection != null) {
                    val gridState = rememberLazyGridState()

                    // section 切换时滚动到顶部
                    LaunchedEffect(safeSelectedSectionIndex) {
                        gridState.scrollToItem(0)
                    }

                    // 表情包用更小的单元格和正方形比例，壁纸/漫画保持宽屏比例
                    val isStickerPage = pageName.contains("表情包")
                    val gridMinSize = if (isStickerPage) 120.dp else 160.dp
                    val imageAspectRatio = if (isStickerPage) 1f / 1f else 16f / 10f

                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = gridMinSize),
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 12.dp, end = 12.dp,
                            top = 8.dp, bottom = 16.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            currentSection.images,
                            key = { it.fileName }
                        ) { image ->
                            GalleryImageCard(
                                image = image,
                                imageAspectRatio = imageAspectRatio,
                                onClick = { previewImage = image }
                            )
                        }
                    }
                }
            }
        }
    }

    // ── 全屏预览 Dialog（支持缩放 + 长按保存） ──
    previewImage?.let { image ->
        ImagePreviewDialog(
            model = image.imageUrl,
            contentDescription = image.caption,
            onDismiss = { previewImage = null },
            onSave = { saveTargetImage = image }
        ) {
            if (image.caption.isNotBlank()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = image.caption,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    if (image.description.isNotBlank()) {
                        Text(
                            text = image.description,
                            color = Color.White.copy(alpha = 0.82f),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                    }
                    if (image.obtainMethod.isNotBlank()) {
                        Text(
                            text = "获取方式：${image.obtainMethod}",
                            color = Color.White.copy(alpha = 0.82f),
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }

    // ── 保存图片确认对话框 ──
    saveTargetImage?.let { image ->
        ConfirmImageSaveDialog(url = image.imageUrl, label = image.fileName,
            onDismiss = { saveTargetImage = null }, showMessage = showMessage)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GallerySectionSelector(
    sections: List<GallerySection>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var showSheet by remember { mutableStateOf(false) }
    val selectedSection = sections.getOrNull(selectedIndex) ?: return

    // ── 触发器 ──
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(smoothCornerShape(24.dp))
            .clickable { showSheet = true },
        shape = smoothCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = smoothCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                GallerySectionThumbnail(
                    imageUrl = selectedSection.images.firstOrNull()?.imageUrl,
                    icon = Icons.Outlined.Image,
                    modifier = Modifier.size(42.dp),
                    iconModifier = Modifier.padding(10.dp).size(22.dp)
                )
            }

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = selectedSection.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${selectedIndex + 1}/${sections.size}  ·  共 ${selectedSection.images.size} 张",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                shape = smoothCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
            ) {
                Icon(
                    Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.padding(4.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // ── ModalBottomSheet ──
    if (showSheet) {
        WikiSelectionSheet(title = "选择分区", onDismiss = { showSheet = false }) {
            items(sections.size, key = { "section:$it:${sections[it].title}" }, contentType = { "section" }) { index ->
                GallerySectionGridItem(section = sections[index], index = index, totalCount = sections.size,
                    selected = index == selectedIndex, onClick = { onSelectedIndexChange(index); showSheet = false })
            }
        }
    }
}

@Composable
private fun GallerySectionGridItem(
    modifier: Modifier = Modifier,
    section: GallerySection,
    index: Int,
    totalCount: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(smoothCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = smoothCornerShape(20.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainer,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
            else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = smoothCornerShape(14.dp),
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    GallerySectionThumbnail(
                        imageUrl = section.images.firstOrNull()?.imageUrl,
                        icon = Icons.Outlined.Image,
                        modifier = Modifier.size(42.dp),
                        iconModifier = Modifier.padding(10.dp).size(22.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                if (selected) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(4.dp)
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    section.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${index + 1}/$totalCount · ${section.images.size} 张",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                        else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun GallerySectionThumbnail(
    imageUrl: String?,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier
) {
    var failed by remember(imageUrl) { mutableStateOf(false) }
    val showImage = !imageUrl.isNullOrBlank() && !failed

    Box(modifier, contentAlignment = Alignment.Center) {
        if (showImage) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onError = { failed = true },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                icon,
                contentDescription = null,
                modifier = iconModifier
            )
        }
    }
}

@Composable
private fun GallerySkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = smoothCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                ShimmerBox(
                    modifier = Modifier.size(42.dp),
                    shape = smoothCornerShape(14.dp)
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ShimmerBox(Modifier.fillMaxWidth(0.46f).height(18.dp))
                    ShimmerBox(Modifier.fillMaxWidth(0.32f).height(12.dp))
                }
                ShimmerBox(
                    modifier = Modifier.size(28.dp),
                    shape = smoothCornerShape(12.dp)
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().weight(1f),
            userScrollEnabled = false
        ) {
            items(8) {
                Card(
                    shape = smoothCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Column {
                        ShimmerBox(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 10f),
                            shape = smoothCornerShape(16.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        ShimmerBox(
                            modifier = Modifier
                                .fillMaxWidth(0.75f)
                                .height(11.dp)
                                .padding(start = 12.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

// ────────────────────────────────────────────
//  图片卡片
// ────────────────────────────────────────────

@Composable
private fun GalleryImageCard(
    image: GalleryImage,
    imageAspectRatio: Float = 16f / 10f,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val cardShape = smoothCornerShape(16.dp)

    Card(
        onClick = onClick,
        shape = cardShape,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(image.imageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = image.caption,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(imageAspectRatio)
                    .clip(smoothCornerShape(16.dp)),
                contentScale = ContentScale.Crop
            )
            if (image.caption.isNotBlank()) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = image.caption,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (image.obtainMethod.isNotBlank()) {
                        Text(
                            text = "获取方式：${image.obtainMethod}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else if (image.description.isNotBlank()) {
                        Text(
                            text = image.description,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
