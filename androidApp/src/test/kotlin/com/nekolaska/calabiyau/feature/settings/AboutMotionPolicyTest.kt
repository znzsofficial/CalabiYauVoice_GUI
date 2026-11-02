package com.nekolaska.calabiyau.feature.settings

import androidx.lifecycle.Lifecycle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AboutMotionPolicyTest {
    @Test
    fun onlyResumedPagesAnimateAtPositiveFiniteSystemScales() {
        val scales = listOf(-1f, 0f, 0.5f, 1f, 10f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)
        for (state in Lifecycle.State.entries) {
            for (scale in scales) {
                assertEquals(
                    scale.isFinite() && scale > 0f && state == Lifecycle.State.RESUMED,
                    aboutMeshMotionEnabled(scale, state),
                    "scale=$scale, state=$state"
                )
            }
        }
        // STARTED may be covered by another route/dialog: do not keep decorative frame work alive.
        assertFalse(aboutMeshMotionEnabled(1f, Lifecycle.State.STARTED))
    }

    @Test
    fun initiallyDisabledMotionCanResumeAndPauseRepeatedly() {
        val changes = listOf(
            0f to Lifecycle.State.RESUMED,
            1f to Lifecycle.State.RESUMED,
            1f to Lifecycle.State.STARTED,
            0f to Lifecycle.State.RESUMED,
            0.5f to Lifecycle.State.RESUMED,
            0.5f to Lifecycle.State.CREATED,
            0.5f to Lifecycle.State.RESUMED
        )
        assertEquals(
            listOf(false, true, false, false, true, false, true),
            changes.map { (scale, state) -> aboutMeshMotionEnabled(scale, state) }
        )
    }
}
