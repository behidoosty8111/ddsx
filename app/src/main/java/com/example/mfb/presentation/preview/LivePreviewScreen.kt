package com.example.mfb.presentation.preview

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mfb.data.preview.PreviewWebChromeClient
import com.example.mfb.data.preview.PreviewWebViewClient
import com.example.mfb.data.preview.PreviewWebViewSecurity
import com.example.mfb.domain.model.PreviewLoadState
import com.example.mfb.domain.model.ViewportPreset
import kotlinx.coroutines.launch

@Composable
fun LivePreviewScreen(
    viewModel: LivePreviewViewModel,
    onNavigateBack: () -> Unit,
    onReturnToFileBrowser: () -> Unit = onNavigateBack,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.previewSettings.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var showSettingsDialog by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var webClientRef by remember { mutableStateOf<PreviewWebViewClient?>(null) }
    var loadedEntryUrl by remember { mutableStateOf<String?>(null) }
    var appliedGenerationId by remember { mutableStateOf(-1L) }

    val handleBackNavigation: () -> Unit = {
        val wv = webViewRef
        when {
            uiState.isFullScreen -> viewModel.exitFullScreen()
            wv != null && wv.canGoBack() -> wv.goBack()
            else -> onNavigateBack()
        }
    }

    BackHandler(onBack = handleBackNavigation)

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> webViewRef?.onResume()
                Lifecycle.Event.ON_PAUSE -> webViewRef?.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            webViewRef?.apply {
                stopLoading()
                webChromeClient = null
                destroy()
            }
            webViewRef = null
            webClientRef = null
        }
    }

    LaunchedEffect(settings, webViewRef) {
        webViewRef?.let { wv ->
            PreviewWebViewSecurity.applySecureSettings(wv, settings)
        }
        webClientRef?.updateExternalNetworkEnabled(settings.externalNetworkEnabled)
    }

    // Apply CSS viewport & zoom simulation dynamically in-memory without modifying project files
    LaunchedEffect(uiState.selectedViewport, uiState.zoomPercent, uiState.loadState, webViewRef) {
        val wv = webViewRef ?: return@LaunchedEffect
        if (uiState.loadState is PreviewLoadState.Loaded) {
            val targetWidthPx = uiState.selectedViewport.widthDp
            val script = PreviewWebViewSecurity.buildViewportSimulationScript(
                viewportWidthPx = targetWidthPx,
                zoomPercent = uiState.zoomPercent
            )
            wv.evaluateJavascript(script, null)
        }
    }

    Scaffold(
        topBar = {
            if (!uiState.isFullScreen) {
                Column {
                    PreviewToolbar(
                        projectName = uiState.project?.name ?: "MFB Project",
                        currentFileLabel = uiState.currentFileLabel,
                        loadState = uiState.loadState,
                        canGoBack = uiState.canGoBack,
                        canGoForward = uiState.canGoForward,
                        selectedViewport = uiState.selectedViewport,
                        errorCount = uiState.errorCount,
                        onBack = handleBackNavigation,
                        onForward = {
                            webViewRef?.takeIf { it.canGoForward() }?.goForward()
                        },
                        onRefreshOrStop = {
                            val isLoading = uiState.loadState is PreviewLoadState.PreparingProject ||
                                uiState.loadState is PreviewLoadState.LoadingPage ||
                                uiState.loadState is PreviewLoadState.Rendering
                            val wv = webViewRef
                            if (isLoading) {
                                wv?.stopLoading()
                                viewModel.markCancelled()
                            } else if (wv != null) {
                                wv.clearCache(true)
                                if (wv.url.isNullOrBlank() && uiState.entryUrl.isNotBlank()) {
                                    wv.loadUrl(uiState.entryUrl)
                                } else {
                                    wv.reload()
                                }
                            } else {
                                loadedEntryUrl = null
                                viewModel.preparePreview()
                            }
                        },
                        onReloadEntryPoint = {
                            val url = uiState.entryUrl
                            val wv = webViewRef
                            if (url.isNotBlank() && wv != null) {
                                wv.clearCache(true)
                                wv.loadUrl(url)
                            } else {
                                loadedEntryUrl = null
                                viewModel.preparePreview()
                            }
                        },
                        onSelectViewport = { viewModel.selectViewport(it) },
                        onToggleFullScreen = { viewModel.toggleFullScreen() },
                        onToggleConsole = { viewModel.toggleConsoleExpanded() },
                        onOpenSettings = { showSettingsDialog = true }
                    )
                    PreviewViewportZoomBar(
                        selectedViewport = uiState.selectedViewport,
                        zoomPercent = uiState.zoomPercent,
                        zoomControlsEnabled = settings.zoomControlsEnabled,
                        onZoomOut = { viewModel.zoomOut() },
                        onResetZoom = { viewModel.resetZoom() },
                        onZoomIn = { viewModel.zoomIn() }
                    )
                }
            }
        },
        bottomBar = {
            if (!uiState.isFullScreen) {
                PreviewConsolePanel(
                    logs = uiState.logs,
                    isExpanded = uiState.isConsoleExpanded,
                    errorCount = uiState.errorCount,
                    warningCount = uiState.warningCount,
                    onToggleExpand = { viewModel.toggleConsoleExpanded() },
                    onCopyLogs = {
                        val text = viewModel.formatLogsForCopy()
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        cm?.setPrimaryClip(ClipData.newPlainText("Preview Console Logs", text))
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Copied ${uiState.logs.size} log entry(ies).")
                        }
                    },
                    onClearLogs = { viewModel.clearLogs() }
                )
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (!uiState.isFullScreen && settings.externalNetworkEnabled) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "External network access enabled: remote assets can contact external servers.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when (val state = uiState.loadState) {
                    is PreviewLoadState.PreparingProject -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Preparing isolated project runtime...", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    is PreviewLoadState.Failed -> {
                        PreviewErrorState(
                            title = if (state.isEntryMissing) "Entry Point Not Found" else "Preview Failed",
                            message = state.message,
                            isEntryMissing = state.isEntryMissing,
                            onRetry = {
                                loadedEntryUrl = null
                                viewModel.preparePreview()
                            },
                            onReturnToFileBrowser = onReturnToFileBrowser
                        )
                    }

                    else -> {
                        val handler = viewModel.getResourceHandler()
                        val entryUrl = uiState.entryUrl
                        val generationId = uiState.activeGenerationId

                        if (handler != null && entryUrl.isNotBlank()) {
                            val assetLoader = remember(handler) { handler.buildAssetLoader() }
                            val viewport = uiState.selectedViewport

                            DisposableEffect(Unit) {
                                onDispose {
                                    webViewRef?.apply {
                                        stopLoading()
                                        webChromeClient = null
                                        destroy()
                                    }
                                    webViewRef = null
                                    webClientRef = null
                                    loadedEntryUrl = null
                                }
                            }

                            BoxWithConstraints(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(
                                        if (viewport == ViewportPreset.RESPONSIVE || uiState.isFullScreen) {
                                            Modifier
                                        } else {
                                            Modifier
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                                .padding(12.dp)
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                val frameModifier = computeViewportFrameModifier(
                                    viewport = viewport,
                                    isFullScreen = uiState.isFullScreen,
                                    availableWidth = maxWidth,
                                    availableHeight = maxHeight
                                )

                                AndroidView(
                                    factory = { ctx ->
                                        WebView(ctx).apply {
                                            layoutParams = ViewGroup.LayoutParams(
                                                ViewGroup.LayoutParams.MATCH_PARENT,
                                                ViewGroup.LayoutParams.MATCH_PARENT
                                            )
                                            PreviewWebViewSecurity.applySecureSettings(this, settings)
                                            val client = createPreviewClient(
                                                handler = handler,
                                                assetLoader = assetLoader,
                                                externalNetworkEnabled = settings.externalNetworkEnabled,
                                                generationId = generationId,
                                                viewModel = viewModel
                                            )
                                            val chrome = createPreviewChromeClient(
                                                generationId = generationId,
                                                viewModel = viewModel
                                            )
                                            webViewClient = client
                                            webChromeClient = chrome
                                            webClientRef = client
                                            webViewRef = this
                                            appliedGenerationId = generationId
                                            loadedEntryUrl = entryUrl
                                            loadUrl(entryUrl)
                                        }
                                    },
                                    update = { wv ->
                                        webViewRef = wv
                                        if (appliedGenerationId != generationId) {
                                            val client = createPreviewClient(
                                                handler = handler,
                                                assetLoader = assetLoader,
                                                externalNetworkEnabled = settings.externalNetworkEnabled,
                                                generationId = generationId,
                                                viewModel = viewModel
                                            )
                                            val chrome = createPreviewChromeClient(
                                                generationId = generationId,
                                                viewModel = viewModel
                                            )
                                            wv.webViewClient = client
                                            wv.webChromeClient = chrome
                                            webClientRef = client
                                            appliedGenerationId = generationId
                                        }
                                        if (loadedEntryUrl != entryUrl) {
                                            loadedEntryUrl = entryUrl
                                            wv.clearCache(true)
                                            wv.loadUrl(entryUrl)
                                        }
                                    },
                                    modifier = frameModifier.testTag("live_preview_webview")
                                )
                            }
                        }
                    }
                }

                if (uiState.isFullScreen) {
                    SmallFloatingActionButton(
                        onClick = { viewModel.exitFullScreen() },
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(12.dp)
                            .testTag("preview_exit_fullscreen_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FullscreenExit,
                            contentDescription = "Exit full-screen"
                        )
                    }
                }
            }
        }
    }

    uiState.pendingExternalNavigation?.let { pending ->
        ExternalNavigationDialog(
            pending = pending,
            onConfirmOpen = { target ->
                viewModel.dismissExternalNavigation()
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(target.url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("No application available to open ${target.scheme}: link.")
                    }
                }
            },
            onDismiss = { viewModel.dismissExternalNavigation() }
        )
    }

    if (showSettingsDialog) {
        PreviewSettingsDialog(
            settings = settings,
            onUpdateSettings = { transform -> viewModel.updatePreviewSettings(transform) },
            onRequestClearProjectStorage = {
                val origin = uiState.projectOrigin
                if (origin.isNotBlank()) {
                    WebStorage.getInstance().deleteOrigin(origin)
                }
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Cleared web storage for this project.")
                }
            },
            onDismiss = { showSettingsDialog = false }
        )
    }
}

