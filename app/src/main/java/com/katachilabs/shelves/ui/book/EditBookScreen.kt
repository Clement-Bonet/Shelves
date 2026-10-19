package com.katachilabs.shelves.ui.book

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.katachilabs.shelves.data.BookRepository
import com.katachilabs.shelves.data.db.Book
import com.katachilabs.shelves.data.remote.parseVolumeNumber
import com.katachilabs.shelves.ui.common.appViewModel
import com.katachilabs.shelves.ui.common.formatVolumeRanges
import com.katachilabs.shelves.util.Isbn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class EditBookViewModel(private val repository: BookRepository, bookId: Long) : ViewModel() {
    val book = repository.book(bookId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Enregistre puis appelle [onDone], ou renvoie l'erreur à afficher via [onError]. */
    fun save(edited: Book, onDone: () -> Unit, onError: (String) -> Unit) = viewModelScope.launch {
        val error = repository.saveEdited(edited)
        if (error == null) onDone() else onError(error)
    }
}

/** Champs du formulaire, tous en texte tant qu'on les saisit. */
private data class BookForm(
    val title: String,
    val subtitle: String,
    val authors: String,
    val publisher: String,
    val publishedDate: String,
    val pageCount: String,
    val isbn: String,
    val volume: String,
    val summary: String,
) {
    constructor(book: Book) : this(
        title = book.title,
        subtitle = book.subtitle.orEmpty(),
        authors = book.authors.orEmpty(),
        publisher = book.publisher.orEmpty(),
        publishedDate = book.publishedDate.orEmpty(),
        pageCount = book.pageCount?.toString().orEmpty(),
        isbn = book.isbn.orEmpty(),
        volume = book.volumeLabel ?: book.volumeNumber?.let { formatVolumeRanges(listOf(it)) }.orEmpty(),
        summary = book.summary.orEmpty(),
    )

    /** Erreur de saisie à corriger avant d'enregistrer, ou `null`. */
    fun error(): String? = when {
        title.isBlank() -> "Le titre est obligatoire."
        pageCount.isNotBlank() && pageCount.trim().toIntOrNull()?.takeIf { it > 0 } == null -> "Nombre de pages invalide."
        isbn.isNotBlank() && Isbn.toSearchCode(isbn) == null -> "ISBN ou EAN invalide."
        else -> null
    }

    fun applyTo(book: Book): Book {
        val volumeText = volume.trim().ifBlank { null }
        return book.copy(
            title = title.trim(),
            subtitle = subtitle.trim().ifBlank { null },
            authors = authors.trim().ifBlank { null },
            publisher = publisher.trim().ifBlank { null },
            publishedDate = publishedDate.trim().ifBlank { null },
            pageCount = pageCount.trim().toIntOrNull(),
            isbn = isbn.takeIf { it.isNotBlank() }?.let { Isbn.toSearchCode(it) },
            volumeLabel = volumeText,
            volumeNumber = parseVolumeNumber(volumeText),
            summary = summary.trim().ifBlank { null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditBookScreen(bookId: Long, onClose: () -> Unit) {
    val viewModel = appViewModel { EditBookViewModel(it.repository, bookId) }
    val book by viewModel.book.collectAsStateWithLifecycle()
    val original = book ?: return
    // Le formulaire part de la fiche telle qu'elle est à l'ouverture.
    var form by remember(original.id) { mutableStateOf(BookForm(original)) }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Modifier le livre") },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Annuler") }
                },
                actions = {
                    TextButton(onClick = {
                        error = form.error()
                        if (error == null) viewModel.save(form.applyTo(original), onDone = onClose, onError = { error = it })
                    }) { Text("Enregistrer") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Field("Titre", form.title) { form = form.copy(title = it) }
            Field("Sous-titre", form.subtitle) { form = form.copy(subtitle = it) }
            Field("Auteurs (séparés par des virgules)", form.authors) { form = form.copy(authors = it) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Field("Tome", form.volume, Modifier.weight(1f), KeyboardType.Text) { form = form.copy(volume = it) }
                Field("Pages", form.pageCount, Modifier.weight(1f), KeyboardType.Number) { form = form.copy(pageCount = it) }
            }
            Field("Éditeur", form.publisher) { form = form.copy(publisher = it) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Field("Date de publication", form.publishedDate, Modifier.weight(1f)) { form = form.copy(publishedDate = it) }
                Field("ISBN", form.isbn, Modifier.weight(1f), KeyboardType.Number) { form = form.copy(isbn = it) }
            }
            OutlinedTextField(
                value = form.summary,
                onValueChange = { form = form.copy(summary = it) },
                label = { Text("Résumé") },
                minLines = 4,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "La série se change depuis la fiche du livre (« Modifier » à côté de la série). " +
                    "« Actualiser depuis les sources » ne remplit que les champs laissés vides.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    keyboardType: KeyboardType = KeyboardType.Text,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            capitalization = if (keyboardType == KeyboardType.Text) KeyboardCapitalization.Sentences else KeyboardCapitalization.None,
        ),
        modifier = modifier,
    )
}
