package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.db.AlbumMediaCrossRef
import com.example.data.local.db.FavoriteEntity
import com.example.data.local.db.MGalleryDatabase
import com.example.data.local.db.TrashEntity
import com.example.data.local.db.UserAlbumEntity
import com.example.data.preferences.SettingsDataStore
import com.example.domain.model.AppSettings
import com.example.domain.model.MediaItem
import com.example.domain.model.MediaType
import com.example.domain.model.ThemeMode
import com.example.ui.GalleryUiState
import com.example.ui.MediaAccessLevel
import com.example.ui.screens.FavoritesScreen
import com.example.ui.screens.PhotosScreen
import com.example.ui.screens.RecycleBinScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MGalleryTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `app name matches M Gallery`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("M Gallery", context.getString(R.string.app_name))
    }

    @Test
    fun `settings datastore persists theme and grid columns`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = SettingsDataStore(context)
        store.setThemeMode(ThemeMode.LIGHT)
        store.setGridColumns(4)
        val loaded = store.settingsFlow.first()
        assertEquals(ThemeMode.LIGHT, loaded.themeMode)
        assertEquals(4, loaded.gridColumns)
    }

    @Test
    fun `room database persists user albums favorites and recycle bin`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, MGalleryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = db.galleryDao()

        dao.addFavorite(FavoriteEntity("img_10", 1000L))
        assertEquals(listOf("img_10"), dao.observeFavoriteKeys().first())

        dao.addFavorites(listOf(FavoriteEntity("vid_11", 2000L), FavoriteEntity("img_12", 3000L)))
        assertEquals(3, dao.observeFavorites().first().size)
        dao.removeFavorites(listOf("img_10", "vid_11"))
        assertEquals(listOf("img_12"), dao.observeFavoriteKeys().first())
        dao.clearAllFavorites()
        assertTrue(dao.observeFavoriteKeys().first().isEmpty())

        dao.addFavorite(FavoriteEntity("img_10", 1000L))

        dao.upsertUserAlbum(UserAlbumEntity("user_1", "Road Trip", null, 1000L))
        dao.addMediaToAlbum(AlbumMediaCrossRef("user_1", "img_10", 1000L))
        assertEquals(1, dao.observeUserAlbums().first().size)
        assertEquals(1, dao.observeAllAlbumCrossRefs().first().size)

        dao.insertTrashItem(
            TrashEntity(
                id = "t_1",
                originalUri = null,
                displayName = "old.jpg",
                mimeType = "image/jpeg",
                isVideo = false,
                trashedFilePath = "/tmp/old.jpg",
                trashedThumbPath = null,
                sizeBytes = 1024L,
                width = 100,
                height = 100,
                durationMs = 0L,
                trashedAtMs = 1000L,
                expiresAtMs = 9999999999L
            )
        )
        assertEquals(1, dao.observeTrashItems().first().size)
        db.close()
    }

    @Test
    fun `photos screen renders M Gallery tabs and permission state actions`() {
        composeTestRule.setContent {
            MGalleryTheme {
                PhotosScreen(
                    state = GalleryUiState(isLoading = false, accessLevel = MediaAccessLevel.DENIED),
                    onSelectTab = {},
                    onSelectQuickFilter = {},
                    onOpenSearch = {},
                    onOpenSettings = {},
                    onOpenMedia = { _, _ -> },
                    onToggleSelect = {},
                    onSelectAll = {},
                    onClearSelection = {},
                    onToggleFavorite = {},
                    onFavoriteSelected = {},
                    onShareSelected = {},
                    onMoveSelectedToVault = {},
                    onDeleteSelected = {},
                    onAddSelectedToAlbum = {},
                    onCreateAlbumWithSelected = {},
                    onOpenAlbum = { _, _ -> },
                    onOpenFavorites = {},
                    onOpenRecycleBin = {},
                    onOpenSmartTools = {},
                    onSortChange = {},
                    onViewModeChange = {},
                    onGridColsChange = {},
                    onRequestMediaPermission = {},
                    onLaunchPhotoPicker = {},
                    onOpenAppSettings = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("M Gallery").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_tab_photos").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_tab_videos").assertIsDisplayed()
        composeTestRule.onNodeWithTag("grant_media_permission_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("photo_picker_import_button").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `settings screen triggers theme and grid density callbacks`() {
        var selectedCols = 3
        composeTestRule.setContent {
            MGalleryTheme {
                SettingsScreen(
                    settings = AppSettings(),
                    accessLevel = MediaAccessLevel.FULL,
                    storageBreakdown = com.example.domain.model.StorageBreakdown(),
                    trashCount = 0,
                    onThemeModeChange = {},
                    onAccentPresetChange = {},
                    onGridColumnsChange = { selectedCols = it },
                    onThumbnailQualityChange = {},
                    onAnimationsChange = {},
                    onHapticsChange = {},
                    onDefaultSortChange = {},
                    onVaultAutoLockChange = {},
                    onBiometricChange = {},
                    onTrashRetentionChange = {},
                    onOpenRecycleBin = {},
                    onOpenSmartTools = {},
                    onRequestMediaPermission = {},
                    onOpenAppSystemSettings = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_grid_4").performScrollTo().performClick()
        assertTrue(selectedCols == 4)
    }

    @Test
    fun `recycle bin screen displays restore and empty actions`() {
        composeTestRule.setContent {
            MGalleryTheme {
                RecycleBinScreen(
                    trashItems = listOf(
                        com.example.domain.model.TrashItem(
                            id = "1",
                            originalUri = null,
                            displayName = "sunset.jpg",
                            mimeType = "image/jpeg",
                            mediaType = MediaType.PHOTO,
                            trashedFilePath = "/tmp/sunset.jpg",
                            trashedThumbPath = null,
                            sizeBytes = 2048L,
                            width = 1080,
                            height = 1920,
                            durationMs = 0L,
                            trashedAtMs = System.currentTimeMillis(),
                            expiresAtMs = System.currentTimeMillis() + 86_400_000L * 10
                        )
                    ),
                    retentionDays = 30,
                    onBack = {},
                    onRestoreItems = {},
                    onPermanentlyDelete = {},
                    onEmptyAll = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("recycle_restore_all_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("recycle_empty_all_button").assertIsDisplayed()
    }

    @Test
    fun `favorites screen displays empty state and browse button`() {
        var browseClicked = false
        composeTestRule.setContent {
            MGalleryTheme {
                FavoritesScreen(
                    state = GalleryUiState(isLoading = false, favorites = emptyList()),
                    onBack = { browseClicked = true },
                    onOpenMedia = { _, _ -> },
                    onToggleFavorite = {},
                    onToggleFavoriteSelected = {},
                    onClearAllFavorites = {},
                    onToggleSelect = {},
                    onSelectAll = {},
                    onClearSelection = {},
                    onAddSelectedToAlbum = {},
                    onCreateAlbumWithSelected = {},
                    onMoveSelectedToVault = {},
                    onShareSelected = {},
                    onDeleteSelected = {},
                    onViewModeChange = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("No Favorites Yet").assertIsDisplayed()
        composeTestRule.onNodeWithTag("favorites_browse_button").performClick()
        assertTrue(browseClicked)
    }

    @Test
    fun `favorites screen filters media and toggles favorites`() {
        val photo = MediaItem(
            mediaKey = "p1",
            id = 1L,
            contentUri = "content://media/external/images/media/1",
            displayName = "beach.jpg",
            mimeType = "image/jpeg",
            mediaType = MediaType.PHOTO,
            dateTakenMs = 1000L,
            dateAddedSec = 1000L,
            dateModifiedSec = 1000L,
            sizeBytes = 1024L,
            width = 1080,
            height = 1920,
            durationMs = 0L,
            bucketId = "b1",
            bucketName = "Camera",
            relativePath = "DCIM/Camera",
            isFavorite = true
        )
        val video = MediaItem(
            mediaKey = "v1",
            id = 2L,
            contentUri = "content://media/external/video/media/2",
            displayName = "waves.mp4",
            mimeType = "video/mp4",
            mediaType = MediaType.VIDEO,
            dateTakenMs = 2000L,
            dateAddedSec = 2000L,
            dateModifiedSec = 2000L,
            sizeBytes = 4096L,
            width = 1920,
            height = 1080,
            durationMs = 15000L,
            bucketId = "b1",
            bucketName = "Camera",
            relativePath = "DCIM/Camera",
            isFavorite = true
        )
        var toggledKey: String? = null
        composeTestRule.setContent {
            MGalleryTheme {
                FavoritesScreen(
                    state = GalleryUiState(isLoading = false, favorites = listOf(photo, video)),
                    onBack = {},
                    onOpenMedia = { _, _ -> },
                    onToggleFavorite = { toggledKey = it.mediaKey },
                    onToggleFavoriteSelected = {},
                    onClearAllFavorites = {},
                    onToggleSelect = {},
                    onSelectAll = {},
                    onClearSelection = {},
                    onAddSelectedToAlbum = {},
                    onCreateAlbumWithSelected = {},
                    onMoveSelectedToVault = {},
                    onShareSelected = {},
                    onDeleteSelected = {},
                    onViewModeChange = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("1 photos • 1 videos").assertIsDisplayed()
        composeTestRule.onNodeWithTag("favorites_filter_videos").performClick()
        composeTestRule.onNodeWithTag("favorite_toggle_v1").performClick()
        assertEquals("v1", toggledKey)
    }
}