private fun createPreviewClient(
    handler: com.example.mfb.data.preview.ProjectResourceHandler,
    assetLoader: androidx.webkit.WebViewAssetLoader,
    externalNetworkEnabled: Boolean,
    generationId: Long,
    viewModel: LivePreviewViewModel
): PreviewWebViewClient {
    return PreviewWebViewClient(
        resourceHandler = handler,
        assetLoader = assetLoader,
        externalNetworkEnabled = externalNetworkEnabled,
        onPageStarted = { ref -> viewModel.onPageStarted(ref, generationId) },
        onPageFinished = { ref, title, back, fwd ->
            viewModel.onPageFinished(ref, title, back, fwd, generationId)
        },
        onHistoryUpdated = { ref, back, fwd ->
            viewModel.onHistoryUpdated(ref, back, fwd, generationId)
        },
        onMainPageError = { msg, ref -> viewModel.onMainPageError(msg, ref, generationId) },
        onLogEvent = { sev, type, msg, ref, lineNo ->
            if (generationId == viewModel.currentGenerationId()) {
                viewModel.appendLog(sev, type, msg, ref, lineNo)
            }
        },
        onExternalNavigationRequested = { pending ->
            if (generationId == viewModel.currentGenerationId()) {
                viewModel.requestExternalNavigation(pending)
            }
        }
    )
}

