package com.katachilabs.shelves.ui.add

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.katachilabs.shelves.data.BookRepository
import com.katachilabs.shelves.data.remote.userMessage
import com.katachilabs.shelves.data.db.Book
import com.katachilabs.shelves.data.db.Shelf
import com.katachilabs.shelves.data.remote.BookMetadata
import com.katachilabs.shelves.ui.common.Cover
import com.katachilabs.shelves.ui.common.appViewModel
import com.katachilabs.shelves.util.Isbn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface LookupStatus {
    data object Idle : LookupStatus
    data object Loading : LookupStatus
    data class Found(val meta: BookMetadata, val existing: Book?) : LookupStatus
    data class NotFound(val code: String) : LookupStatus
    data class Error(val message: String) : LookupStatus
}

data class AddBookState(
    val input: String = "",
    val status: LookupStatus = LookupStatus.Idle,
    val selectedShelves: Set<Long> = emptySet(),
    val fetchSeries: Boolean = true,
    val saving: Boolean = false,
)

class AddBookViewModel(private val repository: BookRepository, initialShelfId: Long?) : ViewModel() {
    private val _state = MutableStateFlow(AddBookState(selectedShelves = setOfNotNull(initialShelfId)))
    val state = _state.asStateFlow()

    val shelves = repository.shelves().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onInput(value: String) = _state.update { it.copy(input = value) }

    fun toggleShelf(id: Long) = _state.update {
        it.copy(selectedShelves = if (id in it.selectedShelves) it.selectedShelves - id else it.selectedShelves + id)
    }

    fun setFetchSeries(value: Boolean) = _state.update { it.copy(fetchSeries = value) }

    fun search(raw: String = _state.value.input) {
        val code = Isbn.toSearchCode(raw)
        if (code == null) {
            _state.update { it.copy(input = raw, status = LookupStatus.Error("Code invalide : saisissez un ISBN ou un EAN-13 / EAN-8.")) }
            return
        }
        _state.update { it.copy(input = code, status = LookupStatus.Loading) }
        viewModelScope.launch {
            val existing = repository.findByCode(code)
            val status = try {
                val meta = repository.lookup(code)
                when {
                    meta != null -> LookupStatus.Found(meta, existing)
                    existing != null -> LookupStatus.Found(existing.toMetadata(), existing)
                    else -> LookupStatus.NotFound(code)
                }
            } catch (e: Exception) {
                // Hors connexion, un livre déjà connu reste affichable tel qu'il est enregistré.
                if (existing != null) LookupStatus.Found(existing.toMetadata(), existing)
                else LookupStatus.Error(e.userMessage("Recherche impossible"))
            }
            _state.update { it.copy(status = status) }
        }
    }

    /** Enregistre le livre ; les tomes de la série sont récupérés ensuite, sans bloquer l'écran. */
    fun save(meta: BookMetadata, onSaved: (Long) -> Unit) {
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val current = _state.value
            val bookId = repository.saveOwned(meta, current.selectedShelves)
            val seriesId = repository.getBook(bookId)?.seriesId
            if (current.fetchSeries && seriesId != null) repository.syncSeriesInBackground(seriesId)
            _state.update { it.copy(saving = false) }
            onSaved(bookId)
        }
    }

    private fun Book.toMetadata() = BookMetadata(
        isbn = isbn,
        title = title,
        subtitle = subtitle,
        authors = authors?.split(", ").orEmpty(),
        publisher = publisher,
        publishedDate = publishedDate,
        pageCount = pageCount,
        summary = summary,
        coverUrl = coverUrl,
        bnfArk = bnfArk,
    )
}

private val unavailableCodes = setOf(
    MlKitException.UNAVAILABLE,
    MlKitException.CODE_SCANNER_UNAVAILABLE,
    MlKitException.CODE_SCANNER_CAMERA_PERMISSION_NOT_GRANTED,
    MlKitException.CODE_SCANNER_APP_NAME_UNAVAILABLE,
)

