package com.androidbooks.presentation.reader

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.androidbooks.data.epub.SpineItem
import com.androidbooks.data.local.datastore.UserPreferences
import java.io.File

@Composable
fun EpubWebView(
    spineItem: SpineItem?,
    bookDir: File,
    userPreferences: UserPreferences,
    onProgressUpdate: (Float) -> Unit,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    currentSpineIndex: Int = 0
) {
    val context = LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            webView?.destroy()
        }
    }

    LaunchedEffect(spineItem, userPreferences, currentSpineIndex) {
        webView?.let { web ->
            if (spineItem != null) {
                loadSpineContent(web, spineItem, bookDir, context, userPreferences, onProgressUpdate, currentSpineIndex)
            }
        }
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                webView = this
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    builtInZoomControls = false
                    displayZoomControls = false
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    allowFileAccess = true
                }

                webViewClient = WebViewClient()

                addJavascriptInterface(
                    WebAppInterface(onProgressUpdate),
                    "Android"
                )
            }
        },
        modifier = modifier
    )
}

private fun loadSpineContent(
    webView: WebView,
    spineItem: SpineItem,
    bookDir: File,
    context: Context,
    userPreferences: UserPreferences,
    onProgressUpdate: (Float) -> Unit,
    currentSpineIndex: Int
) {
    val contentDir = File(bookDir, "content")
    val htmlFile = File(contentDir, "spine_$currentSpineIndex.xhtml")

    if (!htmlFile.exists()) {
        // Try alternative file extensions
        val alternativeFile = File(contentDir, "spine_$currentSpineIndex.html")
        if (alternativeFile.exists()) {
            loadHtmlFile(webView, alternativeFile, contentDir, userPreferences, onProgressUpdate)
            return
        }

        // If file doesn't exist, show error message
        val errorHtml = """
            <html>
            <head><meta charset="UTF-8"></head>
            <body>
                <h2>无法加载章节</h2>
                <p>文件路径: ${htmlFile.absolutePath}</p>
                <p>文件不存在</p>
            </body>
            </html>
        """.trimIndent()
        webView.loadData(errorHtml, "text/html; charset=UTF-8", "UTF-8")
        return
    }

    loadHtmlFile(webView, htmlFile, contentDir, userPreferences, onProgressUpdate)
}

private fun loadHtmlFile(
    webView: WebView,
    htmlFile: File,
    contentDir: File,
    userPreferences: UserPreferences,
    onProgressUpdate: (Float) -> Unit
) {
    val htmlContent = htmlFile.readText()
    val styledHtml = injectStyles(htmlContent, userPreferences)

    webView.loadDataWithBaseURL(
        "file://${contentDir.absolutePath}/",
        styledHtml,
        "text/html",
        "UTF-8",
        null
    )

    // Setup scroll tracking
    webView.evaluateJavascript("""
        window.addEventListener('scroll', function() {
            var scrollPercent = window.scrollY / (document.body.scrollHeight - window.innerHeight);
            Android.onScroll(scrollPercent);
        });
    """.trimIndent(), null)
}

private fun injectStyles(htmlContent: String, userPreferences: UserPreferences): String {
    val themeColors = when (userPreferences.themeMode) {
        UserPreferences.ThemeMode.NIGHT -> Pair("#1C1B1F", "#E6E1E5")
        UserPreferences.ThemeMode.SEPIA -> Pair("#F4ECD8", "#3E2723")
        else -> Pair("#FFFBFE", "#1C1B1F")
    }

    val customCss = """
        <style>
            body {
                font-family: ${userPreferences.fontFamily};
                font-size: ${userPreferences.fontSize}rem !important;
                line-height: ${userPreferences.lineHeight} !important;
                background-color: ${themeColors.first} !important;
                color: ${themeColors.second} !important;
                margin: 0 auto;
                max-width: 800px;
                padding: 20px;
            }
            p {
                margin-bottom: 1em;
            }
            img {
                max-width: 100%;
                height: auto;
            }
        </style>
    """.trimIndent()

    return if (htmlContent.contains("</head>", ignoreCase = true)) {
        htmlContent.replace("</head>", "$customCss</head>", ignoreCase = true)
    } else if (htmlContent.contains("<body", ignoreCase = true)) {
        htmlContent.replace("<body", "$customCss<body", ignoreCase = true)
    } else {
        "<html><head>$customCss</head><body>$htmlContent</body></html>"
    }
}

class WebAppInterface(private val onProgressUpdate: (Float) -> Unit) {
    @JavascriptInterface
    fun onScroll(percent: Float) {
        Handler(Looper.getMainLooper()).post {
            onProgressUpdate(percent.coerceIn(0f, 1f))
        }
    }

    @JavascriptInterface
    fun onSelection(text: String, start: Int, end: Int) {
        // Future: Handle text selection for highlights
    }
}
