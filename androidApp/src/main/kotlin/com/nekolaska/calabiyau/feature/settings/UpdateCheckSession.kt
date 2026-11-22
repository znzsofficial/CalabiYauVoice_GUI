package com.nekolaska.calabiyau.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

internal val LocalUpdateCheckSession = staticCompositionLocalOf<UpdateCheckSession> { error("Missing update session") }

/** Activity-retained metadata only. After process death a new check must rediscover the offer. */
internal class UpdateCheckSession : ViewModel() {
    var available by mutableStateOf<UpdateApi.UpdateInfo?>(null)
    val install = UpdateInstallFlight()
    private var startup: Deferred<UpdateApi.Result>? = null
    private var claimedBySettings = false

    suspend fun checkAtStartup(read: suspend () -> UpdateApi.Result, publish: (UpdateApi.Result) -> Unit) = coroutineScope {
        if (claimedBySettings) return@coroutineScope
        val request = async(start = CoroutineStart.LAZY) { read() }
        startup = request
        try {
            val result = request.await()
            if (startup === request && !claimedBySettings) publish(result)
        } finally {
            if (startup === request) startup = null
        }
    }

    // Claim on the tap, before recomposition starts the page effect. A dismissed or abandoned
    // manual check must not be replayed by the older silent check, even if its reply arrives last.
    fun manualRequest(read: suspend () -> UpdateApi.Result): (suspend () -> UpdateApi.Result)? {
        if (available != null) return null
        claimedBySettings = true
        val pending = startup?.takeUnless { it.isCancelled } ?: return read
        return { pending.await() }
    }
}

/** Main-thread gate: preparation is page-cancellable; after launch only the system return unlocks it. */
internal class UpdateInstallFlight {
    var busy by mutableStateOf(false)
        private set

    suspend fun launch(openInstaller: suspend () -> Boolean) {
        if (busy) return
        busy = true
        var launched = false
        try {
            launched = openInstaller()
        } finally {
            if (!launched) busy = false
        }
    }

    fun returned() { busy = false }
}
