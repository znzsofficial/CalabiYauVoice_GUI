package ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import data.VotingApi
import io.github.composefluent.ExperimentalFluentApi
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.*
import io.github.composefluent.icons.Icons
import io.github.composefluent.icons.regular.*
import io.github.composefluent.surface.Card
import kotlinx.coroutines.launch
import ui.components.NetworkImage
import ui.components.StyledWindow

// ────────────────────────────────────────────
//  时装投票窗口
// ────────────────────────────────────────────

@OptIn(ExperimentalFluentApi::class)
@Composable
fun VotingWindow(onCloseRequest: () -> Unit) {
    val windowState = rememberWindowState(
        width = 900.dp,
        height = 720.dp,
        position = WindowPosition(Alignment.Center)
    )

    StyledWindow(
        title = "时装投票",
        onCloseRequest = onCloseRequest,
        state = windowState,
        onKeyEvent = { keyEvent ->
            if (keyEvent.key == Key.Escape && keyEvent.type == KeyEventType.KeyDown) {
                onCloseRequest(); true
            } else false
        },
        useLayer = false
    ) { insetModifier ->
        VotingWindowContent(modifier = insetModifier)
    }
}

// ────────────────────────────────────────────
//  投票窗口主内容
// ────────────────────────────────────────────

@OptIn(ExperimentalFluentApi::class)
@Composable
private fun VotingWindowContent(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()

    var isLoadingConfig by remember { mutableStateOf(true) }
    var isLoadingData by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var pollConfig by remember { mutableStateOf<VotingApi.PollConfig?>(null) }
    var voteState by remember { mutableStateOf<VotingApi.VoteState?>(null) }
    var selectedNames by remember { mutableStateOf<Set<String>>(emptySet()) }

    fun loadData() {
        scope.launch {
            isLoadingConfig = true
            errorMessage = null
            statusMessage = null
            when (val result = VotingApi.fetchPollConfig()) {
                is VotingApi.ApiResult.Success -> {
                    pollConfig = result.value
                    isLoadingData = true
                    when (val dataResult = VotingApi.fetchVoteData(result.value)) {
                        is VotingApi.ApiResult.Success -> {
                            voteState = dataResult.value
                            selectedNames = dataResult.value.pollDataMap
                                .filter { it.value.userVoted }
                                .keys
                        }
                        is VotingApi.ApiResult.Error -> {
                            errorMessage = dataResult.message
                        }
                    }
                    isLoadingData = false
                }
                is VotingApi.ApiResult.Error -> {
                    errorMessage = result.message
                }
            }
            isLoadingConfig = false
        }
    }

    LaunchedEffect(Unit) { loadData() }

    Column(modifier.fillMaxSize()) {
        // ── 顶部工具栏 ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Regular.Vote, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("时装投票", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.weight(1f))

            // 刷新按钮
            Button(
                onClick = { loadData() },
                disabled = isLoadingConfig || isLoadingData
            ) {
                Icon(Icons.Regular.ArrowSync, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("刷新", fontSize = 12.sp)
            }
        }

        // ── 状态消息 ──
        if (statusMessage != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Regular.Info,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = Color(0xFF4CAF50)
                )
                Text(
                    statusMessage!!,
                    fontSize = 12.sp,
                    color = Color(0xFF4CAF50)
                )
            }
        }

        // ── 内容区 ──
        when {
            isLoadingConfig -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ProgressRing()
                        Text("正在加载投票页面…", fontSize = 13.sp, color = FluentTheme.colors.text.text.secondary)
                    }
                }
            }
            errorMessage != null && pollConfig == null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            Icons.Regular.ErrorCircle,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = Color(0xFFE57373)
                        )
                        Text(
                            errorMessage!!,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            color = FluentTheme.colors.text.text.secondary
                        )
                        Spacer(Modifier.height(4.dp))
                        Button(onClick = { loadData() }) {
                            Text("重试")
                        }
                    }
                }
            }
            pollConfig != null -> {
                Column(Modifier.fillMaxSize()) {
                    // 投票内容网格
                    Box(Modifier.weight(1f)) {
                        VotingGrid(
                            config = pollConfig!!,
                            voteState = voteState,
                            selectedNames = selectedNames,
                            isLoadingData = isLoadingData,
                            errorMessage = errorMessage,
                            onToggleCandidate = { name ->
                                selectedNames = if (name in selectedNames) {
                                    selectedNames - name
                                } else {
                                    selectedNames + name
                                }
                            }
                        )
                    }

                    // 底部提交栏
                    VotingBottomBar(
                        selectedCount = selectedNames.size,
                        voteLimit = pollConfig!!.voteLimit,
                        hasChanges = hasVoteChanges(voteState, selectedNames),
                        isSubmitting = isSubmitting,
                        onSubmit = {
                            val state = voteState ?: return@VotingBottomBar
                            scope.launch {
                                isSubmitting = true
                                statusMessage = null
                                when (val result = VotingApi.submitVotes(state, selectedNames)) {
                                    is VotingApi.ApiResult.Success -> {
                                        statusMessage = "投票提交成功！"
                                        // 刷新数据
                                        isLoadingData = true
                                        when (val refreshResult = VotingApi.fetchVoteData(state.config)) {
                                            is VotingApi.ApiResult.Success -> {
                                                voteState = refreshResult.value
                                                selectedNames = refreshResult.value.pollDataMap
                                                    .filter { it.value.userVoted }
                                                    .keys
                                            }
                                            is VotingApi.ApiResult.Error -> { /* ignore */ }
                                        }
                                        isLoadingData = false
                                    }
                                    is VotingApi.ApiResult.Error -> {
                                        statusMessage = "提交失败: ${result.message}"
                                    }
                                }
                                isSubmitting = false
                            }
                        }
                    )
                }
            }
        }
    }
}

