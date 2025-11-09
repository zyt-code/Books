# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is an Android EPUB reader app inspired by Apple Books, built with modern Android development practices. The project uses **Jetpack Compose** for UI, **Kotlin Coroutines** for async operations, and a **custom EPUB parsing and rendering engine** based on WebView with JavaScript bridge communication.

**Target Platform:** Android 8.0+ (API 26), optimized for Android 10+ (API 29+)

## Architecture

The project follows **Unidirectional Data Flow (UDF)** architecture with clean separation:

```
UI Layer (Jetpack Compose + Material 3)
    ↓
ViewModel Layer (StateFlow<UIState> + UseCases)
    ↓
Domain Layer (Pure Kotlin business logic)
    ↓
Data Layer (Room + DataStore + File System)
```

All state changes flow unidirectionally from user interactions through ViewModels to immutable UIState objects consumed by Compose.

## Core Technology Stack

- **Language:** Kotlin 2.0+
- **UI:** Jetpack Compose 1.7+ (no XML layouts, fully Compose-first)
- **Theme:** Material 3 with Dynamic Color extraction from book covers
- **State Management:** StateFlow + `collectAsStateWithLifecycle()`
- **Dependency Injection:** Hilt 2.50+
- **Database:** Room 2.6+ (book metadata, bookmarks, highlights)
- **Preferences:** DataStore Proto (user settings)
- **Image Loading:** Coil 2.6+ for Compose
- **Storage:** Storage Access Framework (SAF) + DocumentFile for scoped storage
- **Navigation:** Compose Navigation (type-safe, no Fragments)
- **EPUB Parsing:** Custom parser using Kotlin stdlib + XML Pull Parser
- **EPUB Rendering:** Custom WebView with JavaScript Bridge + dynamic CSS injection

## Key Domain Models

### BookEntity (Room)
```kotlin
@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String,
    val filePath: String,       // Path to EPUB file in app private directory
    val coverPath: String?,     // Path to extracted cover image
    val lastReadAt: Long,
    val progressSpineIndex: Int,  // Current chapter/spine position
    val progressOffset: Float     // Scroll percentage within chapter
)
```

### UserPreferences (Proto DataStore)
```protobuf
message UserPreferences {
    string font_family = 1;
    float font_size = 2;
    float line_height = 3;
    ThemeMode theme_mode = 4;
    enum ThemeMode { DAY = 0; NIGHT = 1; SEPIA = 2; }
}
```

## Critical Implementation Details

### EPUB File Handling

1. **Import Process:**
   - User selects EPUB via SAF (Storage Access Framework)
   - File is copied to app's private directory: `/files/books/{uuid}/book.epub`
   - This avoids scoped storage permission issues on Android 10+
   - Metadata is extracted and stored in Room

2. **EPUB Structure Parsing:**
   - EPUB is a ZIP archive
   - Parse `META-INF/container.xml` → get `content.opf` location
   - From `content.opf` extract:
     - `<manifest>`: All HTML/resource file paths (spine items)
     - `<metadata>`: Title, author, language
     - `<guide>` / `<nav>`: Table of contents (NCX or XHTML Nav)
   - Cover image: Extract from `<meta name="cover">` or `<item properties="cover-image">`

### WebView Rendering Engine

**Critical WebView Configuration:**
```kotlin
webView.settings.apply {
    javaScriptEnabled = true
    domStorageEnabled = true
    builtInZoomControls = false
    displayZoomControls = false
    loadWithOverviewMode = true
    useWideViewPort = true
}
```

**Security Constraints:**
- Only load content from `content://` URIs or app private directory
- Do NOT enable `setAllowFileAccessFromFileURLs`
- Consider filtering `<script>` tags from EPUB content for security

**Dynamic CSS Injection:**
User preferences (font, size, line height, theme colors) are injected via JavaScript:
```kotlin
val css = """
    body {
        font-family: '${userPrefs.fontFamily}';
        font-size: ${userPrefs.fontSize}rem;
        line-height: ${userPrefs.lineHeight};
        background-color: ${theme.bgColor};
        color: ${theme.textColor};
        margin: 0 auto;
        max-width: 800px;
        padding: 20px;
    }
""".trimIndent()
webView.evaluateJavascript("var style = document.createElement('style'); style.innerHTML = `$css`; document.head.appendChild(style);", null)
```

### Kotlin ↔ JavaScript Bridge

**Kotlin → JavaScript:**
```kotlin
webView.evaluateJavascript(jsCode, callback)
```

**JavaScript → Kotlin:**
```kotlin
class WebAppInterface(private val onProgressUpdate: (Float) -> Unit) {
    @JavascriptInterface
    fun onScroll(percent: Float) {
        Handler(Looper.getMainLooper()).post { onProgressUpdate(percent) }
    }

    @JavascriptInterface
    fun onSelection(text: String, start: Int, end: Int) {
        // Trigger highlight menu
    }
}

webView.addJavascriptInterface(WebAppInterface { offset ->
    viewModel.updateReadingProgress(offset)
}, "Android")
```

### Page Navigation

