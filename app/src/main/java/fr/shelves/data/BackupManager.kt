package fr.shelves.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import fr.shelves.data.db.Book
import fr.shelves.data.db.Series
import fr.shelves.data.db.Shelf
import fr.shelves.data.db.ShelfBook
import fr.shelves.data.db.ShelvesDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException

@Serializable
data class BackupData(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val shelves: List<Shelf>,
    val series: List<Series>,
    val books: List<Book>,
    val shelfBooks: List<ShelfBook>,
)

/**
 * Export et import de toutes les données dans un fichier JSON. Le fichier est écrit
 * à l'endroit choisi dans le sélecteur de documents Android, donc aussi sur Google Drive.
 */
class BackupManager(
    private val context: Context,
    private val db: ShelvesDatabase,
    private val json: Json,
) {
    suspend fun export(uri: Uri): BackupData = withContext(Dispatchers.IO) {
        val data = BackupData(
            shelves = db.shelfDao().all(),
            series = db.seriesDao().all(),
            books = db.bookDao().all(),
            shelfBooks = db.shelfDao().allLinks(),
        )
        context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
            out.write(json.encodeToString(BackupData.serializer(), data).toByteArray())
        } ?: throw IOException("Impossible d'écrire le fichier")
        data
    }

    /** Remplace toutes les données par celles du fichier. */
    suspend fun import(uri: Uri): BackupData = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            ?: throw IOException("Impossible de lire le fichier")
        val data = json.decodeFromString(BackupData.serializer(), text)
        db.withTransaction {
            db.shelfDao().deleteAll()
            db.bookDao().deleteAll()
            db.seriesDao().deleteAll()
            db.seriesDao().insertAll(data.series)
            db.bookDao().insertAll(data.books)
            db.shelfDao().insertAll(data.shelves)
            db.shelfDao().insertAllLinks(data.shelfBooks)
        }
        data
    }
}
