package com.katachilabs.shelves.data.remote

/** Fiche d'un livre telle que renvoyée par une source, avant enregistrement. */
data class BookMetadata(
    val isbn: String?,
    val title: String,
    val subtitle: String? = null,
    val authors: List<String> = emptyList(),
    val publisher: String? = null,
    val publishedDate: String? = null,
    val pageCount: Int? = null,
    val summary: String? = null,
    val coverUrl: String? = null,
    val series: SeriesRef? = null,
    val bnfArk: String? = null,
    val sources: List<String> = emptyList(),
)

/** Rattachement d'un volume à sa série. */
data class SeriesRef(
    val title: String,
    val bnfRecordId: String? = null,
    val volumeLabel: String? = null,
    val volumeNumber: Double? = null,
)

/** Extrait le numéro d'un libellé de tome : « tome 12 » → 12.0, « 10.5 » → 10.5. */
fun parseVolumeNumber(label: String?): Double? =
    label?.let { Regex("""\d+(?:[.,]\d+)?""").find(it)?.value?.replace(',', '.')?.toDoubleOrNull() }
