package com.nekolaska.calabiyau.feature.wiki.bio

import androidx.compose.runtime.*
import com.nekolaska.calabiyau.feature.wiki.bio.api.BioDeckShareApi
import com.nekolaska.calabiyau.feature.wiki.bio.api.BioCardApi
import com.nekolaska.calabiyau.feature.wiki.bio.model.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import data.ApiResult

internal class BioDeckSubmissionState(
    private val invalidate: () -> Unit = BioCardApi::invalidateDeck,
    private val submit: suspend (SubmitDeckPayload, Map<String, List<DeckCardOption>>) -> ApiResult<SubmitDeckResult> = BioDeckShareApi::submitDeck
) {
    var isSubmitting by mutableStateOf(false)
        private set
    suspend fun publish(payload: SubmitDeckPayload, cards: Map<String, List<DeckCardOption>>): ApiResult<SubmitDeckResult>? {
        if (isSubmitting) return null
        isSubmitting = true
        return try {
            invalidate()
            submit(payload, cards).also { currentCoroutineContext().ensureActive() }
        } finally {
            // Even an uncertain/cancelled write must not leave a stale deck-list memory hit.
            invalidate()
            isSubmitting = false
        }
    }
}
