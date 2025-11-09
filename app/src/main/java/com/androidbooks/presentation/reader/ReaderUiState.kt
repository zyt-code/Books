package com.androidbooks.presentation.reader

import com.androidbooks.data.epub.SpineItem
import com.androidbooks.data.epub.TocEntry
import com.androidbooks.data.local.datastore.UserPreferences

data class ReaderUiState(
    val bookId: String = "",
    val bookTitle: String = "",
    val currentSpineIndex: Int = 0,
    val spineItems: List<SpineItem> = emptyList(),
    val toc: List<TocEntry> = emptyList(),
    val userPreferences: UserPreferences = UserPreferences.getDefaultInstance(),
    val isMenuVisible: Boolean = false,
    val isLoading: Boolean = true,
    val showToc: Boolean = false,
    val showSettings: Boolean = false,
    val error: String? = null
)

sealed class ReaderEvent {
    data object ToggleMenu : ReaderEvent()
    data object ShowToc : ReaderEvent()
    data object HideToc : ReaderEvent()
    data object ShowSettings : ReaderEvent()
    data object HideSettings : ReaderEvent()
    data class NavigateToSpine(val index: Int) : ReaderEvent()
    data object NextChapter : ReaderEvent()
    data object PreviousChapter : ReaderEvent()
    data class UpdateProgress(val spineIndex: Int, val offset: Float) : ReaderEvent()
    data class UpdateFontSize(val size: Float) : ReaderEvent()
    data class UpdateLineHeight(val height: Float) : ReaderEvent()
    data class UpdateThemeMode(val mode: UserPreferences.ThemeMode) : ReaderEvent()
}
