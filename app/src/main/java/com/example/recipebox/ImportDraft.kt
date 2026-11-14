package com.example.recipebox

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.recipebox.data.Platform

/**
 * Everything on the import screen. It lives in the ViewModel, not in the screen,
 * so a configuration change (theme, font size, language) neither loses what you typed
 * nor fetches the page again.
 */
class ImportDraft(
    val sharedText: String,
    val url: String?,
    val platform: Platform?
) {
    var title by mutableStateOf("")
    var body by mutableStateOf("")
    var image by mutableStateOf<String?>(null)
    /** An image the user chose from their phone. It replaces the one found on the page. */
    var pickedImage by mutableStateOf<Uri?>(null)
    var note by mutableStateOf<String?>(null)
    var loading by mutableStateOf(url != null)
    /** 0f..1f, one step per source the extractor tries. */
    var progress by mutableStateOf(0f)
    var saving by mutableStateOf(false)
    var selected by mutableStateOf(emptySet<Long>())

    /** Set when this link is already saved, so the app can open that recipe instead. */
    var duplicateId by mutableStateOf<Long?>(null)
}
