package com.example.mfb.data.preview

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.ConsoleMessage
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import com.example.mfb.domain.model.ConsoleLogSeverity
import com.example.mfb.domain.model.ConsoleLogType
import com.example.mfb.domain.model.PendingExternalNavigation
import com.example.mfb.domain.model.PreviewSettings

object PreviewWebViewSecurity {

    private val BLOCKED_SCHEMES = setOf("file", "content", "javascript", "intent", "data")
    private val EXTERNAL_CONFIRM_SCHEMES = setOf("mailto", "tel")

    @SuppressLint("SetJavaScriptEnabled")
    @Suppress("DEPRECATION")
    fun applySecureSettings(webView: WebView, settings: PreviewSettings) {
        webView.settings.apply {
            javaScriptEnabled = settings.javaScriptEnabled
            allowFileAccess = false
            allowContentAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            setGeolocationEnabled(false)
            databaseEnabled = false
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_NO_CACHE
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(settings.zoomControlsEnabled)
            builtInZoomControls = settings.zoomControlsEnabled
            displayZoomControls = false
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                safeBrowsingEnabled = true
            }
            if (settings.desktopViewportMode) {
                userAgentString = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
            } else {
                userAgentString = null // Default Android WebView UA
            }
        }
    }

    fun isBlockedScheme(scheme: String?): Boolean {
        return scheme?.lowercase() in BLOCKED_SCHEMES
    }

    fun isExternalConfirmScheme(scheme: String?): Boolean {
        return scheme?.lowercase() in EXTERNAL_CONFIRM_SCHEMES
    }
    fun buildViewportSimulationScript(viewportWidthPx: Int?, zoomPercent: Int): String {
        val zoomRatio = (zoomPercent.coerceIn(50, 200)) / 100.0
        val viewportContent = if (viewportWidthPx != null) {
            "width=$viewportWidthPx, initial-scale=1.0"
        } else {
            "width=device-width, initial-scale=1.0"
        }
        return """
            (function() {
                try {
                    var meta = document.querySelector('meta[name="viewport"]');
                    if (!meta && document.head) {
                        meta = document.createElement('meta');
                        meta.name = 'viewport';
                        document.head.appendChild(meta);
                    }
                    if (meta) {
                        meta.setAttribute('content', '$viewportContent');
                    }
                    if (document.documentElement) {
                        document.documentElement.style.zoom = '$zoomRatio';
                    }
                } catch (e) {}
            })();
        """.trimIndent()
    }
}

class PreviewWebViewClient(
    private val resourceHandler: ProjectResourceHandler,
    private val assetLoader: WebViewAssetLoader,
    private var externalNetworkEnabled: Boolean,
    private val onPageStarted: (String) -> Unit,
    private val onPageFinished: (String, String, Boolean, Boolean) -> Unit,
    private val onHistoryUpdated: (String, Boolean, Boolean) -> Unit = { _, _, _ -> },
    private val onMainPageError: (String, String) -> Unit,
    private val onLogEvent: (ConsoleLogSeverity, ConsoleLogType, String, String, Int?) -> Unit,
    private val onExternalNavigationRequested: (PendingExternalNavigation) -> Unit
) : WebViewClient() {

    fun updateExternalNetworkEnabled(enabled: Boolean) {
        externalNetworkEnabled = enabled
    }

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest
    ): WebResourceResponse? {
        val uri = request.url
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase()

        if (PreviewWebViewSecurity.isBlockedScheme(scheme)) {
            onLogEvent(
                ConsoleLogSeverity.ERROR,
                ConsoleLogType.SECURITY_POLICY,
                "Blocked restricted scheme '$scheme:'",
                scheme ?: "unknown",
                null
            )
            return ProjectResourceHandler.forbiddenResponse()
        }

        if (scheme == "https" && host == resourceHandler.projectHost) {
            return assetLoader.shouldInterceptRequest(uri)
        }

        if (!externalNetworkEnabled) {
            onLogEvent(
                ConsoleLogSeverity.WARNING,
                ConsoleLogType.NAVIGATION_BLOCKED,
                "Blocked external resource (${host ?: "unknown"}) under offline-by-default policy.",
                host ?: "external",
                null
            )
            return ProjectResourceHandler.forbiddenResponse()
        }

        return super.shouldInterceptRequest(view, request)
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean {
        val uri = request.url
        val scheme = uri.scheme?.lowercase().orEmpty()
        val host = uri.host?.lowercase().orEmpty()

        if (scheme == "https" && host == resourceHandler.projectHost) {
            return false // Allow in-project navigation
        }

        if (PreviewWebViewSecurity.isBlockedScheme(scheme)) {
            onLogEvent(
                ConsoleLogSeverity.ERROR,
                ConsoleLogType.SECURITY_POLICY,
                "Blocked unsafe navigation scheme '$scheme:'",
                scheme,
                null
            )
            return true
        }

        if (PreviewWebViewSecurity.isExternalConfirmScheme(scheme)) {
            onExternalNavigationRequested(
                PendingExternalNavigation(
                    url = uri.toString(),
                    scheme = scheme,
                    isSpecialScheme = true
                )
            )
            return true
        }

        if (scheme == "http" || scheme == "https") {
            onExternalNavigationRequested(
                PendingExternalNavigation(
                    url = uri.toString(),
                    scheme = scheme,
                    isSpecialScheme = false
                )
            )
            return true
        }

        onLogEvent(
            ConsoleLogSeverity.WARNING,
            ConsoleLogType.NAVIGATION_BLOCKED,
            "Blocked unsupported URL scheme '$scheme:'",
            scheme,
            null
        )
        return true
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        val safeRef = if (url != null) resourceHandler.toSafeRelativeRef(android.net.Uri.parse(url)) else ""
        onPageStarted(safeRef)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        val title = view?.title?.takeIf { it.isNotBlank() } ?: "Preview"
        val safeRef = if (url != null) resourceHandler.toSafeRelativeRef(android.net.Uri.parse(url)) else ""
        onPageFinished(
            safeRef,
            title,
            view?.canGoBack() == true,
            view?.canGoForward() == true
        )
    }

    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
        super.doUpdateVisitedHistory(view, url, isReload)
        val safeRef = if (url != null) resourceHandler.toSafeRelativeRef(android.net.Uri.parse(url)) else ""
        onHistoryUpdated(
            safeRef,
            view?.canGoBack() == true,
            view?.canGoForward() == true
        )
    }

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest,
        error: WebResourceError
    ) {
        super.onReceivedError(view, request, error)
        val safeRef = resourceHandler.toSafeRelativeRef(request.url)
        val desc = error.description?.toString() ?: "Unknown loading error"
        if (request.isForMainFrame) {
            onMainPageError("Failed to load '$safeRef': $desc", safeRef)
        } else {
            onLogEvent(
                ConsoleLogSeverity.ERROR,
                ConsoleLogType.RESOURCE_ERROR,
                "Resource failed ($desc)",
                safeRef,
                null
            )
        }
    }

    override fun onReceivedHttpError(
        view: WebView?,
        request: WebResourceRequest,
        errorResponse: WebResourceResponse
    ) {
        super.onReceivedHttpError(view, request, errorResponse)
        val safeRef = resourceHandler.toSafeRelativeRef(request.url)
        val status = errorResponse.statusCode
        onLogEvent(
            if (status >= 500) ConsoleLogSeverity.ERROR else ConsoleLogSeverity.WARNING,
            ConsoleLogType.HTTP_ERROR,
            "HTTP $status ${errorResponse.reasonPhrase ?: ""}".trim(),
            safeRef,
            null
        )
    }

    override fun onReceivedSslError(
        view: WebView?,
        handler: SslErrorHandler,
        error: SslError?
    ) {
        // NEVER ignore SSL errors or call handler.proceed()
        handler.cancel()
        onLogEvent(
            ConsoleLogSeverity.ERROR,
            ConsoleLogType.SSL_ERROR,
            "SSL certificate error (${error?.primaryError ?: "unknown"}). Connection cancelled.",
            "https",
            null
        )
    }
}

