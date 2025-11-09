package com.androidbooks.presentation.reader

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.androidbooks.data.epub.EpubParser
import com.androidbooks.data.local.datastore.UserPreferences
import com.androidbooks.domain.repository.BookRepository
import com.androidbooks.domain.repository.UserPreferencesRepository
import com.androidbooks.domain.usecase.UpdateReadingProgressUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val updateReadingProgressUseCase: UpdateReadingProgressUseCase,
    private val epubParser: EpubParser
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var progressUpdateJob: Job? = null
    private var lastProgressUpdate = 0L

    init {
        observeUserPreferences()
    }

    private fun observeUserPreferences() {
        viewModelScope.launch {
            userPreferencesRepository.userPreferencesFlow
                .collect { prefs ->
                    _uiState.update { it.copy(userPreferences = prefs) }
                }
        }
    }

    fun loadBook(bookId: String, context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, bookId = bookId) }

            try {
                val bookEntity = bookRepository.getBookEntity(bookId)
                if (bookEntity == null) {
                    _uiState.update { it.copy(error = "Book not found", isLoading = false) }
                    return@launch
                }

                val bookDir = File(context.filesDir, "books/$bookId")
                val epubFile = File(bookEntity.filePath)

                // Parse EPUB to get spine and TOC
                val parseResult = epubParser.parseEpub(epubFile, bookDir)

                parseResult.fold(
                    onSuccess = { epubBook ->
                        _uiState.update {
                            it.copy(
                                bookTitle = bookEntity.title,
                                currentSpineIndex = bookEntity.progressSpineIndex,
                                spineItems = epubBook.spineItems,
                                toc = epubBook.toc,
                                isLoading = false,
                                error = null
                            )
                        }
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                error = error.message ?: "Failed to load book",
                                isLoading = false
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        error = e.message ?: "Failed to load book",
                        isLoading = false
                    )
                }
            }
        }
    }

    fun onEvent(event: ReaderEvent) {
        when (event) {
            ReaderEvent.ToggleMenu -> {
                _uiState.update { it.copy(isMenuVisible = !it.isMenuVisible) }
            }
            ReaderEvent.ShowToc -> {
                _uiState.update { it.copy(showToc = true, isMenuVisible = false) }
            }
            ReaderEvent.HideToc -> {
                _uiState.update { it.copy(showToc = false) }
            }
            ReaderEvent.ShowSettings -> {
                _uiState.update { it.copy(showSettings = true, isMenuVisible = false) }
            }
            ReaderEvent.HideSettings -> {
                _uiState.update { it.copy(showSettings = false) }
            }
            is ReaderEvent.NavigateToSpine -> {
                _uiState.update { it.copy(currentSpineIndex = event.index) }
            }
            ReaderEvent.NextChapter -> {
                val currentIndex = _uiState.value.currentSpineIndex
                val maxIndex = _uiState.value.spineItems.size - 1
                if (currentIndex < maxIndex) {
                    _uiState.update { it.copy(currentSpineIndex = currentIndex + 1) }
                }
            }
            ReaderEvent.PreviousChapter -> {
                val currentIndex = _uiState.value.currentSpineIndex
                if (currentIndex > 0) {
                    _uiState.update { it.copy(currentSpineIndex = currentIndex - 1) }
                }
            }
            is ReaderEvent.UpdateProgress -> {
                scheduleProgressUpdate(event.spineIndex, event.offset)
            }
            is ReaderEvent.UpdateFontSize -> {
                viewModelScope.launch {
                    userPreferencesRepository.updateFontSize(event.size)
                }
            }
            is ReaderEvent.UpdateLineHeight -> {
                viewModelScope.launch {
                    userPreferencesRepository.updateLineHeight(event.height)
                }
            }
            is ReaderEvent.UpdateThemeMode -> {
                viewModelScope.launch {
                    userPreferencesRepository.updateThemeMode(event.mode)
                }
            }
        }
    }

    private fun scheduleProgressUpdate(spineIndex: Int, offset: Float) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastProgressUpdate < 2000) return

        progressUpdateJob?.cancel()
        progressUpdateJob = viewModelScope.launch {
            delay(2000)
            updateReadingProgressUseCase(
                _uiState.value.bookId,
                spineIndex,
                offset
            )
            lastProgressUpdate = currentTime
        }
    }

    override fun onCleared() {
        super.onCleared()
        progressUpdateJob?.cancel()
    }
}
