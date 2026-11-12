package com.example.recipebox.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Backup file format: a .zip containing
 *   recipes.json   readable JSON, always the FIRST entry
 *   images/NAME    the cover images
 *
 * recipes.json looks like:
 *   { "app": "RecipeBox", "formatVersion": 1, "exportedAt": "2026-11-12T10:00:00Z",
 *     "categories": ["Pasta", ...],
 *     "recipes": [ { "title", "bodyText", "sourceUrl", "sourcePlatform", "image", "createdAt",
 *                    "categories": ["Pasta"] } ] }
 *
 * Categories are stored by name, and database IDs are not stored at all, so a backup can be
 * restored into any database. Raise FORMAT_VERSION if the format ever changes in a way that
 * older versions of the app cannot read.
 */
object Backup {
    const val FORMAT_VERSION = 1
    private const val APP_NAME = "RecipeBox"
    private const val JSON_ENTRY = "recipes.json"
    private const val IMAGE_DIR = "images/"
    private const val MAX_JSON_BYTES = 50L * 1024 * 1024
    private const val MAX_IMAGE_BYTES = 8L * 1024 * 1024

    /** An error with a message that is fine to show to the user. */
    class BackupException(message: String) : Exception(message)

    data class ExportResult(val recipes: Int, val categories: Int, val images: Int)

    data class ImportResult(
        val imported: Int,
        val skipped: Int,
        val categoriesAdded: Int,
        val imagesMissing: Int
    )

    private class ParsedRecipe(
        val title: String,
        val body: String,
        val url: String?,
        val platform: String?,
        val image: String?,
        val createdAt: Long,
        val categories: List<String>
    )

    // ------------------------------------------------------------------ export

