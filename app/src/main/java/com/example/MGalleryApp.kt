package com.example

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.data.local.db.MGalleryDatabase
import com.example.data.preferences.SettingsDataStore
import com.example.data.repository.AlbumAndVaultRepository
import com.example.data.repository.MediaRepository
import com.example.data.security.VaultCryptoManager
import com.example.domain.editor.PhotoEditorProcessor
import com.example.domain.smart.MediaAnalyzer

class AppContainer(application: Application) {
    val database: MGalleryDatabase by lazy { MGalleryDatabase.build(application) }
    val settingsDataStore: SettingsDataStore by lazy { SettingsDataStore(application) }
    val vaultCryptoManager: VaultCryptoManager by lazy { VaultCryptoManager() }
    val mediaRepository: MediaRepository by lazy {
        MediaRepository(application, database.galleryDao())
    }
    val albumAndVaultRepository: AlbumAndVaultRepository by lazy {
        AlbumAndVaultRepository(
            context = application,
            dao = database.galleryDao(),
            mediaRepository = mediaRepository,
            cryptoManager = vaultCryptoManager
        )
    }
    val photoEditorProcessor: PhotoEditorProcessor by lazy {
        PhotoEditorProcessor(application, mediaRepository)
    }
    val mediaAnalyzer: MediaAnalyzer by lazy { MediaAnalyzer(application) }
}

class MGalleryApp : Application(), ImageLoaderFactory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.22)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil_media_cache"))
                    .maxSizePercent(0.03)
                    .build()
            }
            .crossfade(160)
            .build()
    }
}
