package com.androidbooks.presentation.bookshelf

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.androidbooks.domain.repository.SortOption
import com.androidbooks.domain.usecase.DeleteBookUseCase
import com.androidbooks.domain.usecase.GetAllBooksUseCase
import com.androidbooks.domain.usecase.ImportEpubUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class BookshelfViewModel @Inject constructor(
    private val getAllBooksUseCase: GetAllBooksUseCase,
    private val importEpubUseCase: ImportEpubUseCase,
    private val deleteBookUseCase: DeleteBookUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookshelfUiState())
    val uiState: StateFlow<BookshelfUiState> = _uiState.asStateFlow()

    init {
        loadBooks()
    }

    fun onEvent(event: BookshelfEvent) {
        when (event) {
            is BookshelfEvent.DeleteBook -> deleteBook(event.bookId)
            is BookshelfEvent.ChangeSortOption -> changeSortOption(event.sortOption)
            BookshelfEvent.ShowSortDialog -> _uiState.update { it.copy(showSortDialog = true) }
            BookshelfEvent.HideSortDialog -> _uiState.update { it.copy(showSortDialog = false) }
            else -> {}
        }
    }

    private fun loadBooks() {
        viewModelScope.launch {
            getAllBooksUseCase(_uiState.value.sortOption)
                .catch { error ->
                    _uiState.update { it.copy(error = error.message, isLoading = false) }
                }
                .collect { books ->
                    _uiState.update {
                        it.copy(
                            books = books,
                            isLoading = false,
                            error = null
                        )
                    }
                }
        }
    }

    fun importEpub(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                // Copy URI content to temp file
                val tempFile = File(context.cacheDir, "temp_${System.currentTimeMillis()}.epub")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                val result = importEpubUseCase(tempFile)

                tempFile.delete()

                result.fold(
                    onSuccess = {
                        _uiState.update { it.copy(isLoading = false, error = null) }
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = error.message ?: "Failed to import EPUB"
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to import EPUB"
                    )
                }
            }
        }
    }

    private fun deleteBook(bookId: String) {
        viewModelScope.launch {
            deleteBookUseCase(bookId)
        }
    }

    private fun changeSortOption(sortOption: SortOption) {
        _uiState.update { it.copy(sortOption = sortOption, showSortDialog = false) }
        loadBooks()
    }
}
