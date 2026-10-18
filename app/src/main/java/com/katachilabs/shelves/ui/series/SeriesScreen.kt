package com.katachilabs.shelves.ui.series

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.katachilabs.shelves.data.BookRepository
import com.katachilabs.shelves.data.db.Book
import com.katachilabs.shelves.ui.common.Cover
import com.katachilabs.shelves.ui.common.appViewModel
import androidx.compose.material3.Checkbox
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
    private val shelfId: Long?,
) : ViewModel() {
    val series = repository.series(seriesId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val volumes = repository.seriesVolumes(seriesId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
                "Synchronisation impossible : ${e.message}"
            }
            _syncing.value = false
            _messages.send(message)
        }
    }

    /** Cocher un tome le marque possédé et le range dans l'étagère d'où l'on vient. */
    fun setOwned(book: Book, owned: Boolean) = viewModelScope.launch { repository.setOwned(book, owned, shelfId) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesScreen(seriesId: String, shelfId: Long?, onBack: () -> Unit, onOpenBook: (Long) -> Unit) {
    val viewModel = appViewModel { SeriesViewModel(it.repository, seriesId, shelfId) }
    val series by viewModel.series.collectAsStateWithLifecycle()
    val volumes by viewModel.volumes.collectAsStateWithLifecycle()
    val syncing by viewModel.syncing.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
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
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (syncing) LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(volumes, key = { it.id }) { book ->
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
                            Checkbox(checked = book.owned, onCheckedChange = { viewModel.setOwned(book, it) })
                        },
                        modifier = Modifier.clickable { onOpenBook(book.id) },
                    )
                }
            }
        }
    }
}
