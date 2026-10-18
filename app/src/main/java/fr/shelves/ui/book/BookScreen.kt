package fr.shelves.ui.book

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import fr.shelves.data.BookRepository
import fr.shelves.data.db.Book
import fr.shelves.data.db.Series
import fr.shelves.data.db.Shelf
import fr.shelves.ui.common.ConfirmDialog
import fr.shelves.ui.common.Cover
import fr.shelves.ui.common.appViewModel
import fr.shelves.ui.common.contactName
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

data class BookUiState(
    val book: Book?,
    val series: Series?,
    val shelvesOfBook: List<Shelf>,
    val allShelves: List<Shelf>,
)

@OptIn(ExperimentalCoroutinesApi::class)
class BookViewModel(private val repository: BookRepository, bookId: Long) : ViewModel() {
    private val book = repository.book(bookId)

    val state = combine(
        book,
        book.flatMapLatest { b -> b?.seriesId?.let { repository.series(it) } ?: flowOf(null) },
        repository.shelvesOfBook(bookId),
        repository.shelves(),
    ) { b, series, mine, all -> BookUiState(b, series, mine, all) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val current get() = state.value?.book

    fun setOwned(owned: Boolean) = viewModelScope.launch { current?.let { repository.setOwned(it, owned, shelfId = null) } }

    fun toggleShelf(shelf: Shelf, inShelf: Boolean) = viewModelScope.launch {
        val b = current ?: return@launch
        if (inShelf) repository.removeFromShelf(shelf.id, b.id) else repository.addToShelf(shelf.id, b.id)
    }

    fun lend(name: String, uri: String?) = viewModelScope.launch { current?.let { repository.lend(it, name, uri) } }
    fun returned() = viewModelScope.launch { current?.let { repository.returned(it) } }
    fun saveNotes(notes: String) = viewModelScope.launch { current?.let { repository.update(it.copy(notes = notes.ifBlank { null })) } }
    fun delete() = viewModelScope.launch { current?.let { repository.delete(it) } }

    suspend fun refresh(): Boolean = current?.let { runCatching { repository.refresh(it) }.getOrDefault(false) } ?: false
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BookScreen(bookId: Long, onBack: () -> Unit, onOpenSeries: (String) -> Unit) {
    val viewModel = appViewModel { BookViewModel(it.repository, bookId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    val pickContact = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        if (uri != null) {
            val name = contactName(context, uri) ?: return@rememberLauncherForActivityResult
            viewModel.lend(name, uri.toString())
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(state?.book?.title.orEmpty(), maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Plus") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("Actualiser depuis les sources") }, onClick = {
                                menuOpen = false
                                scope.launch {
                                    val ok = viewModel.refresh()
                                    snackbar.showSnackbar(if (ok) "Fiche complétée" else "Aucune information supplémentaire")
                                }
                            })
                            DropdownMenuItem(text = { Text("Supprimer le livre") }, onClick = {
                                menuOpen = false
                                deleting = true
                            })
                        }
                    }
                },
            )
        },
    ) { padding ->
        val book = state?.book ?: return@Scaffold
        val ui = state!!
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Cover(book.coverUrl, width = 120.dp)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ui.series?.let { series ->
                        Text(
                            listOfNotNull(series.title, book.volumeLabel).joinToString(" · "),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { onOpenSeries(series.id) },
                        )
                    }
                    Text(book.title, style = MaterialTheme.typography.titleLarge)
                    book.subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    book.authors?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Je possède ce livre", modifier = Modifier.weight(1f))
                Switch(checked = book.owned, onCheckedChange = viewModel::setOwned)
            }

            Loan(book, onLend = { pickContact.launch(null) }, onReturned = viewModel::returned)

            book.summary?.let {
                Text("Résumé", style = MaterialTheme.typography.titleSmall)
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }

            Card {
                Column(Modifier.padding(12.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Info("ISBN", book.isbn)
                    Info("Éditeur", book.publisher)
                    Info("Date", book.publishedDate)
                    Info("Pages", book.pageCount?.toString())
                    Info("Sources", book.sources)
                    Info("Notice BnF", book.bnfArk)
                }
            }

            Text("Étagères", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val mine = ui.shelvesOfBook.map { it.id }.toSet()
                ui.allShelves.forEach { shelf ->
                    val inShelf = shelf.id in mine
                    FilterChip(selected = inShelf, onClick = { viewModel.toggleShelf(shelf, inShelf) }, label = { Text(shelf.name) })
                }
            }

            HorizontalDivider()
            Notes(book, viewModel::saveNotes)
        }
    }

    if (deleting) {
        ConfirmDialog(
            title = "Supprimer ce livre ?",
            message = "Il sera retiré de toutes les étagères.",
            confirmLabel = "Supprimer",
            onDismiss = { deleting = false },
            onConfirm = {
                deleting = false
                viewModel.delete()
                onBack()
            },
        )
    }
}

@Composable
private fun Loan(book: Book, onLend: () -> Unit, onReturned: () -> Unit) {
    if (book.loanedTo == null) {
        OutlinedButton(onClick = onLend) { Text("Prêter à un contact") }
    } else {
        val since = book.loanedAt?.let { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it)) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            AssistChip(onClick = {}, label = { Text(listOfNotNull("Prêté à ${book.loanedTo}", since?.let { "depuis le $it" }).joinToString(" ")) })
            TextButton(onClick = onReturned) { Text("Rendu") }
        }
    }
}

@Composable
private fun Info(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row {
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(0.35f))
        Text(value, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.65f))
    }
}

@Composable
private fun Notes(book: Book, onSave: (String) -> Unit) {
    var notes by remember(book.id) { mutableStateOf(book.notes.orEmpty()) }
    LaunchedEffect(book.notes) { if (book.notes.orEmpty() != notes) notes = book.notes.orEmpty() }
    OutlinedTextField(
        value = notes,
        onValueChange = { notes = it },
        label = { Text("Notes personnelles") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 3,
    )
    if (notes != book.notes.orEmpty()) {
        TextButton(onClick = { onSave(notes) }) { Text("Enregistrer les notes") }
    }
}