    suspend fun export(context: Context, db: AppDatabase, uri: Uri): ExportResult =
        withContext(Dispatchers.IO) {
            val dao = db.recipeDao()
            // One consistent snapshot of the three tables
            val (recipes, categories, links) = db.withTransaction {
                Triple(dao.allRecipes(), dao.allCategories(), dao.allLinks())
            }
            val categoryNames = categories.associate { it.id to it.name }

            val imageNames = mutableListOf<String>()
            val recipeArray = JSONArray()
            for (r in recipes) {
                val image = r.coverImage?.takeIf { ImageStore.file(context, it).isFile }
                if (image != null) imageNames += image
                recipeArray.put(
                    JSONObject().apply {
                        put("title", r.title)
                        put("bodyText", r.bodyText)
                        put("sourceUrl", r.sourceUrl ?: JSONObject.NULL)
                        put("sourcePlatform", r.sourcePlatform ?: JSONObject.NULL)
                        put("image", image?.let { IMAGE_DIR + it } ?: JSONObject.NULL)
                        put("createdAt", r.createdAt)
                        put(
                            "categories",
                            JSONArray(
                                links.filter { it.recipeId == r.id }
                                    .mapNotNull { categoryNames[it.categoryId] }
                                    .sorted()
                            )
                        )
                    }
                )
            }
            val root = JSONObject().apply {
                put("app", APP_NAME)
                put("formatVersion", FORMAT_VERSION)
                put("exportedAt", isoNow())
                put("categories", JSONArray(categories.map { it.name }.sorted()))
                put("recipes", recipeArray)
            }
            // org.json writes "/" as "\/", which makes links in the file hard to read
            val json = root.toString(2).replace("\\/", "/")

            val output = context.contentResolver.openOutputStream(uri, "w")
                ?: throw BackupException("Couldn't open the file to write to.")
            ZipOutputStream(output.buffered()).use { zip ->
                zip.putNextEntry(ZipEntry(JSON_ENTRY))
                zip.write(json.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                for (name in imageNames) {
                    zip.putNextEntry(ZipEntry(IMAGE_DIR + name))
                    ImageStore.file(context, name).inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            ExportResult(recipes.size, categories.size, imageNames.size)
        }

    // ------------------------------------------------------------------ import

    /**
     * Adds the recipes from a backup. Nothing is deleted or changed. A recipe that is already
     * in the app is skipped: same link, or (for recipes without a link) same title and text.
     */
    suspend fun import(context: Context, db: AppDatabase, uri: Uri): ImportResult =
        withContext(Dispatchers.IO) {
            val dao = db.recipeDao()
            val input = context.contentResolver.openInputStream(uri)
                ?: throw BackupException("Couldn't open the file.")
            val savedImages = mutableListOf<String>()
            try {
                ZipInputStream(input.buffered()).use { zip ->
                    // 1. recipes.json must be the first entry
                    val first = zip.nextEntry
                    if (first == null || first.name != JSON_ENTRY) {
                        throw BackupException("This doesn't look like a RecipeBox backup.")
                    }
                    val jsonBytes = readLimited(zip, MAX_JSON_BYTES)
                        ?: throw BackupException("The backup file is too large to read.")
                    val root = try {
                        JSONObject(String(jsonBytes, Charsets.UTF_8))
                    } catch (e: JSONException) {
                        throw BackupException("The backup file is damaged.")
                    }
                    if (root.optString("app") != APP_NAME) {
                        throw BackupException("This doesn't look like a RecipeBox backup.")
                    }
                    val version = root.optInt("formatVersion", 0)
                    if (version > FORMAT_VERSION) {
                        throw BackupException(
                            "This backup was made by a newer version of the app. Update the app and try again."
                        )
                    }
                    if (version < 1) throw BackupException("The backup file is damaged.")
                    val array = root.optJSONArray("recipes")
                        ?: throw BackupException("The backup has no recipes.")

                    // 2. Work out which recipes are new
                    val seen = HashSet<String>()
                    val fresh = mutableListOf<ParsedRecipe>()
                    var skipped = 0
                    for (r in parseRecipes(array)) {
                        val key = r.url ?: "manual:${r.title}\u0000${r.body}"
                        val duplicate = !seen.add(key) || (
                            if (r.url != null) dao.findBySourceUrl(r.url) != null
                            else dao.countManual(r.title, r.body) > 0
                            )
                        if (duplicate) skipped++ else fresh += r
                    }

                    // 3. Stream the images of the new recipes straight to disk
                    val needed = fresh.mapNotNull { it.image }.toSet()
                    val saved = HashMap<String, String>()
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        val safe = !entry.isDirectory && name in needed &&
                            name.startsWith(IMAGE_DIR) &&
                            name.indexOf('/', IMAGE_DIR.length) == -1 && !name.contains("..")
                        if (safe) {
                            val bytes = readLimited(zip, MAX_IMAGE_BYTES)
                            val fileName = bytes?.let { ImageStore.saveBytes(context, it) }
                            if (fileName != null) {
                                saved[name] = fileName
                                savedImages += fileName
                            }
                        }
                        entry = zip.nextEntry
                    }

                    // 4. Insert everything in one transaction, all or nothing
                    val categoryIds = HashMap<String, Long>()
                    var added = 0
                    var missing = 0
                    db.withTransaction {
                        dao.allCategories().forEach { categoryIds[it.name.lowercase()] = it.id }
                        for (r in fresh) {
                            val ids = r.categories.map { it.trim() }.filter { it.isNotEmpty() }
                                .map { name ->
                                    categoryIds.getOrPut(name.lowercase()) {
                                        added++
                                        dao.insertCategory(Category(name = name))
                                    }
                                }.toSet()
                            val imageName = r.image?.let { saved[it] }.orEmpty()
                            if (r.image != null && imageName.isEmpty()) missing++
                            val id = dao.insert(
                                Recipe(
                                    title = r.title,
                                    bodyText = r.body,
                                    sourceUrl = r.url,
                                    sourcePlatform = r.platform,
                                    imageName = imageName,
                                    createdAt = r.createdAt
                                )
                            )
                            dao.insertLinks(ids.map { RecipeCategory(id, it) })
                        }
                    }
                    ImportResult(fresh.size, skipped, added, missing)
                }
            } catch (e: Throwable) {
                // Don't leave image files behind from an import that failed
                ImageStore.deleteAll(context, savedImages)
                throw e
            }
        }

    // ----------------------------------------------------------------- helpers

    private fun parseRecipes(array: JSONArray): List<ParsedRecipe> =
        (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            ParsedRecipe(
                title = o.optString("title").trim().ifEmpty { "Untitled" },
                body = o.optString("bodyText"),
                url = o.stringOrNull("sourceUrl"),
                platform = o.stringOrNull("sourcePlatform"),
                image = o.stringOrNull("image"),
                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                categories = o.optJSONArray("categories")
                    ?.let { a -> (0 until a.length()).map { a.optString(it) } }
                    .orEmpty()
            )
        }

    private fun JSONObject.stringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    /** Reads the stream to its end, or returns null if it is longer than max bytes. */
    private fun readLimited(stream: InputStream, max: Long): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val n = stream.read(buffer)
            if (n < 0) break
            total += n
            if (total > max) return null
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    private fun isoNow(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date())
}
