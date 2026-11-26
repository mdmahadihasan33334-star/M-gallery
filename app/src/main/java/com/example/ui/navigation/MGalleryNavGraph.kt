package com.example.ui.navigation

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.AppContainer
import com.example.R
import com.example.domain.model.MediaItem
import com.example.ui.GalleryViewModel
import com.example.ui.MediaAccessLevel
import com.example.ui.editor.PhotoEditorViewModel
import com.example.ui.screens.AlbumDetailScreen
import com.example.ui.screens.AlbumsScreen
import com.example.ui.screens.FavoritesScreen
import com.example.ui.screens.MediaViewerScreen
import com.example.ui.screens.PhotoEditorScreen
import com.example.ui.screens.PhotosScreen
import com.example.ui.screens.RecycleBinScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SmartToolsScreen
import com.example.ui.screens.VaultScreen
import com.example.ui.screens.VideoPlayerScreen
import com.example.ui.vault.VaultViewModel
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable data object PhotosRoute
@Serializable data object AlbumsRoute
@Serializable data object VaultRoute
@Serializable data object SettingsRoute
@Serializable data class AlbumDetailRoute(val albumId: String, val albumTitle: String)
@Serializable data object FavoritesRoute
@Serializable data class MediaViewerRoute(val initialMediaKey: String)
@Serializable data class VideoPlayerRoute(val uriString: String, val title: String, val isVault: Boolean)
@Serializable data class PhotoEditorRoute(val uriString: String, val displayName: String)
@Serializable data object SearchRoute
@Serializable data object RecycleBinRoute
@Serializable data object SmartToolsRoute

