package data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CancellationException
import util.runApiCatching

class ApiDslTest {

    @Test
    fun ioApiCallRethrowsCancellationException() {
        val exception = assertFailsWith<CancellationException> {
            kotlinx.coroutines.runBlocking {
                ioApiCall<Unit>("失败") {
                    throw CancellationException("cancelled")
                }
            }
        }

        assertEquals("cancelled", exception.message)
    }

    @Test
    fun runApiCatchingRethrowsCancellationException() {
        val exception = assertFailsWith<CancellationException> {
            runApiCatching<Unit> {
                throw CancellationException("cancelled")
            }
        }

        assertEquals("cancelled", exception.message)
    }
}
