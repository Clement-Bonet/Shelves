package com.katachilabs.shelves.data.remote

import com.katachilabs.shelves.util.Isbn
import org.w3c.dom.Element
import java.io.StringReader
import java.net.URLEncoder
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

/**
 * Catalogue général de la BnF, via son service SRU (sans clé, notices UNIMARC).
 * Documentation : https://www.bnf.fr/fr/service-sru-catalogue-general-de-la-bnf
 */
class BnfClient(private val http: Http) {

    suspend fun findByIsbn(code: String): BookMetadata? {
        val index = if (Isbn.isIsbn13(code)) "bib.fuzzyIsbn" else "bib.ean"
        return search("$index all \"$code\"", maximumRecords = 5).records
            .firstOrNull()
            ?.toMetadata()
    }

    /**
     * Volumes d'une série : la BnF n'a pas d'index sur le lien vers la notice d'ensemble,
     * on cherche donc par titre (et éditeur) puis on garde les notices qui pointent vers la série.
     * Les éditions successives d'un même manga ont des notices d'ensemble distinctes.
     */
    suspend fun findSeriesVolumes(
        seriesTitle: String,
        publisher: String?,
        bnfRecordId: String?,
        maxPages: Int = 10,
    ): List<BookMetadata> {
        val query = buildString {
            append("bib.title all \"${seriesTitle.sruEscape()}\"")
            if (!publisher.isNullOrBlank()) append(" and bib.publisher all \"${publisher.sruEscape()}\"")
        }
        val wantedTitle = seriesTitle.normalizedKey()
        val volumes = mutableListOf<BookMetadata>()
        var start = 1
        repeat(maxPages) {
            val page = search(query, maximumRecords = PAGE_SIZE, startRecord = start)
            page.records
                .filter { record ->
                    val link = record.seriesLink()
                    if (bnfRecordId != null) link?.recordId == bnfRecordId
                    else record.seriesTitleAndVolume()?.first?.normalizedKey() == wantedTitle
                }
                .mapTo(volumes) { it.toMetadata() }
            start = page.nextRecordPosition ?: return volumes.distinctByVolume()
        }
        return volumes.distinctByVolume()
    }

    private suspend fun search(query: String, maximumRecords: Int, startRecord: Int = 1): SruPage {
        val url = "$BASE_URL?version=1.2&operation=searchRetrieve" +
            "&query=${URLEncoder.encode(query, "UTF-8")}" +
            "&recordSchema=unimarcxchange" +
            "&maximumRecords=$maximumRecords&startRecord=$startRecord"
        val xml = http.get(url) ?: return SruPage(emptyList(), null)
        return UnimarcParser.parse(xml)
    }

    private fun List<BookMetadata>.distinctByVolume(): List<BookMetadata> =
        distinctBy { it.isbn ?: it.bnfArk ?: it.title }

    private fun String.sruEscape() = replace("\"", " ")

    companion object {
        const val BASE_URL = "https://catalogue.bnf.fr/api/SRU"
        private const val PAGE_SIZE = 100
    }
}

data class SruPage(val records: List<UnimarcRecord>, val nextRecordPosition: Int?)

