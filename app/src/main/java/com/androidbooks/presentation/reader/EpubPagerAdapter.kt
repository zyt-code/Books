package com.androidbooks.presentation.reader

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.androidbooks.data.epub.ChapterContent
import com.androidbooks.data.local.datastore.UserPreferences
import kotlinx.coroutines.*
import java.io.File

/**
 * ViewPager2 adapter for smooth chapter navigation
 * Based on Apple Books pagination strategy
 */
class EpubPagerAdapter(
    private val bookDir: File,
    private var userPreferences: UserPreferences,
    private val onChapterLoad: (Int) -> Unit = {}
) : RecyclerView.Adapter<EpubPagerAdapter.ChapterViewHolder>() {

    private var chapters: List<ChapterContent> = emptyList()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChapterViewHolder {
        val webView = PaginatedEpubWebView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        return ChapterViewHolder(webView)
    }

    override fun onBindViewHolder(holder: ChapterViewHolder, position: Int) {
        val chapter = chapters.getOrNull(position) ?: return

        // Delay loading slightly to avoid blocking main thread
        scope.launch {
            delay(50) // Small delay to let RecyclerView settle
            if (holder.bindingAdapterPosition == position) {
                holder.webView.loadChapterWithPagination(
                    htmlContent = chapter.html,
                    baseUrl = "file://${bookDir.absolutePath}/content/",
                    userPreferences = userPreferences
                )
                onChapterLoad(position)
            }
        }
    }

    override fun getItemCount(): Int = chapters.size

    override fun onViewRecycled(holder: ChapterViewHolder) {
        super.onViewRecycled(holder)
        // Clean up WebView content but don't destroy it
        holder.webView.cleanup()
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        scope.cancel() // Cancel all pending loads
    }

    /**
     * Update chapters list
     */
    fun updateChapters(newChapters: List<ChapterContent>) {
        chapters = newChapters
        notifyDataSetChanged()
    }

    /**
     * Update user preferences and refresh current page
     */
    fun updatePreferences(prefs: UserPreferences) {
        userPreferences = prefs
        notifyDataSetChanged()
    }

    /**
     * ViewHolder for chapter pages
     */
    class ChapterViewHolder(val webView: PaginatedEpubWebView) :
        RecyclerView.ViewHolder(webView)
}
