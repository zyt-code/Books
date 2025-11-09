package com.androidbooks.presentation.reader

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.androidbooks.data.epub.*
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

    // Cache manager and preloader
    private var cacheManager: EpubCacheManager? = null
    private var preloader: ChapterPreloader? = null
    private var lastPageTurnDirection = PageTurnDirection.FORWARD

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

                // Initialize cache manager and preloader
                cacheManager = EpubCacheManager(context, bookId)
                preloader = ChapterPreloader(
                    cacheManager = cacheManager!!,
                    scope = viewModelScope
                )

                // Parse EPUB to get spine and TOC
                val parseResult = epubParser.parseEpub(epubFile, bookDir)

                parseResult.fold(
                    onSuccess = { epubBook ->
                        // Initialize preloader with chapter count
                        preloader?.initialize(epubBook.spineItems.size)

                        // Load chapters with cache
                        val chapterContents = loadChaptersWithCache(
                            startIndex = bookEntity.progressSpineIndex,
                            totalChapters = epubBook.spineItems.size
                        )

                        _uiState.update {
                            it.copy(
                                bookTitle = bookEntity.title,
                                currentSpineIndex = bookEntity.progressSpineIndex,
                                spineItems = epubBook.spineItems,
                                chapterContents = chapterContents,
                                toc = epubBook.toc,
                                isLoading = false,
                                error = null
                            )
                        }

                        // Start preloading adjacent chapters
                        preloader?.onPageTurn(
                            bookEntity.progressSpineIndex,
                            PageTurnDirection.JUMP
                        )
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

    /**
     * Load chapters with cache manager
     */
    private suspend fun loadChaptersWithCache(
        startIndex: Int,
        totalChapters: Int
    ): List<ChapterContent> {
        val contents = mutableListOf<ChapterContent>()

        // Load current and adjacent chapters first
        val indicesToLoad = listOf(
            startIndex - 1,
            startIndex,
            startIndex + 1
        ).filter { it in 0 until totalChapters }

        indicesToLoad.forEach { index ->
            cacheManager?.getChapterContent(index)?.getOrNull()?.let {
                contents.add(it)
            }
        }

        // Load remaining chapters lazily
        for (i in 0 until totalChapters) {
            if (i !in indicesToLoad) {
                cacheManager?.getChapterContent(i)?.getOrNull()?.let {
                    contents.add(it)
                }
            }
        }

        return contents.sortedBy { it.index }
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
                val oldIndex = _uiState.value.currentSpineIndex
                _uiState.update { it.copy(currentSpineIndex = event.index) }

                // Notify preloader of jump
                preloader?.onPageTurn(event.index, PageTurnDirection.JUMP)
                scheduleProgressUpdate(event.index, 0f)
            }
            ReaderEvent.NextChapter -> {
                val currentIndex = _uiState.value.currentSpineIndex
                val maxIndex = _uiState.value.spineItems.size - 1
                if (currentIndex < maxIndex) {
                    _uiState.update { it.copy(currentSpineIndex = currentIndex + 1) }
                    preloader?.onPageTurn(currentIndex + 1, PageTurnDirection.FORWARD)
                    scheduleProgressUpdate(currentIndex + 1, 0f)
                }
            }
            ReaderEvent.PreviousChapter -> {
                val currentIndex = _uiState.value.currentSpineIndex
                if (currentIndex > 0) {
                    _uiState.update { it.copy(currentSpineIndex = currentIndex - 1) }
                    preloader?.onPageTurn(currentIndex - 1, PageTurnDirection.BACKWARD)
                    scheduleProgressUpdate(currentIndex - 1, 0f)
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
            is ReaderEvent.OnPageChanged -> {
                // Determine direction
                val direction = when {
                    event.newIndex > _uiState.value.currentSpineIndex -> PageTurnDirection.FORWARD
                    event.newIndex < _uiState.value.currentSpineIndex -> PageTurnDirection.BACKWARD
                    else -> PageTurnDirection.JUMP
                }

                _uiState.update { it.copy(currentSpineIndex = event.newIndex) }
                preloader?.onPageTurn(event.newIndex, direction)
                scheduleProgressUpdate(event.newIndex, 0f)
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
        preloader?.cancel()
        cacheManager?.clearMemoryCache()
    }
}
