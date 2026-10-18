package com.katachilabs.shelves.data.remote

import android.util.Log
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Interroge les sources en parallèle et fusionne les réponses :
 * la BnF fait foi pour la notice et la série, Google Books et Open Library complètent
 * le résumé et la couverture.
 */
class BookLookupService(
    private val bnf: BnfClient,
    private val openLibrary: OpenLibraryClient,
    private val googleBooks: GoogleBooksClient,
) {
    suspend fun lookup(code: String, googleApiKey: String?): BookMetadata? = coroutineScope {
        val bnfResult = async { attempt("BnF") { bnf.findByIsbn(code) } }
        val googleResult = async { attempt("Google Books") { googleBooks.findByIsbn(code, googleApiKey) } }
        val olResult = async { attempt("Open Library") { openLibrary.findByIsbn(code) } }
        merge(code, listOfNotNull(bnfResult.await(), googleResult.await(), olResult.await()))
    }

    suspend fun seriesVolumes(title: String, publisher: String?, bnfRecordId: String?): List<BookMetadata> =
        bnf.findSeriesVolumes(title, publisher, bnfRecordId)

    private suspend fun <T> attempt(source: String, block: suspend () -> T?): T? =
        try {
            block()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("Shelves", "Recherche $source en échec", e)
            null
        }

    companion object {
        /** Les résultats sont triés par priorité : le premier qui a une valeur l'emporte. */
        fun merge(code: String, results: List<BookMetadata>): BookMetadata? {
            if (results.isEmpty()) return null
            fun <T> pick(selector: (BookMetadata) -> T?): T? = results.firstNotNullOfOrNull(selector)
            return BookMetadata(
                isbn = pick { it.isbn } ?: code,
                title = results.first().title,
                subtitle = pick { it.subtitle },
                authors = results.firstOrNull { it.authors.isNotEmpty() }?.authors.orEmpty(),
                publisher = pick { it.publisher },
                publishedDate = pick { it.publishedDate },
                pageCount = pick { it.pageCount },
                summary = pick { it.summary },
                coverUrl = pick { it.coverUrl } ?: OpenLibraryClient.coverUrl(pick { it.isbn } ?: code),
                series = pick { it.series },
                bnfArk = pick { it.bnfArk },
                sources = results.flatMap { it.sources }.distinct(),
            )
        }
    }
}
