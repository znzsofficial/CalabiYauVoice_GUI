package com.nekolaska.calabiyau.feature.wiki.voting.api

import com.nekolaska.calabiyau.feature.wiki.voting.model.PollData
import com.nekolaska.calabiyau.feature.wiki.voting.model.PollConfig
import com.nekolaska.calabiyau.feature.wiki.voting.model.VoteState
import com.nekolaska.calabiyau.feature.wiki.voting.parser.VotingParsers
import com.nekolaska.calabiyau.feature.wiki.voting.source.VoteSubmitOperation
import com.nekolaska.calabiyau.feature.wiki.voting.source.VotingRemoteSource
import data.ApiResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Wiki 投票 API。 */
object VotingApi {

    private const val VOTE_PAGE = "角色时装投票"

    suspend fun fetchPollConfig(): ApiResult<PollConfig> = withContext(Dispatchers.IO) {
        try {
            val html = VotingRemoteSource.fetchVotePageHtml(VOTE_PAGE)
                ?: return@withContext ApiResult.Error("无法解析页面内容")

            val config = VotingParsers.parsePollConfigFromHtml(html)
                ?: return@withContext ApiResult.Error("未找到投票组件")
            ApiResult.Success(config)
        } catch (e: Exception) {
            ApiResult.Error("获取投票页失败: ${e.message}")
        }
    }

    suspend fun fetchVoteData(config: PollConfig): ApiResult<VoteState> = withContext(Dispatchers.IO) {
        val cookies = VotingRemoteSource.getWikiCookies()
        if (cookies.isNullOrBlank()) {
            return@withContext ApiResult.Error("未检测到登录 Cookie，请先在 Wiki 页面登录")
        }

        try {
            val pollTexts = config.candidates.map { candidate ->
                "<poll show-results-before-voting=1>\n${config.name}-${candidate.name}\n投给TA\n</poll>"
            }
            val totalPollText = "<poll show-results-before-voting=1>\n${config.name}-总计锚点\n已参加\n</poll>"
            val fullText = (pollTexts + totalPollText).joinToString("\n")

            val html = VotingRemoteSource.fetchVoteDataHtml(fullText, cookies)
                ?: return@withContext ApiResult.Error("无法解析投票数据")

            val pollDataList = VotingParsers.parseAjaxPollElements(html)
            if (pollDataList.isEmpty()) {
                return@withContext ApiResult.Error("服务器未返回投票数据，可能被拦截")
            }

            val pollDataMap = mutableMapOf<String, PollData>()
            config.candidates.forEachIndexed { index, candidate ->
                if (index < pollDataList.size) {
                    pollDataMap[candidate.name] = pollDataList[index]
                }
            }

            val totalData = if (pollDataList.size > config.candidates.size) {
                pollDataList.last()
            } else null

            ApiResult.Success(
                VoteState(
                    config = config,
                    totalParticipants = totalData?.votes ?: 0,
                    totalPollId = totalData?.pollId ?: "",
                    userVotedTotal = totalData?.userVoted ?: false,
                    pollDataMap = pollDataMap
                )
            )
        } catch (e: Exception) {
            ApiResult.Error("获取投票数据失败: ${e.message}")
        }
    }

    suspend fun submitVotes(
        voteState: VoteState,
        selectedNames: Set<String>
    ): ApiResult<Unit> = withContext(Dispatchers.IO) {
        val cookies = VotingRemoteSource.getWikiCookies()
        if (cookies.isNullOrBlank()) {
            return@withContext ApiResult.Error("未登录")
        }

        try {
            val token = VotingRemoteSource.fetchCsrfToken(cookies)
                ?: return@withContext ApiResult.Error("获取 CSRF Token 失败")

            val operations = mutableListOf<VoteSubmitOperation>()

            for (candidate in voteState.config.candidates) {
                val data = voteState.pollDataMap[candidate.name] ?: continue
                val isSelected = candidate.name in selectedNames
                if (isSelected && !data.userVoted) {
                    operations.add(VoteSubmitOperation(data.pollId, "1"))
                } else if (!isSelected && data.userVoted) {
                    operations.add(VoteSubmitOperation(data.pollId, "0"))
                }
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

            if (operations.isEmpty()) {
                return@withContext ApiResult.Success(Unit)
            }

            for (operation in operations) {
                val statusCode = VotingRemoteSource.submitVoteOperation(cookies, token, operation)
                if (statusCode !in 200..299) {
                    return@withContext ApiResult.Error("提交投票失败: HTTP $statusCode")
                }
            }

            ApiResult.Success(Unit)
        } catch (e: Exception) {
            ApiResult.Error("提交投票失败: ${e.message}")
        }
    }
}
