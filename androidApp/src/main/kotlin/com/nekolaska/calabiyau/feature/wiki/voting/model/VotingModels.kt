package com.nekolaska.calabiyau.feature.wiki.voting.model

import com.nekolaska.calabiyau.core.wiki.CookieSessionGuard

/** Non-persisted capability bound to the verified Wiki account and original browser login. */
class VoteSession internal constructor(internal val name: String, internal val request: () -> CookieSessionGuard.Ticket?)

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
    val userVoted: Boolean,
    val question: String? = null
)

/** 完整投票状态 */
data class VoteState(
    val config: PollConfig,
    val totalParticipants: Int,
    val totalPollId: String,
    val userVotedTotal: Boolean,
    val pollDataMap: Map<String, PollData>,
    /** Authenticated poll selections belong to the session that read them, even after an optimistic copy. */
    val session: VoteSession? = null
)
