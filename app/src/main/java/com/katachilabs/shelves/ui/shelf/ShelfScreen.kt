package com.katachilabs.shelves.ui.shelf

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.katachilabs.shelves.data.BookRepository
import com.katachilabs.shelves.data.db.Book
import com.katachilabs.shelves.data.db.Series
import com.katachilabs.shelves.data.db.Shelf
import com.katachilabs.shelves.data.remote.normalizedKey
import com.katachilabs.shelves.ui.common.ConfirmDialog
import com.katachilabs.shelves.ui.common.Cover
import com.katachilabs.shelves.ui.common.OwnedIcon
import com.katachilabs.shelves.ui.common.PickContactDetail
import com.katachilabs.shelves.ui.common.ShelfDialog
import com.katachilabs.shelves.ui.common.appViewModel
import com.katachilabs.shelves.ui.common.sendEmail
import com.katachilabs.shelves.ui.common.sendSms
import com.katachilabs.shelves.ui.common.shareText
import com.katachilabs.shelves.ui.common.shelfShareText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ShelfItem {
    val key: String
    val sortTitle: String
    val sortAuthor: String
    val addedAt: Long

    /** Vrai si le titre, les auteurs, l'ISBN (ou ceux d'un tome de la série) contiennent la recherche. */
    fun matches(query: String): Boolean

    data class SeriesItem(val series: Series, val owned: Int, val total: Int, val inShelf: List<Book>) : ShelfItem {
        override val key = "s:${series.id}"
        override val sortTitle = series.title.normalizedKey()
        override val sortAuthor = inShelf.firstNotNullOfOrNull { it.authors }.orEmpty().normalizedKey()
        override val addedAt = inShelf.maxOfOrNull { it.addedAt } ?: 0L
        val coverUrl = inShelf.minByOrNull { it.volumeNumber ?: Double.MAX_VALUE }?.coverUrl
        override fun matches(query: String) =
            series.title.normalizedKey().contains(query) || series.publisher.orEmpty().normalizedKey().contains(query) ||
                inShelf.any { it.matches(query) }
    }

    data class BookItem(val book: Book) : ShelfItem {
        override val key = "b:${book.id}"
        override val sortTitle = book.title.normalizedKey()
        override val sortAuthor = book.authors.orEmpty().normalizedKey()
        override val addedAt = book.addedAt
        override fun matches(query: String) = book.matches(query)
    }
}

private fun Book.matches(query: String) =
    title.normalizedKey().contains(query) || subtitle.orEmpty().normalizedKey().contains(query) ||
        authors.orEmpty().normalizedKey().contains(query) || isbn.orEmpty().contains(query)

enum class ShelfSort(val label: String) {
    Title("Titre"),
    Author("Auteur"),
    Recent("Ajout le plus récent"),
}

/** [allItems] : tout le contenu (pour le partage) ; [items] : ce qui est affiché après recherche et tri. */
data class ShelfUiState(val shelf: Shelf?, val allItems: List<ShelfItem>, val items: List<ShelfItem>)

class ShelfViewModel(private val repository: BookRepository, private val shelfId: Long) : ViewModel() {
    val query = MutableStateFlow("")
    val sort = MutableStateFlow(ShelfSort.Title)

    private val content = combine(
        repository.shelfOrVirtual(shelfId),
        repository.booksInShelf(shelfId),
        repository.allSeries(),
        repository.seriesStats(),
    ) { shelf, books, series, stats ->
        val seriesById = series.associateBy { it.id }
        val statsById = stats.associateBy { it.seriesId }
        val (grouped, standalone) = books.partition { it.seriesId in seriesById }
        val items = grouped.groupBy { it.seriesId!! }.map { (id, volumes) ->
            val s = statsById[id]
            ShelfItem.SeriesItem(seriesById.getValue(id), owned = s?.owned ?: 0, total = s?.total ?: volumes.size, inShelf = volumes)
        } + standalone.map { ShelfItem.BookItem(it) }
        shelf to items
    }

