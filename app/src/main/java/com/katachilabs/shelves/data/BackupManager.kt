package com.katachilabs.shelves.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.katachilabs.shelves.data.db.Book
import com.katachilabs.shelves.data.db.Series
import com.katachilabs.shelves.data.db.Shelf
import com.katachilabs.shelves.data.db.ShelfBook
import com.katachilabs.shelves.data.db.ShelvesDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Serializable
data class BackupData(
    val version: Int = 2,
    val exportedAt: Long = System.currentTimeMillis(),
    val shelves: List<Shelf>,
    val series: List<Series>,
    val books: List<Book>,
    val shelfBooks: List<ShelfBook>,
)

/**
 * Export et import de toutes les données dans une archive ZIP : `shelves.json` et les photos
 * de couverture (`covers/`). Le fichier est écrit à l'endroit choisi dans le sélecteur de
 * documents Android, donc aussi sur Google Drive. Les anciennes sauvegardes JSON restent lisibles.
 */
class BackupManager(
    private val context: Context,
    private val db: ShelvesDatabase,
    private val covers: CoverStore,
    private val json: Json,
) {
    data class Result(val shelves: Int, val books: Int, val photos: Int)

    suspend fun export(uri: Uri): Result = withContext(Dispatchers.IO) {
        val data = BackupData(
            shelves = db.shelfDao().all(),
            series = db.seriesDao().all(),
            books = db.bookDao().all(),
            shelfBooks = db.shelfDao().allLinks(),
        )
        val photos = covers.allFiles()
        val out = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Impossible d'écrire le fichier")
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(JSON_ENTRY))
            zip.write(json.encodeToString(BackupData.serializer(), data).toByteArray())
            zip.closeEntry()
            photos.forEach { file ->
                zip.putNextEntry(ZipEntry("${CoverStore.COVERS_DIR}/${file.name}"))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        Result(data.shelves.size, data.books.size, photos.size)
    }

    /** Remplace toutes les données (et les photos) par celles de la sauvegarde. */
    suspend fun import(uri: Uri): Result = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IOException("Impossible de lire le fichier")
        var text: String? = null
        val photos = mutableMapOf<String, ByteArray>()
        if (bytes.isZip()) {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                generateSequence { zip.nextEntry }.filterNot { it.isDirectory }.forEach { entry ->
                    when {
                        entry.name == JSON_ENTRY -> text = zip.readBytes().decodeToString()
                        entry.name.startsWith("${CoverStore.COVERS_DIR}/") -> photos[entry.name.substringAfterLast('/')] = zip.readBytes()
                    }
                }
            }
        } else {
            text = bytes.decodeToString()
        }
        val data = json.decodeFromString(BackupData.serializer(), text ?: throw IOException("Sauvegarde sans $JSON_ENTRY"))
        db.withTransaction {
            db.shelfDao().deleteAll()
            db.bookDao().deleteAll()
            db.seriesDao().deleteAll()
            db.seriesDao().insertAll(data.series)
            db.bookDao().insertAll(data.books)
            db.shelfDao().insertAll(data.shelves)
            db.shelfDao().insertAllLinks(data.shelfBooks)
        }
        covers.replaceAll(photos)
        Result(data.shelves.size, data.books.size, photos.size)
    }

    private fun ByteArray.isZip() = size >= 4 && this[0] == 'P'.code.toByte() && this[1] == 'K'.code.toByte()

    companion object {
        const val JSON_ENTRY = "shelves.json"
    }
}
