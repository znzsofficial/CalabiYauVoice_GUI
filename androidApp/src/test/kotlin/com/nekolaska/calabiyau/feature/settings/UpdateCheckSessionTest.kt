package com.nekolaska.calabiyau.feature.settings

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdateCheckSessionTest {
    private val newer = UpdateApi.Result.NewVersion(UpdateApi.UpdateInfo("v3", "3", 67L, "", "", null))

    @Test fun manualTapClaimsSlowStartupBeforeThePageEffectRuns() = runBlocking {
        val session = UpdateCheckSession()
        val started = CompletableDeferred<Unit>()
        val reply = CompletableDeferred<UpdateApi.Result>()
        var reads = 0
        val silent = launch {
            session.checkAtStartup({ reads++; started.complete(Unit); reply.await() }) { error("stale startup prompt") }
        }
        started.await()
        val manual = checkNotNull(session.manualRequest { error("duplicate GET") })
        reply.complete(newer)
        silent.join()
        // Until the page consumes the result (or if it left), there must be no prompt.
        assertNull(session.available)
        assertEquals(newer, manual())
        assertEquals(1, reads)
    }

    @Test fun startupWinsBeforeTapAndDismissalAllowsAFreshManualCheck() = runBlocking {
        val session = UpdateCheckSession()
        session.checkAtStartup({ newer }) { session.available = (it as UpdateApi.Result.NewVersion).info }
        assertEquals(newer.info, session.available)
        assertNull(session.manualRequest { error("already showing") })
        session.available = null
        val manual = checkNotNull(session.manualRequest { UpdateApi.Result.AlreadyLatest })
        assertEquals(UpdateApi.Result.AlreadyLatest, manual())
    }

    @Test fun retiringActivityCannotClearReplacementRequest() = runBlocking {
        val session = UpdateCheckSession()
        val old = launch(start = CoroutineStart.UNDISPATCHED) {
            session.checkAtStartup({ awaitCancellation() }) { error("retiring prompt") }
        }
        old.cancel()
        val reply = CompletableDeferred<UpdateApi.Result>()
        val replacement = launch(start = CoroutineStart.UNDISPATCHED) {
            session.checkAtStartup({ reply.await() }) { error("claimed prompt") }
        }
        old.join() // Run the old finally after installation of the replacement.
        val manual = checkNotNull(session.manualRequest { error("lost replacement GET") })
        reply.complete(newer)
        assertEquals(newer, manual())
        replacement.join()
    }

    @Test fun installHasOneWriteAndLaunchUntilActualReturnAcrossActivityRecreation() = runBlocking {
        val factory = viewModelFactory { initializer { UpdateCheckSession() } }
        fun activity(store: ViewModelStore) = ViewModelProvider.create(store, factory)[UpdateCheckSession::class]
        val store = ViewModelStore()
        val first = activity(store)
        first.available = newer.info
        val written = CompletableDeferred<Unit>()
        var writes = 0
        var launches = 0
        suspend fun tap(session: UpdateCheckSession) = session.install.launch { writes++; written.await(); launches++; true }
        val preparation = launch(start = CoroutineStart.UNDISPATCHED) { tap(first) }
        tap(first) // during IO; must not even write the marker twice
        written.complete(Unit)
        preparation.join()
        val recreated = activity(store)
        assertEquals(newer.info, recreated.available)
        tap(recreated) // still in the system installer
        assertEquals(1 to 1, writes to launches)
        recreated.install.returned() // RESULT_CANCELED is a return, not an installed-version signal
        tap(recreated)
        assertEquals(2 to 2, writes to launches)
        val fresh = activity(ViewModelStore())
        assertNull(fresh.available)
        assertFalse(fresh.install.busy)
    }

    @Test fun cancellingBeforeInstallerLaunchReleasesGateWithoutWaitingForAnActivityResult() = runBlocking {
        val flight = UpdateInstallFlight()
        val preparing = launch(start = CoroutineStart.UNDISPATCHED) { flight.launch { awaitCancellation() } }
        assertTrue(flight.busy)
        preparing.cancelAndJoin()
        assertFalse(flight.busy)
        flight.launch { false } // e.g. unresolvable installer intent
        assertFalse(flight.busy)
    }
}
