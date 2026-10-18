package com.katachilabs.shelves.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Couvertures photographiées par l'utilisateur, rangées dans `files/covers/`.
 * En base, elles sont référencées par `local:covers/<fichier>` plutôt que par un chemin absolu,
 * pour rester valables après une restauration sur un autre téléphone.
 */
class CoverStore(private val context: Context) {
    private val coversDir get() = File(context.filesDir, COVERS_DIR).apply { mkdirs() }
    private val captureFile get() = File(File(context.cacheDir, "captures").apply { mkdirs() }, "capture.jpg")

    /** Adresse où l'application photo écrit l'image (via FileProvider). */
    fun captureUri(): Uri {
        captureFile.delete()
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", captureFile)
    }

    /** Copie une image reçue d'une autre application (scanner de documents) à la place de la dernière photo. */
    suspend fun importCapture(uri: Uri) = withContext(Dispatchers.IO) {
        val target = captureFile
        context.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } }
            ?: throw IOException("Image illisible")
    }

    /** Redresse, réduit et enregistre la dernière photo ; renvoie la référence à stocker sur le livre. */
    suspend fun saveCapture(bookId: Long): String = withContext(Dispatchers.IO) {
        val source = captureFile
        if (!source.exists() || source.length() == 0L) throw IOException("Aucune photo reçue")
        val bitmap = decodeScaled(source, MAX_SIDE) ?: throw IOException("Photo illisible")
        val oriented = rotate(bitmap, ExifInterface(source.absolutePath).rotationDegrees)
        val name = "book-$bookId-${System.currentTimeMillis()}.jpg"
        File(coversDir, name).outputStream().use { oriented.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        source.delete()
        "$LOCAL_PREFIX$COVERS_DIR/$name"
    }

    fun delete(reference: String?) {
        localFile(context, reference)?.delete()
    }

    fun allFiles(): List<File> = coversDir.listFiles()?.filter { it.isFile }.orEmpty()

    /** Remplace toutes les couvertures (restauration d'une sauvegarde). */
    fun replaceAll(files: Map<String, ByteArray>) {
        coversDir.listFiles()?.forEach { it.delete() }
        files.forEach { (name, bytes) -> File(coversDir, File(name).name).writeBytes(bytes) }
    }

    private fun decodeScaled(file: File, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val decoded = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        val scale = maxSide.toFloat() / maxOf(decoded.width, decoded.height)
        return if (scale >= 1f) decoded
        else Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
    }

    private fun rotate(bitmap: Bitmap, degrees: Int): Bitmap =
        if (degrees == 0) bitmap
        else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees.toFloat()) }, true)

    companion object {
        const val LOCAL_PREFIX = "local:"
        const val COVERS_DIR = "covers"
        private const val MAX_SIDE = 1000

        fun isLocal(reference: String?) = reference?.startsWith(LOCAL_PREFIX) == true

        fun localFile(context: Context, reference: String?): File? =
            reference?.takeIf { isLocal(it) }?.let { File(context.filesDir, it.removePrefix(LOCAL_PREFIX)) }

        /** Ce que l'on donne au chargeur d'images : un fichier pour une photo, l'URL sinon. */
        fun model(context: Context, reference: String?): Any? = localFile(context, reference) ?: reference
    }
}
