package com.androidbooks.presentation.reader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.androidbooks.R
import com.androidbooks.data.local.datastore.UserPreferences
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    bookId: String,
    onNavigateBack: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(bookId) {
        viewModel.loadBook(bookId, context)
    }

    BackHandler {
        if (uiState.showToc || uiState.showSettings) {
            if (uiState.showToc) viewModel.onEvent(ReaderEvent.HideToc)
            if (uiState.showSettings) viewModel.onEvent(ReaderEvent.HideSettings)
        } else {
            onNavigateBack()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (uiState.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else {
            val currentSpineItem = uiState.spineItems.getOrNull(uiState.currentSpineIndex)
            val bookDir = File(context.filesDir, "books/$bookId")

            EpubWebView(
                spineItem = currentSpineItem,
                bookDir = bookDir,
                userPreferences = uiState.userPreferences,
                currentSpineIndex = uiState.currentSpineIndex,
                onProgressUpdate = { offset ->
                    viewModel.onEvent(
                        ReaderEvent.UpdateProgress(uiState.currentSpineIndex, offset)
                    )
                },
                modifier = Modifier.fillMaxSize()
            )

            // Top menu bar
            if (uiState.isMenuVisible) {
                TopBar(
                    title = uiState.bookTitle,
                    onBackClick = onNavigateBack,
                    onTocClick = { viewModel.onEvent(ReaderEvent.ShowToc) },
                    onSettingsClick = { viewModel.onEvent(ReaderEvent.ShowSettings) },
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }

            // Bottom navigation bar
            if (uiState.isMenuVisible) {
                BottomNavigationBar(
                    currentIndex = uiState.currentSpineIndex,
                    totalItems = uiState.spineItems.size,
                    onPreviousClick = { viewModel.onEvent(ReaderEvent.PreviousChapter) },
                    onNextClick = { viewModel.onEvent(ReaderEvent.NextChapter) },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }

            // Click detector for menu toggle
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    ) {
                        viewModel.onEvent(ReaderEvent.ToggleMenu)
                    }
            )
        }

        // TOC Drawer
        if (uiState.showToc) {
            TocDrawer(
                toc = uiState.toc,
                onItemClick = { index ->
                    viewModel.onEvent(ReaderEvent.NavigateToSpine(index))
                    viewModel.onEvent(ReaderEvent.HideToc)
                },
                onDismiss = { viewModel.onEvent(ReaderEvent.HideToc) }
            )
        }

        // Settings Panel
        if (uiState.showSettings) {
            SettingsPanel(
                userPreferences = uiState.userPreferences,
                onFontSizeChange = { viewModel.onEvent(ReaderEvent.UpdateFontSize(it)) },
                onLineHeightChange = { viewModel.onEvent(ReaderEvent.UpdateLineHeight(it)) },
                onThemeModeChange = { viewModel.onEvent(ReaderEvent.UpdateThemeMode(it)) },
                onDismiss = { viewModel.onEvent(ReaderEvent.HideSettings) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar(
    title: String,
    onBackClick: () -> Unit,
    onTocClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = { Text(title, maxLines = 1) },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        actions = {
            IconButton(onClick = onTocClick) {
                Icon(Icons.AutoMirrored.Filled.List, contentDescription = stringResource(R.string.table_of_contents))
            }
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
            }
        },
        modifier = modifier
    )
}

@Composable
fun BottomNavigationBar(
    currentIndex: Int,
    totalItems: Int,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onPreviousClick,
                enabled = currentIndex > 0
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous")
            }

            Text(
                text = "${currentIndex + 1} / $totalItems",
                style = MaterialTheme.typography.bodyMedium
            )

            IconButton(
                onClick = onNextClick,
                enabled = currentIndex < totalItems - 1
            ) {
                Icon(Icons.Default.ArrowForward, contentDescription = "Next")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TocDrawer(
    toc: List<com.androidbooks.data.epub.TocEntry>,
    onItemClick: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    ModalDrawerSheet {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(stringResource(R.string.table_of_contents)) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
            )

            LazyColumn {
                items(toc.size) { index ->
                    ListItem(
                        headlineContent = { Text(toc[index].title) },
                        modifier = Modifier.clickable { onItemClick(index) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPanel(
    userPreferences: UserPreferences,
    onFontSizeChange: (Float) -> Unit,
    onLineHeightChange: (Float) -> Unit,
    onThemeModeChange: (UserPreferences.ThemeMode) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.settings),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Font size slider
            Text(stringResource(R.string.font_size))
            Slider(
                value = userPreferences.fontSize,
                onValueChange = onFontSizeChange,
                valueRange = 0.8f..2.0f,
                steps = 11
            )
            Text("${(userPreferences.fontSize * 100).toInt()}%")

            Spacer(modifier = Modifier.height(16.dp))

            // Line height slider
            Text(stringResource(R.string.line_height))
            Slider(
                value = userPreferences.lineHeight,
                onValueChange = onLineHeightChange,
                valueRange = 1.0f..2.5f,
                steps = 14
            )
            Text("${userPreferences.lineHeight}")

            Spacer(modifier = Modifier.height(16.dp))

            // Theme selection
            Text(stringResource(R.string.theme))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                FilterChip(
                    selected = userPreferences.themeMode == UserPreferences.ThemeMode.DAY,
                    onClick = { onThemeModeChange(UserPreferences.ThemeMode.DAY) },
                    label = { Text(stringResource(R.string.theme_day)) }
                )
                FilterChip(
                    selected = userPreferences.themeMode == UserPreferences.ThemeMode.NIGHT,
                    onClick = { onThemeModeChange(UserPreferences.ThemeMode.NIGHT) },
                    label = { Text(stringResource(R.string.theme_night)) }
                )
                FilterChip(
                    selected = userPreferences.themeMode == UserPreferences.ThemeMode.SEPIA,
                    onClick = { onThemeModeChange(UserPreferences.ThemeMode.SEPIA) },
                    label = { Text(stringResource(R.string.theme_sepia)) }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
