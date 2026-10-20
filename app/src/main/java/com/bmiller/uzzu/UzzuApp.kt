package com.bmiller.uzzu

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import okio.Path.Companion.toOkioPath
import coil3.network.okhttp.OkHttpNetworkFetcherFactory

class UzzuApp : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.logger.i("App", "start v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) debug=${BuildConfig.DEBUG}")
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { container.okHttp })) }
            // Logos are tiny (decoded at the 32/48 dp slot size); keep the caches small for the 1 GB Fire TV Stick.
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.1).build() }
            .diskCache { DiskCache.Builder().directory(cacheDir.resolve("logos").toOkioPath()).maxSizeBytes(32L * 1024 * 1024).build() }
            .build()
}
