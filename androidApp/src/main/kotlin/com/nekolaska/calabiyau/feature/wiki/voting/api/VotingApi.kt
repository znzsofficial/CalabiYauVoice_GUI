package com.nekolaska.calabiyau.feature.wiki.voting.api

import com.nekolaska.calabiyau.feature.wiki.voting.model.PollData
import com.nekolaska.calabiyau.feature.wiki.voting.model.PollConfig
import com.nekolaska.calabiyau.feature.wiki.voting.model.VoteState
import com.nekolaska.calabiyau.feature.wiki.voting.model.VoteSession
import com.nekolaska.calabiyau.feature.wiki.voting.parser.VotingParsers
import com.nekolaska.calabiyau.feature.wiki.voting.source.VoteSubmitOperation
import com.nekolaska.calabiyau.feature.wiki.voting.source.VoteSubmitResult
import com.nekolaska.calabiyau.feature.wiki.voting.source.VotingRemoteSource
import data.ApiResult
import data.ioApiCall
import com.nekolaska.calabiyau.core.wiki.WikiWriteOwner
import com.nekolaska.calabiyau.core.wiki.WikiAuthHelper

object VotingApi {

    private const val VOTE_PAGE = "角色时装投票"

    suspend fun fetchPollConfig(): ApiResult<PollConfig> = ioApiCall("获取投票页失败") {
        val html = VotingRemoteSource.fetchVotePageHtml(VOTE_PAGE)
            ?: return@ioApiCall ApiResult.Error("无法解析页面内容")
        val config = VotingParsers.parsePollConfigFromHtml(html)
            ?: return@ioApiCall ApiResult.Error("未找到投票组件")
        ApiResult.Success(config)
    }

    suspend fun fetchVoteData(config: PollConfig): ApiResult<VoteState> = ioApiCall("获取投票数据失败") {
        val login = WikiAuthHelper.beginVoteLogin() ?: return@ioApiCall ApiResult.Error("登录 Wiki 后可投票")
        val ticket = WikiAuthHelper.voteRequest(login) ?: return@ioApiCall ApiResult.Error("账号已切换或退出，请刷新投票")
        val user = VotingRemoteSource.verifiedUser(ticket)
        if (!user.isLoggedIn || user.name.isBlank()) return@ioApiCall ApiResult.Error("登录 Wiki 后可投票")
        val session = VoteSession(user.name) { WikiAuthHelper.voteRequest(login) }

        val pollTexts = config.candidates.map { candidate ->
            "<poll show-results-before-voting=1>\n${config.name}-${candidate.name}\n投给TA\n</poll>"
        }
        val totalPollText = "<poll show-results-before-voting=1>\n${config.name}-总计锚点\n已参加\n</poll>"
        val fullText = (pollTexts + totalPollText).joinToString("\n")

        val html = VotingRemoteSource.fetchVoteDataHtml(fullText, session)
            ?: return@ioApiCall ApiResult.Error("无法解析投票数据")

        val pollDataList = VotingParsers.parseAjaxPollElements(html)
        if (session.request() == null) return@ioApiCall ApiResult.Error("账号已切换或退出，请刷新投票")
        bindPolls(config, pollDataList)?.let { ApiResult.Success(it.copy(session = session)) }
            ?: ApiResult.Error("投票数据不完整，请刷新后再试")
    }

    internal fun bindPolls(config: PollConfig, polls: List<PollData>): VoteState? {
        val names = config.candidates.map { it.name }
        if (names.isEmpty() || names.distinct().size != names.size || polls.size != names.size + 1 ||
            polls.any { it.pollId.isBlank() } || polls.distinctBy { it.pollId.lowercase() }.size != polls.size) return null
        val questions = (names + "总计锚点").map { "${config.name}-$it" }
        if (questions.distinct().size != questions.size || polls.indices.any { i ->
                polls[i].question?.let { it != questions[i] } == true }) return null
        val total = polls.last()
        return VoteState(config, total.votes, total.pollId, total.userVoted, names.zip(polls).toMap())
    }

    suspend fun submitVotes(voteState: VoteState, selectedNames: Set<String>): ApiResult<Unit> =
        submit(voteState, selectedNames, WikiWriteOwner { voteState.session?.request?.invoke() != null },
            { VotingRemoteSource.fetchCsrfToken(checkNotNull(voteState.session)) },
            { token, operation -> VotingRemoteSource.submitVoteOperation(checkNotNull(voteState.session), token, operation) })

    internal suspend fun submit(voteState: VoteState, selectedNames: Set<String>, owner: WikiWriteOwner,
        getToken: suspend () -> String?, write: suspend (String, VoteSubmitOperation) -> VoteSubmitResult): ApiResult<Unit> =
        ioApiCall("提交投票失败") {
            val candidateNames = voteState.config.candidates.map { it.name }.toSet()
            if (voteState.config.voteLimit > 0 && selectedNames.size > voteState.config.voteLimit)
                return@ioApiCall ApiResult.Error("超过本次投票票数上限")
            val ids = voteState.pollDataMap.values.map { it.pollId } + voteState.totalPollId
            if (candidateNames.size != voteState.config.candidates.size || voteState.pollDataMap.keys != candidateNames ||
                ids.any(String::isBlank) || ids.distinctBy { it.lowercase() }.size != ids.size || !selectedNames.all { it in candidateNames }) {
                return@ioApiCall ApiResult.Error("包含无效的投票候选项")
            }

            val token = owner.run { getToken() }
                ?: return@ioApiCall ApiResult.Error("获取 CSRF Token 失败")

            val operations = mutableListOf<VoteSubmitOperation>()
            for ((name) in voteState.config.candidates) {
                val data = voteState.pollDataMap.getValue(name)
                val isSelected = name in selectedNames
                if (isSelected && !data.userVoted) operations.add(VoteSubmitOperation(data.pollId, "1"))
                else if (!isSelected && data.userVoted) operations.add(VoteSubmitOperation(data.pollId, "0"))
            }

            if (selectedNames.isNotEmpty()) {
                if (!voteState.userVotedTotal && voteState.totalPollId.isNotEmpty()) {
                    operations.add(VoteSubmitOperation(voteState.totalPollId, "1"))
                }
            } else {
                if (voteState.userVotedTotal && voteState.totalPollId.isNotEmpty()) {
                    operations.add(VoteSubmitOperation(voteState.totalPollId, "0"))
                }
            }

            if (operations.isEmpty()) return@ioApiCall ApiResult.Success(Unit)

            for (operation in operations) {
                val submitResult = owner.run { write(token, operation) }
                if (!submitResult.success) {
                    val detail = submitResult.message?.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()
                    return@ioApiCall ApiResult.Error("提交投票失败: HTTP ${submitResult.statusCode}$detail")
                }
            }

            ApiResult.Success(Unit)
        }
}
