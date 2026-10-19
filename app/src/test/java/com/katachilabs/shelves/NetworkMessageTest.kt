package com.katachilabs.shelves

import com.katachilabs.shelves.data.remote.BnfUnavailableException
import com.katachilabs.shelves.data.remote.NoNetworkException
import com.katachilabs.shelves.data.remote.isNetworkFailure
import com.katachilabs.shelves.data.remote.userMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.UnknownHostException

class NetworkMessageTest {
    @Test
    fun recognizesNetworkFailures() {
        assertTrue(UnknownHostException("catalogue.bnf.fr").isNetworkFailure())
        assertTrue(IOException("enveloppe", java.net.SocketTimeoutException()).isNetworkFailure())
        assertFalse(IOException("HTTP 503").isNetworkFailure())
    }

    @Test
    fun explainsErrorsToTheUser() {
        assertEquals(NoNetworkException.MESSAGE, UnknownHostException().userMessage("Recherche impossible"))
        assertEquals("La BnF ne répond pas pour le moment : réessayez plus tard.", BnfUnavailableException(IOException("HTTP 503")).userMessage("Recherche impossible"))
        assertEquals("Recherche impossible : HTTP 500", IOException("HTTP 500").userMessage("Recherche impossible"))
    }
}