**Gesture-based:**
- Use `Modifier.pointerInput` to detect swipe gestures
- Left swipe → next chapter
- Right swipe → previous chapter

**Tap-based:**
- Left 20% of screen → previous page
- Right 20% of screen → next page
- Center 60% → toggle menu/controls

### Reading Progress Persistence

- Progress saved every 2 seconds or on chapter change
- JavaScript queries current scroll position: `window.scrollY / document.body.scrollHeight`
- Stored as `(spineIndex, scrollPercent)` tuple in Room
- On book reopen, WebView loads correct spine item and scrolls to saved position

## UI Components Structure

### Bookshelf Screen
- `LazyVerticalGrid(columns = GridCells.Adaptive(150.dp))` for book covers
- 3:4 aspect ratio book cards
- Coil loads covers from: `/files/books/{bookId}/cover.jpg`
- Sort options: Title, Author, Last Read
- Long press → context menu (delete, rename, view details)

### Reader Screen
- Full-screen immersive mode (hide status/nav bars)
- `AndroidView` wrapper around custom WebView
- Overlay controls: top (title, menu) / bottom (progress slider, page number)
- Settings panel: font, size, spacing, theme
- TOC drawer: Navigate to chapters from parsed `<nav>` structure

## File Organization Conventions

When implementing this project, follow this structure:

```
app/src/main/java/com/yourpackage/
├── data/
│   ├── local/
│   │   ├── dao/           # Room DAOs
│   │   ├── entity/        # Room entities
│   │   └── datastore/     # Proto DataStore serializers
│   ├── repository/        # Repository implementations
│   └── epub/              # EPUB parser & file handling
├── domain/
│   ├── model/             # Domain models (not tied to Room/DB)
│   ├── repository/        # Repository interfaces
│   └── usecase/           # Business logic use cases
├── presentation/
│   ├── bookshelf/         # Bookshelf screen + ViewModel
│   ├── reader/            # Reader screen + ViewModel
│   ├── components/        # Shared Compose components
│   └── theme/             # Material 3 theme definitions
└── di/                    # Hilt modules
```

## Common Development Patterns

### Loading EPUB Content
1. Use `withContext(Dispatchers.IO)` for file I/O operations
2. Parse EPUB structure once during import, cache in database
3. Extract spine items to cache directory for faster WebView loading
4. Handle malformed EPUB gracefully (missing TOC, invalid XML, etc.)

### State Management in Reader
```kotlin
data class ReaderUiState(
    val book: BookEntity,
    val currentSpineIndex: Int,
    val spineItems: List<SpineItem>,
    val toc: List<TocEntry>,
    val userPrefs: UserPreferences,
    val isMenuVisible: Boolean,
    val isLoading: Boolean
)
```

### WebView Lifecycle
- Create WebView in `AndroidView { factory = { WebView(context) } }`
- Cleanup: `DisposableEffect` with `onDispose { webView.destroy() }`
- Avoid memory leaks: clear JavaScript interfaces on dispose

## EPUB Format Compatibility

- Support EPUB 2.0 and 3.0 standards
- Handle both NCX (EPUB 2) and XHTML Nav (EPUB 3) for TOC
- Fallback strategies for missing metadata:
  - No cover → use placeholder
  - No author → "Unknown Author"
  - No TOC → generate from spine items
- Test with both fixed-layout and reflowable EPUBs (prioritize reflowable)

## Performance Considerations

- **Baseline Profiles:** Generate for faster app startup
- **R8 Full Mode:** Enable in release builds for code shrinking
- **Preload Content:** Cache next chapter while reading current
- **Image Optimization:** Use Coil's memory cache, limit bitmap sizes
- **Room Query Optimization:** Index frequently queried columns (lastReadAt)

## Android Version Compatibility

- **API 26-28:** Use legacy file access, test SAF thoroughly
- **API 29+:** Fully adopt Scoped Storage, no WRITE_EXTERNAL_STORAGE needed
- **API 30+:** Ensure MANAGE_EXTERNAL_STORAGE not used (violates Play Store policy)

## Testing Strategy

- **Unit Tests:** Domain layer (UseCases) with Kotlin test + MockK
- **Integration Tests:** Repository layer with Room in-memory database
- **UI Tests:** Compose tests for Bookshelf/Reader screens
- **EPUB Parser Tests:** Test against real-world EPUB samples (include edge cases)

## Dependencies Reference

```kotlin
dependencies {
    // Compose
    implementation("androidx.compose.ui:ui:1.7.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.0")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.0")

    // DI
    implementation("com.google.dagger:hilt-android:2.50")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    ksp("com.google.dagger:hilt-compiler:2.50")

    // Database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Image Loading
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Core
    implementation("androidx.core:core-ktx:1.13.1")
}
```

## Code Style Conventions

- Use Kotlin coroutines, not RxJava or callbacks
- Prefer sealed classes for UI states and events
- Use `StateFlow` for observable state, `Flow` for streams
- Follow Material 3 design guidelines strictly
- All business logic in UseCases, keep ViewModels thin
- Compose functions: PascalCase, end with "Screen" or "Component"
- ViewModels: End with "ViewModel"
- Repositories: End with "Repository"
