package com.example

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.GalleryViewModel
import com.example.ui.navigation.MGalleryNavRoot
import com.example.ui.theme.MGalleryTheme

class MainActivity : FragmentActivity() {

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as MGalleryApp).container

        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            val galleryViewModel: GalleryViewModel = viewModel(
                factory = GalleryViewModel.factory(container)
            )
            val uiState by galleryViewModel.uiState.collectAsStateWithLifecycle()

            MGalleryTheme(
                themeMode = uiState.settings.themeMode,
                accentPreset = uiState.settings.accentPreset
            ) {
                MGalleryNavRoot(
                    container = container,
                    widthSizeClass = windowSizeClass.widthSizeClass,
                    galleryViewModel = galleryViewModel
                )
            }
        }
    }
}