class PreviewWebChromeClient(
    private val sanitizeMessage: (String) -> String,
    private val onProgressChanged: (Int) -> Unit,
    private val onTitleChanged: (String) -> Unit,
    private val onLogEvent: (ConsoleLogSeverity, ConsoleLogType, String, String, Int?) -> Unit
) : WebChromeClient() {

    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        onProgressChanged(newProgress)
    }

    override fun onReceivedTitle(view: WebView?, title: String?) {
        super.onReceivedTitle(view, title)
        if (!title.isNullOrBlank()) {
            onTitleChanged(title)
        }
    }

    override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
        val severity = when (consoleMessage.messageLevel()) {
            ConsoleMessage.MessageLevel.ERROR -> ConsoleLogSeverity.ERROR
            ConsoleMessage.MessageLevel.WARNING -> ConsoleLogSeverity.WARNING
            else -> ConsoleLogSeverity.INFO
        }
        val type = when (severity) {
            ConsoleLogSeverity.ERROR -> ConsoleLogType.CONSOLE_ERROR
            ConsoleLogSeverity.WARNING -> ConsoleLogType.CONSOLE_WARNING
            ConsoleLogSeverity.INFO -> ConsoleLogType.CONSOLE_INFO
        }
        val cleanMsg = sanitizeMessage(consoleMessage.message().orEmpty())
        val lineNo = consoleMessage.lineNumber().takeIf { it > 0 }
        val source = sanitizeMessage(consoleMessage.sourceId().orEmpty())
            .substringAfter(".mfb.local/")
            .let { path ->
                when {
                    path.isBlank() && lineNo != null -> "line $lineNo"
                    path.isBlank() -> "console"
                    lineNo != null -> "$path:$lineNo"
                    else -> path
                }
            }

        onLogEvent(severity, type, cleanMsg, source, lineNo)
        return true
    }

    override fun onPermissionRequest(request: PermissionRequest) {
        // Deny camera, microphone, and other hardware permissions by default
        request.deny()
        onLogEvent(
            ConsoleLogSeverity.WARNING,
            ConsoleLogType.SECURITY_POLICY,
            "Denied web permission request (${request.resources.joinToString()})",
            "permissions",
            null
        )
    }

    override fun onGeolocationPermissionsShowPrompt(
        origin: String?,
        callback: GeolocationPermissions.Callback
    ) {
        callback.invoke(origin, false, false)
        onLogEvent(
            ConsoleLogSeverity.WARNING,
            ConsoleLogType.SECURITY_POLICY,
            "Denied geolocation request.",
            "geolocation",
            null
        )
    }
}
