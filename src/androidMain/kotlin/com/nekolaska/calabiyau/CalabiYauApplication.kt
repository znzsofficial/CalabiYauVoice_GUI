package com.nekolaska.calabiyau

import android.app.Application
import android.content.Context
import android.os.Build
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.nekolaska.calabiyau.data.WikiEngine
import java.io.File

class CalabiYauApplication : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        if (isMainProcess()) {
            CrashHandler.install(this)
        }
    }

    override fun newImageLoader(context: Context): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { WikiEngine.client }))
                if (Build.VERSION.SDK_INT >= 28) {
                    add(AnimatedImageDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(128L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()
    }

    private fun isMainProcess(): Boolean {
        val processName = currentProcessName() ?: return true
        return processName == packageName
    }

    private fun currentProcessName(): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Application.getProcessName()
        }

        return runCatching {
            File("/proc/self/cmdline")
                .readText(Charsets.UTF_8)
                .trim('\u0000', ' ', '\n', '\r', '\t')
                .ifBlank { null }
        }.getOrNull()
    }
}
