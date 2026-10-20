package com.nekolaska.calabiyau.feature.wiki.hub

/** One physical picker at a time. Invalidating a callback retains a tombstone until its result arrives. */
internal class WikiFileChooserState<O : Any, T>(
    restoredPending: Boolean = false,
    private val currentOwner: (O) -> Boolean,
    private val pendingChanged: (Boolean) -> Unit = {}
) {
    private data class Request<O, T>(val id: Long, val owner: O, var callback: ((T?) -> Unit)?)
    private var pending = restoredPending
    private var nextId = 0L
    private var active: Request<O, T>? = null
    private var disposed = false

    fun begin(owner: O, callback: (T?) -> Unit): Long? {
        if (disposed || pending || !currentOwner(owner)) { runCatching { callback(null) }; return null }
        val id = ++nextId
        active = Request(id, owner, callback)
        pending = true; pendingChanged(true)
        return id
    }
    fun complete(value: T?) {
        if (disposed) return
        val request = active
        active = null; pending = false; pendingChanged(false)
        request?.callback?.let { callback -> runCatching { callback(value.takeIf { currentOwner(request.owner) }) } }
    }
    fun launchFailed(id: Long) { if (active?.id == id) complete(null) }
    fun invalidate(owner: O) {
        active?.takeIf { it.owner === owner }?.let { request ->
            val callback = request.callback; request.callback = null
            runCatching { callback?.invoke(null) }
        }
    }
    fun dispose() {
        active?.let { invalidate(it.owner) }
        disposed = true
        // Do not reset the saved pending flag: ActivityResult may arrive after recreation.
    }
}
