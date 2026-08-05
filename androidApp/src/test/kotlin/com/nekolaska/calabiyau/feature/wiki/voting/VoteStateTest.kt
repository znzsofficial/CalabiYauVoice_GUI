package com.nekolaska.calabiyau.feature.wiki.voting

import com.nekolaska.calabiyau.feature.wiki.voting.model.PollCandidate
import com.nekolaska.calabiyau.feature.wiki.voting.model.PollConfig
import com.nekolaska.calabiyau.feature.wiki.voting.model.PollData
import com.nekolaska.calabiyau.feature.wiki.voting.model.VoteState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VoteStateTest {

    @Test
    fun selectingFirstCandidateAddsParticipantAndVote() {
        val updated = voteState().withSelectedNames(setOf("A"))

        assertEquals(11, updated.totalParticipants)
        assertTrue(updated.userVotedTotal)
        assertEquals(4, updated.pollDataMap.getValue("A").votes)
        assertTrue(updated.pollDataMap.getValue("A").userVoted)
    }

    @Test
    fun switchingCandidateKeepsParticipantCount() {
        val updated = voteState(
            totalParticipants = 10,
            userVotedTotal = true,
            a = PollData("a", votes = 3, userVoted = true)
        ).withSelectedNames(setOf("B"))

        assertEquals(10, updated.totalParticipants)
        assertEquals(2, updated.pollDataMap.getValue("A").votes)
        assertFalse(updated.pollDataMap.getValue("A").userVoted)
        assertEquals(6, updated.pollDataMap.getValue("B").votes)
        assertTrue(updated.pollDataMap.getValue("B").userVoted)
    }

    @Test
    fun clearingSelectionRemovesParticipantWithoutGoingNegative() {
        val updated = voteState(
            totalParticipants = 0,
            userVotedTotal = true,
            a = PollData("a", votes = 0, userVoted = true)
        ).withSelectedNames(emptySet())

        assertEquals(0, updated.totalParticipants)
        assertFalse(updated.userVotedTotal)
        assertEquals(0, updated.pollDataMap.getValue("A").votes)
        assertFalse(updated.pollDataMap.getValue("A").userVoted)
    }

    @Test
    fun missingTotalPollDoesNotChangeParticipantState() {
        val updated = voteState(totalPollId = "").withSelectedNames(setOf("A"))

        assertEquals(10, updated.totalParticipants)
        assertFalse(updated.userVotedTotal)
        assertEquals(4, updated.pollDataMap.getValue("A").votes)
        assertTrue(updated.pollDataMap.getValue("A").userVoted)
    }

    private fun voteState(
        totalParticipants: Int = 10,
        userVotedTotal: Boolean = false,
        totalPollId: String = "total",
        a: PollData = PollData("a", votes = 3, userVoted = false),
    ) = VoteState(
        config = PollConfig(
            name = "test",
            voteLimit = 2,
            endTime = "",
            candidates = listOf(
                PollCandidate("A", ""),
                PollCandidate("B", "")
            )
        ),
        totalParticipants = totalParticipants,
        totalPollId = totalPollId,
        userVotedTotal = userVotedTotal,
        pollDataMap = mapOf(
            "A" to a,
            "B" to PollData("b", votes = 5, userVoted = false)
        )
    )
}
