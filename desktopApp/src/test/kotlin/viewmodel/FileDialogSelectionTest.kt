package viewmodel

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.*

class FileDialogSelectionTest {
    @Test
    fun loadingFailureAndStaleConfirmationsCannotReplaceManualSelection() = runBlocking {
        val files = listOf("一.mp3" to "url-1", "二.mp3" to "url-2")
        var response = CompletableDeferred(files)
        val vm = MainViewModel(this) { _, _ -> response.await() }
        vm.onSearchKeywordChange("") // No network search in this scenario.
        val cat = "Category:语音"
        fun open(): Long {
            vm.openFileDialog(cat)
            return assertNotNull(vm.fileDialog.value).requestId
        }
        val first = open()
        yield()
        vm.confirmFileDialog(first, setOf("url-2"))
        val saved = listOf(files.last())
        assertEquals(saved, vm.manualSelectionMap.value[cat])

        response = CompletableDeferred()
        val failed = open()
        vm.confirmFileDialog(failed, emptySet())
        assertIs<FileDialogContent.Loading>(vm.fileDialog.value?.content)
        response.completeExceptionally(java.io.IOException("offline"))
        yield()
        assertIs<FileDialogContent.Failed>(vm.fileDialog.value?.content)
        vm.confirmFileDialog(failed, emptySet())
        assertEquals(saved, vm.manualSelectionMap.value[cat])
        assertEquals(2, vm.categoryTotalCountMap.value[cat])

        response = CompletableDeferred(files)
        open()
        yield()
        assertEquals(setOf("url-2"), assertIs<FileDialogContent.Ready>(vm.fileDialog.value?.content).initialUrls)
        vm.confirmFileDialog(failed, emptySet())
        assertEquals(saved, vm.manualSelectionMap.value[cat])

        response = CompletableDeferred(emptyList())
        val empty = open()
        yield()
        vm.confirmFileDialog(empty, emptySet())
        assertEquals(emptyList(), vm.manualSelectionMap.value[cat])
        assertNull(vm.fileDialog.value)

        response = CompletableDeferred(files)
        val oldGroup = open()
        yield()
        vm.onSelectGroup(data.CharacterGroup("新角色", "Category:新角色", emptyList()))
        coroutineContext.cancelChildren() // The group scan is not part of this local test.
        vm.confirmFileDialog(oldGroup, setOf("url-1"))
        assertNull(vm.fileDialog.value)
        assertTrue(cat !in vm.checkedCategories.value)
    }
}
