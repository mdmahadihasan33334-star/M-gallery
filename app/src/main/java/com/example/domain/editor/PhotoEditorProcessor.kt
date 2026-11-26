package com.example.domain.editor

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.example.data.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.abs

enum class FilterPreset(val label: String) {
    ORIGINAL("Original"),
    VIVID("Vivid"),
    NOIR("Noir"),
    WARM_AMBER("Amber"),
    COOL_SLATE("Slate"),
    MATTE_FILM("Matte"),
    VIOLET_DUSK("Dusk")
}

enum class AspectRatioPreset(val label: String, val ratio: Float?) {
    FREE("Free", null),
    SQUARE("1:1", 1f),
    FOUR_THREE("4:3", 4f / 3f),
    THREE_FOUR("3:4", 3f / 4f),
    SIXTEEN_NINE("16:9", 16f / 9f),
    NINE_SIXTEEN("9:16", 9f / 16f)
}

data class NormalizedCropRect(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f
) {
    val width: Float get() = (right - left).coerceAtLeast(0.05f)
    val height: Float get() = (bottom - top).coerceAtLeast(0.05f)
    val isFull: Boolean get() = left <= 0.005f && top <= 0.005f && right >= 0.995f && bottom >= 0.995f
}

data class PhotoEditState(
    val cropRect: NormalizedCropRect = NormalizedCropRect(),
    val aspectPreset: AspectRatioPreset = AspectRatioPreset.FREE,
    val rotationQuarterTurns: Int = 0, // 0, 1, 2, 3 (90 deg steps)
    val straightAngleDeg: Float = 0f,  // -45f..45f
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val brightness: Float = 0f,        // -100f..100f
    val contrast: Float = 1f,          // 0.5f..1.5f
    val saturation: Float = 1f,        // 0f..2.0f
    val warmth: Float = 0f,            // -1f..1f
    val vignette: Float = 0f,          // 0f..1f
    val filterPreset: FilterPreset = FilterPreset.ORIGINAL
) {
    val isDefault: Boolean
        get() = cropRect.isFull &&
            rotationQuarterTurns == 0 &&
            abs(straightAngleDeg) < 0.1f &&
            !flipHorizontal &&
            !flipVertical &&
            abs(brightness) < 0.5f &&
            abs(contrast - 1f) < 0.01f &&
            abs(saturation - 1f) < 0.01f &&
            abs(warmth) < 0.01f &&
            vignette < 0.01f &&
            filterPreset == FilterPreset.ORIGINAL
}

