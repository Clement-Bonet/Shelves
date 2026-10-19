package com.katachilabs.shelves.data

import android.util.Log
import androidx.room.withTransaction
import com.katachilabs.shelves.data.db.Book
import com.katachilabs.shelves.data.db.DismissedVolume
import com.katachilabs.shelves.data.db.Series
import com.katachilabs.shelves.data.db.Shelf
import com.katachilabs.shelves.data.db.ShelfBook
import com.katachilabs.shelves.data.db.ShelvesDatabase
import com.katachilabs.shelves.data.remote.BookLookupService
import com.katachilabs.shelves.data.remote.BookMetadata
import com.katachilabs.shelves.data.remote.OpenLibraryClient
import com.katachilabs.shelves.data.remote.SeriesRef
import com.katachilabs.shelves.data.remote.normalizedKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

class BookRepository(
    private val db: ShelvesDatabase,
    private val lookupService: BookLookupService,
    private val settings: SettingsStore,
    private val covers: CoverStore,
    private val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val shelfDao = db.shelfDao()
    private val bookDao = db.bookDao()
    private val seriesDao = db.seriesDao()
    private val dismissedDao = db.dismissedDao()

    // --- Lecture ---

    fun shelvesWithCount() = shelfDao.observeWithCount()
    fun shelves() = shelfDao.observeAll()
    fun shelf(id: Long) = shelfDao.observe(id)
    fun shelvesOfBook(bookId: Long) = shelfDao.observeShelvesOfBook(bookId)
    fun booksInShelf(shelfId: Long) =
        if (shelfId == UNSHELVED_ID) bookDao.observeUnshelved() else bookDao.observeInShelf(shelfId)
    fun unshelvedCount() = bookDao.observeUnshelvedCount()

    /** Étagère virtuelle « Non rangés » : jamais en base, reconstruite à l'affichage. */
    fun shelfOrVirtual(id: Long) =
        if (id == UNSHELVED_ID) flowOf(Shelf(id = UNSHELVED_ID, name = UNSHELVED_NAME, description = UNSHELVED_DESCRIPTION))
        else shelfDao.observe(id)
    fun book(id: Long) = bookDao.observe(id)
    fun series(id: String) = seriesDao.observe(id)
    fun allSeries() = seriesDao.observeAll()
    fun activeSeries() = seriesDao.observeActive()
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
    suspend fun delete(book: Book) = deleteBooks(listOf(book.id))

    /** Enregistre comme couverture une image recadrée par le scanner de documents. */
    suspend fun saveScannedCover(book: Book, uri: android.net.Uri) {
        covers.importCapture(uri)
        saveCoverPhoto(book)
    }

    /** Enregistre la photo de couverture qui vient d'être prise ; l'ancienne photo éventuelle est supprimée. */
    suspend fun saveCoverPhoto(book: Book) {
        val reference = covers.saveCapture(book.id)
        bookDao.update(book.copy(coverUrl = reference))
        covers.delete(book.coverUrl)
    }

    /** Retire la photo : on revient à la couverture en ligne (Open Library) quand il y a un ISBN. */
    suspend fun removeCoverPhoto(book: Book) {
        bookDao.update(book.copy(coverUrl = book.isbn?.let { OpenLibraryClient.coverUrl(it) }))
        covers.delete(book.coverUrl)
    }

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
        // Les séries fusionnées dans celle-ci apportent aussi leurs volumes BnF.
        val volumes = (listOf(series) + seriesDao.mergedInto(seriesId))
            .filter { it.bnfRecordId != null || !it.id.startsWith(MANUAL_PREFIX) }
            .flatMap { lookupService.seriesVolumes(it.bnfTitle ?: it.title, it.bnfPublisher ?: it.publisher, it.bnfRecordId) }
        val dismissed = dismissedDao.keys().toSet()
        var added = 0
        db.withTransaction {
            for (volume in volumes) {
                // Tome supprimé par l'utilisateur (doublon d'édition, par exemple) : on ne le rajoute pas.
                if (volume.isbn in dismissed || volume.bnfArk in dismissed) continue
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

    // --- Opérations sur plusieurs livres ---

    fun booksWithoutSeries() = bookDao.observeWithoutSeries()

    /** Range les livres choisis dans une série (ou aucune) ; les synchronisations n'y toucheront plus. */
    suspend fun assignToSeries(bookIds: List<Long>, seriesId: String?) {
        if (bookIds.isNotEmpty()) bookDao.setSeries(bookIds, seriesId)
    }

    /** Range les livres dans [targetShelfId] ; ils quittent [fromShelfId] s'il est indiqué (déplacement). */
    suspend fun moveToShelf(bookIds: List<Long>, targetShelfId: Long, fromShelfId: Long?) = db.withTransaction {
        bookIds.forEach { shelfDao.addBook(ShelfBook(targetShelfId, it)) }
        if (fromShelfId != null && fromShelfId != targetShelfId) shelfDao.removeBooks(fromShelfId, bookIds)
    }

    /** Supprime définitivement les livres choisis (et leurs photos de couverture). */
    suspend fun deleteBooks(bookIds: List<Long>) {
        val books = bookDao.getAll(bookIds)
        db.withTransaction {
            bookDao.deleteAll(bookIds)
            // Les tomes de série supprimés ne doivent pas revenir à la prochaine synchronisation.
            dismissedDao.insertAll(
                books.filter { it.seriesId != null }.mapNotNull { book ->
                    (book.isbn ?: book.bnfArk)?.let { DismissedVolume(it, book.seriesId) }
                },
            )
        }
        books.forEach { covers.delete(it.coverUrl) }
    }

    fun dismissedCount(seriesId: String) = dismissedDao.observeCount(seriesId)

    /** Oublie les tomes supprimés de la série : la prochaine synchronisation les rajoutera. */
    suspend fun restoreDismissed(seriesId: String) = dismissedDao.clearSeries(seriesId)

    /** Supprime une série avec tous ses volumes ; renvoie le nombre de livres supprimés. */
    suspend fun deleteSeries(seriesId: String): Int {
        val volumes = bookDao.seriesVolumes(seriesId)
        db.withTransaction {
            bookDao.deleteAll(volumes.map { it.id })
            dismissedDao.clearSeries(seriesId)
            seriesDao.deleteWithMerged(seriesId)
        }
        volumes.forEach { covers.delete(it.coverUrl) }
        return volumes.size
    }

    // --- Corrections manuelles ---

    /**
     * Enregistre une fiche modifiée à la main. Renvoie un message d'erreur, ou `null` si tout va bien.
     * Les actualisations ne remplissent que les champs vides : les valeurs saisies sont conservées.
     */
    suspend fun saveEdited(edited: Book): String? {
        val isbn = edited.isbn
        if (isbn != null) {
            val other = bookDao.findByIsbn(isbn)
            if (other != null && other.id != edited.id) return "Un autre livre porte déjà cet ISBN : « ${other.title} »"
        }
        val original = bookDao.get(edited.id) ?: return "Livre introuvable"
        val volumeChanged = edited.volumeNumber != original.volumeNumber || edited.volumeLabel != original.volumeLabel
        // Un tome corrigé à la main ne doit plus être modifié par les synchronisations.
        bookDao.update(if (volumeChanged) edited.copy(seriesLocked = true) else edited)
        return null
    }

    /** Range un livre dans une série (ou aucune) avec son tome ; les synchronisations n'y toucheront plus. */
    suspend fun setBookSeries(book: Book, seriesId: String?, volumeNumber: Double?, volumeLabel: String?) =
        bookDao.update(
            book.copy(
                seriesId = seriesId,
                // Sans série, le tome est conservé : il resservira si le livre est rattaché à nouveau.
                volumeNumber = volumeNumber ?: book.volumeNumber.takeIf { seriesId == null },
                volumeLabel = volumeLabel?.trim()?.ifBlank { null } ?: book.volumeLabel.takeIf { seriesId == null },
                seriesLocked = true,
            ),
        )

    /** Crée une série saisie par l'utilisateur (ou renvoie celle qui porte déjà ce titre chez cet éditeur). */
    suspend fun createSeries(title: String, publisher: String?): String {
        val id = MANUAL_PREFIX + "${title.normalizedKey()}|${publisher.orEmpty().normalizedKey()}"
        val existing = seriesDao.get(id)
        if (existing != null) return existing.mergedInto ?: existing.id
        seriesDao.upsert(Series(id = id, title = title.trim(), publisher = publisher?.trim()?.ifBlank { null }))
        return id
    }

    suspend fun renameSeries(series: Series, title: String, publisher: String?) =
        seriesDao.upsert(series.copy(title = title.trim(), publisher = publisher?.trim()?.ifBlank { null }))

    /**
     * Fusionne une série dans une autre : ses volumes y sont déplacés, et les prochaines
     * synchronisations de la série cible rapatrieront aussi ceux de la série fusionnée.
     */
    suspend fun mergeSeries(sourceId: String, targetId: String) {
        if (sourceId == targetId) return
        db.withTransaction {
            bookDao.moveSeries(sourceId, targetId)
            seriesDao.redirect(sourceId, targetId)
        }
    }

    private suspend fun upsertSeries(ref: SeriesRef, publisher: String?): String {
        val id = seriesKey(ref, publisher)
        val existing = seriesDao.get(id)
        // Série fusionnée par l'utilisateur : les nouveaux volumes vont dans la série cible.
        existing?.mergedInto?.let { return it }
        seriesDao.upsert(
            Series(
                id = id,
                title = existing?.title ?: ref.title,
                publisher = existing?.publisher ?: publisher,
                bnfRecordId = existing?.bnfRecordId ?: ref.bnfRecordId,
                lastSyncedAt = existing?.lastSyncedAt,
                bnfTitle = existing?.bnfTitle ?: ref.title,
                bnfPublisher = existing?.bnfPublisher ?: publisher,
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
            seriesId = if (seriesLocked) this.seriesId else this.seriesId ?: seriesId,
            volumeNumber = if (seriesLocked) volumeNumber else volumeNumber ?: meta.series?.volumeNumber,
            volumeLabel = if (seriesLocked) volumeLabel else volumeLabel ?: meta.series?.volumeLabel,
            bnfArk = bnfArk ?: meta.bnfArk,
            sources = (sources?.split(", ").orEmpty() + meta.sources).distinct().joinToString(", ").ifBlank { null },
        )

        /** Identifiant de l'étagère virtuelle des livres possédés rangés nulle part. */
        const val UNSHELVED_ID = -2L
        const val UNSHELVED_NAME = "Non rangés"
        const val UNSHELVED_DESCRIPTION = "Livres possédés qui ne sont dans aucune étagère"

        /** Préfixe des séries créées à la main (aucune notice BnF à interroger). */
        const val MANUAL_PREFIX = "u:"

        private const val OL_COVER_PREFIX ="https://covers.openlibrary.org/b/isbn/"
    }
}
