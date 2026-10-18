package com.katachilabs.shelves

import com.katachilabs.shelves.data.remote.AppSignature
import org.junit.Assert.assertEquals
import org.junit.Test

class AppSignatureTest {
    @Test
    fun formatsSha1LikeGoogleExpects() {
        // Hexadécimal majuscule sans séparateur, comme attendu dans l'en-tête X-Android-Cert.
        assertEquals("A9993E364706816ABA3E25717850C26C9CD0D89D", AppSignature.sha1Hex("abc".toByteArray()))
    }
}
