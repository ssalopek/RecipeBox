package com.example.recipebox.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import androidx.core.graphics.scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
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

    private fun download(context: Context, url: String): String {
        val request = Request.Builder().url(url).header("User-Agent", Http.USER_AGENT).build()
        val bytes = Http.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val source = response.body.source()
            if (source.request(MAX_BYTES)) error("image too large")
            source.readByteArray()
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) error("not an image")

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= TARGET_WIDTH) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: error("could not decode")

        val scaled = if (decoded.width > TARGET_WIDTH) {
            decoded.scale(TARGET_WIDTH, decoded.height * TARGET_WIDTH / decoded.width)
        } else decoded

        val name = "${UUID.randomUUID()}.jpg"
        val target = file(context, name)
        target.parentFile?.mkdirs()
        FileOutputStream(target).use { out ->
            flattenOnWhite(scaled).compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
        return name
    }

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
}
