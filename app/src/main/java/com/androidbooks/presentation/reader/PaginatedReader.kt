package com.androidbooks.presentation.reader

import android.view.ViewGroup
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.viewpager2.widget.ViewPager2
import com.androidbooks.data.epub.ChapterContent
import com.androidbooks.data.local.datastore.UserPreferences
import java.io.File

/**
 * Paginated reader composable using ViewPager2
 * Provides smooth page turn animations like Apple Books
 */
@Composable
fun PaginatedReader(
    chapters: List<ChapterContent>,
    currentChapterIndex: Int,
    bookDir: File,
    userPreferences: UserPreferences,
    onPageChanged: (Int) -> Unit,
    onChapterLoad: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var viewPager: ViewPager2? by remember { mutableStateOf(null) }
    var adapter: EpubPagerAdapter? by remember { mutableStateOf(null) }

    // Update preferences when changed
    LaunchedEffect(userPreferences) {
        adapter?.updatePreferences(userPreferences)
    }

    // Update current page when index changes
    LaunchedEffect(currentChapterIndex) {
        viewPager?.setCurrentItem(currentChapterIndex, true)
    }

    // Update chapters when list changes
    LaunchedEffect(chapters.size) {
        adapter?.updateChapters(chapters)
    }

    Box(modifier = modifier) {
        if (chapters.isEmpty()) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            AndroidView(
                factory = { ctx ->
                    ViewPager2(ctx).apply {
                        viewPager = this
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )

                        // Configure ViewPager2 - reduce offscreen limit to 1
                        orientation = ViewPager2.ORIENTATION_HORIZONTAL
                        offscreenPageLimit = 1 // Reduced from 2 to avoid creating too many WebViews

                        // Create and set adapter
                        adapter = EpubPagerAdapter(
                            bookDir = bookDir,
                            userPreferences = userPreferences,
                            onChapterLoad = onChapterLoad
                        ).also {
                            it.updateChapters(chapters)
                            this.adapter = it
                        }

                        // Page change callback
                        registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                            override fun onPageSelected(position: Int) {
                                super.onPageSelected(position)
                                onPageChanged(position)
                            }
                        })

                        // Set initial position without animation
                        post {
                            setCurrentItem(currentChapterIndex, false)
                        }
                    }
                },
                update = { pager ->
                    // Update when chapters change
                    if (chapters.isNotEmpty()) {
                        (pager.adapter as? EpubPagerAdapter)?.updateChapters(chapters)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // Cleanup
            adapter = null
            viewPager = null
        }
    }
}
