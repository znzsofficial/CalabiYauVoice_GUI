package com.nekolaska.calabiyau.core.wiki

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** One original session for token acquisition and every individual mutation in the batch. */
internal class WikiWriteOwner(private val isCurrent: () -> Boolean) {
    suspend fun <T> run(operation: suspend () -> T): T {
        currentCoroutineContext().ensureActive()
        check(isCurrent()) { "登录状态已变化，请重新确认" }
        return operation()
    }

    companion object {
        fun capture(cookies: String): WikiWriteOwner {
            val ticket = WikiAuthHelper.beginCookieRequest(cookies)
            return WikiWriteOwner { WikiAuthHelper.isCurrentRequest(ticket) }
        }
    }
}
