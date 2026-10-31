package com.sdocument.sdk.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.sdocument.sdk.BuildConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WScreen(
    url: String,
    onBackToApp: () -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }
    var webView: WebView? by remember { mutableStateOf(null) }

    val androidInterface = remember(onBackToApp) { AndroidInterface(onBackToApp) }

    // Block system back button
    BackHandler(enabled = true) {
        // Do nothing to prevent back navigation
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    setupWebView(
                        webView = this,
                        androidInterface = androidInterface,
                        onStateChange = { loading, error ->
                            isLoading = loading
                            hasError = error
                        }
                    )
                    loadUrl(url)
                }.also { webView = it }
            },
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            update = { webView ->
            }
        )

        DisposableEffect(Unit) {
            onDispose {
                webView?.apply {
                    stopLoading()
                    removeAllViews()
                    try {
                        removeJavascriptInterface("Android")
                    } catch (_: Exception) {
                    }
                    destroy()
                }
                webView = null
            }
        }

        if (isLoading && !hasError) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Загрузка...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

    }
}

class AndroidInterface(
    private val onBackToApp: () -> Unit
) {
    @JavascriptInterface
    fun backToApp() {
        Handler(Looper.getMainLooper()).post {
            onBackToApp()
        }
    }
}

@SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
private fun setupWebView(
    webView: WebView,
    androidInterface: AndroidInterface,
    onStateChange: (loading: Boolean, error: Boolean) -> Unit,
) {
    webView.apply {
        // ⚠️ Add JavaScript interface - must be done before loading URL
        addJavascriptInterface(androidInterface, "Android")

        var hasErrorOccurred = false

        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            loadsImagesAutomatically = true
            blockNetworkImage = false
            loadWithOverviewMode = true
            useWideViewPort = true

            // Allow mixed content for development (disable in production)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }

            allowFileAccess = true
            allowContentAccess = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)

            // Improve performance
            loadWithOverviewMode = true
            useWideViewPort = true
            databaseEnabled = true
        }

        webViewClient = object : WebViewClient() {

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                hasErrorOccurred = false
                onStateChange(true, false)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Inject a small script to verify Android interface is accessible (debug only)
                if (BuildConfig.DEBUG) {
                    evaluateJavascript(
                        """
                        (function() {
                            if (typeof Android !== 'undefined' && typeof Android.backToApp === 'function') {
                                console.log('[WScreen] Android interface ready');
                            } else {
                                console.warn('[WScreen] Android interface NOT accessible');
                            }
                        })();
                        """.trimIndent(),
                        null
                    )
                }
                onStateChange(false, hasErrorOccurred)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                // Only flag error for main frame (not iframe/resources)
                if (request?.isForMainFrame == true) {
                    hasErrorOccurred = true
                    onStateChange(false, true)
                }
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?
            ) {
                super.onReceivedHttpError(view, request, errorResponse)
                if (request?.isForMainFrame == true && errorResponse?.statusCode ?: 0 >= 400) {
                    hasErrorOccurred = true
                    onStateChange(false, true)
                }
            }

            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?
            ) {
                // ⚠️ For production: show warning dialog instead of auto-cancel
                // For now, cancel to avoid insecure connections
                handler?.cancel()
                hasErrorOccurred = true
                onStateChange(false, true)
            }

            // Optional: Override URL loading to keep navigation within WebView
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                // Let WebView handle all URLs (including redirects)
                return false
            }
        }

        // Optional: WebChromeClient for JS dialogs/console
        webChromeClient = object : android.webkit.WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                if (BuildConfig.DEBUG) {
                    android.util.Log.d(
                        "WScreen",
                        "${consoleMessage?.sourceId()}:${consoleMessage?.lineNumber()} - ${consoleMessage?.message()}"
                    )
                }
                return true
            }
        }
    }
}