private data class TopLevelDestination(
    val route: Any,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val topLevelDestinations = listOf(
    TopLevelDestination(PhotosRoute, R.string.nav_photos, Icons.Filled.Collections, Icons.Outlined.Collections, "nav_photos"),
    TopLevelDestination(AlbumsRoute, R.string.nav_albums, Icons.Filled.Folder, Icons.Outlined.Folder, "nav_albums"),
    TopLevelDestination(VaultRoute, R.string.nav_vault, Icons.Filled.Lock, Icons.Outlined.Lock, "nav_vault"),
    TopLevelDestination(SettingsRoute, R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings, "nav_settings")
)

@Composable
fun MGalleryNavRoot(
    container: AppContainer,
    widthSizeClass: WindowWidthSizeClass,
    galleryViewModel: GalleryViewModel
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val vaultViewModel: VaultViewModel = viewModel(factory = VaultViewModel.factory(container))
    val editorViewModel: PhotoEditorViewModel = viewModel(factory = PhotoEditorViewModel.factory(container))

    val galleryState by galleryViewModel.uiState.collectAsStateWithLifecycle()
    val searchQuery by galleryViewModel.searchQuery.collectAsStateWithLifecycle()
    val searchFilter by galleryViewModel.searchFilter.collectAsStateWithLifecycle()
    val searchResults by galleryViewModel.searchResults.collectAsStateWithLifecycle()
    val smartState by galleryViewModel.smartState.collectAsStateWithLifecycle()

    // Track ordered list of keys shown in the active viewer queue
    var viewerMediaKeys by remember { mutableStateOf<List<String>>(emptyList()) }
    var pendingConsentTrashIds by remember { mutableStateOf<List<String>>(emptyList()) }

    val requiredPermissions = remember { requiredMediaPermissions() }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        galleryViewModel.updateAccessLevel(evaluateMediaAccessLevel(context))
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 25)
    ) { uris ->
        if (uris.isNotEmpty()) {
            galleryViewModel.importPickerUris(uris)
        }
    }

    val deleteConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        galleryViewModel.onDeleteConsentResult(
            approved = result.resultCode == Activity.RESULT_OK,
            stagedIds = pendingConsentTrashIds
        )
        pendingConsentTrashIds = emptyList()
    }

    // Lifecycle observer: sync permissions on resume & auto-lock vault on background/foreground
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, galleryState.settings.vaultAutoLockSeconds) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    galleryViewModel.updateAccessLevel(evaluateMediaAccessLevel(context))
                }
                Lifecycle.Event.ON_START -> {
                    vaultViewModel.onAppForegrounded(galleryState.settings.vaultAutoLockSeconds)
                }
                Lifecycle.Event.ON_STOP -> {
                    vaultViewModel.onAppBackgrounded()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Request permissions on initial launch if not yet granted
    LaunchedEffect(Unit) {
        val level = evaluateMediaAccessLevel(context)
        galleryViewModel.updateAccessLevel(level)
        if (level == MediaAccessLevel.DENIED) {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    LaunchedEffect(Unit) {
        galleryViewModel.deleteConsentEvents.collect { consent ->
            pendingConsentTrashIds = consent.stagedTrashIds
            deleteConsentLauncher.launch(IntentSenderRequest.Builder(consent.pendingIntent).build())
        }
    }

    LaunchedEffect(Unit) {
        galleryViewModel.snackbarMessages.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val openMediaViewer: (MediaItem, List<MediaItem>) -> Unit = { item, queue ->
        viewerMediaKeys = queue.map { it.mediaKey }
        navController.navigate(MediaViewerRoute(item.mediaKey))
    }

    val shareMediaItems: (List<MediaItem>) -> Unit = { items ->
        val intent = galleryViewModel.buildShareIntent(items)
        if (intent != null) {
            runCatching {
                context.startActivity(Intent.createChooser(intent, "Share media"))
            }.onFailure {
                scope.launch { snackbarHostState.showSnackbar("No app available to share this file") }
            }
        }
    }

    val openSystemAppSettings = {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        )
        runCatching { context.startActivity(intent) }
        Unit
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = navBackStackEntry?.destination
    val isTopLevel = topLevelDestinations.any { dest ->
        when (dest.route) {
            is PhotosRoute -> currentDest?.hasRoute<PhotosRoute>() == true
            is AlbumsRoute -> currentDest?.hasRoute<AlbumsRoute>() == true
            is VaultRoute -> currentDest?.hasRoute<VaultRoute>() == true
            is SettingsRoute -> currentDest?.hasRoute<SettingsRoute>() == true
            else -> false
        }
    }

    val useRail = widthSizeClass != WindowWidthSizeClass.Compact

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (isTopLevel && !useRail && !galleryState.isSelectionMode) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.navigationBarsPadding()
                ) {
                    topLevelDestinations.forEach { dest ->
                        val selected = when (dest.route) {
                            is PhotosRoute -> currentDest?.hasRoute<PhotosRoute>() == true
                            is AlbumsRoute -> currentDest?.hasRoute<AlbumsRoute>() == true
                            is VaultRoute -> currentDest?.hasRoute<VaultRoute>() == true
                            is SettingsRoute -> currentDest?.hasRoute<SettingsRoute>() == true
                            else -> false
                        }
                        val label = stringResource(dest.labelRes)
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                    contentDescription = label
                                )
                            },
                            label = { Text(label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            modifier = Modifier.testTag(dest.testTag)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isTopLevel && useRail) {
                NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                    topLevelDestinations.forEach { dest ->
                        val selected = when (dest.route) {
                            is PhotosRoute -> currentDest?.hasRoute<PhotosRoute>() == true
                            is AlbumsRoute -> currentDest?.hasRoute<AlbumsRoute>() == true
                            is VaultRoute -> currentDest?.hasRoute<VaultRoute>() == true
                            is SettingsRoute -> currentDest?.hasRoute<SettingsRoute>() == true
                            else -> false
                        }
                        val label = stringResource(dest.labelRes)
                        NavigationRailItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                    contentDescription = label
                                )
                            },
                            label = { Text(label) }
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                NavHost(
                    navController = navController,
                    startDestination = PhotosRoute
                ) {
                    composable<PhotosRoute> {
                        PhotosScreen(
                            state = galleryState,
                            onSelectTab = galleryViewModel::selectHomeTab,
                            onSelectQuickFilter = galleryViewModel::setQuickFilter,
                            onOpenSearch = { navController.navigate(SearchRoute) },
                            onOpenSettings = {
                                navController.navigate(SettingsRoute) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            onOpenMedia = openMediaViewer,
                            onToggleSelect = galleryViewModel::toggleSelection,
                            onSelectAll = { galleryViewModel.selectAllVisible(galleryState.filteredHomeMedia) },
                            onClearSelection = galleryViewModel::clearSelection,
                            onToggleFavorite = galleryViewModel::toggleFavorite,
                            onFavoriteSelected = galleryViewModel::toggleFavoriteForSelected,
                            onShareSelected = { shareMediaItems(galleryState.selectedItems) },
                            onMoveSelectedToVault = {
                                val selected = galleryState.selectedItems
                                if (!vaultViewModel.uiState.value.isConfigured) {
                                    galleryViewModel.clearSelection()
                                    scope.launch { snackbarHostState.showSnackbar("Set up your Private Vault PIN first") }
                                    navController.navigate(VaultRoute)
                                } else {
                                    vaultViewModel.importMediaItems(selected)
                                    galleryViewModel.clearSelection()
                                }
                            },
                            onDeleteSelected = { galleryViewModel.moveItemsToRecycleBin(galleryState.selectedItems) },
                            onAddSelectedToAlbum = { albumId ->
                                galleryViewModel.addItemsToAlbum(albumId, galleryState.selectedItems)
                            },
                            onCreateAlbumWithSelected = { name ->
                                galleryViewModel.createUserAlbum(name, galleryState.selectedItems)
                            },
                            onOpenAlbum = { id, title ->
                                navController.navigate(AlbumDetailRoute(id, title))
                            },
                            onOpenFavorites = { navController.navigate(FavoritesRoute) },
                            onOpenRecycleBin = { navController.navigate(RecycleBinRoute) },
                            onOpenSmartTools = { navController.navigate(SmartToolsRoute) },
                            onSortChange = galleryViewModel::setSortOrder,
                            onViewModeChange = galleryViewModel::setViewMode,
                            onGridColsChange = galleryViewModel::setGridColumns,
                            onRequestMediaPermission = { permissionLauncher.launch(requiredPermissions) },
                            onLaunchPhotoPicker = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                )
                            },
                            onOpenAppSettings = openSystemAppSettings
                        )
                    }

                    composable<AlbumsRoute> {
                        AlbumsScreen(
                            state = galleryState,
                            onOpenAlbum = { id, title -> navController.navigate(AlbumDetailRoute(id, title)) },
                            onOpenFavorites = { navController.navigate(FavoritesRoute) },
                            onCreateAlbum = { name -> galleryViewModel.createUserAlbum(name) },
                            onRenameAlbum = galleryViewModel::renameUserAlbum,
                            onDeleteAlbum = galleryViewModel::deleteUserAlbum,
                            onOpenRecycleBin = { navController.navigate(RecycleBinRoute) },
                            onOpenSmartTools = { navController.navigate(SmartToolsRoute) }
                        )
                    }

                    composable<AlbumDetailRoute> { entry ->
                        val route = entry.toRoute<AlbumDetailRoute>()
                        AlbumDetailScreen(
                            albumId = route.albumId,
                            albumTitle = route.albumTitle,
                            state = galleryState,
                            onBack = {
                                galleryViewModel.clearSelection()
                                navController.popBackStack()
                            },
                            onOpenMedia = openMediaViewer,
                            onAddItemsToAlbum = galleryViewModel::addItemsToAlbum,
                            onRemoveItemFromUserAlbum = galleryViewModel::removeItemFromAlbum,
                            onRenameAlbum = galleryViewModel::renameUserAlbum,
                            onUpdateCover = galleryViewModel::updateAlbumCover,
                            onDeleteAlbum = galleryViewModel::deleteUserAlbum,
                            onToggleSelect = galleryViewModel::toggleSelection,
                            onSelectAll = galleryViewModel::selectAllVisible,
                            onClearSelection = galleryViewModel::clearSelection,
                            onToggleFavorite = galleryViewModel::toggleFavorite,
                            onFavoriteSelected = galleryViewModel::toggleFavoriteForSelected,
                            onShareSelected = { shareMediaItems(galleryState.selectedItems) },
                            onMoveSelectedToVault = {
                                val selected = galleryState.selectedItems
                                if (!vaultViewModel.uiState.value.isConfigured) {
                                    galleryViewModel.clearSelection()
                                    scope.launch { snackbarHostState.showSnackbar("Set up your Private Vault PIN first") }
                                    navController.navigate(VaultRoute)
                                } else {
                                    vaultViewModel.importMediaItems(selected)
                                    galleryViewModel.clearSelection()
                                }
                            },
                            onDeleteSelected = { galleryViewModel.moveItemsToRecycleBin(galleryState.selectedItems) },
                            onCreateAlbumWithSelected = { name ->
                                galleryViewModel.createUserAlbum(name, galleryState.selectedItems)
                            }
                        )
                    }

                    composable<FavoritesRoute> {
                        FavoritesScreen(
                            state = galleryState,
                            onBack = {
                                galleryViewModel.clearSelection()
                                navController.popBackStack()
                            },
                            onOpenMedia = openMediaViewer,
                            onToggleFavorite = galleryViewModel::toggleFavorite,
                            onToggleFavoriteSelected = galleryViewModel::toggleFavoriteForSelected,
                            onClearAllFavorites = galleryViewModel::clearAllFavorites,
                            onToggleSelect = galleryViewModel::toggleSelection,
                            onSelectAll = galleryViewModel::selectAllVisible,
                            onClearSelection = galleryViewModel::clearSelection,
                            onAddSelectedToAlbum = { albumId ->
                                galleryViewModel.addItemsToAlbum(albumId, galleryState.selectedItems)
                            },
                            onCreateAlbumWithSelected = { name ->
                                galleryViewModel.createUserAlbum(name, galleryState.selectedItems)
                            },
                            onMoveSelectedToVault = {
                                val selected = galleryState.selectedItems
                                if (!vaultViewModel.uiState.value.isConfigured) {
                                    galleryViewModel.clearSelection()
                                    scope.launch { snackbarHostState.showSnackbar("Set up your Private Vault PIN first") }
                                    navController.navigate(VaultRoute)
                                } else {
                                    vaultViewModel.importMediaItems(selected)
                                    galleryViewModel.clearSelection()
                                }
                            },
                            onShareSelected = { shareMediaItems(galleryState.selectedItems) },
                            onDeleteSelected = { galleryViewModel.moveItemsToRecycleBin(galleryState.selectedItems) },
                            onViewModeChange = galleryViewModel::setViewMode
                        )
                    }

                    composable<VaultRoute> {
                        VaultScreen(
                            vaultViewModel = vaultViewModel,
                            allGalleryMedia = galleryState.allMedia,
                            biometricEnabledInSettings = galleryState.settings.biometricEnabled,
                            hapticsEnabled = galleryState.settings.hapticsEnabled,
                            onOpenVaultVideo = { vaultItem ->
                                navController.navigate(
                                    VideoPlayerRoute(
                                        uriString = "mgvault://vault/${vaultItem.encryptedFileName}",
                                        title = vaultItem.originalName,
                                        isVault = true
                                    )
                                )
                            },
                            onRefreshGalleryAfterOriginalDelete = galleryViewModel::refreshMedia,
                            onShowSnackbar = { msg ->
                                scope.launch { snackbarHostState.showSnackbar(msg) }
                            }
                        )
                    }

                    composable<SettingsRoute> {
                        SettingsScreen(
                            settings = galleryState.settings,
                            accessLevel = galleryState.accessLevel,
                            storageBreakdown = smartState.breakdown,
                            trashCount = galleryState.trashItems.size,
                            onThemeModeChange = galleryViewModel::setThemeMode,
                            onAccentPresetChange = galleryViewModel::setAccentPreset,
                            onGridColumnsChange = galleryViewModel::setGridColumns,
                            onThumbnailQualityChange = galleryViewModel::setThumbnailQuality,
                            onAnimationsChange = galleryViewModel::setAnimationsEnabled,
                            onHapticsChange = galleryViewModel::setHapticsEnabled,
                            onDefaultSortChange = galleryViewModel::setDefaultSort,
                            onVaultAutoLockChange = galleryViewModel::setVaultAutoLockSeconds,
                            onBiometricChange = galleryViewModel::setBiometricEnabled,
                            onTrashRetentionChange = galleryViewModel::setTrashRetentionDays,
                            onOpenRecycleBin = { navController.navigate(RecycleBinRoute) },
                            onOpenSmartTools = { navController.navigate(SmartToolsRoute) },
                            onRequestMediaPermission = { permissionLauncher.launch(requiredPermissions) },
                            onOpenAppSystemSettings = openSystemAppSettings
                        )
                    }

                    composable<MediaViewerRoute> { entry ->
                        val route = entry.toRoute<MediaViewerRoute>()
                        val byKey = remember(galleryState.allMedia) {
                            galleryState.allMedia.associateBy { it.mediaKey }
                        }
                        val queueItems = remember(viewerMediaKeys, byKey) {
                            val resolved = viewerMediaKeys.mapNotNull { byKey[it] }
                            resolved.ifEmpty {
                                byKey[route.initialMediaKey]?.let { listOf(it) } ?: emptyList()
                            }
                        }

                        MediaViewerScreen(
                            items = queueItems,
                            initialMediaKey = route.initialMediaKey,
                            userAlbums = galleryState.userAlbums,
                            onBack = { navController.popBackStack() },
                            onViewed = galleryViewModel::recordViewed,
                            onLoadExif = galleryViewModel::loadExif,
                            onToggleFavorite = galleryViewModel::toggleFavorite,
                            onOpenVideoPlayer = { item ->
                                navController.navigate(
                                    VideoPlayerRoute(
                                        uriString = item.contentUri,
                                        title = item.displayName,
                                        isVault = false
                                    )
                                )
                            },
                            onOpenPhotoEditor = { item ->
                                navController.navigate(
                                    PhotoEditorRoute(
                                        uriString = item.contentUri,
                                        displayName = item.displayName
                                    )
                                )
                            },
                            onShareItem = { item -> shareMediaItems(listOf(item)) },
                            onAddItemToAlbum = { albumId, item ->
                                galleryViewModel.addItemsToAlbum(albumId, listOf(item))
                            },
                            onCreateAlbumWithItem = { name, item ->
                                galleryViewModel.createUserAlbum(name, listOf(item))
                            },
                            onMoveToVault = { item ->
                                if (!vaultViewModel.uiState.value.isConfigured) {
                                    scope.launch { snackbarHostState.showSnackbar("Set up your Private Vault PIN first") }
                                    navController.navigate(VaultRoute)
                                } else {
                                    vaultViewModel.importMediaItems(listOf(item))
                                    scope.launch { snackbarHostState.showSnackbar("Encrypted copy added to Private Vault") }
                                }
                            },
                            onDeleteItem = { item ->
                                galleryViewModel.moveItemsToRecycleBin(listOf(item))
                                navController.popBackStack()
                            }
                        )
                    }

                    composable<VideoPlayerRoute> { entry ->
                        val route = entry.toRoute<VideoPlayerRoute>()
                        VideoPlayerScreen(
                            videoUriString = route.uriString,
                            videoTitle = route.title,
                            isEncryptedVaultVideo = route.isVault,
                            vaultDir = container.albumAndVaultRepository.vaultDir,
                            cryptoManager = container.vaultCryptoManager,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable<PhotoEditorRoute> { entry ->
                        val route = entry.toRoute<PhotoEditorRoute>()
                        PhotoEditorScreen(
                            mediaUri = route.uriString,
                            mediaName = route.displayName,
                            viewModel = editorViewModel,
                            onSavedAndExit = { message ->
                                galleryViewModel.refreshMedia()
                                navController.popBackStack()
                                scope.launch { snackbarHostState.showSnackbar(message) }
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable<SearchRoute> {
                        SearchScreen(
                            query = searchQuery,
                            activeFilter = searchFilter,
                            results = searchResults,
                            settings = galleryState.settings,
                            onQueryChange = galleryViewModel::setSearchQuery,
                            onFilterChange = galleryViewModel::setSearchFilter,
                            onOpenMedia = openMediaViewer,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable<RecycleBinRoute> {
                        RecycleBinScreen(
                            trashItems = galleryState.trashItems,
                            retentionDays = galleryState.settings.trashRetentionDays,
                            onBack = { navController.popBackStack() },
                            onRestoreItems = galleryViewModel::restoreTrashItems,
                            onPermanentlyDelete = galleryViewModel::permanentlyDeleteTrashItem,
                            onEmptyAll = galleryViewModel::emptyRecycleBin
                        )
                    }

                    composable<SmartToolsRoute> {
                        SmartToolsScreen(
                            smartState = smartState,
                            onBack = { navController.popBackStack() },
                            onStartScan = galleryViewModel::startSmartScan,
                            onCancelScan = galleryViewModel::cancelSmartScan,
                            onOpenMedia = openMediaViewer,
                            onDeleteItems = galleryViewModel::moveItemsToRecycleBin
                        )
                    }
                }
            }
        }
    }
}

private fun requiredMediaPermissions(): Array<String> {
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO
        )
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

fun evaluateMediaAccessLevel(context: Context): MediaAccessLevel {
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
            val imagesGranted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED
            val videoGranted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_MEDIA_VIDEO
            ) == PackageManager.PERMISSION_GRANTED
            val partialGranted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            ) == PackageManager.PERMISSION_GRANTED
            when {
                imagesGranted || videoGranted -> MediaAccessLevel.FULL
                partialGranted -> MediaAccessLevel.PARTIAL
                else -> MediaAccessLevel.DENIED
            }
        }
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
            val imagesGranted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED
            val videoGranted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_MEDIA_VIDEO
            ) == PackageManager.PERMISSION_GRANTED
            if (imagesGranted || videoGranted) MediaAccessLevel.FULL else MediaAccessLevel.DENIED
        }
        else -> {
            val readGranted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            if (readGranted) MediaAccessLevel.FULL else MediaAccessLevel.DENIED
        }
    }
}
