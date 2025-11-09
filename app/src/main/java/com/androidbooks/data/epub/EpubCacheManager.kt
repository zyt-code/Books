package com.androidbooks.data.epub

import android.content.Context
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Three-level cache system for EPUB content
 * L1: Memory cache (current + adjacent 2 pages)
 * L2: Disk cache (extracted HTML/CSS/images)
 * L3: Original EPUB file
 */
class EpubCacheManager(
    private val context: Context,
    private val bookId: String
) {
    companion object {
        private const val TAG = "EpubCacheManager"
        private const val MEMORY_CACHE_SIZE = 5 // Number of chapters to keep in memory
    }

    // L1: Memory cache - LRU cache for parsed HTML content
    private val memoryCache = object : LruCache<Int, ChapterContent>(MEMORY_CACHE_SIZE) {
        override fun sizeOf(key: Int, value: ChapterContent): Int = 1
    }

    // L2: Disk cache directory
    private val diskCacheDir: File by lazy {
        File(context.filesDir, "books/$bookId/content").apply {
            mkdirs()
        }
    }

    /**
     * Get chapter content with three-level cache strategy
     */
    suspend fun getChapterContent(
        chapterIndex: Int,
        forceReload: Boolean = false
    ): Result<ChapterContent> = withContext(Dispatchers.IO) {
        try {
            // Try L1: Memory cache
            if (!forceReload) {
                memoryCache.get(chapterIndex)?.let {
                    Log.d(TAG, "L1 cache hit: chapter $chapterIndex")
                    return@withContext Result.success(it)
                }
            }

            // Try L2: Disk cache
            val diskFile = File(diskCacheDir, "spine_$chapterIndex.xhtml")
            if (!forceReload && diskFile.exists()) {
                Log.d(TAG, "L2 cache hit: chapter $chapterIndex")
                val content = ChapterContent(
                    index = chapterIndex,
                    html = diskFile.readText(),
                    resources = extractResourcePaths(diskFile.readText())
                )
                // Populate L1 cache
                memoryCache.put(chapterIndex, content)
                return@withContext Result.success(content)
            }

            // L3: Original EPUB - should already be extracted by EpubParser
            Log.w(TAG, "Chapter $chapterIndex not found in cache")
            Result.failure(Exception("Chapter $chapterIndex not found"))
        } catch (e: Exception) {
            Log.e(TAG, "Error loading chapter $chapterIndex", e)
            Result.failure(e)
        }
    }

    /**
     * Preload chapters around current index (predictive loading)
     */
    suspend fun preloadChapters(
        currentIndex: Int,
        totalChapters: Int,
        range: Int = 2
    ) = withContext(Dispatchers.IO) {
        val indicesToPreload = mutableListOf<Int>()

        // Preload previous chapters
        for (i in (currentIndex - range).coerceAtLeast(0) until currentIndex) {
            indicesToPreload.add(i)
        }

        // Preload next chapters
        for (i in (currentIndex + 1)..(currentIndex + range).coerceAtMost(totalChapters - 1)) {
            indicesToPreload.add(i)
        }

        indicesToPreload.forEach { index ->
            if (memoryCache.get(index) == null) {
                getChapterContent(index)
            }
        }

        Log.d(TAG, "Preloaded chapters around $currentIndex: $indicesToPreload")
    }

    /**
     * Extract resource paths (images, CSS) from HTML
     */
    private fun extractResourcePaths(html: String): List<String> {
        val resources = mutableListOf<String>()

        // Extract img src
        Regex("""<img[^>]+src=["']([^"']+)["']""").findAll(html).forEach {
            resources.add(it.groupValues[1])
        }

        // Extract link href (CSS)
        Regex("""<link[^>]+href=["']([^"']+)["']""").findAll(html).forEach {
            resources.add(it.groupValues[1])
        }

        return resources
    }

    /**
     * Clear L1 memory cache
     */
    fun clearMemoryCache() {
        memoryCache.evictAll()
        Log.d(TAG, "Memory cache cleared")
    }

    /**
     * Clear L2 disk cache
     */
    suspend fun clearDiskCache() = withContext(Dispatchers.IO) {
        diskCacheDir.deleteRecursively()
        diskCacheDir.mkdirs()
        Log.d(TAG, "Disk cache cleared")
    }

    /**
     * Get cache statistics
     */
    fun getCacheStats(): CacheStats {
        val diskFiles = diskCacheDir.listFiles()?.size ?: 0
        return CacheStats(
            memorySize = memoryCache.size(),
            diskSize = diskFiles,
            maxMemorySize = MEMORY_CACHE_SIZE
        )
    }
}

/**
 * Represents cached chapter content
 */
data class ChapterContent(
    val index: Int,
    val html: String,
    val resources: List<String> = emptyList()
)

/**
 * Cache statistics
 */
data class CacheStats(
    val memorySize: Int,
    val diskSize: Int,
    val maxMemorySize: Int
)