private fun createPreviewChromeClient(
    generationId: Long,
    viewModel: LivePreviewViewModel
): PreviewWebChromeClient {
    return PreviewWebChromeClient(
        sanitizeMessage = { viewModel.sanitizeText(it) },
        onProgressChanged = { p -> viewModel.onProgressChanged(p, generationId) },
        onTitleChanged = { /* handled on finish */ },
        onLogEvent = { sev, type, msg, ref, lineNo ->
            if (generationId == viewModel.currentGenerationId()) {
                viewModel.appendLog(sev, type, msg, ref, lineNo)
            }
        }
    )
}

@Composable
private fun computeViewportFrameModifier(
    viewport: ViewportPreset,
    isFullScreen: Boolean,
    availableWidth: Dp,
    availableHeight: Dp
): Modifier {
    if (viewport == ViewportPreset.RESPONSIVE || isFullScreen) {
        return Modifier.fillMaxSize()
    }
    val targetW = (viewport.widthDp ?: 390).toFloat()
    val targetH = (viewport.heightDp ?: 844).toFloat()
    val aspect = targetW / targetH

    val maxW = availableWidth.value.coerceAtLeast(120f)
    val maxH = availableHeight.value.coerceAtLeast(120f)

    var fittedW = maxW
    var fittedH = fittedW / aspect
    if (fittedH > maxH) {
        fittedH = maxH
        fittedW = fittedH * aspect
    }

    return Modifier
        .width(fittedW.dp)
        .height(fittedH.dp)
        .clip(RoundedCornerShape(12.dp))
        .border(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
            RoundedCornerShape(12.dp)
        )
}

@Composable
private fun PreviewErrorState(
    title: String,
    message: String,
    isEntryMissing: Boolean,
    onRetry: () -> Unit,
    onReturnToFileBrowser: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag("preview_error_state"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (isEntryMissing) {
                    Button(
                        onClick = onReturnToFileBrowser,
                        modifier = Modifier.testTag("preview_return_to_files_button")
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Select Entry File")
                    }
                }
                OutlinedButton(onClick = onRetry) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Retry")
                }
            }
        }
    }
}
