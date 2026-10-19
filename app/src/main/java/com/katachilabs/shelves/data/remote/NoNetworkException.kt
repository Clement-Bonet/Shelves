package com.katachilabs.shelves.data.remote

import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Aucune source n'a pu être jointe : pas de réseau, ou réseau inutilisable. */
class NoNetworkException(cause: Throwable) : IOException(MESSAGE, cause) {
    companion object {
        const val MESSAGE = "Pas de connexion à Internet : vérifiez le réseau puis réessayez."
    }
}

/** La BnF a répondu par une erreur (service en panne ou en maintenance). */
class BnfUnavailableException(cause: Throwable) :
    IOException("La BnF ne répond pas pour le moment : réessayez plus tard.", cause)

/** Échec dû au réseau (hôte introuvable, connexion refusée ou expirée), et non à la source elle-même. */
fun Throwable.isNetworkFailure(): Boolean =
    this is NoNetworkException || this is UnknownHostException || this is ConnectException ||
        this is SocketTimeoutException || this is NoRouteToHostException ||
        (cause?.let { it !== this && it.isNetworkFailure() } ?: false)

/** Message à montrer à l'utilisateur pour une erreur de recherche ou de synchronisation. */
fun Throwable.userMessage(prefix: String): String = when {
    isNetworkFailure() -> NoNetworkException.MESSAGE
    this is BnfUnavailableException -> message.orEmpty()
    else -> "$prefix : ${message ?: javaClass.simpleName}"
}
