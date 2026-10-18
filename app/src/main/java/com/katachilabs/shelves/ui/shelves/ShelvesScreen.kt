package com.katachilabs.shelves.ui.shelves

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.katachilabs.shelves.data.BookRepository
import com.katachilabs.shelves.ui.common.ShelfDialog
import com.katachilabs.shelves.ui.common.appViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ShelvesViewModel(private val repository: BookRepository) : ViewModel() {
    val shelves = repository.shelvesWithCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    suspend fun create(name: String, description: String): Long = repository.createShelf(name, description)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShelvesScreen(
    onOpenShelf: (Long) -> Unit,
    onOpenLoans: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val viewModel = appViewModel { ShelvesViewModel(it.repository) }
    val shelves by viewModel.shelves.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var creating by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mes étagères") },
                actions = {
                    IconButton(onClick = onOpenLoans) { Icon(Icons.Outlined.Handshake, contentDescription = "Prêts") }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, contentDescription = "Réglages") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Nouvelle étagère") },
            )
        },
    ) { padding ->
        val list = shelves
        when {
            list == null -> Unit
            list.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Créez une première étagère pour y ranger vos livres : une série, une bibliothèque, une liste d'envies…",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 96.dp),
            ) {
                items(list, key = { it.shelf.id }) { item ->
                    ListItem(
                        headlineContent = { Text(item.shelf.name) },
                        supportingContent = item.shelf.description?.let { d -> @Composable { Text(d, maxLines = 2) } },
                        trailingContent = {
                            Text(
                                if (item.bookCount <= 1) "${item.bookCount} livre" else "${item.bookCount} livres",
                                style = MaterialTheme.typography.labelLarge,
                            )
                        },
                        modifier = Modifier.clickable { onOpenShelf(item.shelf.id) },
                    )
                }
            }
        }
    }

    if (creating) {
        ShelfDialog(
            title = "Nouvelle étagère",
            onDismiss = { creating = false },
            onConfirm = { name, description ->
                creating = false
                scope.launch { onOpenShelf(viewModel.create(name, description)) }
            },
        )
    }
}
