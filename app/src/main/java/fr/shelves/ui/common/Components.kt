package fr.shelves.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil3.compose.SubcomposeAsyncImage
import fr.shelves.AppContainer
import fr.shelves.ShelvesApp

/** ViewModel construit à partir du conteneur de l'application. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(crossinline create: (AppContainer) -> VM): VM {
    val app = LocalContext.current.applicationContext as ShelvesApp
    return viewModel(factory = viewModelFactory { initializer { create(app.container) } })
}

/** Couverture du livre, ou une icône quand il n'y en a pas. Format 2:3. */
@Composable
fun Cover(url: String?, width: Dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(4.dp)
    val boxModifier = modifier
        .size(width, width * 1.5f)
        .clip(shape)
        .background(MaterialTheme.colorScheme.surfaceVariant)
    val placeholder = @Composable {
        Box(Modifier.size(width, width * 1.5f), contentAlignment = Alignment.Center) {
            Icon(
                Icons.AutoMirrored.Outlined.MenuBook,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(width / 2),
            )
        }
    }
    Box(boxModifier) {
        if (url == null) {
            placeholder()
        } else {
            SubcomposeAsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                loading = { placeholder() },
                error = { placeholder() },
                modifier = Modifier.size(width, width * 1.5f),
            )
        }
    }
}

/** Indicateur de possession. */
@Composable
fun OwnedIcon(owned: Boolean, modifier: Modifier = Modifier) {
    if (owned) {
        Icon(Icons.Filled.CheckCircle, contentDescription = "Possédé", tint = MaterialTheme.colorScheme.primary, modifier = modifier)
    } else {
        Icon(Icons.Outlined.Circle, contentDescription = "Non possédé", tint = MaterialTheme.colorScheme.outline, modifier = modifier)
    }
}

/** Saisie d'un nom et d'une description, pour créer ou renommer une étagère. */
@Composable
fun ShelfDialog(
    title: String,
    initialName: String = "",
    initialDescription: String = "",
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var description by remember { mutableStateOf(initialDescription) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nom") }, singleLine = true)
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description (facultatif)") })
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, description) }, enabled = name.isNotBlank()) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
fun ConfirmDialog(title: String, message: String, confirmLabel: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
