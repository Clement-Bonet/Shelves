package com.katachilabs.shelves.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class Http(
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build(),
) {
    /** GET texte ; `null` sur 404, exception sur les autres erreurs. */
    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Shelves/0.1 (Android)")
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .build()
        client.newCall(request).execute().use { response ->
            when {
                response.isSuccessful -> response.body.string()
                response.code == 404 -> null
                else -> throw IOException("HTTP ${response.code} sur $url")
            }
        }
    }
}
