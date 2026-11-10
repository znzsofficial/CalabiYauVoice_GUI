package com.nekolaska.calabiyau.feature.wiki.extraction.model

import kotlinx.serialization.Serializable

@Serializable
internal enum class DropTask(val title: String) { ITEMS("找物品"), SOURCES("查来源") }

@Serializable
internal data class DropLocation(
    val task: DropTask = DropTask.ITEMS,
    val reference: ExtractionDropView? = null,
    val itemName: String? = null,
    val itemQuality: String? = null,
    val sourceId: String? = null,
    val containerId: String? = null,
    val linkedPool: Boolean = false,
    val query: String = "",
    val poolQuality: String? = null
) {
    val hasDetail: Boolean get() = sourceId != null || containerId != null
    val contentKey: String get() = if (reference != null) "reference:$reference"
        else if (task == DropTask.ITEMS && itemName != null) "item:${itemName.length}:$itemName:$itemQuality"
        else task.name
}

/** Each exploratory hop keeps its exact origin, rather than reconstructing it from the active tab. */
@Serializable
internal data class DropNavigation(val history: List<DropLocation> = listOf(DropLocation())) {
    val current: DropLocation get() = history.last()
    val canGoBack: Boolean get() = history.size > 1
    // A detail overlays its originating list. Re-entering the same item through another pool is
    // a new list context, not permission to overwrite the filters/scroll of that earlier visit.
    val contentStatePath: List<DropLocation>
        get() = history.take((history.indexOfLast { !it.hasDetail } + 1).coerceAtLeast(1))
    fun back(): DropNavigation = if (canGoBack) copy(history = history.dropLast(1)) else this
    fun selectTask(task: DropTask): DropNavigation = DropNavigation(listOf(DropLocation(task = task)))
    private fun push(location: DropLocation): DropNavigation =
        if (current == location) this else copy(history = history + location)

    fun openItem(item: ExtractionDropItem): DropNavigation = push(DropLocation(
        task = DropTask.ITEMS, itemName = item.name, itemQuality = item.qualityLabel))

    fun openSource(id: String, query: String = ""): DropNavigation = push(current.copy(
        sourceId = id, containerId = null, linkedPool = false, query = query, poolQuality = null))

    fun openContainer(id: String, query: String = "", quality: String? = null): DropNavigation = push(current.copy(
        sourceId = null, containerId = id, linkedPool = false, query = query, poolQuality = quality))

    fun openLinkedPool(): DropNavigation = if (current.sourceId == null) this
        else push(current.copy(linkedPool = true))

    fun openReference(view: ExtractionDropView = ExtractionDropView.POOLS): DropNavigation =
        push(DropLocation(task = current.task, reference = view))

    fun selectReference(view: ExtractionDropView): DropNavigation =
        copy(history = history.dropLast(1) + current.copy(reference = view))
}

internal data class DropSourceRecord(
    val entry: ExtractionEntry, val isContainer: Boolean,
    val sourceNotes: ExtractionEntry? = null
) {
    /** A search hit in the linked notes opens those notes, never an unexplained container result. */
    fun matchingRecord(query: String): DropSourceRecord? = when {
        entry.matches(query) -> this
        sourceNotes?.matches(query) == true -> DropSourceRecord(sourceNotes, false)
        else -> null
    }
}

/** Suppress a duplicate source only when the immutable container ID has a unique counterpart both ways. */
internal fun dropSourceRecords(containers: List<ExtractionEntry>, catalog: List<ExtractionEntry>): List<DropSourceRecord> {
    val sources = extractionEntriesForView(catalog, ExtractionDropView.SOURCES)
    val containerCounts = containers.mapNotNull { it.containerId }.groupingBy { it }.eachCount()
    val sourceCounts = sources.mapNotNull { it.containerId }.groupingBy { it }.eachCount()
    return containers.map { container ->
        DropSourceRecord(container, true, uniqueDropSourceNotes(container, catalog)
            .takeIf { containerCounts[container.containerId] == 1 })
    } + sources.filter { source ->
        source.containerId == null || containerCounts[source.containerId] != 1 || sourceCounts[source.containerId] != 1
    }.map { DropSourceRecord(it, false) }
}

internal fun uniqueDropSourceNotes(container: ExtractionEntry, catalog: List<ExtractionEntry>): ExtractionEntry? =
    container.containerId?.let { id ->
        catalog.singleOrNull { it.type == ExtractionEntryType.DROP_SOURCE && it.containerId == id }
    }
