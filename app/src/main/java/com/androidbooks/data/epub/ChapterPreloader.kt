package com.androidbooks.data.epub

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Intelligent chapter preloader with predictive loading
 * Based on Apple Books preloading strategy
 */
class ChapterPreloader(
    private val cacheManager: EpubCacheManager,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "ChapterPreloader"
        private const val PRELOAD_RANGE = 2 // Preload ±2 chapters
        private const val PREDICTION_THRESHOLD = 3 // Consecutive page turns to trigger prediction
    }

    private var currentIndex = 0
    private var totalChapters = 0
    private var consecutiveForwardTurns = 0
    private var consecutiveBackwardTurns = 0
    private var preloadJob: Job? = null

    private val _isPreloading = MutableStateFlow(false)
    val isPreloading: StateFlow<Boolean> = _isPreloading.asStateFlow()

    /**
     * Initialize preloader with chapter count
     */
    fun initialize(total: Int) {
        totalChapters = total
        Log.d(TAG, "Initialized with $total chapters")
    }

    /**
     * Notify page turn and trigger predictive loading
     */
    fun onPageTurn(newIndex: Int, direction: PageTurnDirection) {
        val oldIndex = currentIndex
        currentIndex = newIndex

        // Track consecutive turns for prediction
        when (direction) {
            PageTurnDirection.FORWARD -> {
                consecutiveForwardTurns++
                consecutiveBackwardTurns = 0
            }
            PageTurnDirection.BACKWARD -> {
                consecutiveBackwardTurns++
                consecutiveForwardTurns = 0
            }
            PageTurnDirection.JUMP -> {
                consecutiveForwardTurns = 0
                consecutiveBackwardTurns = 0
            }
        }

        // Trigger preload
        preloadAroundCurrentPage()

        // Predictive loading if user is reading forward consistently
        if (consecutiveForwardTurns >= PREDICTION_THRESHOLD) {
            Log.d(TAG, "User reading forward, expand preload range")
            preloadExtended(forward = true)
        } else if (consecutiveBackwardTurns >= PREDICTION_THRESHOLD) {
            Log.d(TAG, "User reading backward, expand preload range")
            preloadExtended(forward = false)
        }
    }

    /**
     * Standard preload: current ± PRELOAD_RANGE
     */
    private fun preloadAroundCurrentPage() {
        preloadJob?.cancel()
        preloadJob = scope.launch(Dispatchers.IO) {
            _isPreloading.value = true
            try {
                cacheManager.preloadChapters(
                    currentIndex = currentIndex,
                    totalChapters = totalChapters,
                    range = PRELOAD_RANGE
                )
            } catch (e: Exception) {
                Log.e(TAG, "Preload error", e)
            } finally {
                _isPreloading.value = false
            }
        }
    }

    /**
     * Extended preload for predictive reading
     */
    private fun preloadExtended(forward: Boolean) {
        scope.launch(Dispatchers.IO) {
            val extendedRange = PRELOAD_RANGE + 2
            val startIndex = if (forward) {
                currentIndex + PRELOAD_RANGE + 1
            } else {
                (currentIndex - extendedRange).coerceAtLeast(0)
            }

            val endIndex = if (forward) {
                (currentIndex + extendedRange).coerceAtMost(totalChapters - 1)
            } else {
                currentIndex - PRELOAD_RANGE - 1
            }

            for (i in startIndex..endIndex) {
                if (!isActive) break
                cacheManager.getChapterContent(i)
                delay(50) // Throttle to avoid overwhelming I/O
            }

            Log.d(TAG, "Extended preload complete: $startIndex to $endIndex")
        }
    }

    /**
     * Force preload specific chapter
     */
    suspend fun preloadChapter(index: Int) {
        withContext(Dispatchers.IO) {
            cacheManager.getChapterContent(index)
        }
    }

    /**
     * Cancel ongoing preload operations
     */
    fun cancel() {
        preloadJob?.cancel()
        _isPreloading.value = false
    }

    /**
     * Reset prediction state
     */
    fun resetPrediction() {
        consecutiveForwardTurns = 0
        consecutiveBackwardTurns = 0
    }
}

/**
 * Page turn direction for predictive loading
 */
enum class PageTurnDirection {
    FORWARD,    // User turning to next page
    BACKWARD,   // User turning to previous page
    JUMP        // User jumped to specific chapter (e.g., from TOC)
}
