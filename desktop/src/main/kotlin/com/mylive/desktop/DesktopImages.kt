package com.mylive.desktop

import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath

internal fun configureDesktopImages(client: OkHttpClient) {
    SingletonImageLoader.setSafe { context ->
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { client })) }
            .memoryCache { MemoryCache.Builder().maxSizeBytes(32L * 1024 * 1024).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(defaultDesktopLibraryPath().toAbsolutePath().parent.resolve("cache/images").toOkioPath())
                    .maxSizeBytes(256L * 1024 * 1024)
                    .build()
            }
            .build()
    }
}
