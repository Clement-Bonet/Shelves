package fr.shelves.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Shelf::class, Series::class, Book::class, ShelfBook::class],
    version = 1,
    exportSchema = true,
)
abstract class ShelvesDatabase : RoomDatabase() {
    abstract fun shelfDao(): ShelfDao
    abstract fun bookDao(): BookDao
    abstract fun seriesDao(): SeriesDao

    companion object {
        const val NAME = "shelves.db"

        fun create(context: Context): ShelvesDatabase =
            Room.databaseBuilder(context, ShelvesDatabase::class.java, NAME).build()
    }
}