    val state = combine(content, query, sort) { (shelf, items), query, sort ->
        val key = query.normalizedKey()
        val visible = items
            .filter { key.isEmpty() || it.matches(key) }
            .let { list ->
                when (sort) {
                    ShelfSort.Title -> list.sortedBy { it.sortTitle }
                    ShelfSort.Author -> list.sortedWith(compareBy({ it.sortAuthor.ifEmpty { "￿" } }, { it.sortTitle }))
                    ShelfSort.Recent -> list.sortedByDescending { it.addedAt }
                }
            }
        ShelfUiState(shelf, items.sortedBy { it.sortTitle }, visible)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun rename(name: String, description: String) = viewModelScope.launch {
        state.value?.shelf?.let { repository.updateShelf(it.copy(name = name.trim(), description = description.trim().ifBlank { null })) }
    }

    fun delete() = viewModelScope.launch { state.value?.shelf?.let { repository.deleteShelf(it) } }

    suspend fun shareText(): String {
        val current = state.value ?: return ""
        val shelf = current.shelf ?: return ""
        val books = current.allItems.flatMap { item ->
            when (item) {
                is ShelfItem.SeriesItem -> item.inShelf
                is ShelfItem.BookItem -> listOf(item.book)
            }
        }
        val series = current.allItems.filterIsInstance<ShelfItem.SeriesItem>().map { it.series }
        val volumes = series.associate { it.id to repository.seriesVolumes(it.id).first() }
        return shelfShareText(shelf, books, series.associateBy { it.id }, volumes)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShelfScreen(
    shelfId: Long,
    onBack: () -> Unit,
    onAddBook: () -> Unit,
    onOpenBook: (Long) -> Unit,
    onOpenSeries: (String) -> Unit,
) {
    val viewModel = appViewModel { ShelfViewModel(it.repository, shelfId) }
    val virtual = shelfId == BookRepository.UNSHELVED_ID
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }
    var shareMenuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    val shelfName = state?.shelf?.name.orEmpty()
    val phonePicker = remember { PickContactDetail(PickContactDetail.Kind.Phone) }
    val emailPicker = remember { PickContactDetail(PickContactDetail.Kind.Email) }
    val pickPhone = rememberLauncherForActivityResult(phonePicker) { uri ->
        val contact = uri?.let { phonePicker.read(context, it) } ?: return@rememberLauncherForActivityResult
        scope.launch { sendSms(context, contact.value, viewModel.shareText()) }
    }
    val pickEmail = rememberLauncherForActivityResult(emailPicker) { uri ->
        val contact = uri?.let { emailPicker.read(context, it) } ?: return@rememberLauncherForActivityResult
        scope.launch { sendEmail(context, contact.value, "Étagère « $shelfName »", viewModel.shareText()) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(shelfName) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") }
                },
                actions = {
                    Box {
                        IconButton(onClick = { shareMenuOpen = true }) { Icon(Icons.Outlined.Share, contentDescription = "Partager") }
                        DropdownMenu(expanded = shareMenuOpen, onDismissRequest = { shareMenuOpen = false }) {
                            DropdownMenuItem(text = { Text("Par SMS à un contact") }, onClick = {
                                shareMenuOpen = false
                                pickPhone.launch(Unit)
                            })
                            DropdownMenuItem(text = { Text("Par e-mail à un contact") }, onClick = {
                                shareMenuOpen = false
                                pickEmail.launch(Unit)
                            })
                            DropdownMenuItem(text = { Text("Avec une autre application") }, onClick = {
                                shareMenuOpen = false
                                scope.launch { shareText(context, "Étagère « $shelfName »", viewModel.shareText()) }
                            })
                        }
                    }
                    if (!virtual) Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Plus") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("Renommer") }, onClick = {
                                menuOpen = false
                                renaming = true
                            })
                            DropdownMenuItem(text = { Text("Supprimer l'étagère") }, onClick = {
                                menuOpen = false
                                deleting = true
                            })
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddBook,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Ajouter un livre") },
            )
        },
    ) { padding ->
        val current = state
        when {
            current == null -> Unit
            current.allItems.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (virtual) "Tous vos livres sont rangés dans une étagère."
                    else "Étagère vide. Scannez le code-barres d'un livre ou saisissez son ISBN pour l'ajouter.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            else -> Column(Modifier.fillMaxSize().padding(padding)) {
                SearchAndSortBar(
                    query = query,
                    onQueryChange = { viewModel.query.value = it },
                    sort = sort,
                    onSortChange = { viewModel.sort.value = it },
                )
                if (current.items.isEmpty()) {
                    Text(
                        "Aucun livre ne correspond à « $query ».",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(24.dp),
                    )
                }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                    items(current.items, key = { it.key }) { item ->
                        when (item) {
                            is ShelfItem.SeriesItem -> SeriesRow(item) { onOpenSeries(item.series.id) }
                            is ShelfItem.BookItem -> BookRow(item.book) { onOpenBook(item.book.id) }
                        }
                    }
                }
            }
        }
    }

    if (renaming) {
        ShelfDialog(
            title = "Renommer l'étagère",
            initialName = state?.shelf?.name.orEmpty(),
            initialDescription = state?.shelf?.description.orEmpty(),
            onDismiss = { renaming = false },
            onConfirm = { name, description ->
                renaming = false
                viewModel.rename(name, description)
            },
        )
    }
    if (deleting) {
        ConfirmDialog(
            title = "Supprimer « $shelfName » ?",
            message = "Les livres restent dans l'application, seule l'étagère est supprimée.",
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
private fun SeriesRow(item: ShelfItem.SeriesItem, onClick: () -> Unit) {
    ListItem(
        leadingContent = { Cover(item.coverUrl, width = 48.dp) },
        headlineContent = { Text(item.series.title) },
        supportingContent = {
            androidx.compose.foundation.layout.Column {
                Text(
                    listOfNotNull(item.series.publisher, "${item.owned} / ${item.total} tomes possédés").joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (item.total > 0) {
                    LinearProgressIndicator(
                        progress = { item.owned.toFloat() / item.total },
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    )
                }
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
fun BookRow(book: Book, onClick: () -> Unit) {
    ListItem(
        leadingContent = { Cover(book.coverUrl, width = 48.dp) },
        headlineContent = { Text(book.title, maxLines = 2) },
        supportingContent = {
            Text(
                listOfNotNull(book.authors, book.loanedTo?.let { "Prêté à $it" }).joinToString(" · "),
                maxLines = 2,
                style = MaterialTheme.typography.bodySmall,
            )
        },
        trailingContent = { OwnedIcon(book.owned) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** Recherche dans l'étagère (titre, auteur, ISBN, série) et choix du tri. */
@Composable
private fun SearchAndSortBar(query: String, onQueryChange: (String) -> Unit, sort: ShelfSort, onSortChange: (ShelfSort) -> Unit) {
    var sortMenuOpen by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Titre, auteur, ISBN…", maxLines = 1) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Filled.Clear, contentDescription = "Effacer la recherche") }
                }
            },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Box {
            IconButton(onClick = { sortMenuOpen = true }) {
                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Trier : ${sort.label}")
            }
            DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                ShelfSort.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        leadingIcon = {
                            if (option == sort) Icon(Icons.Filled.Check, contentDescription = "Tri actuel")
                        },
                        onClick = {
                            sortMenuOpen = false
                            onSortChange(option)
                        },
                    )
                }
            }
        }
    }
}
