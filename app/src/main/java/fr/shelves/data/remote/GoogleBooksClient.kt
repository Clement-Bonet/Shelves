package fr.shelves.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Google Books : résumés et couvertures. Sans clé, le quota anonyme partagé est souvent épuisé ;
 * une clé gratuite (console Google Cloud) donne 1 000 requêtes par jour.
 * Documentation : https://developers.google.com/books/docs/v1/using
 */
class GoogleBooksClient(private val http: Http, private val json: Json) {

    suspend fun findByIsbn(isbn: String, apiKey: String?): BookMetadata? {
        val key = apiKey?.takeIf { it.isNotBlank() }?.let { "&key=$it" }.orEmpty()
        val body = http.get("https://www.googleapis.com/books/v1/volumes?q=isbn:$isbn$key") ?: return null
        val info = json.decodeFromString<Response>(body).items.firstOrNull()?.volumeInfo ?: return null
        val title = info.title ?: return null
        return BookMetadata(
            isbn = isbn,
            title = title,
            subtitle = info.subtitle,
            authors = info.authors,
            publisher = info.publisher,
            publishedDate = info.publishedDate,
            pageCount = info.pageCount,
            summary = info.description,
            coverUrl = (info.imageLinks?.thumbnail ?: info.imageLinks?.smallThumbnail)
                ?.replace("http://", "https://")
                ?.replace("&edge=curl", ""),
            sources = listOf("Google Books"),
        )
    }

    @Serializable
    private data class Response(val items: List<Item> = emptyList())

    @Serializable
    private data class Item(val volumeInfo: VolumeInfo? = null)

    @Serializable
    private data class VolumeInfo(
        val title: String? = null,
        val subtitle: String? = null,
        val authors: List<String> = emptyList(),
        val publisher: String? = null,
        val publishedDate: String? = null,
        val description: String? = null,
        val pageCount: Int? = null,
        val imageLinks: ImageLinks? = null,
    )

    @Serializable
    private data class ImageLinks(val smallThumbnail: String? = null, val thumbnail: String? = null)
}
