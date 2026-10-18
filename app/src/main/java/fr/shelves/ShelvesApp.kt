package fr.shelves

import android.app.Application
import fr.shelves.data.BackupManager
import fr.shelves.data.BookRepository
import fr.shelves.data.SettingsStore
import fr.shelves.data.db.ShelvesDatabase
import fr.shelves.data.remote.BnfClient
import fr.shelves.data.remote.BookLookupService
import fr.shelves.data.remote.GoogleBooksClient
import fr.shelves.data.remote.Http
import fr.shelves.data.remote.OpenLibraryClient
import kotlinx.serialization.json.Json

class ShelvesApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Assemblage manuel des dépendances, partagé par les écrans. */
class AppContainer(app: Application) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val http = Http()
    private val database = ShelvesDatabase.create(app)

    val settings = SettingsStore(app)
    val repository = BookRepository(
        db = database,
        lookupService = BookLookupService(
            bnf = BnfClient(http),
            openLibrary = OpenLibraryClient(http, json),
            googleBooks = GoogleBooksClient(http, json),
        ),
        settings = settings,
    )
    val backup = BackupManager(app, database, json)
}
