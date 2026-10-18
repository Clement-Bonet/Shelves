package fr.shelves.ui.common

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContract
import fr.shelves.data.db.Book
import fr.shelves.data.db.Series
import fr.shelves.data.db.Shelf

/** Contact choisi : son nom et son numéro (ou son adresse e-mail). */
data class PickedContact(val name: String, val value: String)

/**
 * Sélecteur de contacts Android filtré sur les numéros de téléphone ou les adresses e-mail.
 * Le sélecteur accorde un accès ponctuel au contact choisi : pas besoin de la permission READ_CONTACTS.
 */
class PickContactDetail(private val kind: Kind) : ActivityResultContract<Unit, Uri?>() {
    enum class Kind(val contentUri: Uri, val valueColumn: String) {
        Phone(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, ContactsContract.CommonDataKinds.Phone.NUMBER),
        Email(ContactsContract.CommonDataKinds.Email.CONTENT_URI, ContactsContract.CommonDataKinds.Email.ADDRESS),
    }

    override fun createIntent(context: Context, input: Unit) = Intent(Intent.ACTION_PICK, kind.contentUri)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        intent?.data?.takeIf { resultCode == Activity.RESULT_OK }

    fun read(context: Context, uri: Uri): PickedContact? {
        val projection = arrayOf(ContactsContract.Contacts.DISPLAY_NAME, kind.valueColumn)
        return context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            PickedContact(name = cursor.getString(0).orEmpty(), value = cursor.getString(1).orEmpty())
        }
    }
}

/** Contact choisi pour un prêt : seul le nom compte. */
fun contactName(context: Context, uri: Uri): String? =
    context.contentResolver.query(uri, arrayOf(ContactsContract.Contacts.DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0) else null
    }

fun sendSms(context: Context, number: String, text: String) =
    launch(context, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(number)}")).putExtra("sms_body", text))

fun sendEmail(context: Context, address: String, subject: String, text: String) =
    launch(
        context,
        Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
            .putExtra(Intent.EXTRA_EMAIL, arrayOf(address))
            .putExtra(Intent.EXTRA_SUBJECT, subject)
            .putExtra(Intent.EXTRA_TEXT, text),
    )

fun shareText(context: Context, subject: String, text: String) =
    launch(
        context,
        Intent.createChooser(
            Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, subject)
                .putExtra(Intent.EXTRA_TEXT, text),
            "Partager",
        ),
    )

private fun launch(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "Aucune application pour envoyer ce message", Toast.LENGTH_SHORT).show()
    }
}

/** Résumé texte d'une étagère, lisible dans un SMS ou un e-mail. */
fun shelfShareText(shelf: Shelf, books: List<Book>, series: Map<String, Series>, seriesVolumes: Map<String, List<Book>>): String =
    buildString {
        appendLine("📚 ${shelf.name}")
        shelf.description?.let { appendLine(it) }
        appendLine()
        val (inSeries, standalone) = books.partition { it.seriesId != null && series.containsKey(it.seriesId) }
        inSeries.map { series.getValue(it.seriesId!!) }.distinct().sortedBy { it.title.lowercase() }.forEach { s ->
            val id = s.id
            val volumes = seriesVolumes[id].orEmpty()
            val owned = volumes.filter { it.owned }
            append("• ${s.title}")
            s.publisher?.let { append(" ($it)") }
            val numbers = owned.mapNotNull { it.volumeNumber }
            if (numbers.isNotEmpty()) append(" — tomes ${formatVolumeRanges(numbers)}")
            appendLine(" [${owned.size}/${volumes.size}]")
        }
        standalone.forEach { book ->
            append("• ${book.title}")
            book.authors?.let { append(" — $it") }
            book.isbn?.let { append(" (ISBN $it)") }
            appendLine()
        }
        appendLine()
        append("Envoyé depuis Shelves")
    }

/** 1, 2, 3, 5, 7, 8 → « 1-3, 5, 7-8 ». */
fun formatVolumeRanges(numbers: List<Double>): String {
    val sorted = numbers.distinct().sorted()
    if (sorted.isEmpty()) return ""
    fun fmt(d: Double) = if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()
    val ranges = mutableListOf<Pair<Double, Double>>()
    for (n in sorted) {
        val last = ranges.lastOrNull()
        if (last != null && n == last.second + 1 && n % 1.0 == 0.0 && last.second % 1.0 == 0.0) {
            ranges[ranges.lastIndex] = last.first to n
        } else {
            ranges += n to n
        }
    }
    return ranges.joinToString(", ") { (a, b) -> if (a == b) fmt(a) else "${fmt(a)}-${fmt(b)}" }
}
