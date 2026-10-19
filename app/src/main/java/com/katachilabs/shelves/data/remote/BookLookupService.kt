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
    /**
     * Renvoie la fiche fusionnée, ou `null` si aucune source ne connaît le code.
     * Lève [NoNetworkException] quand aucune source n'a pu être jointe : sans réseau, « inconnu » serait trompeur.
     */
    suspend fun lookup(code: String, googleApiKey: String?): BookMetadata? = coroutineScope {
        val failures = java.util.Collections.synchronizedList(mutableListOf<Exception>())
        val bnfResult = async {
            attempt("BnF", failures) {
                try {
                    bnf.findByIsbn(code)
                } catch (e: java.io.IOException) {
                    throw if (e.isNetworkFailure()) e else BnfUnavailableException(e)
                }
            }
        }
        // Google Books est facultatif : sans clé de l'utilisateur, le quota anonyme est toujours épuisé.
        val key = googleApiKey?.takeIf { it.isNotBlank() }
        val googleResult = async { key?.let { attempt("Google Books", failures) { googleBooks.findByIsbn(code, it) } } }
        val olResult = async { attempt("Open Library", failures) { openLibrary.findByIsbn(code) } }
        val results = listOfNotNull(bnfResult.await(), googleResult.await(), olResult.await())
        val sourcesTried = if (key != null) 3 else 2
        if (results.isEmpty() && failures.size >= sourcesTried && failures.all { it.isNetworkFailure() }) {
            throw NoNetworkException(failures.first())
        }
        if (results.isEmpty() && failures.any { it is BnfUnavailableException }) {
            // La BnF, source principale, n'a pas répondu : « livre inconnu » serait faux.
            throw failures.first { it is BnfUnavailableException }
        }
        merge(code, results)
    }

    suspend fun seriesVolumes(title: String, publisher: String?, bnfRecordId: String?): List<BookMetadata> =
        try {
            bnf.findSeriesVolumes(title, publisher, bnfRecordId)
        } catch (e: java.io.IOException) {
            if (e.isNetworkFailure()) throw NoNetworkException(e) else throw e
        }

    private suspend fun <T> attempt(source: String, failures: MutableList<Exception>, block: suspend () -> T?): T? =
        try {
            block()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("Shelves", "Recherche $source en échec", e)
            failures += e
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
