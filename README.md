# M Gallery

A modern, offline-first Android photo and video gallery built with Kotlin, Jetpack Compose, Material 3, Room, DataStore, Media3 ExoPlayer, Coil, and Android Keystore AES-256-GCM encryption.

## Architecture
- **UI Layer (`com.example.ui.*`)**: Jetpack Compose screens with Material 3 adaptive navigation (`NavigationBar` / `NavigationRail`), ViewModels (`GalleryViewModel`, `VaultViewModel`, `PhotoEditorViewModel`), and `StateFlow` collected via `collectAsStateWithLifecycle()`.
- **Domain Layer (`com.example.domain.*`)**: Media, album, settings, vault, and recycle-bin models, `PhotoEditorProcessor` (real Bitmap crop, rotate, flip, `ColorMatrix` adjustments & filters, non-destructive copy saving), and `MediaAnalyzer` (streaming SHA-256 duplicate detection, 9x8 dHash visual similarity, large-file discovery, and storage analytics).
- **Data Layer (`com.example.data.*`)**:
  - `MediaRepository`: `MediaStore` querying with `ContentObserver` auto-refresh, zero-permission Photo Picker imports, `ExifInterface` metadata reading, and Scoped Storage consent deletion (`MediaStore.createDeleteRequest`).
  - `AlbumAndVaultRepository` & `MGalleryDatabase`: Room persistence for logical user albums, favorites, recently viewed, imported picker media, and app-managed Recycle Bin with configurable auto-cleanup.
  - `VaultCryptoManager` & `EncryptedVaultDataSource`: Hardware-backed Android Keystore AES-256-GCM chunked seekable encryption for Private Vault photos, videos, thumbnails, and manifest, plus PBKDF2-HMAC-SHA256 PIN and 24-character recovery key verification.
  - `SettingsDataStore`: Persistent preferences across app restarts.

## Build & Setup
- **Requirements**: Android Studio Ladybug+ / Gradle 9.x, JDK 17+, Android SDK 36 (`minSdk = 24`, `targetSdk = 36`).
- **Build Debug APK**: `gradle :app:assembleDebug` (outputs to `app/build/outputs/apk/debug/app-debug.apk`).
- **Run Unit & Robolectric UI Tests**: `gradle :app:testDebugUnitTest`.

## Testing Guidance
- **Permissions**: Verify all three states on Android 14+ (Full Access, Selected Photos Partial Access, and Denied with Photo Picker fallback and System Settings deep link).
- **Large Libraries**: Coil downsamples thumbnails to 360px/640px (`ThumbnailQuality`) with bounded memory/disk caches; `MediaAnalyzer` runs on `Dispatchers.Default` with cancellation support and progress reporting.
- **Low Storage / Low Memory**: `PhotoEditorProcessor` samples bitmaps to <=1440px during editing and <=3840px on export; `EncryptedVaultDataSource` streams 256 KB AES-GCM chunks in memory without writing plaintext video copies to disk.
