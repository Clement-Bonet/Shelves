package com.katachilabs.shelves.ui.series

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.katachilabs.shelves.data.BookRepository
import com.katachilabs.shelves.data.remote.userMessage
import com.katachilabs.shelves.data.db.Book
import com.katachilabs.shelves.ui.common.BookPickerDialog
import com.katachilabs.shelves.ui.common.ConfirmDialog
import com.katachilabs.shelves.ui.common.Cover
import com.katachilabs.shelves.ui.common.RenameSeriesDialog
import com.katachilabs.shelves.ui.common.SeriesChoice
import com.katachilabs.shelves.ui.common.SeriesPickerDialog
import com.katachilabs.shelves.ui.common.ShelfChoice
import com.katachilabs.shelves.ui.common.ShelfPickerDialog
import com.katachilabs.shelves.ui.common.appViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SeriesViewModel(
    private val repository: BookRepository,
    private val seriesId: String,
    /** Étagère d'où l'on vient : les tomes cochés y sont rangés, et « Déplacer » les en retire. */
    private val shelfId: Long?,
) : ViewModel() {
    val series = repository.series(seriesId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val volumes = repository.seriesVolumes(seriesId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val otherSeries = repository.activeSeries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val shelves = repository.shelves().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val booksWithoutSeries = repository.booksWithoutSeries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _syncing = MutableStateFlow(false)
    val syncing = _syncing.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    init {
        // Première visite : on va chercher les tomes à la BnF.
        viewModelScope.launch {
            if (repository.seriesNeverSynced(seriesId)) sync()
        }
    }

    fun sync() {
        if (_syncing.value) return
        _syncing.value = true
        viewModelScope.launch {
            val message = try {
                when (val added = repository.syncSeries(seriesId)) {
                    0 -> "Aucun nouveau tome trouvé à la BnF"
                    1 -> "1 tome ajouté"
                    else -> "$added tomes ajoutés"
                }
            } catch (e: Exception) {
                e.userMessage("Synchronisation impossible")
            }
            _syncing.value = false
            _messages.send(message)
        }
    }

    /** Cocher un tome le marque possédé et le range dans l'étagère d'où l'on vient. */
    fun setOwned(book: Book, owned: Boolean) = viewModelScope.launch { repository.setOwned(book, owned, shelfId) }

    fun rename(title: String, publisher: String) = viewModelScope.launch {
        series.value?.let { repository.renameSeries(it, title, publisher) }
    }

    /** Fusionne cette série dans [targetId] puis appelle [onDone] avec l'identifiant de la série restante. */
    fun mergeInto(targetId: String, onDone: (String) -> Unit) = viewModelScope.launch {
        repository.mergeSeries(seriesId, targetId)
        onDone(targetId)
    }

    val movesFromShelf get() = shelfId != null

    /** Range les livres dans une étagère (existante ou nouvelle) ; ils quittent l'étagère d'origine. */
    fun moveToShelf(bookIds: List<Long>, choice: ShelfChoice) = viewModelScope.launch {
        val target = when (choice) {
            is ShelfChoice.Existing -> choice.id
            is ShelfChoice.New -> repository.createShelf(choice.name, null)
        }
        repository.moveToShelf(bookIds, target, fromShelfId = shelfId)
        val name = shelves.value.firstOrNull { it.id == target }?.name ?: (choice as? ShelfChoice.New)?.name
        _messages.send(plural(bookIds.size, "livre rangé", "livres rangés") + " dans « $name »")
    }

    /** Déplace les livres choisis vers une autre série (existante, nouvelle) ou les sort de toute série. */
    fun moveToSeries(bookIds: List<Long>, choice: SeriesChoice) = viewModelScope.launch {
        val target = when (choice) {
            SeriesChoice.None -> null
            is SeriesChoice.Existing -> choice.id
            is SeriesChoice.New -> repository.createSeries(choice.title, choice.publisher)
        }
        repository.assignToSeries(bookIds, target)
        _messages.send(plural(bookIds.size, "tome déplacé", "tomes déplacés"))
    }

    /** Rattache à cette série des livres qui n'en avaient pas. */
    fun attach(bookIds: List<Long>) = viewModelScope.launch {
        repository.assignToSeries(bookIds, seriesId)
        _messages.send(plural(bookIds.size, "livre ajouté", "livres ajoutés") + " à la série")
    }

    fun deleteBooks(bookIds: List<Long>) = viewModelScope.launch {
        repository.deleteBooks(bookIds)
        _messages.send(plural(bookIds.size, "livre supprimé", "livres supprimés"))
    }

    val dismissedCount = repository.dismissedCount(seriesId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Les tomes supprimés redeviennent synchronisables : on relance aussitôt la synchronisation. */
    fun restoreDismissed() = viewModelScope.launch {
        repository.restoreDismissed(seriesId)
        sync()
    }

    fun deleteSeries(onDone: () -> Unit) = viewModelScope.launch {
        repository.deleteSeries(seriesId)
        onDone()
    }

    private fun plural(n: Int, one: String, many: String) = if (n <= 1) "$n $one" else "$n $many"
}

/** Boîte de dialogue ouverte sur l'écran de la série. */
private sealed interface SeriesDialog {
    data object Rename : SeriesDialog
    data object Merge : SeriesDialog
    data object Attach : SeriesDialog
    data object DeleteSeries : SeriesDialog
    data class Shelf(val bookIds: List<Long>, val wholeSeries: Boolean) : SeriesDialog
    data class MoveSeries(val bookIds: List<Long>) : SeriesDialog
    data class DeleteBooks(val bookIds: List<Long>) : SeriesDialog
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesScreen(
    seriesId: String,
    shelfId: Long?,
    onBack: () -> Unit,
    onOpenBook: (Long) -> Unit,
    onMerged: (targetId: String) -> Unit,
) {
    val viewModel = appViewModel { SeriesViewModel(it.repository, seriesId, shelfId) }
    val series by viewModel.series.collectAsStateWithLifecycle()
    val volumes by viewModel.volumes.collectAsStateWithLifecycle()
    val syncing by viewModel.syncing.collectAsStateWithLifecycle()
    val otherSeries by viewModel.otherSeries.collectAsStateWithLifecycle()
    val shelves by viewModel.shelves.collectAsStateWithLifecycle()
    val booksWithoutSeries by viewModel.booksWithoutSeries.collectAsStateWithLifecycle()
    val dismissedCount by viewModel.dismissedCount.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<SeriesDialog?>(null) }
    // Sélection multiple : un appui long sur un tome l'active.
    var selection by remember { mutableStateOf(emptySet<Long>()) }
    val selecting = selection.isNotEmpty()
    val moveLabel = if (viewModel.movesFromShelf) "Déplacer vers une étagère…" else "Ranger dans une étagère…"

    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    // Les tomes qui quittent la série (déplacés, supprimés) quittent aussi la sélection.
    LaunchedEffect(volumes) { selection = selection intersect volumes.map { it.id }.toSet() }
    BackHandler(enabled = selecting) { selection = emptySet() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (selecting) {
                TopAppBar(
                    title = { Text(if (selection.size == 1) "1 tome sélectionné" else "${selection.size} tomes sélectionnés") },
                    navigationIcon = {
                        IconButton(onClick = { selection = emptySet() }) { Icon(Icons.Filled.Close, contentDescription = "Annuler la sélection") }
                    },
                    actions = {
                        IconButton(onClick = { selection = volumes.map { it.id }.toSet() }) {
                            Icon(Icons.Filled.SelectAll, contentDescription = "Tout sélectionner")
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Actions") }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                MenuEntry(moveLabel) { menuOpen = false; dialog = SeriesDialog.Shelf(selection.toList(), wholeSeries = false) }
                                MenuEntry("Changer de série…") { menuOpen = false; dialog = SeriesDialog.MoveSeries(selection.toList()) }
                                MenuEntry("Supprimer…") { menuOpen = false; dialog = SeriesDialog.DeleteBooks(selection.toList()) }
                            }
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(series?.title.orEmpty(), maxLines = 1)
                            val owned = volumes.count { it.owned }
                            Text(
                                listOfNotNull(series?.publisher, "$owned / ${volumes.size} possédés").joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") }
                    },
                    actions = {
                        IconButton(onClick = viewModel::sync, enabled = !syncing) {
                            Icon(Icons.Filled.Sync, contentDescription = "Chercher les tomes à la BnF")
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Plus") }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                MenuEntry(if (viewModel.movesFromShelf) "Déplacer la série vers une étagère…" else "Ranger la série dans une étagère…") {
                                    menuOpen = false
                                    dialog = SeriesDialog.Shelf(volumes.filter { it.owned }.map { it.id }, wholeSeries = true)
                                }
                                MenuEntry("Ajouter des livres à la série…") { menuOpen = false; dialog = SeriesDialog.Attach }
                                MenuEntry("Sélectionner des tomes") { menuOpen = false; selection = volumes.take(1).map { it.id }.toSet() }
                                HorizontalDivider()
                                MenuEntry("Renommer la série") { menuOpen = false; dialog = SeriesDialog.Rename }
                                MenuEntry("Fusionner dans une autre série…") { menuOpen = false; dialog = SeriesDialog.Merge }
                                MenuEntry("Supprimer la série…") { menuOpen = false; dialog = SeriesDialog.DeleteSeries }
                                if (dismissedCount > 0) {
                                    MenuEntry("Réafficher les tomes supprimés ($dismissedCount)") { menuOpen = false; viewModel.restoreDismissed() }
                                }
                            }
                        }
                    },
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (syncing) LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(volumes, key = { it.id }) { book ->
                    val selected = book.id in selection
                    val toggle = { selection = if (selected) selection - book.id else selection + book.id }
                    ListItem(
                        leadingContent = { Cover(book.coverUrl, width = 40.dp) },
                        overlineContent = book.volumeLabel?.let { label -> @Composable { Text(label) } },
                        headlineContent = { Text(book.title, maxLines = 2) },
                        supportingContent = {
                            Text(
                                listOfNotNull(book.publishedDate, book.isbn, book.loanedTo?.let { "Prêté à $it" }).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                        trailingContent = {
                            Checkbox(checked = book.owned, onCheckedChange = { viewModel.setOwned(book, it) }, enabled = !selecting)
                        },
                        colors = if (selected) ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                        else ListItemDefaults.colors(),
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surface)
                            .combinedClickable(
                                onClick = { if (selecting) toggle() else onOpenBook(book.id) },
                                onLongClick = toggle,
                                onLongClickLabel = "Sélectionner",
                            ),
                    )
                }
            }
        }
    }

    val s = series ?: return
    when (val d = dialog) {
        null -> Unit
        SeriesDialog.Rename -> RenameSeriesDialog(
            series = s,
            onDismiss = { dialog = null },
            onConfirm = { title, publisher ->
                dialog = null
                viewModel.rename(title, publisher)
            },
        )
        SeriesDialog.Merge -> SeriesPickerDialog(
            title = "Fusionner « ${s.title} » dans…",
            series = otherSeries,
            currentId = null,
            confirmLabel = "Fusionner",
            allowNone = false,
            allowCreate = false,
            showVolume = false,
            excludeId = s.id,
            onDismiss = { dialog = null },
            onConfirm = { choice, _ ->
                dialog = null
                (choice as? SeriesChoice.Existing)?.let { viewModel.mergeInto(it.id, onMerged) }
            },
        )
        SeriesDialog.Attach -> BookPickerDialog(
            title = "Ajouter à « ${s.title} »",
            books = booksWithoutSeries,
            confirmLabel = "Ajouter",
            emptyMessage = "Tous vos livres possédés font déjà partie d'une série. Pour en déplacer un, ouvrez sa fiche ou sa série.",
            onDismiss = { dialog = null },
            onConfirm = { ids ->
                dialog = null
                viewModel.attach(ids)
            },
        )
        SeriesDialog.DeleteSeries -> {
            val owned = volumes.count { it.owned }
            ConfirmDialog(
                title = "Supprimer « ${s.title} » ?",
                message = (if (volumes.size == 1) "La série et son tome seront supprimés" else "La série et ses ${volumes.size} tomes seront supprimés") +
                    (if (owned == 0) "" else if (owned == 1) ", dont 1 possédé" else ", dont $owned possédés") +
                    ", avec leurs photos et leurs prêts. Cette action est définitive.",
                confirmLabel = "Supprimer",
                onDismiss = { dialog = null },
                onConfirm = {
                    dialog = null
                    viewModel.deleteSeries(onBack)
                },
            )
        }
        is SeriesDialog.Shelf -> ShelfPickerDialog(
            title = if (viewModel.movesFromShelf) "Déplacer vers…" else "Ranger dans…",
            message = when {
                d.bookIds.isEmpty() -> "Aucun tome possédé dans cette série."
                d.wholeSeries -> "Les ${d.bookIds.size} tomes possédés de la série" +
                    if (viewModel.movesFromShelf) " quitteront l'étagère actuelle." else "."
                viewModel.movesFromShelf -> "Les tomes choisis quitteront l'étagère actuelle."
                else -> null
            },
            shelves = shelves,
            excludeId = shelfId,
            confirmLabel = if (viewModel.movesFromShelf) "Déplacer" else "Ranger",
            onDismiss = { dialog = null },
            onConfirm = { choice ->
                dialog = null
                if (d.bookIds.isNotEmpty()) viewModel.moveToShelf(d.bookIds, choice)
                selection = emptySet()
            },
        )
        is SeriesDialog.MoveSeries -> SeriesPickerDialog(
            title = if (d.bookIds.size == 1) "Déplacer 1 tome vers…" else "Déplacer ${d.bookIds.size} tomes vers…",
            series = otherSeries,
            currentId = null,
            confirmLabel = "Déplacer",
            showVolume = false,
            excludeId = s.id,
            onDismiss = { dialog = null },
            onConfirm = { choice, _ ->
                dialog = null
                viewModel.moveToSeries(d.bookIds, choice)
                selection = emptySet()
            },
        )
        is SeriesDialog.DeleteBooks -> ConfirmDialog(
            title = if (d.bookIds.size == 1) "Supprimer ce tome ?" else "Supprimer ces ${d.bookIds.size} tomes ?",
            message = "Ils seront retirés de toutes les étagères, avec leurs photos. La synchronisation avec la BnF " +
                "ne les rajoutera pas ; le menu de la série permet de les faire revenir.",
            confirmLabel = "Supprimer",
            onDismiss = { dialog = null },
            onConfirm = {
                dialog = null
                viewModel.deleteBooks(d.bookIds)
                selection = emptySet()
            },
        )
    }
}

@Composable
private fun MenuEntry(label: String, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(label) }, onClick = onClick)
}
