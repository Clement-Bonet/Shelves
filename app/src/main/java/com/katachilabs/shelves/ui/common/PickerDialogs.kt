package com.katachilabs.shelves.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.katachilabs.shelves.data.db.Book
import com.katachilabs.shelves.data.db.Shelf
import com.katachilabs.shelves.data.remote.normalizedKey

/** Étagère choisie dans [ShelfPickerDialog] : existante, ou à créer avec ce nom. */
sealed interface ShelfChoice {
    data class Existing(val id: Long) : ShelfChoice
    data class New(val name: String) : ShelfChoice
}

/** Choix d'une étagère de destination, avec possibilité d'en créer une. */
@Composable
fun ShelfPickerDialog(
    title: String,
    message: String?,
    shelves: List<Shelf>,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (ShelfChoice) -> Unit,
    excludeId: Long? = null,
) {
    var choice by remember { mutableStateOf<ShelfChoice?>(null) }
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    items(shelves.filter { it.id != excludeId }, key = { it.id }) { shelf ->
                        PickerRow(
                            selected = choice == ShelfChoice.Existing(shelf.id),
                            label = shelf.name,
                            detail = shelf.description,
                            radio = true,
                        ) { choice = ShelfChoice.Existing(shelf.id) }
                    }
                    item {
                        PickerRow(choice is ShelfChoice.New, "Nouvelle étagère…", null, radio = true) {
                            choice = ShelfChoice.New(newName)
                        }
                    }
                }
                if (choice is ShelfChoice.New) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Nom de la nouvelle étagère") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when (val c = choice) {
                        is ShelfChoice.New -> onConfirm(ShelfChoice.New(newName.trim()))
                        is ShelfChoice.Existing -> onConfirm(c)
                        null -> Unit
                    }
                },
                enabled = choice is ShelfChoice.Existing || (choice is ShelfChoice.New && newName.isNotBlank()),
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

/** Sélection de plusieurs livres dans une liste, avec recherche par titre, auteur ou ISBN. */
@Composable
fun BookPickerDialog(
    title: String,
    books: List<Book>,
    confirmLabel: String,
    emptyMessage: String,
    onDismiss: () -> Unit,
    onConfirm: (List<Long>) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(emptySet<Long>()) }
    val key = query.normalizedKey()
    val visible = books.filter {
        key.isEmpty() || it.title.normalizedKey().contains(key) ||
            it.authors.orEmpty().normalizedKey().contains(key) || it.isbn.orEmpty().contains(key)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (books.isEmpty()) {
                    Text(emptyMessage, style = MaterialTheme.typography.bodyMedium)
                } else {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Rechercher") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(visible, key = { it.id }) { book ->
                            PickerRow(
                                selected = book.id in selected,
                                label = book.title,
                                detail = listOfNotNull(book.authors, book.publisher).joinToString(" · ").ifBlank { null },
                                radio = false,
                            ) { selected = if (book.id in selected) selected - book.id else selected + book.id }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected.toList()) }, enabled = selected.isNotEmpty()) {
                Text(if (selected.isEmpty()) confirmLabel else "$confirmLabel (${selected.size})")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
private fun PickerRow(selected: Boolean, label: String, detail: String?, radio: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
    ) {
        if (radio) RadioButton(selected = selected, onClick = onClick)
        else Checkbox(checked = selected, onCheckedChange = { onClick() })
        Column {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1) }
        }
    }
}
