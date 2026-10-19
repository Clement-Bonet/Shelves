package com.katachilabs.shelves.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.katachilabs.shelves.data.BackupManager
import com.katachilabs.shelves.data.SettingsStore
import com.katachilabs.shelves.ui.common.ConfirmDialog
import com.katachilabs.shelves.ui.common.appViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class SettingsViewModel(private val settings: SettingsStore, private val backup: BackupManager) : ViewModel() {
    val googleKey = settings.googleBooksApiKey.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    fun saveGoogleKey(value: String) = viewModelScope.launch {
        settings.setGoogleBooksApiKey(value)
        _messages.send("Clé enregistrée")
    }

    fun export(uri: Uri) = viewModelScope.launch {
        _messages.send(
            try {
                val data = backup.export(uri)
                "Sauvegarde terminée : ${data.shelves} étagères, ${data.books} livres, ${data.photos} photos"
            } catch (e: Exception) {
                "Échec de la sauvegarde : ${e.message}"
            },
        )
    }

    fun import(uri: Uri) = viewModelScope.launch {
        _messages.send(
            try {
                val data = backup.import(uri)
                "Restauration terminée : ${data.shelves} étagères, ${data.books} livres, ${data.photos} photos"
            } catch (e: Exception) {
                "Fichier illisible : ${e.message}"
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { SettingsViewModel(it.settings, it.backup) }
    val savedKey by viewModel.googleKey.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val versionName = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }
    var key by remember(savedKey) { mutableStateOf(savedKey.orEmpty()) }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    // Le sélecteur de documents propose Google Drive : c'est ainsi que l'on sauvegarde sur Drive.
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let(viewModel::export)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pendingImport = uri
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Réglages") },
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Sauvegarde", style = MaterialTheme.typography.titleMedium)
            Text(
                "Exportez vos étagères et vos photos de couverture dans un fichier ZIP. Choisissez « Drive » dans le sélecteur pour l'enregistrer " +
                    "sur votre espace Google Drive. Android sauvegarde aussi automatiquement l'application sur Drive " +
                    "si la sauvegarde Google est activée sur le téléphone.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = { exportLauncher.launch("shelves-${LocalDate.now()}.zip") }, modifier = Modifier.fillMaxWidth()) {
                Text("Exporter (Google Drive ou téléphone)")
            }
            OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/zip", "application/json", "application/octet-stream")) }, modifier = Modifier.fillMaxWidth()) {
                Text("Restaurer une sauvegarde")
            }

            HorizontalDivider()

            Text("Google Books", style = MaterialTheme.typography.titleMedium)
            Text(
                "Facultatif. Avec votre propre clé (console Google Cloud, API « Books »), Google Books complète les résumés " +
                    "et les couvertures. Sans clé, Google Books n'est pas interrogé.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                label = { Text("Clé d'API Google Books") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = { viewModel.saveGoogleKey(key) }, enabled = key != savedKey.orEmpty()) { Text("Enregistrer la clé") }

            HorizontalDivider()
            Text(
                "Données : catalogue général de la BnF (licence ouverte), Google Books, Open Library.",
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = { uriHandler.openUri(PRIVACY_POLICY_URL) }) { Text("Politique de confidentialité") }
            Text("Shelves $versionName", style = MaterialTheme.typography.bodySmall)
        }
    }

    pendingImport?.let { uri ->
        ConfirmDialog(
            title = "Restaurer cette sauvegarde ?",
            message = "Toutes les données actuelles seront remplacées par celles du fichier.",
            confirmLabel = "Restaurer",
            onDismiss = { pendingImport = null },
            onConfirm = {
                pendingImport = null
                viewModel.import(uri)
            },
        )
    }
}

/** Page publique exigée par le Play Store ; le texte source est dans docs/ du dépôt. */
private const val PRIVACY_POLICY_URL =
    "https://github.com/Clement-Bonet/Shelves/blob/main/docs/politique-de-confidentialite.md"
