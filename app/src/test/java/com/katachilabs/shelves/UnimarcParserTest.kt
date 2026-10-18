package com.katachilabs.shelves

import com.katachilabs.shelves.data.remote.BookLookupService
import com.katachilabs.shelves.data.remote.BookMetadata
import com.katachilabs.shelves.data.remote.UnimarcParser
import com.katachilabs.shelves.data.remote.normalizedKey
import com.katachilabs.shelves.ui.common.formatVolumeRanges
import org.junit.Assert.assertEquals
import org.junit.Test

class UnimarcParserTest {
    // Extrait réel d'une réponse du SRU de la BnF (One Piece, tome 1, Glénat).
    private val xml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <srw:searchRetrieveResponse xmlns:srw="http://www.loc.gov/zing/srw/">
        <srw:numberOfRecords>242</srw:numberOfRecords>
        <srw:records><srw:record><srw:recordData>
        <mxc:record xmlns:mxc="info:lc/xmlns/marcxchange-v2" format="UNIMARC" type="Bibliographic">
          <mxc:controlfield tag="003">http://catalogue.bnf.fr/ark:/12148/cb43636708p</mxc:controlfield>
          <mxc:datafield tag="010" ind1=" " ind2=" "><mxc:subfield code="a">978-2-7234-8852-5</mxc:subfield></mxc:datafield>
          <mxc:datafield tag="200" ind1="1" ind2=" ">
            <mxc:subfield code="a">Romance dawn</mxc:subfield>
            <mxc:subfield code="b">Texte imprimé</mxc:subfield>
            <mxc:subfield code="e">à l'aube d'une grande aventure</mxc:subfield>
            <mxc:subfield code="f">Eiichirô Oda</mxc:subfield>
          </mxc:datafield>
          <mxc:datafield tag="210" ind1=" " ind2=" ">
            <mxc:subfield code="a">Grenoble</mxc:subfield>
            <mxc:subfield code="c">Glénat</mxc:subfield>
            <mxc:subfield code="d">impr. 2013</mxc:subfield>
          </mxc:datafield>
          <mxc:datafield tag="215" ind1=" " ind2=" "><mxc:subfield code="a">1 vol. (203 p.)</mxc:subfield></mxc:datafield>
          <mxc:datafield tag="225" ind1="1" ind2="9">
            <mxc:subfield code="a">One piece</mxc:subfield>
            <mxc:subfield code="v">tome 1</mxc:subfield>
          </mxc:datafield>
          <mxc:datafield tag="461" ind1=" " ind2="0">
            <mxc:subfield code="0">43702987</mxc:subfield>
            <mxc:subfield code="t">One piece</mxc:subfield>
            <mxc:subfield code="v">1</mxc:subfield>
          </mxc:datafield>
          <mxc:datafield tag="700" ind1=" " ind2="|">
            <mxc:subfield code="a">Oda</mxc:subfield>
            <mxc:subfield code="b">Eiichirō</mxc:subfield>
          </mxc:datafield>
        </mxc:record>
        </srw:recordData></srw:record></srw:records>
        <srw:nextRecordPosition>101</srw:nextRecordPosition>
        </srw:searchRetrieveResponse>
    """.trimIndent()

    @Test
    fun parsesBnfRecord() {
        val page = UnimarcParser.parse(xml)
        assertEquals(101, page.nextRecordPosition)
        val meta = page.records.single().toMetadata()
        assertEquals("9782723488525", meta.isbn)
        assertEquals("Romance dawn", meta.title)
        assertEquals("à l'aube d'une grande aventure", meta.subtitle)
        assertEquals(listOf("Eiichirō Oda"), meta.authors)
        assertEquals("Glénat", meta.publisher)
        assertEquals("2013", meta.publishedDate)
        assertEquals(203, meta.pageCount)
        assertEquals("One piece", meta.series?.title)
        assertEquals("43702987", meta.series?.bnfRecordId)
        assertEquals(1.0, meta.series?.volumeNumber)
        assertEquals("http://catalogue.bnf.fr/ark:/12148/cb43636708p", meta.bnfArk)
    }

    @Test
    fun mergeKeepsBnfFirstAndFillsGaps() {
        val bnf = BookMetadata(isbn = "9782723488525", title = "Romance dawn", sources = listOf("BnF"))
        val google = BookMetadata(isbn = "9782723488525", title = "One Piece T1", summary = "Luffy…", coverUrl = "https://img", sources = listOf("Google Books"))
        val merged = BookLookupService.merge("9782723488525", listOf(bnf, google))!!
        assertEquals("Romance dawn", merged.title)
        assertEquals("Luffy…", merged.summary)
        assertEquals("https://img", merged.coverUrl)
        assertEquals(listOf("BnF", "Google Books"), merged.sources)
    }

    @Test
    fun formatsVolumeRanges() {
        assertEquals("1-3, 5, 7-8", formatVolumeRanges(listOf(8.0, 1.0, 2.0, 3.0, 5.0, 7.0)))
        assertEquals("1, 1.5, 2", formatVolumeRanges(listOf(1.0, 1.5, 2.0)))
    }

    @Test
    fun normalizesKeys() {
        assertEquals("glenat", "Glénat".normalizedKey())
        assertEquals("one piece", "One  Piece !".normalizedKey())
    }
}
