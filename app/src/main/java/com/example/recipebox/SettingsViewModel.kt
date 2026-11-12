package com.example.recipebox

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.recipebox.data.AppDatabase
import com.example.recipebox.data.Backup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)

    var busy by mutableStateOf(false)
        private set

    /** The result of the last export or import, shown on the settings screen. */
    var message by mutableStateOf<String?>(null)
        private set

    fun exportBackup(uri: Uri) = runTask("Export failed") {
        val r = Backup.export(getApplication(), db, uri)
        "Exported ${plural(r.recipes, "recipe")}, ${plural(r.categories, "category", "categories")} " +
            "and ${plural(r.images, "image")}."
    }

    fun importBackup(uri: Uri) = runTask("Import failed") {
        val r = Backup.import(getApplication(), db, uri)
        if (r.imported == 0) {
            if (r.skipped > 0) "Nothing new to import. All ${plural(r.skipped, "recipe")} are already in the app."
            else "The backup contains no recipes."
        } else buildString {
            append("Imported ${plural(r.imported, "recipe")}.")
            if (r.skipped > 0) append(" ${plural(r.skipped, "recipe")} already in the app were skipped.")
            if (r.categoriesAdded > 0) append(" ${plural(r.categoriesAdded, "new category", "new categories")} added.")
            if (r.imagesMissing > 0) append(" ${plural(r.imagesMissing, "image")} could not be restored.")
        }
    }

    private fun runTask(errorPrefix: String, block: suspend () -> String) {
        if (busy) return
        busy = true
        message = null
        viewModelScope.launch {
            message = try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Backup.BackupException) {
                "$errorPrefix: ${e.message}"
            } catch (e: Exception) {
                "$errorPrefix. ${e.message ?: "Something went wrong."}"
            }
            busy = false
        }
    }

    private fun plural(n: Int, one: String, many: String = "${one}s") =
        if (n == 1) "$n $one" else "$n $many"
}