/** Notice UNIMARC réduite à ce dont l'application a besoin : zones → sous-zones. */
data class UnimarcRecord(
    val controlFields: Map<String, String>,
    val dataFields: List<DataField>,
) {
    data class DataField(val tag: String, val subfields: List<Pair<Char, String>>) {
        fun first(code: Char): String? = subfields.firstOrNull { it.first == code }?.second?.trim()?.ifBlank { null }
        fun all(code: Char): List<String> = subfields.filter { it.first == code }.map { it.second.trim() }
    }

    data class SeriesLink(val recordId: String?, val title: String?, val volume: String?)

    fun fields(tag: String) = dataFields.filter { it.tag == tag }
    fun field(tag: String) = dataFields.firstOrNull { it.tag == tag }

    /** Zone 461 : lien vers la notice d'ensemble (la série). */
    fun seriesLink(): SeriesLink? = field("461")?.let { SeriesLink(it.first('0'), it.first('t'), it.first('v')) }

    /** Zone 225 : mention de collection telle qu'imprimée (« One piece », « tome 1 »). */
    fun seriesTitleAndVolume(): Pair<String, String?>? =
        field("225")?.let { f -> f.first('a')?.let { it to f.first('v') } }

    fun toMetadata(): BookMetadata {
        val title200 = field("200")
        val isbn = (field("010")?.first('a') ?: field("073")?.first('a'))
            ?.let { Isbn.normalize(it) }
            ?.let { if (Isbn.isValidIsbn10(it)) Isbn.isbn10To13(it) else it }
        val link = seriesLink()
        val collection = seriesTitleAndVolume()
        val seriesTitle = link?.title ?: collection?.first
        val volumeLabel = link?.volume ?: collection?.second
            ?: title200?.first('h')
        val publication = field("214") ?: field("210")
        val authors = (fields("700") + fields("701") + fields("710") + fields("711"))
            .mapNotNull { f ->
                val name = f.first('a') ?: return@mapNotNull null
                listOfNotNull(f.first('b'), name).joinToString(" ")
            }
            .distinct()
        val title = title200?.first('a')
            ?: seriesTitle
            ?: "Sans titre"
        return BookMetadata(
            isbn = isbn?.ifBlank { null },
            title = title,
            subtitle = listOfNotNull(title200?.first('i'), title200?.first('e')).joinToString(" : ").ifBlank { null },
            authors = authors.ifEmpty { listOfNotNull(title200?.first('f')) },
            publisher = publication?.first('c'),
            publishedDate = publication?.first('d')?.let { Regex("""\d{4}""").find(it)?.value },
            pageCount = field("215")?.first('a')?.let { Regex("""(\d+)\s*p""").find(it)?.groupValues?.get(1)?.toIntOrNull() },
            summary = fields("330").mapNotNull { it.first('a') }.joinToString("\n\n").ifBlank { null },
            series = seriesTitle?.let {
                SeriesRef(
                    title = it,
                    bnfRecordId = link?.recordId,
                    volumeLabel = volumeLabel,
                    volumeNumber = parseVolumeNumber(volumeLabel),
                )
            },
            bnfArk = controlFields["003"],
            sources = listOf("BnF"),
        )
    }
}

object UnimarcParser {
    private const val SRW = "http://www.loc.gov/zing/srw/"
    private const val MARC = "info:lc/xmlns/marcxchange-v2"

    fun parse(xml: String): SruPage {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val document = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
        val next = document.getElementsByTagNameNS(SRW, "nextRecordPosition")
            .item(0)?.textContent?.trim()?.toIntOrNull()
        val records = document.getElementsByTagNameNS(MARC, "record").elements().map { record ->
            val control = record.getElementsByTagNameNS(MARC, "controlfield").elements()
                .associate { it.getAttribute("tag") to it.textContent.trim() }
            val data = record.getElementsByTagNameNS(MARC, "datafield").elements().map { field ->
                UnimarcRecord.DataField(
                    tag = field.getAttribute("tag"),
                    subfields = field.getElementsByTagNameNS(MARC, "subfield").elements().mapNotNull { sub ->
                        sub.getAttribute("code").firstOrNull()?.let { it to sub.textContent }
                    },
                )
            }
            UnimarcRecord(control, data)
        }
        return SruPage(records, next)
    }

    private fun org.w3c.dom.NodeList.elements(): List<Element> =
        (0 until length).mapNotNull { item(it) as? Element }
}

/** Clé de comparaison insensible à la casse, aux accents et à la ponctuation. */
fun String.normalizedKey(): String =
    java.text.Normalizer.normalize(lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("""\p{M}"""), "")
        .replace(Regex("""[^a-z0-9]+"""), " ")
        .trim()
