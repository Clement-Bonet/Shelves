package com.katachilabs.shelves.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ShelfDao {
    @Query(
        """
        SELECT s.*, COUNT(sb.bookId) AS bookCount
        FROM shelves s LEFT JOIN shelf_books sb ON sb.shelfId = s.id
        GROUP BY s.id
        ORDER BY s.name COLLATE NOCASE
        """,
    )
    fun observeWithCount(): Flow<List<ShelfWithCount>>

    @Query("SELECT * FROM shelves ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Shelf>>

    @Query("SELECT * FROM shelves WHERE id = :id")
    fun observe(id: Long): Flow<Shelf?>

    @Query(
        """
        SELECT s.* FROM shelves s JOIN shelf_books sb ON sb.shelfId = s.id
        WHERE sb.bookId = :bookId ORDER BY s.name COLLATE NOCASE
        """,
    )
    fun observeShelvesOfBook(bookId: Long): Flow<List<Shelf>>

    @Insert
    suspend fun insert(shelf: Shelf): Long

    @Update
    suspend fun update(shelf: Shelf)

    @Delete
    suspend fun delete(shelf: Shelf)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addBook(link: ShelfBook)

    @Query("DELETE FROM shelf_books WHERE shelfId = :shelfId AND bookId = :bookId")
    suspend fun removeBook(shelfId: Long, bookId: Long)

    @Query("DELETE FROM shelf_books WHERE shelfId = :shelfId AND bookId IN (:bookIds)")
    suspend fun removeBooks(shelfId: Long, bookIds: List<Long>)

    @Query("SELECT * FROM shelves")
    suspend fun all(): List<Shelf>

    @Query("SELECT * FROM shelf_books")
    suspend fun allLinks(): List<ShelfBook>

    @Insert
    suspend fun insertAll(shelves: List<Shelf>)

    @Insert
    suspend fun insertAllLinks(links: List<ShelfBook>)

    @Query("DELETE FROM shelves")
    suspend fun deleteAll()
}

@Dao
interface BookDao {
    @Query(
        """
        SELECT b.* FROM books b JOIN shelf_books sb ON sb.bookId = b.id
        WHERE sb.shelfId = :shelfId
        ORDER BY b.title COLLATE NOCASE
        """,
    )
    fun observeInShelf(shelfId: Long): Flow<List<Book>>

    /**
     * Livres possédés rangés dans aucune étagère. Les tomes de série non possédés, récupérés
     * à la BnF, n'y figurent pas : ils restent visibles depuis leur série.
     */
    @Query("SELECT * FROM books WHERE owned = 1 AND id NOT IN (SELECT bookId FROM shelf_books) ORDER BY title COLLATE NOCASE")
    fun observeUnshelved(): Flow<List<Book>>

    @Query("SELECT COUNT(*) FROM books WHERE owned = 1 AND id NOT IN (SELECT bookId FROM shelf_books)")
    fun observeUnshelvedCount(): Flow<Int>

    @Query("SELECT * FROM books WHERE id = :id")
    fun observe(id: Long): Flow<Book?>

    @Query("SELECT * FROM books WHERE seriesId = :seriesId ORDER BY volumeNumber IS NULL, volumeNumber, title COLLATE NOCASE")
    fun observeSeriesVolumes(seriesId: String): Flow<List<Book>>

    @Query("SELECT seriesId, COUNT(*) AS total, SUM(owned) AS owned FROM books WHERE seriesId IS NOT NULL GROUP BY seriesId")
    fun observeSeriesStats(): Flow<List<SeriesStats>>

    @Query("SELECT * FROM books WHERE loanedTo IS NOT NULL ORDER BY loanedAt DESC")
    fun observeLoans(): Flow<List<Book>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun get(id: Long): Book?

    @Query("SELECT * FROM books WHERE isbn = :isbn")
    suspend fun findByIsbn(isbn: String): Book?

    @Query("SELECT * FROM books WHERE bnfArk = :ark")
    suspend fun findByArk(ark: String): Book?

    @Insert
    suspend fun insert(book: Book): Long

    /** Renvoie -1 si le livre (même ISBN ou même notice BnF) existe déjà. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(book: Book): Long

    @Update
    suspend fun update(book: Book)

    /** Range des livres choisis dans une série (ou aucune), à la main. */
    @Query("UPDATE books SET seriesId = :seriesId, seriesLocked = 1 WHERE id IN (:bookIds)")
    suspend fun setSeries(bookIds: List<Long>, seriesId: String?)

    @Query("SELECT * FROM books WHERE id IN (:bookIds)")
    suspend fun getAll(bookIds: List<Long>): List<Book>

    @Query("SELECT * FROM books WHERE seriesId = :seriesId")
    suspend fun seriesVolumes(seriesId: String): List<Book>

    /** Livres possédés sans série, candidats pour être rattachés à une série existante. */
    @Query("SELECT * FROM books WHERE seriesId IS NULL AND owned = 1 ORDER BY title COLLATE NOCASE")
    fun observeWithoutSeries(): Flow<List<Book>>

    @Query("DELETE FROM books WHERE id IN (:bookIds)")
    suspend fun deleteAll(bookIds: List<Long>)

    /** Déplace tous les volumes d'une série dans une autre (fusion manuelle). */
    @Query("UPDATE books SET seriesId = :targetId, seriesLocked = 1 WHERE seriesId = :sourceId")
    suspend fun moveSeries(sourceId: String, targetId: String)

    @Delete
    suspend fun delete(book: Book)

    @Query("SELECT * FROM books")
    suspend fun all(): List<Book>

    @Insert
    suspend fun insertAll(books: List<Book>)

    @Query("DELETE FROM books")
    suspend fun deleteAll()
}

@Dao
interface SeriesDao {
    @Query("SELECT * FROM series")
    fun observeAll(): Flow<List<Series>>

    /** Séries proposées à l'utilisateur : celles qui n'ont pas été fusionnées dans une autre. */
    @Query("SELECT * FROM series WHERE mergedInto IS NULL ORDER BY title COLLATE NOCASE")
    fun observeActive(): Flow<List<Series>>

    @Query("SELECT * FROM series WHERE mergedInto = :targetId")
    suspend fun mergedInto(targetId: String): List<Series>

    @Query("UPDATE series SET mergedInto = :targetId WHERE id = :sourceId OR mergedInto = :sourceId")
    suspend fun redirect(sourceId: String, targetId: String)

    @Query("SELECT * FROM series WHERE id = :id")
    fun observe(id: String): Flow<Series?>

    @Query("SELECT * FROM series WHERE id = :id")
    suspend fun get(id: String): Series?

    @Upsert
    suspend fun upsert(series: Series)

    /** Supprime une série et celles qui y avaient été fusionnées. */
    @Query("DELETE FROM series WHERE id = :id OR mergedInto = :id")
    suspend fun deleteWithMerged(id: String)

    @Query("SELECT * FROM series")
    suspend fun all(): List<Series>

    @Insert
    suspend fun insertAll(series: List<Series>)

    @Query("DELETE FROM series")
    suspend fun deleteAll()
}
