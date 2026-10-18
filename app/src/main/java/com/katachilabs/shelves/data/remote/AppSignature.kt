package com.katachilabs.shelves.data.remote

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.security.MessageDigest

/**
 * En-têtes attendus par les API Google quand la clé est restreinte aux applications Android :
 * nom du package et empreinte SHA-1 du certificat de signature (hexadécimal, sans « : »).
 * Les bibliothèques Google les ajoutent seules ; nos appels HTTP directs doivent le faire eux-mêmes.
 */
object AppSignature {
    fun googleApiHeaders(context: Context): Map<String, String> {
        val sha1 = signingCertificateSha1(context) ?: return emptyMap()
        return mapOf(
            "X-Android-Package" to context.packageName,
            "X-Android-Cert" to sha1,
        )
    }

    private fun signingCertificateSha1(context: Context): String? = try {
        val pm = context.packageManager
        val signatures: Array<Signature>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
        }
        signatures?.firstOrNull()?.let { sha1Hex(it.toByteArray()) }
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    fun sha1Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02X".format(it) }
}
