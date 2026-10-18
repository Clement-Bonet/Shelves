package com.katachilabs.shelves

import android.app.Application
import com.katachilabs.shelves.data.BackupManager
import com.katachilabs.shelves.data.BookRepository
import com.katachilabs.shelves.data.SettingsStore
import com.katachilabs.shelves.data.db.ShelvesDatabase
import com.katachilabs.shelves.data.remote.AppSignature
import com.katachilabs.shelves.data.remote.BnfClient
import com.katachilabs.shelves.data.remote.BookLookupService
import com.katachilabs.shelves.data.remote.GoogleBooksClient
import com.katachilabs.shelves.data.remote.Http
import com.katachilabs.shelves.data.remote.OpenLibraryClient
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
            googleBooks = GoogleBooksClient(http, json, AppSignature.googleApiHeaders(app)),
        ),
        settings = settings,
    )
    val backup = BackupManager(app, database, json)
}
