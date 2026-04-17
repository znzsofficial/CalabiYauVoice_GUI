package com.nekolaska.calabiyau.ui.tools

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nekolaska.calabiyau.data.AppPrefs
import com.nekolaska.calabiyau.ui.shared.rememberSnackbarLauncher
import com.nekolaska.calabiyau.ui.shared.smoothCornerShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

private enum class TimelineExportFormat(val label: String, val ext: String) {
    SRT("SRT", "srt"),
    LRC("LRC", "lrc"),
    VTT("VTT", "vtt"),
    ASS("ASS", "ass")
}

private data class TimelineClip(
    val id: Long,
    val startMs: Long,
    val endMs: Long,
    val text: String
)

private data class ParsedTimeline(
    val clips: List<TimelineClip>,
    val truncated: Boolean
)

private data class HistoryState(
    val clips: List<TimelineClip>,
    val selectedId: Long
)

private const val MIN_CLIP_MS = 100L
private const val MAX_CLIPS = 8000

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TextToolsPage(
    outputPath: String,
    isBusy: Boolean,
    onBusyChange: (Boolean) -> Unit,
    onResult: (ToolOutput) -> Unit,
    onPickFilesFromFileManager: (
        initialPath: String?,
        title: String,
        description: String,
        allowMultiSelect: Boolean,
        onOpenSystemPicker: () -> Unit,
        onPicked: (List<String>) -> Unit
    ) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val showSnack = rememberSnackbarLauncher()
    val scope = rememberCoroutineScope()

    val clips = remember { mutableStateListOf<TimelineClip>() }
    val undoStack = remember { mutableStateListOf<HistoryState>() }
    val redoStack = remember { mutableStateListOf<HistoryState>() }
    var selectedId by rememberSaveable { mutableLongStateOf(-1L) }
    var nextId by rememberSaveable { mutableLongStateOf(1L) }
    var exportFormat by rememberSaveable { mutableStateOf(TimelineExportFormat.SRT) }
    var exportFormatExpanded by remember { mutableStateOf(false) }
    var globalShiftMs by rememberSaveable { mutableIntStateOf(0) }
    var snapMs by rememberSaveable { mutableIntStateOf(10) }
    var playheadMs by rememberSaveable { mutableLongStateOf(0L) }
    var viewportStartMs by rememberSaveable { mutableFloatStateOf(0f) }
    var viewportDurationMs by rememberSaveable { mutableFloatStateOf(30_000f) }
    var showExportDialog by rememberSaveable { mutableStateOf(false) }
    var exportNormalize by rememberSaveable { mutableStateOf(true) }
    var exportSkipEmptyText by rememberSaveable { mutableStateOf(false) }

    fun snapTime(value: Long): Long {
        val step = max(1, snapMs).toLong()
        return ((value + step / 2) / step) * step
    }

    fun totalDurationMs(): Long = clips.maxOfOrNull { it.endMs }?.coerceAtLeast(1L) ?: 1L

    fun selectedClip(): TimelineClip? = clips.firstOrNull { it.id == selectedId }

    fun saveInternalState() {
        undoStack.add(HistoryState(clips.toList(), selectedId))
        if (undoStack.size > 50) {
            undoStack.removeAt(0)
        }
        redoStack.clear()
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            redoStack.add(HistoryState(clips.toList(), selectedId))
            val state = undoStack.removeAt(undoStack.lastIndex)
            clips.clear()
            clips.addAll(state.clips)
            selectedId = state.selectedId
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            undoStack.add(HistoryState(clips.toList(), selectedId))
            val state = redoStack.removeAt(redoStack.lastIndex)
            clips.clear()
            clips.addAll(state.clips)
            selectedId = state.selectedId
        }
    }

    fun setSelected(id: Long) {
        selectedId = id
    }

    fun replaceClip(updated: TimelineClip) {
        val i = clips.indexOfFirst { it.id == updated.id }
        if (i >= 0) {
            saveInternalState()
            clips[i] = updated
        }
    }

    fun moveClipByDelta(id: Long, deltaMs: Long) {
        if (deltaMs == 0L) return
        val i = clips.indexOfFirst { it.id == id }
        if (i < 0) return
        val clip = clips[i]
        val total = max(totalDurationMs(), clip.endMs)
        val minDelta = -clip.startMs
        val maxDelta = total - clip.endMs
        val realDelta = deltaMs.coerceIn(minDelta, maxDelta)
        clips[i] = clip.copy(
            startMs = clip.startMs + realDelta,
            endMs = clip.endMs + realDelta
        )
    }

    fun importFromInput(input: PickedInput) {
        scope.launch {
            onBusyChange(true)
            runCatching {
                withContext(Dispatchers.IO) {
                    parseTimelineFromInput(context, input, nextId)
                }
            }.onSuccess { parsed ->
                saveInternalState()
                clips.clear()
                clips.addAll(parsed.clips)
                nextId = (parsed.clips.maxOfOrNull { it.id } ?: 0L) + 1L
                selectedId = parsed.clips.firstOrNull()?.id ?: -1L
                viewportStartMs = 0f
                viewportDurationMs = min(60_000f, max(10_000f, totalDurationMs().toFloat()))
                showSnack(
                    if (parsed.truncated) "已导入 ${parsed.clips.size} 条（过大文件已截断）"
                    else "已导入 ${parsed.clips.size} 条"
                )
            }.onFailure {
                showSnack("导入失败：${it.message ?: "未知错误"}")
            }
            onBusyChange(false)
        }
    }

    fun exportTimeline(shiftMs: Int, normalize: Boolean, skipEmptyText: Boolean) {
        if (clips.isEmpty()) {
            showSnack("没有可导出的片段")
            return
        }
        scope.launch {
            onBusyChange(true)
            runCatching {
                withContext(Dispatchers.IO) {
                    val outputDir = resolveOutputDirectory(outputPath, "文本工具/时间轴")
                    val target = buildUniqueFile(outputDir, "timeline_${System.currentTimeMillis()}", exportFormat.ext)
                    var source = clips.toList()
                    if (skipEmptyText) {
                        source = source.filter { it.text.isNotBlank() }
                    }
                    if (source.isEmpty()) error("所有片段都为空文本，无法导出")

                    source = if (normalize) {
                        val sorted = source.sortedBy { it.startMs }
                        var cursor = 0L
                        sorted.map { clip ->
                            val s = max(cursor, max(0L, clip.startMs))
                            val e = max(s + MIN_CLIP_MS, clip.endMs)
                            cursor = e
                            clip.copy(startMs = s, endMs = e)
                        }
                    } else {
                        source.sortedBy { it.startMs }
                    }

                    val shifted = source
                        .map {
                            val s = max(0L, it.startMs + shiftMs)
                            val e = max(s + MIN_CLIP_MS, it.endMs + shiftMs)
                            it.copy(startMs = s, endMs = e)
                        }
                    val content = when (exportFormat) {
                        TimelineExportFormat.SRT -> toSrt(shifted)
                        TimelineExportFormat.LRC -> toLrc(shifted)
                        TimelineExportFormat.VTT -> toVtt(shifted)
                        TimelineExportFormat.ASS -> toAss(shifted)
                    }
                    target.writeText(content)
                    ToolOutput(
                        title = "时间轴导出完成",
                        message = "已导出 ${target.name}",
                        files = listOf(target),
                        directory = outputDir
                    )
                }
            }.onSuccess {
                onResult(it)
                showSnack(it.message)
            }.onFailure {
                showSnack("导出失败：${it.message ?: "未知错误"}")
            }
            onBusyChange(false)
        }
    }

    fun normalizeSort() {
        saveInternalState()
        val sorted = clips.sortedBy { it.startMs }
        val normalized = mutableListOf<TimelineClip>()
        var cursor = 0L
        sorted.forEach { clip ->
            val start = max(cursor, max(0L, clip.startMs))
            val end = max(start + MIN_CLIP_MS, clip.endMs)
            normalized += clip.copy(startMs = start, endMs = end)
            cursor = end
        }
        clips.clear()
        clips.addAll(normalized)
    }

    fun applyViewport(startMs: Float, durationMs: Float) {
        val total = totalDurationMs().toFloat().coerceAtLeast(1f)
        val minDuration = 5_000f
        val maxDuration = max(10_000f, total)
        val d = durationMs.coerceIn(minDuration, maxDuration)
        val maxStart = max(0f, total - d)
        viewportDurationMs = d
        viewportStartMs = startMs.coerceIn(0f, maxStart)
    }

    fun panViewportBy(deltaMs: Float) {
        applyViewport(viewportStartMs + deltaMs, viewportDurationMs)
    }

    fun zoomViewportBy(zoom: Float, focusRatio: Float) {
        if (zoom <= 0f) return
        val focus = focusRatio.coerceIn(0f, 1f)
        val focusTime = viewportStartMs + viewportDurationMs * focus
        val newDuration = viewportDurationMs / zoom
        val newStart = focusTime - newDuration * focus
        applyViewport(newStart, newDuration)
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { importFromInput(PickedInput(uri = it)) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            shape = smoothCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = {
                            onPickFilesFromFileManager(
                                AppPrefs.savePath,
                                "选择时间轴文件",
                                "支持 .lrc / .srt",
                                false,
                                { picker.launch("*/*") }
                            ) { paths ->
                                paths.firstOrNull()?.let { importFromInput(PickedInput(file = File(it))) }
                            }
                        },
                        enabled = !isBusy,
                        shape = smoothCornerShape(20.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Outlined.FileOpen, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("导入", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    FilledTonalButton(
                        onClick = {
                            val base = clips.lastOrNull()?.endMs ?: 0L
                            val clip = TimelineClip(nextId++, base, base + 2_000, "")
                            clips += clip
                            selectedId = clip.id
                        },
                        enabled = !isBusy,
                        shape = smoothCornerShape(20.dp)
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("新增")
                    }

                    FilledTonalButton(
                        onClick = { normalizeSort() },
                        enabled = !isBusy,
                        shape = smoothCornerShape(20.dp)
                    ) { Text("整理") }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("导出格式", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box {
                        FilterChip(
                            selected = true,
                            onClick = { exportFormatExpanded = true },
                            label = { Text(exportFormat.label) },
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            shape = smoothCornerShape(16.dp)
                        )
                        DropdownMenu(
                            expanded = exportFormatExpanded,
                            onDismissRequest = { exportFormatExpanded = false }
                        ) {
                            TimelineExportFormat.entries.forEach { format ->
                                DropdownMenuItem(
                                    text = { Text(format.label) },
                                    onClick = {
                                        exportFormat = format
                                        exportFormatExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    FilledTonalButton(onClick = { showExportDialog = true }, enabled = !isBusy, shape = smoothCornerShape(20.dp)) {
                        Icon(Icons.Outlined.SaveAlt, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("导出")
                    }
                }
            }
        }

        Card(
            shape = smoothCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TimelineViewport(
                    clips = clips,
                    selectedId = selectedId,
                    playheadMs = playheadMs,
                    viewportStartMs = viewportStartMs,
                    viewportDurationMs = viewportDurationMs,
                    totalDurationMs = totalDurationMs(),
                    onSelect = { setSelected(it) },
                    onPlayheadChange = { playheadMs = it },
                    onMoveClip = { id, delta -> moveClipByDelta(id, delta) },
                    onPanViewport = { delta -> panViewportBy(delta) },
                    onZoomViewport = { zoom, focus -> zoomViewportBy(zoom, focus) },
                    onDragStartSave = { saveInternalState() }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "拖拽平移 · 双指缩放，选中后可左右滑动调整时间段",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("吸附(ms)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    listOf(1, 10, 50, 100).forEach { step ->
                        val selected = snapMs == step
                        FilterChip(
                            selected = selected,
                            onClick = { snapMs = step },
                            label = { Text("$step") },
                            shape = smoothCornerShape(16.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                        )
                    }
                }
            }
        }

        if (showExportDialog) {
            ExportTimelineDialog(
                initialShiftMs = globalShiftMs,
                normalize = exportNormalize,
                skipEmptyText = exportSkipEmptyText,
                onNormalizeChange = { exportNormalize = it },
                onSkipEmptyTextChange = { exportSkipEmptyText = it },
                onDismiss = { showExportDialog = false },
                onConfirm = { shiftMs ->
                    globalShiftMs = shiftMs
                    showExportDialog = false
                    exportTimeline(
                        shiftMs = shiftMs,
                        normalize = exportNormalize,
                        skipEmptyText = exportSkipEmptyText
                    )
                }
            )
        }

        selectedClip()?.let { clip ->
            Card(
                shape = smoothCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("编辑片段", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Row {
                            IconButton(
                                onClick = { undo() },
                                enabled = !isBusy && undoStack.isNotEmpty()
                            ) { Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = "撤销") }
                            IconButton(
                                onClick = { redo() },
                                enabled = !isBusy && redoStack.isNotEmpty()
                            ) { Icon(Icons.AutoMirrored.Outlined.Redo, contentDescription = "重做") }
                            IconButton(
                                onClick = {
                                    saveInternalState()
                                    clips.removeAll { it.id == clip.id }
                                    selectedId = clips.firstOrNull()?.id ?: -1L
                                }
                            ) {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }

                    Text("${formatSrt(clip.startMs)} → ${formatSrt(clip.endMs)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val s = snapTime(max(0L, clip.startMs - snapMs))
                                val e = max(s + MIN_CLIP_MS, clip.endMs)
                                replaceClip(clip.copy(startMs = s, endMs = e))
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("起点 -$snapMs") }
                        OutlinedButton(
                            onClick = {
                                val s = snapTime(max(0L, clip.startMs + snapMs))
                                val e = max(s + MIN_CLIP_MS, clip.endMs)
                                replaceClip(clip.copy(startMs = s, endMs = e))
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("起点 +$snapMs") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val e = snapTime(max(clip.startMs + MIN_CLIP_MS, clip.endMs - snapMs))
                                replaceClip(clip.copy(endMs = e))
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("终点 -$snapMs") }
                        OutlinedButton(
                            onClick = {
                                val e = snapTime(max(clip.startMs + MIN_CLIP_MS, clip.endMs + snapMs))
                                replaceClip(clip.copy(endMs = e))
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("终点 +$snapMs") }
                    }

                    var text by remember(clip.id, clip.text) { mutableStateOf(clip.text) }
                    OutlinedTextField(
                        value = text,
                        onValueChange = {
                            text = it
                            replaceClip(clip.copy(text = it))
                        },
                        label = { Text("字幕文本") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = smoothCornerShape(12.dp),
                        minLines = 2
                    )
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        LazyColumn(
            modifier = Modifier.heightIn(max = 300.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(clips, key = { it.id }) { clip ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (clip.id == selectedId) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                            smoothCornerShape(10.dp)
                        )
                        .clickable { selectedId = clip.id }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.TextFields, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${formatSrt(clip.startMs)} → ${formatSrt(clip.endMs)}", style = MaterialTheme.typography.labelMedium)
                        Text(
                            clip.text.ifBlank { "（空文本）" },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineViewport(
    clips: List<TimelineClip>,
    selectedId: Long,
    playheadMs: Long,
    viewportStartMs: Float,
    viewportDurationMs: Float,
    totalDurationMs: Long,
    onSelect: (Long) -> Unit,
    onPlayheadChange: (Long) -> Unit,
    onMoveClip: (Long, Long) -> Unit,
    onPanViewport: (Float) -> Unit,
    onZoomViewport: (Float, Float) -> Unit,
    onDragStartSave: () -> Unit
) {
    val bg = MaterialTheme.colorScheme.surfaceContainerHighest
    val normal = MaterialTheme.colorScheme.primary
    val selected = MaterialTheme.colorScheme.tertiary
    val lineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    val playheadColor = MaterialTheme.colorScheme.error
    var draggingSelectedClip by remember(clips, selectedId) { mutableStateOf(false) }
    var dragAccumulatedMs by remember(clips, selectedId, viewportStartMs, viewportDurationMs) { mutableFloatStateOf(0f) }
    val gridStep = remember(viewportDurationMs) { chooseGridStepMs(viewportDurationMs.toLong()) }
    val latestClips by rememberUpdatedState(clips)
    val latestSelectedId by rememberUpdatedState(selectedId)
    val latestViewportStartMs by rememberUpdatedState(viewportStartMs)
    val latestViewportDurationMs by rememberUpdatedState(viewportDurationMs)
    val latestTotalDurationMs by rememberUpdatedState(totalDurationMs)
    val latestOnSelect by rememberUpdatedState(onSelect)
    val latestOnPlayheadChange by rememberUpdatedState(onPlayheadChange)
    val latestOnMoveClip by rememberUpdatedState(onMoveClip)
    val latestOnPanViewport by rememberUpdatedState(onPanViewport)
    val latestOnZoomViewport by rememberUpdatedState(onZoomViewport)
    val latestOnDragStartSave by rememberUpdatedState(onDragStartSave)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(148.dp)
            .background(bg, RoundedCornerShape(12.dp))
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val currentClips = latestClips
                    if (currentClips.isEmpty()) return@detectTapGestures
                    val ratio = (offset.x / size.width).coerceIn(0f, 1f)
                    val tappedMs = latestViewportStartMs + latestViewportDurationMs * ratio
                    latestOnPlayheadChange(tappedMs.roundToLong().coerceIn(0L, max(1L, latestTotalDurationMs)))
                    val inRange = currentClips.filter { tappedMs >= it.startMs && tappedMs <= it.endMs }
                    val target = if (inRange.isNotEmpty()) {
                        inRange.minByOrNull { it.endMs - it.startMs }
                    } else {
                        currentClips.minByOrNull {
                            min(
                                kotlin.math.abs(it.startMs - tappedMs),
                                kotlin.math.abs(it.endMs - tappedMs)
                            )
                        }
                    }
                    target?.let { latestOnSelect(it.id) }
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { pos ->
                        val selectedClip = latestClips.firstOrNull { it.id == latestSelectedId }
                        if (selectedClip == null) {
                            draggingSelectedClip = false
                            return@detectDragGestures
                        }
                        
                        val currentStart = latestViewportStartMs
                        val currentDuration = latestViewportDurationMs
                        val left = ((max(currentStart, selectedClip.startMs.toFloat()) - currentStart) / currentDuration) * size.width
                        val right = ((min(currentStart + currentDuration, selectedClip.endMs.toFloat()) - currentStart) / currentDuration) * size.width
                        draggingSelectedClip = pos.x in left..right
                        dragAccumulatedMs = 0f
                        
                        if (draggingSelectedClip) {
                            latestOnDragStartSave()
                        }
                    },
                    onDragEnd = {
                        draggingSelectedClip = false
                        dragAccumulatedMs = 0f
                    },
                    onDragCancel = {
                        draggingSelectedClip = false
                        dragAccumulatedMs = 0f
                    },
                    onDrag = { _, dragAmount ->
                        if (size.width <= 0f) return@detectDragGestures
                        val currentDuration = latestViewportDurationMs
                        if (!draggingSelectedClip) {
                            val deltaMs = (dragAmount.x / size.width) * currentDuration
                            latestOnPanViewport(-deltaMs)
                            return@detectDragGestures
                        }

                        val clipDeltaMs = (dragAmount.x / size.width) * currentDuration
                        dragAccumulatedMs += clipDeltaMs
                        if (kotlin.math.abs(dragAccumulatedMs) >= 1f) {
                            val step = dragAccumulatedMs.toLong()
                            dragAccumulatedMs -= step
                            latestOnMoveClip(latestSelectedId, step)
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures(panZoomLock = true) { centroid, pan, zoom, _ ->
                    if (size.width <= 0f) return@detectTransformGestures
                    if (kotlin.math.abs(zoom - 1f) >= 0.001f) {
                        val focusRatio = (centroid.x / size.width).coerceIn(0f, 1f)
                        latestOnZoomViewport(zoom, focusRatio)
                      } else if (kotlin.math.abs(pan.x) >= 0.5f) {
                        val deltaMs = (pan.x / size.width) * latestViewportDurationMs
                        latestOnPanViewport(-deltaMs)
                    }
                }
            }
            .padding(8.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val start = viewportStartMs
            val end = viewportStartMs + viewportDurationMs

            drawRoundRect(
                color = Color.Gray.copy(alpha = 0.25f),
                cornerRadius = CornerRadius(8f, 8f),
                style = Stroke(width = 2f)
            )

            if (gridStep > 0L) {
                var t = (start.toLong() / gridStep) * gridStep
                if (t < start.toLong()) t += gridStep
                while (t <= end.toLong()) {
                    val x = ((t - start) / viewportDurationMs) * size.width
                    drawLine(
                        color = gridColor,
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = if ((t / gridStep) % 5L == 0L) 1.6f else 1f
                    )
                    t += gridStep
                }
            }

            clips.forEach { clip ->
                if (clip.endMs < start || clip.startMs > end) return@forEach
                val left = ((max(start, clip.startMs.toFloat()) - start) / viewportDurationMs) * size.width
                val right = ((min(end, clip.endMs.toFloat()) - start) / viewportDurationMs) * size.width
                val w = max(4f, right - left)
                drawRoundRect(
                    color = if (clip.id == selectedId) selected else normal,
                    topLeft = Offset(left, size.height * 0.28f),
                    size = androidx.compose.ui.geometry.Size(w, size.height * 0.44f),
                    cornerRadius = CornerRadius(8f, 8f),
                    alpha = 0.86f
                )
            }

            drawLine(
                color = lineColor,
                start = Offset(0f, size.height * 0.5f),
                end = Offset(size.width, size.height * 0.5f),
                strokeWidth = 1.2f
            )

            if (playheadMs.toFloat() in start..end) {
                val x = ((playheadMs - start) / viewportDurationMs) * size.width
                drawLine(
                    color = playheadColor,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 2.4f
                )
            }
        }

    }
}

@Composable
private fun ExportTimelineDialog(
    initialShiftMs: Int,
    normalize: Boolean,
    skipEmptyText: Boolean,
    onNormalizeChange: (Boolean) -> Unit,
    onSkipEmptyTextChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var shiftText by remember(initialShiftMs) { mutableStateOf(initialShiftMs.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导出设置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = shiftText,
                    onValueChange = { shiftText = it },
                    label = { Text("导出时间整体平移（ms）") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = smoothCornerShape(12.dp),
                    singleLine = true
                )
                Text(
                    "说明：正数会让全部字幕整体后移，负数会前移；导出时会自动保证时间不小于 0。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = normalize, onCheckedChange = onNormalizeChange)
                    Text("导出前自动整理时间（防重叠）")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = skipEmptyText, onCheckedChange = onSkipEmptyTextChange)
                    Text("忽略空文本片段")
                }
                Text(
                    "后续可扩展：编号重排、文本过滤、批量替换等导出选项。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(shiftText.toIntOrNull() ?: 0) }) {
                Text("开始导出")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        shape = smoothCornerShape(28.dp)
    )
}

private fun chooseGridStepMs(viewportDurationMs: Long): Long {
    return when {
        viewportDurationMs <= 5_000L -> 100L
        viewportDurationMs <= 10_000L -> 200L
        viewportDurationMs <= 20_000L -> 500L
        viewportDurationMs <= 60_000L -> 1_000L
        viewportDurationMs <= 180_000L -> 2_000L
        else -> 5_000L
    }
}

private fun parseTimelineFromInput(context: Context, input: PickedInput, startId: Long): ParsedTimeline {
    val ext = detectExt(context, input)

    fun openReader(): BufferedReader? {
        return input.file?.takeIf { it.isFile }?.bufferedReader()
            ?: input.uri?.let { context.contentResolver.openInputStream(it)?.bufferedReader() }
    }

    val (clips, truncated) = when (ext) {
        "srt", "vtt" -> parseSrtStream(openReader() ?: return ParsedTimeline(emptyList(), false), startId)
        "lrc" -> parseLrcStream(openReader() ?: return ParsedTimeline(emptyList(), false), startId)
        "ass", "ssa" -> parseAssStream(openReader() ?: return ParsedTimeline(emptyList(), false), startId)
        else -> {
            val srtTry = parseSrtStream(openReader() ?: return ParsedTimeline(emptyList(), false), startId)
            if (srtTry.first.isNotEmpty()) srtTry else {
                val lrcTry = parseLrcStream(openReader() ?: return ParsedTimeline(emptyList(), false), startId)
                if (lrcTry.first.isNotEmpty()) lrcTry else parseAssStream(openReader() ?: return ParsedTimeline(emptyList(), false), startId)
            }
        }
    }
    return ParsedTimeline(clips, truncated)
}

private fun detectExt(context: Context, input: PickedInput): String {
    val byFile = input.file?.extension?.lowercase()?.takeIf { it.isNotBlank() }
    if (byFile != null) return byFile
    val byName = input.uri?.let(context::queryDisplayName)?.substringAfterLast('.', "")?.lowercase()?.takeIf { it.isNotBlank() }
    if (byName != null) return byName
    return ""
}

private fun parseSrtStream(reader: BufferedReader, startId: Long): Pair<List<TimelineClip>, Boolean> {
    reader.use {
        val out = mutableListOf<TimelineClip>()
        var id = startId
        var truncated = false

        var currentStart = -1L
        var currentEnd = -1L
        val textLines = mutableListOf<String>()

        fun flush() {
            if (currentStart in 0 until currentEnd && textLines.isNotEmpty()) {
                if (out.size >= MAX_CLIPS) {
                    truncated = true
                } else {
                    out += TimelineClip(id++, currentStart, currentEnd, textLines.joinToString("\n"))
                }
            }
            currentStart = -1L
            currentEnd = -1L
            textLines.clear()
        }

        val timeRegex = Regex("""(\d{2}:\d{2}:\d{2}[,\.]\d{1,3})\s*-->\s*(\d{2}:\d{2}:\d{2}[,\.]\d{1,3})""")
        it.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isBlank()) {
                flush()
                return@forEach
            }
            val match = timeRegex.find(line)
            if (match != null) {
                flush()
                val s = parseSrtTime(match.groupValues[1])
                val e = parseSrtTime(match.groupValues[2])
                currentStart = s
                currentEnd = max(s + MIN_CLIP_MS, e)
            } else {
                if (currentStart >= 0) textLines += line
            }
        }
        flush()
        return out to truncated
    }
}

private fun parseLrcStream(reader: BufferedReader, startId: Long): Pair<List<TimelineClip>, Boolean> {
    reader.use {
        val points = mutableListOf<Pair<Long, String>>()
        val tagRegex = Regex("""\[(\d{1,2}):(\d{2})(?:[\.:](\d{1,3}))?]""")

        it.lineSequence().forEach { raw ->
            val matches = tagRegex.findAll(raw).toList()
            if (matches.isEmpty()) return@forEach
            val content = raw.replace(tagRegex, "").trim()
            matches.forEach { m ->
                val mm = m.groupValues[1].toLongOrNull() ?: 0L
                val ss = m.groupValues[2].toLongOrNull() ?: 0L
                val frac = m.groupValues[3]
                val ms = when (frac.length) {
                    0 -> 0L
                    1 -> frac.toLongOrNull()?.times(100) ?: 0L
                    2 -> frac.toLongOrNull()?.times(10) ?: 0L
                    else -> frac.take(3).toLongOrNull() ?: 0L
                }
                points += ((mm * 60_000) + (ss * 1_000) + ms) to content
            }
        }

        val sorted = points.sortedBy { it.first }
        val out = mutableListOf<TimelineClip>()
        var id = startId
        var truncated = false
        for (i in sorted.indices) {
            if (out.size >= MAX_CLIPS) {
                truncated = true
                break
            }
            val s = sorted[i].first
            val e = if (i + 1 < sorted.size) sorted[i + 1].first else s + 2_000L
            out += TimelineClip(id++, s, max(s + MIN_CLIP_MS, e), sorted[i].second)
        }
        return out to truncated
    }
}

private fun parseSrtTime(value: String): Long {
    val normalized = value.replace(',', '.')
    val parts = normalized.split(':')
    if (parts.size != 3) return 0L
    val hh = parts[0].toLongOrNull() ?: 0L
    val mm = parts[1].toLongOrNull() ?: 0L
    val secParts = parts[2].split('.')
    val ss = secParts.getOrNull(0)?.toLongOrNull() ?: 0L
    val rawMs = secParts.getOrNull(1)?.padEnd(3, '0')?.take(3)?.toLongOrNull() ?: 0L
    return hh * 3_600_000 + mm * 60_000 + ss * 1_000 + rawMs
}

private fun parseAssTime(value: String): Long {
    val parts = value.trim().split(':')
    if (parts.size != 3) return 0L
    val h = parts[0].toLongOrNull() ?: 0L
    val m = parts[1].toLongOrNull() ?: 0L
    val sParts = parts[2].split('.')
    val s = sParts[0].toLongOrNull() ?: 0L
    val cs = sParts.getOrNull(1)?.padEnd(2, '0')?.take(2)?.toLongOrNull() ?: 0L
    return h * 3_600_000 + m * 60_000 + s * 1_000 + cs * 10
}

private fun parseAssStream(reader: BufferedReader, startId: Long): Pair<List<TimelineClip>, Boolean> {
    reader.use {
        val out = mutableListOf<TimelineClip>()
        var id = startId
        var truncated = false

        val eventsRegex = Regex("""^Dialogue:\s*[^,]*,([^,]+),([^,]+),([^,]*),([^,]*),([^,]*),([^,]*),([^,]*),([^,]*),(.*)$""")

        it.lineSequence().forEach { line ->
            val match = eventsRegex.find(line.trim())
            if (match != null) {
                if (out.size >= MAX_CLIPS) {
                    truncated = true
                    return@forEach
                }
                val sMs = parseAssTime(match.groupValues[1])
                val eMs = parseAssTime(match.groupValues[2])
                val text = match.groupValues[9].replace("\\N", "\n").replace("\\n", "\n")
                val cleanText = text.replace(Regex("""\{[^}]*\}"""), "")
                out += TimelineClip(id++, sMs, max(sMs + MIN_CLIP_MS, eMs), cleanText)
            }
        }
        return out to truncated
    }
}

private fun formatSrt(ms: Long): String {
    val safe = max(0L, ms)
    val hh = safe / 3_600_000
    val mm = (safe % 3_600_000) / 60_000
    val ss = (safe % 60_000) / 1_000
    val ms3 = safe % 1_000
    return "%02d:%02d:%02d,%03d".format(hh, mm, ss, ms3)
}

private fun formatShortTime(ms: Long): String {
    val safe = max(0L, ms)
    val mm = safe / 60_000
    val ss = (safe % 60_000) / 1_000
    val cs = (safe % 1_000) / 10
    return "%02d:%02d.%02d".format(mm, ss, cs)
}

private fun lrcTime(ms: Long): String {
    val safe = max(0L, ms)
    val mm = safe / 60_000
    val ss = (safe % 60_000) / 1_000
    val cs = (safe % 1_000) / 10
    return "%02d:%02d.%02d".format(mm, ss, cs)
}

private fun toSrt(clips: List<TimelineClip>): String = buildString {
    clips.forEachIndexed { idx, c ->
        appendLine(idx + 1)
        appendLine("${formatSrt(c.startMs)} --> ${formatSrt(c.endMs)}")
        appendLine(c.text)
        appendLine()
    }
}

private fun toLrc(clips: List<TimelineClip>): String = buildString {
    clips.forEach { c ->
        append("[")
        append(lrcTime(c.startMs))
        append("]")
        appendLine(c.text)
    }
}

private fun formatAssTime(ms: Long): String {
    val safe = max(0L, ms)
    val hh = safe / 3_600_000
    val mm = (safe % 3_600_000) / 60_000
    val ss = (safe % 60_000) / 1_000
    val cs = (safe % 1_000) / 10
    return "%d:%02d:%02d.%02d".format(hh, mm, ss, cs)
}

private fun formatVttTime(ms: Long): String {
    val safe = max(0L, ms)
    val hh = safe / 3_600_000
    val mm = (safe % 3_600_000) / 60_000
    val ss = (safe % 60_000) / 1_000
    val ms3 = safe % 1_000
    return "%02d:%02d:%02d.%03d".format(hh, mm, ss, ms3)
}

private fun toVtt(clips: List<TimelineClip>): String = buildString {
    appendLine("WEBVTT")
    appendLine()
    clips.forEachIndexed { idx, c ->
        appendLine(idx + 1)
        appendLine("${formatVttTime(c.startMs)} --> ${formatVttTime(c.endMs)}")
        appendLine(c.text)
        appendLine()
    }
}

private fun toAss(clips: List<TimelineClip>): String = buildString {
    appendLine("[Script Info]")
    appendLine("ScriptType: v4.00+")
    appendLine("WrapStyle: 0")
    appendLine()
    appendLine("[V4+ Styles]")
    appendLine("Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding")
    appendLine("Style: Default,Arial,20,&H00FFFFFF,&H000000FF,&H00000000,&H00000000,0,0,0,0,100,100,0,0,1,1,0,2,10,10,10,1")
    appendLine()
    appendLine("[Events]")
    appendLine("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text")
    clips.forEach { c ->
        val text = c.text.replace("\n", "\\N")
        appendLine("Dialogue: 0,${formatAssTime(c.startMs)},${formatAssTime(c.endMs)},Default,,0,0,0,,${text}")
    }
}
