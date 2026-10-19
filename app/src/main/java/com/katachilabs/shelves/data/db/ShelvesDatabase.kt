package com.katachilabs.shelves.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Shelf::class, Series::class, Book::class, ShelfBook::class, DismissedVolume::class],
    version = 3,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
    exportSchema = true,
)
abstract class ShelvesDatabase : RoomDatabase() {
    abstract fun shelfDao(): ShelfDao
    abstract fun bookDao(): BookDao
    abstract fun seriesDao(): SeriesDao
    abstract fun dismissedDao(): DismissedVolumeDao

    companion object {
        const val NAME = "shelves.db"

        fun create(context: Context): ShelvesDatabase =
            Room.databaseBuilder(context, ShelvesDatabase::class.java, NAME).build()
    }
}