/** Ouvre le scanner de codes-barres de Google Play Services (aucune permission caméra à demander). */
private fun scanBarcode(context: Context, onResult: (String) -> Unit, onError: (String) -> Unit) {
    val options = GmsBarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8)
        .build()
    GmsBarcodeScanning.getClient(context, options)
        .startScan()
        .addOnSuccessListener { barcode -> barcode.rawValue?.let(onResult) }
        .addOnFailureListener { e ->
            // Fermer le scanner n'est pas une erreur : selon la façon de le fermer, Google renvoie
            // CODE_SCANNER_CANCELLED ou INTERNAL (bouton retour). On ne prévient que si le scanner
            // ne peut vraiment pas fonctionner.
            val code = (e as? MlKitException)?.errorCode
            if (code == null || code in unavailableCodes) {
                onError("Scanner indisponible : saisissez le code à la main.")
            }
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBookScreen(shelfId: Long?, onBack: () -> Unit, onSaved: (Long) -> Unit) {
    val viewModel = appViewModel { AddBookViewModel(it.repository, shelfId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val shelves by viewModel.shelves.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var scanError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajouter un livre") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Button(
                onClick = {
                    scanError = null
                    scanBarcode(context, onResult = { viewModel.search(it) }, onError = { scanError = it })
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Scanner le code-barres")
            }
            scanError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            OutlinedTextField(
                value = state.input,
                onValueChange = viewModel::onInput,
                label = { Text("ISBN ou EAN") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.search() }),
                trailingIcon = {
                    IconButton(onClick = { viewModel.search() }) { Icon(Icons.Filled.Search, contentDescription = "Rechercher") }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            when (val status = state.status) {
                LookupStatus.Idle -> Unit
                LookupStatus.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator()
                    Spacer(Modifier.width(16.dp))
                    Text("Recherche à la BnF, sur Google Books et Open Library…")
                }
                is LookupStatus.Error -> Text(status.message, color = MaterialTheme.colorScheme.error)
                is LookupStatus.NotFound -> ManualEntry(status.code, state, shelves, viewModel, onSaved)
                is LookupStatus.Found -> {
                    FoundBook(status)
                    SaveOptions(state, shelves, viewModel, hasSeries = status.meta.series != null)
                    Button(
                        onClick = { viewModel.save(status.meta, onSaved) },
                        enabled = !state.saving,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (status.existing?.owned == true) "Mettre à jour" else "Ajouter à ma collection") }
                }
            }
        }
    }
}

@Composable
private fun FoundBook(status: LookupStatus.Found) {
    val meta = status.meta
    Card {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Cover(meta.coverUrl, width = 80.dp)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                meta.series?.let { s ->
                    Text(
                        listOfNotNull(s.title, s.volumeLabel).joinToString(" · "),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(meta.title, style = MaterialTheme.typography.titleMedium)
                meta.subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                if (meta.authors.isNotEmpty()) Text(meta.authors.joinToString(", "), style = MaterialTheme.typography.bodySmall)
                Text(
                    listOfNotNull(meta.publisher, meta.publishedDate).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text("Source : ${meta.sources.joinToString(", ")}", style = MaterialTheme.typography.labelSmall)
                if (status.existing?.owned == true) {
                    Text("Déjà dans votre collection", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SaveOptions(state: AddBookState, shelves: List<Shelf>, viewModel: AddBookViewModel, hasSeries: Boolean) {
    if (shelves.isNotEmpty()) {
        Text("Ranger dans", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            shelves.forEach { shelf ->
                FilterChip(
                    selected = shelf.id in state.selectedShelves,
                    onClick = { viewModel.toggleShelf(shelf.id) },
                    label = { Text(shelf.name) },
                )
            }
        }
    }
    if (hasSeries) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = state.fetchSeries, onCheckedChange = viewModel::setFetchSeries)
            Text("Récupérer les autres tomes de la série")
        }
    }
}

@Composable
private fun ManualEntry(
    code: String,
    state: AddBookState,
    shelves: List<Shelf>,
    viewModel: AddBookViewModel,
    onSaved: (Long) -> Unit,
) {
    var title by remember(code) { mutableStateOf("") }
    var authors by remember(code) { mutableStateOf("") }
    Text("Aucune source ne connaît le code $code. Vous pouvez saisir le livre vous-même.")
    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Titre") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(value = authors, onValueChange = { authors = it }, label = { Text("Auteurs") }, modifier = Modifier.fillMaxWidth())
    SaveOptions(state, shelves, viewModel, hasSeries = false)
    OutlinedButton(
        onClick = {
            viewModel.save(
                BookMetadata(
                    isbn = code,
                    title = title.trim(),
                    authors = authors.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                    sources = listOf("Saisie"),
                ),
                onSaved,
            )
        },
        enabled = title.isNotBlank() && !state.saving,
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Enregistrer") }
}