// ────────────────────────────────────────────
//  投票内容网格
// ────────────────────────────────────────────

@OptIn(ExperimentalFluentApi::class)
@Composable
private fun VotingGrid(
    config: VotingApi.PollConfig,
    voteState: VotingApi.VoteState?,
    selectedNames: Set<String>,
    isLoadingData: Boolean,
    errorMessage: String?,
    onToggleCandidate: (String) -> Unit
) {
    val previewTotal = if (voteState != null) {
        var total = voteState.totalParticipants
        if (!voteState.userVotedTotal && selectedNames.isNotEmpty()) total += 1
        else if (voteState.userVotedTotal && selectedNames.isEmpty()) total -= 1
        total
    } else 0

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 140.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ── 顶部信息区 ──
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 提示信息
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(
                            "本投票不设置结束时间和票数限制",
                            "投票的时装会随游戏更新增加",
                            "可以同时投给多个时装且投票可重新提交"
                        ).forEach { hint ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Regular.Info,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = FluentTheme.colors.text.text.secondary
                                )
                                Text(
                                    hint,
                                    fontSize = 12.sp,
                                    color = FluentTheme.colors.text.text.secondary
                                )
                            }
                        }
                    }
                }

                // 错误消息（仅数据层错误，不是致命的）
                if (errorMessage != null && voteState == null) {
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Regular.Warning,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color(0xFFFF9800)
                            )
                            Text(
                                errorMessage,
                                fontSize = 12.sp,
                                color = Color(0xFFFF9800)
                            )
                        }
                    }
                }

                // 投票统计
                if (voteState != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Regular.People,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = FluentTheme.colors.fillAccent.default
                        )
                        Text(
                            "累计投票人数：$previewTotal",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = FluentTheme.colors.fillAccent.default
                        )
                    }
                }

                // 数据加载中
                if (isLoadingData) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ProgressRing(modifier = Modifier.size(14.dp))
                        Text(
                            "正在加载投票数据…",
                            fontSize = 12.sp,
                            color = FluentTheme.colors.text.text.secondary
                        )
                    }
                }
            }
        }

        // ── 候选项卡片 ──
        items(config.candidates, key = { it.name }) { candidate ->
            val isSelected = candidate.name in selectedNames
            val pollData = voteState?.pollDataMap?.get(candidate.name)
            val previewVotes = if (pollData != null) {
                var v = pollData.votes
                if (isSelected && !pollData.userVoted) v += 1
                if (!isSelected && pollData.userVoted) v -= 1
                v
            } else null
            val rate = if (previewVotes != null && previewTotal > 0) {
                (previewVotes.toFloat() / previewTotal * 100)
            } else null

            CandidateCard(
                name = candidate.name,
                imageUrl = candidate.imageUrl,
                votes = previewVotes,
                rate = rate,
                isSelected = isSelected,
                onClick = { onToggleCandidate(candidate.name) }
            )
        }

        // 底部留白
        item(span = { GridItemSpan(maxLineSpan) }) {
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ────────────────────────────────────────────
//  候选卡片（Fluent UI 风格）
// ────────────────────────────────────────────

@OptIn(ExperimentalFluentApi::class)
@Composable
private fun CandidateCard(
    name: String,
    imageUrl: String,
    votes: Int?,
    rate: Float?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val accentColor = FluentTheme.colors.fillAccent.default
    val borderColor = if (isSelected) accentColor else FluentTheme.colors.stroke.card.default
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(borderWidth, borderColor, RoundedCornerShape(6.dp))
    ) {
        Column {
            // 图片区域
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
            ) {
                NetworkImage(
                    url = imageUrl,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // 选中指示器
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(22.dp)
                            .background(accentColor, RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Regular.Checkmark,
                            contentDescription = "已选",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // 信息区域
            Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = name,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) accentColor else FluentTheme.colors.text.text.primary,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )

                if (votes != null) {
                    Text(
                        text = if (rate != null) "支持 $votes（${"%.1f".format(rate)}%）"
                        else "支持 $votes",
                        fontSize = 11.sp,
                        color = if (isSelected) accentColor else FluentTheme.colors.text.text.secondary,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Text(
                        text = "加载中…",
                        fontSize = 11.sp,
                        color = FluentTheme.colors.text.text.secondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

// ────────────────────────────────────────────
//  底部提交栏
// ────────────────────────────────────────────

@OptIn(ExperimentalFluentApi::class)
@Composable
private fun VotingBottomBar(
    selectedCount: Int,
    voteLimit: Int,
    hasChanges: Boolean,
    isSubmitting: Boolean,
    onSubmit: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(FluentTheme.colors.background.layer.default)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            Icons.Regular.Vote,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = FluentTheme.colors.fillAccent.default
        )
        Text(
            text = "已选 $selectedCount 项",
            fontSize = 13.sp,
            color = FluentTheme.colors.text.text.primary,
            modifier = Modifier.weight(1f)
        )

        if (isSubmitting) {
            ProgressRing(modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("提交中…", fontSize = 12.sp, color = FluentTheme.colors.text.text.secondary)
        } else {
            AccentButton(
                onClick = onSubmit,
                disabled = !hasChanges || isSubmitting
            ) {
                Icon(Icons.Regular.Vote, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (hasChanges) "提交投票" else "无变更", fontSize = 12.sp)
            }
        }
    }
}

// ────────────────────────────────────────────
//  工具函数
// ────────────────────────────────────────────

private fun hasVoteChanges(
    voteState: VotingApi.VoteState?,
    selectedNames: Set<String>
): Boolean {
    if (voteState == null) return false
    for (candidate in voteState.config.candidates) {
        val wasVoted = voteState.pollDataMap[candidate.name]?.userVoted ?: false
        val isSelected = candidate.name in selectedNames
        if (wasVoted != isSelected) return true
    }
    return false
}
