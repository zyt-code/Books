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
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier
) {
    val context = LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            webView?.destroy()
        }
    }

    LaunchedEffect(spineItem, userPreferences) {
        webView?.let { web ->
            if (spineItem != null) {
                loadSpineContent(web, spineItem, bookDir, context, userPreferences, onProgressUpdate)
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
    onProgressUpdate: (Float) -> Unit
) {
    val contentDir = File(bookDir, "content")
    val spineIndex = spineItem.id.removePrefix("spine_").substringBefore(".").toIntOrNull() ?: 0
    val htmlFile = File(contentDir, "spine_$spineIndex.xhtml")

    if (!htmlFile.exists()) return

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
