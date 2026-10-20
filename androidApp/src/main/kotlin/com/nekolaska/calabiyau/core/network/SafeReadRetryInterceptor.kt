package com.nekolaska.calabiyau.core.network

import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.InterruptedIOException
import kotlin.random.Random

/** Never replay mutations. Check cancellation during backoff instead of sleeping for 8 seconds. */
internal class SafeReadRetryInterceptor(
    private val pause: (Long) -> Unit = Thread::sleep,
    private val jitter: () -> Long = { Random.nextLong(0, 500) }
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val readOnly = original.method in setOf("GET", "HEAD")
        // OkHttp's own follow-ups (e.g. 503 Retry-After: 0) must not replay mutation bodies either.
        val body = original.body
        val request = if (!readOnly && body?.isOneShot() != true) {
            original.newBuilder().method(original.method, object : RequestBody() {
                override fun contentType() = body?.contentType()
                override fun contentLength() = body?.contentLength() ?: 0L
                override fun isOneShot() = true
                override fun isDuplex() = body?.isDuplex() ?: false
                override fun writeTo(sink: BufferedSink) { body?.writeTo(sink) }
            }).build()
        } else original
        var response = chain.proceed(request)
        if (!readOnly) return response
        var retries = 0
        while (response.code in setOf(429, 403, 503) && retries < 3) {
            response.close()
            var remaining = (1000L shl ++retries) + jitter()
            while (remaining > 0) {
                if (chain.call().isCanceled()) throw InterruptedIOException("Canceled")
                val step = minOf(remaining, 100L)
                pause(step)
                remaining -= step
            }
            if (chain.call().isCanceled()) throw InterruptedIOException("Canceled")
            response = chain.proceed(request)
        }
        return response
    }
}
