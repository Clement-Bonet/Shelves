package fr.shelves.data

import android.util.Log
import androidx.room.withTransaction
import fr.shelves.data.db.Book
import fr.shelves.data.db.Series
import fr.shelves.data.db.Shelf
import fr.shelves.data.db.ShelfBook
import fr.shelves.data.db.ShelvesDatabase
import fr.shelves.data.remote.BookLookupService
import fr.shelves.data.remote.BookMetadata
import fr.shelves.data.remote.OpenLibraryClient
import fr.shelves.data.remote.SeriesRef
import fr.shelves.data.remote.normalizedKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BookRepository(
    private val db: ShelvesDatabase,
    private val lookupService: BookLookupService,
    private val settings: SettingsStore,
    private val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val shelfDao = db.shelfDao()
    private val bookDao = db.bookDao()
    private val seriesDao = db.seriesDao()

    // --- Lecture ---

    fun shelvesWithCount() = shelfDao.observeWithCount()
    fun shelves() = shelfDao.observeAll()
    fun shelf(id: Long) = shelfDao.observe(id)
    fun shelvesOfBook(bookId: Long) = shelfDao.observeShelvesOfBook(bookId)
    fun booksInShelf(shelfId: Long) = bookDao.observeInShelf(shelfId)
    fun book(id: Long) = bookDao.observe(id)
    fun series(id: String) = seriesDao.observe(id)
    fun allSeries() = seriesDao.observeAll()
    fun seriesVolumes(seriesId: String) = bookDao.observeSeriesVolumes(seriesId)
    fun seriesStats() = bookDao.observeSeriesStats()
    fun loans() = bookDao.observeLoans()

    suspend fun findByCode(code: String): Book? = bookDao.findByIsbn(code)
    suspend fun getBook(id: Long): Book? = bookDao.get(id)

    // --- Étagères ---

    suspend fun createShelf(name: String, description: String?): Long =
        shelfDao.insert(Shelf(name = name.trim(), description = description?.trim()?.ifBlank { null }))

    suspend fun updateShelf(shelf: Shelf) = shelfDao.update(shelf)
    suspend fun deleteShelf(shelf: Shelf) = shelfDao.delete(shelf)
    suspend fun addToShelf(shelfId: Long, bookId: Long) = shelfDao.addBook(ShelfBook(shelfId, bookId))
    suspend fun removeFromShelf(shelfId: Long, bookId: Long) = shelfDao.removeBook(shelfId, bookId)

    // --- Livres ---

    suspend fun lookup(code: String): BookMetadata? =
        lookupService.lookup(code, settings.currentGoogleBooksApiKey())

    /**
     * Enregistre un livre trouvé (ou complète celui déjà connu, par exemple un volume
     * de série non possédé), le marque possédé et le range dans les étagères choisies.
     */
    suspend fun saveOwned(meta: BookMetadata, shelfIds: Set<Long>): Long = db.withTransaction {
        val seriesId = meta.series?.let { upsertSeries(it, meta.publisher) }
        val existing = meta.isbn?.let { bookDao.findByIsbn(it) } ?: meta.bnfArk?.let { bookDao.findByArk(it) }
        val bookId = if (existing != null) {
            bookDao.update(existing.mergedWith(meta, seriesId).copy(owned = true))
            existing.id
        } else {
            bookDao.insert(meta.toBook(seriesId, owned = true))
        }
        shelfIds.forEach { shelfDao.addBook(ShelfBook(it, bookId)) }
        bookId
    }

    suspend fun update(book: Book) = bookDao.update(book)
    suspend fun delete(book: Book) = bookDao.delete(book)

    suspend fun setOwned(book: Book, owned: Boolean, shelfId: Long?) = db.withTransaction {
        bookDao.update(book.copy(owned = owned))
        if (owned && shelfId != null) shelfDao.addBook(ShelfBook(shelfId, book.id))
    }

    suspend fun lend(book: Book, contactName: String, contactUri: String?) =
        bookDao.update(book.copy(loanedTo = contactName, loanedContactUri = contactUri, loanedAt = System.currentTimeMillis()))

    suspend fun returned(book: Book) =
        bookDao.update(book.copy(loanedTo = null, loanedContactUri = null, loanedAt = null))

    /** Relance la recherche en ligne et complète la fiche sans écraser les champs déjà remplis. */
    suspend fun refresh(book: Book): Boolean {
        val code = book.isbn ?: return false
        val meta = lookup(code) ?: return false
        val seriesId = meta.series?.let { upsertSeries(it, meta.publisher) }
        bookDao.update(book.mergedWith(meta, seriesId))
        return true
    }

    // --- Séries ---

    /**
     * Récupère à la BnF les volumes de la série et ajoute ceux qui manquent, non possédés.
     * Renvoie le nombre de volumes ajoutés.
     */
    suspend fun syncSeries(seriesId: String): Int {
        val series = seriesDao.get(seriesId) ?: return 0
        val volumes = lookupService.seriesVolumes(series.title, series.publisher, series.bnfRecordId)
        var added = 0
        db.withTransaction {
            for (volume in volumes) {
                val existing = volume.isbn?.let { bookDao.findByIsbn(it) } ?: volume.bnfArk?.let { bookDao.findByArk(it) }
                if (existing != null) {
                    bookDao.update(existing.mergedWith(volume, seriesId))
                } else {
                    if (bookDao.insertIfAbsent(volume.toBook(seriesId, owned = false)) != -1L) added++
                }
            }
            seriesDao.upsert(series.copy(lastSyncedAt = System.currentTimeMillis()))
        }
        return added
    }

    suspend fun seriesNeverSynced(seriesId: String): Boolean = seriesDao.get(seriesId)?.lastSyncedAt == null

    /** Synchronisation qui survit à la fermeture de l'écran qui l'a lancée. */
    fun syncSeriesInBackground(seriesId: String) {
        appScope.launch {
            try {
                syncSeries(seriesId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("Shelves", "Synchronisation de la série $seriesId en échec", e)
            }
        }
    }

    private suspend fun upsertSeries(ref: SeriesRef, publisher: String?): String {
        val id = seriesKey(ref, publisher)
        val existing = seriesDao.get(id)
        seriesDao.upsert(
            Series(
                id = id,
                title = existing?.title ?: ref.title,
                publisher = existing?.publisher ?: publisher,
                bnfRecordId = existing?.bnfRecordId ?: ref.bnfRecordId,
                lastSyncedAt = existing?.lastSyncedAt,
            ),
        )
        return id
    }

    companion object {
        fun seriesKey(ref: SeriesRef, publisher: String?): String =
            ref.bnfRecordId?.let { "bnf:$it" }
                ?: "t:${ref.title.normalizedKey()}|${publisher.orEmpty().normalizedKey()}"

        fun BookMetadata.toBook(seriesId: String?, owned: Boolean) = Book(
            isbn = isbn,
            title = title,
            subtitle = subtitle,
            authors = authors.joinToString(", ").ifBlank { null },
            publisher = publisher,
            publishedDate = publishedDate,
            pageCount = pageCount,
            summary = summary,
            coverUrl = coverUrl ?: isbn?.let { OpenLibraryClient.coverUrl(it) },
            seriesId = seriesId,
            volumeNumber = series?.volumeNumber,
            volumeLabel = series?.volumeLabel,
            bnfArk = bnfArk,
            sources = sources.joinToString(", ").ifBlank { null },
            owned = owned,
        )

        /** Complète une fiche existante : les valeurs déjà présentes (éventuellement saisies) sont conservées. */
        fun Book.mergedWith(meta: BookMetadata, seriesId: String?) = copy(
            isbn = isbn ?: meta.isbn,
            subtitle = subtitle ?: meta.subtitle,
            authors = authors ?: meta.authors.joinToString(", ").ifBlank { null },
            publisher = publisher ?: meta.publisher,
            publishedDate = publishedDate ?: meta.publishedDate,
            pageCount = pageCount ?: meta.pageCount,
            summary = summary ?: meta.summary,
            coverUrl = meta.coverUrl?.takeIf { coverUrl == null || coverUrl.startsWith(OL_COVER_PREFIX) } ?: coverUrl
                ?: (isbn ?: meta.isbn)?.let { OpenLibraryClient.coverUrl(it) },
            seriesId = this.seriesId ?: seriesId,
            volumeNumber = volumeNumber ?: meta.series?.volumeNumber,
            volumeLabel = volumeLabel ?: meta.series?.volumeLabel,
            bnfArk = bnfArk ?: meta.bnfArk,
            sources = (sources?.split(", ").orEmpty() + meta.sources).distinct().joinToString(", ").ifBlank { null },
        )

        private const val OL_COVER_PREFIX = "https://covers.openlibrary.org/b/isbn/"
    }
}
