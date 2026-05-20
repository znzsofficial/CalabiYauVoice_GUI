package com.nekolaska.calabiyau.feature.wiki.voting.model

/** 投票配置（从页面 HTML 解析） */
data class PollConfig(
    val name: String,
    val voteLimit: Int,
    val endTime: String,
    val candidates: List<PollCandidate>
)

/** 单个候选项 */
data class PollCandidate(
    val name: String,
    val imageUrl: String
)

/** 投票数据（从 AJAXPoll 解析） */
data class PollData(
    val pollId: String,
    val votes: Int,
    val userVoted: Boolean
)

/** 完整投票状态 */
data class VoteState(
    val config: PollConfig,
    val totalParticipants: Int,
    val totalPollId: String,
    val userVotedTotal: Boolean,
    val pollDataMap: Map<String, PollData>
)
