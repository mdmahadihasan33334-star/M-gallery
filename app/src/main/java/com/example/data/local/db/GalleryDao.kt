package com.example.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GalleryDao {

    // Favorites
    @Query("SELECT mediaKey FROM favorites ORDER BY addedAtMs DESC")
    fun observeFavoriteKeys(): Flow<List<String>>

    @Query("SELECT * FROM favorites ORDER BY addedAtMs DESC")
    fun observeFavorites(): Flow<List<FavoriteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(entity: FavoriteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorites(entities: List<FavoriteEntity>)

    @Query("DELETE FROM favorites WHERE mediaKey = :mediaKey")
    suspend fun removeFavorite(mediaKey: String)

    @Query("DELETE FROM favorites WHERE mediaKey IN (:mediaKeys)")
    suspend fun removeFavorites(mediaKeys: List<String>)

    @Query("DELETE FROM favorites")
    suspend fun clearAllFavorites()

    // Recently Viewed
    @Query("SELECT * FROM recently_viewed ORDER BY viewedAtMs DESC LIMIT 40")
    fun observeRecentlyViewed(): Flow<List<RecentlyViewedEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun recordViewed(entity: RecentlyViewedEntity)

    @Query("DELETE FROM recently_viewed WHERE mediaKey = :mediaKey")
    suspend fun removeRecentlyViewed(mediaKey: String)

    // User Albums
    @Query("SELECT * FROM user_albums ORDER BY createdAtMs DESC")
    fun observeUserAlbums(): Flow<List<UserAlbumEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUserAlbum(album: UserAlbumEntity)

    @Query("UPDATE user_albums SET name = :newName WHERE albumId = :albumId")
    suspend fun renameUserAlbum(albumId: String, newName: String)

    @Query("UPDATE user_albums SET coverUri = :coverUri WHERE albumId = :albumId")
    suspend fun updateUserAlbumCover(albumId: String, coverUri: String?)

    @Query("DELETE FROM user_albums WHERE albumId = :albumId")
    suspend fun deleteUserAlbum(albumId: String)

    // Album Media Cross-Ref
    @Query("SELECT * FROM album_media_cross_ref ORDER BY addedAtMs DESC")
    fun observeAllAlbumCrossRefs(): Flow<List<AlbumMediaCrossRef>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addMediaToAlbum(crossRef: AlbumMediaCrossRef)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addMediaListToAlbum(crossRefs: List<AlbumMediaCrossRef>)

    @Query("DELETE FROM album_media_cross_ref WHERE albumId = :albumId AND mediaKey = :mediaKey")
    suspend fun removeMediaFromAlbum(albumId: String, mediaKey: String)

    @Query("DELETE FROM album_media_cross_ref WHERE mediaKey = :mediaKey")
    suspend fun removeMediaFromAllAlbums(mediaKey: String)

    // Imported Picker Media
    @Query("SELECT * FROM imported_media ORDER BY addedAtMs DESC")
    fun observeImportedMedia(): Flow<List<ImportedMediaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertImportedMedia(item: ImportedMediaEntity)

    @Query("DELETE FROM imported_media WHERE mediaKey = :mediaKey")
    suspend fun deleteImportedMedia(mediaKey: String)

    // Recycle Bin
    @Query("SELECT * FROM recycle_bin ORDER BY trashedAtMs DESC")
    fun observeTrashItems(): Flow<List<TrashEntity>>

    @Query("SELECT * FROM recycle_bin WHERE expiresAtMs <= :nowMs")
    suspend fun getExpiredTrashItems(nowMs: Long): List<TrashEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrashItem(item: TrashEntity)

    @Query("DELETE FROM recycle_bin WHERE id = :id")
    suspend fun deleteTrashItem(id: String)

    @Query("DELETE FROM recycle_bin")
    suspend fun clearTrashTable()
}
