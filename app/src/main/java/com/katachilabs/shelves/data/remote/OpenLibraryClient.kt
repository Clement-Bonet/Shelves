package com.katachilabs.shelves.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Open Library (Internet Archive) : sans clé, utile surtout pour les couvertures.
 * Documentation : https://openlibrary.org/developers/api
 */
class OpenLibraryClient(private val http: Http, private val json: Json) {

    suspend fun findByIsbn(isbn: String): BookMetadata? {
        val body = http.get("https://openlibrary.org/api/books?bibkeys=ISBN:$isbn&format=json&jscmd=data") ?: return null
        val book = json.parseToJsonElement(body).jsonObject["ISBN:$isbn"] as? JsonObject ?: return null
        val title = book.string("title") ?: return null
        val cover = book["cover"] as? JsonObject
        return BookMetadata(
            isbn = isbn,
            title = title,
            subtitle = book.string("subtitle"),
            authors = book.names("authors"),
            publisher = book.names("publishers").firstOrNull(),
            publishedDate = book.string("publish_date"),
            pageCount = book["number_of_pages"]?.jsonPrimitive?.intOrNull,
            coverUrl = cover?.string("large") ?: cover?.string("medium"),
            sources = listOf("Open Library"),
        )
    }

    private fun JsonObject.string(key: String) = (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
    private fun JsonObject.names(key: String) =
        (this[key] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.string("name") }

    companion object {
        /** URL de couverture par ISBN ; renvoie 404 (et non une image vide) quand il n'y en a pas. */
        fun coverUrl(isbn: String) = "https://covers.openlibrary.org/b/isbn/$isbn-M.jpg?default=false"
    }
}
