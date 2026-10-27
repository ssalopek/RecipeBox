package com.example.recipebox.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit
import androidx.core.graphics.scale

/** The saved image file names of a recipe. */
val Recipe.coverImage: String?
    get() = imageName.lineSequence().firstOrNull { it.isNotBlank() }
object ImageStore {
    private const val MAX_BYTES = 8L * 1024 * 1024
    private const val TARGET_WIDTH = 1080
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun dir(context: Context) =
        File(context.filesDir, "recipe_images").apply { mkdirs() }

    fun file(context: Context, name: String) = File(dir(context), name)

    /** Downloads the image, shrinks it, saves it as JPEG. Returns the file name, or null if it failed. */
    suspend fun save(context: Context, url: String): String? =
        withContext(Dispatchers.IO) {
            runCatching { download(context, url) }.getOrNull()
        }

    suspend fun deleteAll(context: Context, names: List<String>) =
        withContext(Dispatchers.IO) {
            names.forEach { file(context, it).delete() }
        }

    private fun download(context: Context, url: String): String {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        val bytes = client.newCall(request).execute().use { response ->
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

        val bitmap = if (decoded.width > TARGET_WIDTH) {
            decoded.scale(TARGET_WIDTH, decoded.height * TARGET_WIDTH / decoded.width)
        } else decoded

        val name = "${UUID.randomUUID()}.jpg"
        FileOutputStream(file(context, name)).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
        return name
    }
}