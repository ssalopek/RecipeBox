package com.example.recipebox

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.recipebox.data.Platform

/**
 * Everything on the import screen. It lives in the ViewModel, not in the screen,
 * so rotating the phone neither loses what you typed nor fetches the page again.
 */
class ImportDraft(
    val sharedText: String,
    val url: String?,
    val platform: Platform?
) {
    var title by mutableStateOf("")
    var body by mutableStateOf("")
    var image by mutableStateOf<String?>(null)
    var note by mutableStateOf<String?>(null)
    var loading by mutableStateOf(url != null)
    var saving by mutableStateOf(false)
    var selected by mutableStateOf(emptySet<Long>())

    /** Set when this link is already saved, so the app can open that recipe instead. */
    var duplicateId by mutableStateOf<Long?>(null)
}
