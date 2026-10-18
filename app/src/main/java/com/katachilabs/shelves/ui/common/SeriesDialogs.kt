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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.katachilabs.shelves.data.db.Series
import com.katachilabs.shelves.data.remote.normalizedKey

/** Série choisie dans [SeriesPickerDialog]. */
sealed interface SeriesChoice {
    data object None : SeriesChoice
    data class Existing(val id: String) : SeriesChoice
    data class New(val title: String, val publisher: String) : SeriesChoice
}

/**
 * Choix d'une série parmi celles connues, avec recherche, création d'une nouvelle série
 * et, si demandé, saisie du tome.
 */
@Composable
fun SeriesPickerDialog(
    title: String,
    series: List<Series>,
    currentId: String?,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (choice: SeriesChoice, volume: String) -> Unit,
    initialVolume: String = "",
    allowNone: Boolean = true,
    allowCreate: Boolean = true,
    showVolume: Boolean = true,
    excludeId: String? = null,
) {
    var query by remember { mutableStateOf("") }
    var choice by remember { mutableStateOf<SeriesChoice?>(currentId?.let { SeriesChoice.Existing(it) } ?: SeriesChoice.None.takeIf { allowNone }) }
    var newPublisher by remember { mutableStateOf("") }
    var volume by remember { mutableStateOf(initialVolume) }

    val key = query.normalizedKey()
    val candidates = series
        .filter { it.id != excludeId }
        .filter { key.isEmpty() || it.title.normalizedKey().contains(key) || it.publisher.orEmpty().normalizedKey().contains(key) }
    val canCreate = allowCreate && query.isNotBlank() && candidates.none { it.title.normalizedKey() == key }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(if (allowCreate) "Rechercher ou créer une série" else "Rechercher une série") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.heightIn(max = 280.dp)) {
                    if (allowNone) {
                        item {
                            ChoiceRow(choice == SeriesChoice.None, "Aucune série", null) { choice = SeriesChoice.None }
                        }
                    }
                    if (canCreate) {
                        item {
                            val selected = (choice as? SeriesChoice.New)?.title == query.trim()
                            ChoiceRow(selected, "Créer la série « ${query.trim()} »", null) {
                                choice = SeriesChoice.New(query.trim(), newPublisher)
                            }
                        }
                    }
                    items(candidates, key = { it.id }) { s ->
                        ChoiceRow(choice == SeriesChoice.Existing(s.id), s.title, s.publisher) {
                            choice = SeriesChoice.Existing(s.id)
                        }
                    }
                }
                if (choice is SeriesChoice.New) {
                    OutlinedTextField(
                        value = newPublisher,
                        onValueChange = { newPublisher = it },
                        label = { Text("Éditeur (facultatif)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (showVolume && choice != null && choice != SeriesChoice.None) {
                    OutlinedTextField(
                        value = volume,
                        onValueChange = { volume = it },
                        label = { Text("Tome (ex. 12 ou 10.5)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val selected = choice ?: return@TextButton
                    // Le titre d'une nouvelle série est celui tapé au moment de valider.
                    onConfirm(if (selected is SeriesChoice.New) SeriesChoice.New(query.trim(), newPublisher.trim()) else selected, volume)
                },
                enabled = choice != null && (choice !is SeriesChoice.New || query.isNotBlank()),
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
private fun ChoiceRow(selected: Boolean, label: String, detail: String?, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

/** Renommage d'une série : titre et éditeur affichés. */
@Composable
fun RenameSeriesDialog(series: Series, onDismiss: () -> Unit, onConfirm: (title: String, publisher: String) -> Unit) {
    var title by remember { mutableStateOf(series.title) }
    var publisher by remember { mutableStateOf(series.publisher.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Renommer la série") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Titre") }, singleLine = true)
                OutlinedTextField(value = publisher, onValueChange = { publisher = it }, label = { Text("Éditeur") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(title, publisher) }, enabled = title.isNotBlank()) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
