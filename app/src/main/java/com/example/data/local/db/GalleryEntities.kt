package com.example.data.local.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val mediaKey: String,
    val addedAtMs: Long
)

@Entity(tableName = "recently_viewed")
data class RecentlyViewedEntity(
    @PrimaryKey val mediaKey: String,
    val viewedAtMs: Long
)

@Entity(tableName = "user_albums")
data class UserAlbumEntity(
    @PrimaryKey val albumId: String,
    val name: String,
    val coverUri: String?,
    val createdAtMs: Long
)

@Entity(
    tableName = "album_media_cross_ref",
    primaryKeys = ["albumId", "mediaKey"],
    foreignKeys = [
        ForeignKey(
            entity = UserAlbumEntity::class,
            parentColumns = ["albumId"],
            childColumns = ["albumId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("albumId"), Index("mediaKey")]
)
data class AlbumMediaCrossRef(
    val albumId: String,
    val mediaKey: String,
    val addedAtMs: Long
)

@Entity(tableName = "imported_media")
data class ImportedMediaEntity(
    @PrimaryKey val mediaKey: String,
    val contentUri: String,
    val displayName: String,
    val mimeType: String,
    val isVideo: Boolean,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val dateTakenMs: Long,
    val addedAtMs: Long
)

@Entity(tableName = "recycle_bin")
data class TrashEntity(
    @PrimaryKey val id: String,
    val originalUri: String?,
    val displayName: String,
    val mimeType: String,
    val isVideo: Boolean,
    val trashedFilePath: String,
    val trashedThumbPath: String?,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val trashedAtMs: Long,
    val expiresAtMs: Long
)
