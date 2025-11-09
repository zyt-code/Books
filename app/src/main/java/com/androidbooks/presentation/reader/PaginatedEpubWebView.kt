package com.androidbooks.presentation.reader

import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import com.androidbooks.data.local.datastore.UserPreferences

/**
 * Optimized WebView for EPUB rendering with pagination
 * Based on Apple Books rendering strategy
 */
class PaginatedEpubWebView(context: Context) : WebView(context) {

    companion object {
        private const val TAG = "PaginatedEpubWebView"
    }

    init {
        configureSettings()
        webViewClient = EpubWebViewClient()
    }

    private fun configureSettings() {
        settings.apply {
            // Disable JavaScript for security and performance
            javaScriptEnabled = true // Need for pagination calculation
            domStorageEnabled = false
            databaseEnabled = false

            // Disable zoom
            builtInZoomControls = false
            displayZoomControls = false
            setSupportZoom(false)

            // Rendering optimization
            loadWithOverviewMode = false
            useWideViewPort = false
            allowFileAccess = true
            allowContentAccess = true

            // Text rendering
            textZoom = 100
            minimumFontSize = 1

            // Performance
            setRenderPriority(RenderPriority.HIGH)
            cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE // Use our cache system
        }

        // Enable hardware acceleration
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    /**
     * Load chapter with custom styles and pagination
     */
    fun loadChapterWithPagination(
        htmlContent: String,
        baseUrl: String,
        userPreferences: UserPreferences
    ) {
        val styledHtml = injectPaginationStyles(htmlContent, userPreferences)
        loadDataWithBaseURL(
            baseUrl,
            styledHtml,
            "text/html",
            "UTF-8",
            null
        )
    }

    /**
     * Inject CSS for pagination and custom styling
     */
    private fun injectPaginationStyles(
        htmlContent: String,
        userPreferences: UserPreferences
    ): String {
        val themeColors = when (userPreferences.themeMode) {
            UserPreferences.ThemeMode.NIGHT -> Pair("#1C1B1F", "#E6E1E5")
            UserPreferences.ThemeMode.SEPIA -> Pair("#F4ECD8", "#3E2723")
            else -> Pair("#FFFBFE", "#1C1B1F")
        }

        // CSS inspired by Apple Books pagination
        val paginationCss = """
            <style type="text/css">
                /* Reset default styles */
                * {
                    -webkit-column-break-inside: avoid;
                    column-break-inside: avoid;
                    break-inside: avoid;
                }

                html, body {
                    margin: 0;
                    padding: 0;
                    width: 100%;
                    height: 100%;
                    overflow: hidden;
                    font-family: ${userPreferences.fontFamily};
                    background-color: ${themeColors.first} !important;
                    color: ${themeColors.second} !important;
                }

                body {
                    /* Column-based pagination */
                    -webkit-column-width: 100vw;
                    -moz-column-width: 100vw;
                    column-width: 100vw;

                    -webkit-column-gap: 0;
                    -moz-column-gap: 0;
                    column-gap: 0;

                    -webkit-column-fill: auto;
                    -moz-column-fill: auto;
                    column-fill: auto;

                    /* Typography */
                    font-size: ${userPreferences.fontSize}rem;
                    line-height: ${userPreferences.lineHeight};

                    /* Padding for reading comfort */
                    padding: 20px;
                    box-sizing: border-box;
                }

                /* Prevent breaks in unwanted places */
                p {
                    margin: 0 0 1em 0;
                    orphans: 3;
                    widows: 3;
                }

                h1, h2, h3, h4, h5, h6 {
                    -webkit-column-break-after: avoid;
                    break-after: avoid;
                    page-break-after: avoid;
                }

                /* Image optimization */
                img {
                    max-width: 100%;
                    height: auto;
                    display: block;
                    margin: 0.5em auto;
                }

                /* Links */
                a {
                    color: ${themeColors.second};
                    text-decoration: underline;
                }

                /* Code blocks */
                pre, code {
                    font-family: 'Courier New', monospace;
                    font-size: 0.9em;
                    white-space: pre-wrap;
                }

                /* Lists */
                ul, ol {
                    padding-left: 1.5em;
                }

                /* Tables */
                table {
                    max-width: 100%;
                    border-collapse: collapse;
                }
            </style>
        """.trimIndent()

        return if (htmlContent.contains("</head>", ignoreCase = true)) {
            htmlContent.replace("</head>", "$paginationCss</head>", ignoreCase = true)
        } else if (htmlContent.contains("<body", ignoreCase = true)) {
            htmlContent.replace("<body", "$paginationCss<body", ignoreCase = true)
        } else {
            """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                $paginationCss
            </head>
            <body>
                $htmlContent
            </body>
            </html>
            """.trimIndent()
        }
    }

    /**
     * Custom WebViewClient for performance optimization
     */
    private inner class EpubWebViewClient : WebViewClient() {
        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            // Disable overscroll glow effect
            view?.overScrollMode = OVER_SCROLL_NEVER
        }

        override fun shouldOverrideUrlLoading(
            view: WebView?,
            url: String?
        ): Boolean {
            // Block external navigation
            return true
        }
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        stopLoading()
        clearCache(true)
        clearHistory()
        removeAllViews()
        destroy()
    }
}
