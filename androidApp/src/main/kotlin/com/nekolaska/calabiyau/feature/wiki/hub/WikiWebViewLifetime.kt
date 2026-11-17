package com.nekolaska.calabiyau.feature.wiki.hub

/** UI-thread owner. A retired view can neither navigate its replacement nor recover the screen. */
internal class WikiWebViewLifetime<V : Any>(
    private val setResumed: (V, Boolean) -> Unit = { _, _ -> },
    private val destroy: (V, Boolean) -> Unit) {
    private var resumed = true
    fun setHostResumed(value: Boolean) {
        resumed = value
        main?.let { setResumed(it, value) }
        popupSnapshot.forEach { setResumed(it, value) }
    }
    @Volatile private var main: V? = null
    private val popups = java.util.IdentityHashMap<V, () -> Unit>()
    @Volatile private var popupSnapshot: List<V> = emptyList()
    @Volatile private var closed = false
    fun attach(view: V) {
        check(!closed)
        main?.takeIf { it !== view }?.let { release(it) }
        main = view
        setResumed(view, resumed)
    }
    fun isMain(view: V?): Boolean = !closed && view != null && main === view
    fun isPopup(view: V): Boolean = !closed && popupSnapshot.any { it === view }
    fun addPopup(parent: V, popup: V, onClose: () -> Unit = {}): Boolean {
        if (!isMain(parent) || popups.size >= 4) { destroy(popup, false); return false }
        popups[popup] = onClose
        popupSnapshot = popups.keys.toList()
        setResumed(popup, resumed)
        return true
    }
    fun closePopup(view: V, rendererGone: Boolean = false) {
        popups.remove(view)?.let {
            popupSnapshot = popups.keys.toList()
            it(); destroy(view, rendererGone)
        }
    }
    fun release(view: V, rendererGone: Boolean = false) {
        if (main !== view) return
        main = null
        popups.keys.toList().forEach { closePopup(it, rendererGone) }
        destroy(view, rendererGone)
    }
    fun close() {
        main?.let { release(it) }
        closed = true
    }
}
