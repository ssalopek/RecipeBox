package com.example.recipebox.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.graphics.scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Saved image file names of a recipe. The database column is still called "imagePaths"
 * because an early version stored several names, one per line. New rows hold at most one.
 */
val Recipe.imageFileNames: List<String>
    get() = imageName.lines().filter { it.isNotBlank() }

/** The one image shown for a recipe, if any. */
val Recipe.coverImage: String?
    get() = imageFileNames.firstOrNull()

object ImageStore {
    private const val DIR_NAME = "recipe_images"
    private const val MAX_BYTES = 8L * 1024 * 1024
    private const val MAX_PICKED_BYTES = 40L * 1024 * 1024   // full-size phone photos
    private const val TARGET_WIDTH = 1080

    /** Only builds the path. It does no file I/O, so it is safe to call while composing. */
    fun file(context: Context, name: String) = File(File(context.filesDir, DIR_NAME), name)

    /** Downloads the image, shrinks it, saves it as JPEG. Returns the file name, or null if it failed. */
    suspend fun save(context: Context, url: String): String? =
        withContext(Dispatchers.IO) {
            runCatching { download(context, url) }.getOrNull()
        }

    suspend fun deleteAll(context: Context, names: List<String>) =
        withContext(Dispatchers.IO) {
            names.forEach { file(context, it).delete() }
        }

    /** Deletes every saved image file. Used by the debug tools. */
    suspend fun deleteAllFiles(context: Context) =
        withContext(Dispatchers.IO) {
            File(context.filesDir, DIR_NAME).listFiles()?.forEach { it.delete() }
        }

    /** Reads an image the user picked from their phone, shrinks it, saves it as JPEG. Null if it failed. */
    suspend fun saveFromUri(context: Context, uri: Uri): String? =
        withContext(Dispatchers.IO) {
            runCatching { process(context, readUri(context, uri)) }.getOrNull()
        }

    private fun download(context: Context, url: String): String {
        val request = Request.Builder().url(url).header("User-Agent", Http.USER_AGENT).build()
        val bytes = Http.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val source = response.body.source()
            if (source.request(MAX_BYTES)) error("image too large")
            source.readByteArray()
        }
        return process(context, bytes)
    }

    private fun readUri(context: Context, uri: Uri): ByteArray {
        val input = context.contentResolver.openInputStream(uri) ?: error("can't open the image")
        input.use { stream ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            var total = 0L
            while (true) {
                val n = stream.read(buffer)
                if (n < 0) break
                total += n
                if (total > MAX_PICKED_BYTES) error("image too large")
                out.write(buffer, 0, n)
            }
            return out.toByteArray()
        }
    }

    /** Decodes, straightens, shrinks and saves an image. Used for downloaded and picked images. */
    private fun process(context: Context, bytes: ByteArray): String {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) error("not an image")

        // Phone photos are often stored sideways with a rotation flag, so apply it
        val rotation = rotationDegrees(bytes)
        val sideways = rotation == 90 || rotation == 270
        val finalWidth = if (sideways) bounds.outHeight else bounds.outWidth

        var sample = 1
        while (finalWidth / (sample * 2) >= TARGET_WIDTH) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: error("could not decode")

        val upright = if (rotation == 0) decoded else Bitmap.createBitmap(
            decoded, 0, 0, decoded.width, decoded.height,
            Matrix().apply { postRotate(rotation.toFloat()) }, true
        )
        val scaled = if (upright.width > TARGET_WIDTH) {
            upright.scale(TARGET_WIDTH, upright.height * TARGET_WIDTH / upright.width)
        } else upright

        val name = "${UUID.randomUUID()}.jpg"
        val target = file(context, name)
        target.parentFile?.mkdirs()
        FileOutputStream(target).use { out ->
            flattenOnWhite(scaled).compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
        return name
    }

    private fun rotationDegrees(bytes: ByteArray): Int = runCatching {
        val exif = ExifInterface(ByteArrayInputStream(bytes))
        when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    }.getOrDefault(0)

    // JPEG has no transparency, so transparent PNG/WebP images would turn black
    private fun flattenOnWhite(source: Bitmap): Bitmap {
        if (!source.hasAlpha()) return source
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        Canvas(result).apply {
            drawColor(Color.WHITE)
            drawBitmap(source, 0f, 0f, null)
        }
        return result
    }

    /**
     * Saves an image that comes out of a backup. It was already shrunk when it was first saved,
     * so the bytes are written as they are. Returns the new file name, or null if it isn't an image.
     */
    suspend fun saveBytes(context: Context, bytes: ByteArray): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth <= 0) error("not an image")
                val name = "${UUID.randomUUID()}.jpg"
                val target = file(context, name)
                target.parentFile?.mkdirs()
                target.writeBytes(bytes)
                name
            }.getOrNull()
        }
}
