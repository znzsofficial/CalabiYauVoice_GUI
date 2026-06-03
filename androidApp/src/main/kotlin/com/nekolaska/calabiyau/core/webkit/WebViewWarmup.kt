package com.nekolaska.calabiyau.core.webkit

import android.content.Context
import android.util.Log
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewOutcomeReceiver
import androidx.webkit.WebViewStartUpConfig
import androidx.webkit.WebViewStartUpResult
import androidx.webkit.WebViewStartupException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

object WebViewWarmup {
    private const val TAG = "WebViewWarmup"

    private val started = AtomicBoolean(false)

    fun start(context: Context) {
        if (!started.compareAndSet(false, true)) return

        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "webview-startup").apply { isDaemon = true }
        }
        val config = WebViewStartUpConfig.Builder(executor)
            .setShouldRunUiThreadStartUpTasks(true)
            .build()

        try {
            WebViewCompat.startUpWebView(
                context.applicationContext,
                config,
                object : WebViewOutcomeReceiver<WebViewStartUpResult, WebViewStartupException> {
                    override fun onResult(result: WebViewStartUpResult) {
                        executor.shutdown()
                    }

                    override fun onError(error: WebViewStartupException) {
                        Log.w(TAG, "WebView startup failed", error)
                        executor.shutdown()
                    }
                }
            )
        } catch (e: Throwable) {
            Log.w(TAG, "WebView startup unavailable", e)
            executor.shutdown()
        }
    }
}
