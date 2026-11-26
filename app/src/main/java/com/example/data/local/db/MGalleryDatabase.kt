package com.example.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        FavoriteEntity::class,
        RecentlyViewedEntity::class,
        UserAlbumEntity::class,
        AlbumMediaCrossRef::class,
        ImportedMediaEntity::class,
        TrashEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class MGalleryDatabase : RoomDatabase() {
    abstract fun galleryDao(): GalleryDao

    companion object {
        fun build(context: Context): MGalleryDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                MGalleryDatabase::class.java,
                "mgallery_database.db"
            ).build()
        }
    }
}