class PhotoEditorProcessor(
    private val context: Context,
    private val mediaRepository: MediaRepository
) {
    suspend fun decodeForEditing(uri: Uri, maxDimension: Int = 1600): Bitmap? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

        val sample = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension, maxDimension)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: return@withContext null

        val exifOrientation = runCatching {
            resolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

        val oriented = applyExifOrientation(decoded, exifOrientation)
        scaleDownIfNeeded(oriented, maxDimension)
    }

    fun renderEditedBitmap(source: Bitmap, state: PhotoEditState, applyCrop: Boolean = true): Bitmap {
        // 1. Apply geometric transforms (quarter turns, straight angle, flips)
        val matrix = Matrix().apply {
            if (state.flipHorizontal || state.flipVertical) {
                postScale(
                    if (state.flipHorizontal) -1f else 1f,
                    if (state.flipVertical) -1f else 1f,
                    source.width / 2f,
                    source.height / 2f
                )
            }
            val totalAngle = (state.rotationQuarterTurns * 90f) + state.straightAngleDeg
            if (abs(totalAngle) > 0.05f) {
                postRotate(totalAngle, source.width / 2f, source.height / 2f)
            }
        }
        val transformed = if (!matrix.isIdentity) {
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        } else {
            source
        }

        // 2. Crop if requested
        val cropped = if (applyCrop && !state.cropRect.isFull) {
            val left = (state.cropRect.left * transformed.width).toInt().coerceIn(0, transformed.width - 2)
            val top = (state.cropRect.top * transformed.height).toInt().coerceIn(0, transformed.height - 2)
            val right = (state.cropRect.right * transformed.width).toInt().coerceIn(left + 1, transformed.width)
            val bottom = (state.cropRect.bottom * transformed.height).toInt().coerceIn(top + 1, transformed.height)
            val result = Bitmap.createBitmap(transformed, left, top, right - left, bottom - top)
            if (transformed !== source) transformed.recycle()
            result
        } else {
            transformed
        }

        // 3. Apply ColorMatrix (adjustments + filter) + vignette into output bitmap
        val out = Bitmap.createBitmap(cropped.width, cropped.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        paint.colorFilter = ColorMatrixColorFilter(buildColorMatrix(state))
        canvas.drawBitmap(cropped, 0f, 0f, paint)

        if (state.vignette > 0.01f) {
            val cx = out.width / 2f
            val cy = out.height / 2f
            val radius = maxOf(out.width, out.height) * 0.75f
            val vigPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    cx, cy, radius,
                    intArrayOf(0x00000000, ((state.vignette * 190).toInt() shl 24)),
                    floatArrayOf(0.45f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(RectF(0f, 0f, out.width.toFloat(), out.height.toFloat()), vigPaint)
        }

        if (cropped !== source && cropped !== transformed) {
            cropped.recycle()
        } else if (cropped !== source) {
            cropped.recycle()
        }
        return out
    }

    fun buildColorMatrix(state: PhotoEditState): ColorMatrix {
        val cm = ColorMatrix()

        // Filter preset base matrix
        val preset = ColorMatrix()
        when (state.filterPreset) {
            FilterPreset.ORIGINAL -> Unit
            FilterPreset.VIVID -> {
                preset.setSaturation(1.38f)
                val c = 1.12f
                val t = (-0.5f * c + 0.5f) * 255f
                preset.postConcat(ColorMatrix(floatArrayOf(
                    c, 0f, 0f, 0f, t,
                    0f, c, 0f, 0f, t,
                    0f, 0f, c, 0f, t,
                    0f, 0f, 0f, 1f, 0f
                )))
            }
            FilterPreset.NOIR -> {
                preset.setSaturation(0f)
                val c = 1.2f
                val t = (-0.5f * c + 0.5f) * 255f
                preset.postConcat(ColorMatrix(floatArrayOf(
                    c, 0f, 0f, 0f, t,
                    0f, c, 0f, 0f, t,
                    0f, 0f, c, 0f, t,
                    0f, 0f, 0f, 1f, 0f
                )))
            }
            FilterPreset.WARM_AMBER -> {
                preset.postConcat(ColorMatrix(floatArrayOf(
                    1.12f, 0f, 0f, 0f, 10f,
                    0f, 1.03f, 0f, 0f, 4f,
                    0f, 0f, 0.88f, 0f, -6f,
                    0f, 0f, 0f, 1f, 0f
                )))
            }
            FilterPreset.COOL_SLATE -> {
                preset.setSaturation(0.85f)
                preset.postConcat(ColorMatrix(floatArrayOf(
                    0.92f, 0f, 0f, 0f, -4f,
                    0f, 1.0f, 0f, 0f, 2f,
                    0f, 0f, 1.14f, 0f, 10f,
                    0f, 0f, 0f, 1f, 0f
                )))
            }
            FilterPreset.MATTE_FILM -> {
                preset.setSaturation(0.88f)
                preset.postConcat(ColorMatrix(floatArrayOf(
                    0.92f, 0f, 0f, 0f, 16f,
                    0f, 0.92f, 0f, 0f, 16f,
                    0f, 0f, 0.90f, 0f, 18f,
                    0f, 0f, 0f, 1f, 0f
                )))
            }
            FilterPreset.VIOLET_DUSK -> {
                preset.postConcat(ColorMatrix(floatArrayOf(
                    1.06f, 0f, 0.05f, 0f, 4f,
                    0f, 0.94f, 0f, 0f, 0f,
                    0.06f, 0f, 1.12f, 0f, 12f,
                    0f, 0f, 0f, 1f, 0f
                )))
            }
        }
        cm.postConcat(preset)

        // Saturation
        if (abs(state.saturation - 1f) > 0.01f) {
            val sat = ColorMatrix().apply { setSaturation(state.saturation) }
            cm.postConcat(sat)
        }

        // Contrast & Brightness
        if (abs(state.contrast - 1f) > 0.01f || abs(state.brightness) > 0.5f) {
            val c = state.contrast
            val shift = ((-0.5f * c + 0.5f) * 255f) + state.brightness
            cm.postConcat(ColorMatrix(floatArrayOf(
                c, 0f, 0f, 0f, shift,
                0f, c, 0f, 0f, shift,
                0f, 0f, c, 0f, shift,
                0f, 0f, 0f, 1f, 0f
            )))
        }

        // Warmth
        if (abs(state.warmth) > 0.01f) {
            val w = state.warmth * 26f
            cm.postConcat(ColorMatrix(floatArrayOf(
                1f, 0f, 0f, 0f, w,
                0f, 1f, 0f, 0f, w * 0.25f,
                0f, 0f, 1f, 0f, -w,
                0f, 0f, 0f, 1f, 0f
            )))
        }

        return cm
    }

    suspend fun saveAsNewCopy(
        sourceUri: Uri,
        originalName: String,
        state: PhotoEditState
    ): Uri? = withContext(Dispatchers.IO) {
        val fullSource = decodeForEditing(sourceUri, maxDimension = 3840) ?: return@withContext null
        val edited = renderEditedBitmap(fullSource, state, applyCrop = true)
        fullSource.recycle()

        val baos = ByteArrayOutputStream()
        edited.compress(Bitmap.CompressFormat.JPEG, 95, baos)
        edited.recycle()

        val baseName = originalName.substringBeforeLast('.').ifBlank { "IMG" }
        val newName = "${baseName}_edited_${System.currentTimeMillis() % 100000}.jpg"
        mediaRepository.saveBytesToGallery(
            bytes = baos.toByteArray(),
            displayName = newName,
            mimeType = "image/jpeg",
            isVideo = false
        )
    }

    suspend fun overwriteOriginal(
        targetUri: Uri,
        state: PhotoEditState
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val fullSource = decodeForEditing(targetUri, maxDimension = 3840)
                ?: error("Could not decode original image")
            val edited = renderEditedBitmap(fullSource, state, applyCrop = true)
            fullSource.recycle()

            val baos = ByteArrayOutputStream()
            edited.compress(Bitmap.CompressFormat.JPEG, 95, baos)
            edited.recycle()

            val resolver: ContentResolver = context.contentResolver
            resolver.openOutputStream(targetUri, "wt")?.use { out ->
                out.write(baos.toByteArray())
            } ?: error("Storage denied direct overwrite permission for this item.")
            Unit
        }
    }

    fun needsWriteConsentForOverwrite(uri: Uri): android.app.PendingIntent? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && uri.scheme == "content" && uri.authority == MediaStore.AUTHORITY) {
            return runCatching {
                MediaStore.createWriteRequest(context.contentResolver, listOf(uri))
            }.getOrNull()
        }
        return null
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap, maxDim: Int): Bitmap {
        val maxSide = maxOf(bitmap.width, bitmap.height)
        if (maxSide <= maxDim) return bitmap
        val ratio = maxDim.toFloat() / maxSide
        val w = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val h = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }

    private fun calculateInSampleSize(width: Int, height: Int, reqW: Int, reqH: Int): Int {
        var inSampleSize = 1
        if (height > reqH || width > reqW) {
            val halfH = height / 2
            val halfW = width / 2
            while (halfH / inSampleSize >= reqH && halfW / inSampleSize >= reqW) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }
}
