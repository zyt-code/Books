package com.androidbooks.presentation.bookshelf

import com.androidbooks.domain.model.Book
import com.androidbooks.domain.repository.SortOption

data class BookshelfUiState(
    val books: List<Book> = emptyList(),
    val isLoading: Boolean = false,
    val sortOption: SortOption = SortOption.LAST_READ,
    val showSortDialog: Boolean = false,
    val error: String? = null
)

sealed class BookshelfEvent {
    data class DeleteBook(val bookId: String) : BookshelfEvent()
    data class OpenBook(val bookId: String) : BookshelfEvent()
    data object ImportBook : BookshelfEvent()
    data class ChangeSortOption(val sortOption: SortOption) : BookshelfEvent()
    data object ShowSortDialog : BookshelfEvent()
    data object HideSortDialog : BookshelfEvent()
}
