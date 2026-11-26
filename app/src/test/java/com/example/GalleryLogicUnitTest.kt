package com.example

import com.example.domain.editor.PhotoEditState
import com.example.domain.model.MediaItem
import com.example.domain.model.MediaSortOrder
import com.example.domain.model.MediaType
import com.example.ui.GalleryViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GalleryLogicUnitTest {

    private fun sampleItem(
        key: String,
        name: String,
        type: MediaType,
        size: Long,
        tsMs: Long
    ) = MediaItem(
        mediaKey = key,
        id = key.hashCode().toLong(),
        contentUri = "content://media/$key",
        displayName = name,
        mimeType = if (type == MediaType.PHOTO) "image/jpeg" else "video/mp4",
        mediaType = type,
        dateTakenMs = tsMs,
        dateAddedSec = tsMs / 1000L,
        dateModifiedSec = tsMs / 1000L,
        sizeBytes = size,
        width = 1920,
        height = 1080,
        durationMs = if (type == MediaType.VIDEO) 15_000L else 0L,
        bucketId = "cam",
        bucketName = "Camera",
        relativePath = "DCIM/Camera"
    )

    @Test
    fun `sortMedia sorts accurately by name, size, date, and type`() {
        val a = sampleItem("1", "Zebra.jpg", MediaType.PHOTO, 100L, 1000L)
        val b = sampleItem("2", "Alpha.mp4", MediaType.VIDEO, 5000L, 3000L)
        val c = sampleItem("3", "Mountain.jpg", MediaType.PHOTO, 2500L, 2000L)
        val list = listOf(a, b, c)

        assertEquals(listOf("2", "3", "1"), GalleryViewModel.sortMedia(list, MediaSortOrder.DATE_DESC).map { it.mediaKey })
        assertEquals(listOf("1", "3", "2"), GalleryViewModel.sortMedia(list, MediaSortOrder.DATE_ASC).map { it.mediaKey })
        assertEquals(listOf("2", "3", "1"), GalleryViewModel.sortMedia(list, MediaSortOrder.NAME_ASC).map { it.mediaKey })
        assertEquals(listOf("2", "3", "1"), GalleryViewModel.sortMedia(list, MediaSortOrder.SIZE_DESC).map { it.mediaKey })
        assertEquals(listOf("3", "1", "2"), GalleryViewModel.sortMedia(list, MediaSortOrder.TYPE).map { it.mediaKey })
    }

    @Test
    fun `PhotoEditState tracks default vs modified state`() {
        val defaultState = PhotoEditState()
        assertTrue(defaultState.isDefault)

        val edited = defaultState.copy(brightness = 20f)
        assertFalse(edited.isDefault)
    }
}
