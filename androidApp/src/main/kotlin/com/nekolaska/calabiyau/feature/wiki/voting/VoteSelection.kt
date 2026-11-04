package com.nekolaska.calabiyau.feature.wiki.voting

import com.nekolaska.calabiyau.feature.wiki.voting.model.VoteState

internal fun hasVoteChanges(voteState: VoteState?, selectedNames: Set<String>): Boolean =
    voteState?.config?.candidates?.any { (name) ->
        (voteState.pollDataMap[name]?.userVoted ?: false) != (name in selectedNames)
    } ?: false

internal fun VoteState.withSelectedNames(selectedNames: Set<String>): VoteState {
    val updatedPollData = pollDataMap.mapValues { (name, data) ->
        val selected = name in selectedNames
        val voteDelta = when {
            selected && !data.userVoted -> 1
            !selected && data.userVoted -> -1
            else -> 0
        }
        data.copy(votes = (data.votes + voteDelta).coerceAtLeast(0), userVoted = selected)
    }
    if (totalPollId.isEmpty()) return copy(pollDataMap = updatedPollData)
    val selectedAny = selectedNames.isNotEmpty()
    val participantDelta = when {
        selectedAny && !userVotedTotal -> 1
        !selectedAny && userVotedTotal -> -1
        else -> 0
    }
    return copy(totalParticipants = (totalParticipants + participantDelta).coerceAtLeast(0),
        userVotedTotal = selectedAny, pollDataMap = updatedPollData)
}
