package nx.browser.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.compose.BackHandler
import android.content.ClipData
import android.content.ClipboardManager
import nx.browser.adblock.AdblockEngine
import nx.browser.adblock.FilterListRepo
import nx.browser.browser.BrowserSettings
import nx.browser.browser.TabRegistry
import nx.browser.download.DownloadEntry
import nx.browser.download.DownloadsRegistry
import nx.browser.util.Url
import nx.browser.youtube.YouTubeExtractor
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(onOpenExternal: (String) -> Unit) {
    val tab = TabRegistry.current() ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val incognito by TabRegistry.incognito
    val detectedVideo by TabRegistry.detectedVideoUrl
    val webViewAvailable by TabRegistry.webViewAvailable

    var editing by remember { mutableStateOf(false) }
    var omnibox by remember { mutableStateOf("") }
    var showTabs by remember { mutableStateOf(false) }
    var showDownloads by remember { mutableStateOf(false) }
    var showYt by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf<String?>(null) }
    val longPress by TabRegistry.longPress

    val secure = Url.isSecure(tab.url)

    // System / edge back gesture: navigate web history, then close overlays.
    BackHandler(enabled = showSettings || showTabs || showDownloads || showYt || editing) {
        when {
            showYt -> showYt = false
            showDownloads -> showDownloads = false
            showTabs -> showTabs = false
            editing -> editing = false
            showSettings -> showSettings = false
        }
    }
    BackHandler(enabled = !(showSettings || showTabs || showDownloads || showYt || editing)) {
        val wv = tab.webView
        if (wv != null && wv.canGoBack()) wv.goBack()
        else if (wv != null && tab.url != TabRegistry.HOME) wv.loadUrl(TabRegistry.HOME)
    }

    // Full-screen settings page (no swipe drawer).
    if (showSettings) {
        SettingsScreen(
            context = context,
            onBack = { showSettings = false },
            onOpenDownloads = { showSettings = false; showDownloads = true },
            onOpenTabs = { showSettings = false; showTabs = true },
            onNewTab = { showSettings = false; TabRegistry.openTab() },
            onToggleIncognito = { showSettings = false; TabRegistry.setIncognito(!incognito) },
            onOpenExternal = { onOpenExternal(tab.url) },
        )
        return
    }

    Scaffold(
        topBar = {
            Surface(tonalElevation = 2.dp) {
                Column {
                    Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                    if (editing) {
                        OmniEditField(
                            value = omnibox,
                            onValueChange = { omnibox = it },
                            onSubmit = {
                                tab.webView?.loadUrl(Url.normalize(omnibox)); editing = false
                            },
                            onCancel = { editing = false },
                        )
                    } else {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = { showSettings = true }) {
                                Icon(AppIcons.Menu, "Menú", modifier = Modifier.size(24.dp))
                            }
                            OmniboxBar(
                                url = tab.url,
                                secure = secure,
                                incognito = incognito,
                                title = tab.title,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    omnibox = if (tab.url == TabRegistry.HOME) "" else tab.url
                                    editing = true
                                },
                            )
                            if (tab.isLoading) {
                                IconButton(onClick = { tab.webView?.stopLoading() }) {
                                    Icon(Icons.Default.Close, "Detener", modifier = Modifier.size(20.dp))
                                }
                            } else {
                                IconButton(onClick = { tab.webView?.reload() }) {
                                    Icon(Icons.Default.Refresh, "Recargar", modifier = Modifier.size(22.dp))
                                }
                            }
                        }
                    }
                    if (tab.isLoading) {
                        LinearProgressIndicator(
                            progress = { tab.progress.coerceIn(0, 100) / 100f },
                            modifier = Modifier.fillMaxWidth().height(2.dp),
                        )
                    } else {
                        Spacer(Modifier.height(2.dp))
                    }
                }
            }
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column {
                    AnimatedVisibility(visible = findQuery != null) {
                        FindBar(
                            onFind = { q -> tab.webView?.findAllAsync(q) },
                            onClose = {
                                tab.webView?.clearMatches(); findQuery = null
                            },
                            onNext = { tab.webView?.findNext(true) },
                            onPrev = { tab.webView?.findNext(false) },
                        )
                    }
                    BottomBar(
                        canGoBack = tab.canGoBack,
                        canGoForward = tab.canGoForward,
                        tabCount = TabRegistry.count(),
                        incognito = incognito,
                        adblockCount = AdblockEngine.blockedCount,
                        onBack = { if (tab.canGoBack) tab.webView?.goBack() },
                        onForward = { if (tab.canGoForward) tab.webView?.goForward() },
                        onHome = { tab.webView?.loadUrl(TabRegistry.HOME) },
                        onTabs = { showTabs = true },
                        onMenu = { showSettings = true },
                    )
                    Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (!webViewAvailable) {
                WebViewUnavailableNotice()
            }
            key("webhost") {
                AndroidView(
                    factory = { ctx ->
                        android.widget.FrameLayout(ctx).apply {
                            id = android.view.View.generateViewId()
                            layoutParams = android.view.ViewGroup.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                        }
                    },
                    update = { frame ->
                        val wv = try {
                            tab.ensureWebView(frame.context, incognito)
                        } catch (t: Throwable) {
                            TabRegistry.markWebViewUnavailable()
                            return@AndroidView
                        }
                        if (wv.parent !== frame) {
                            (wv.parent as? ViewGroup)?.removeView(wv)
                            if (frame.indexOfChild(wv) < 0) {
                                frame.addView(
                                    wv,
                                    android.view.ViewGroup.LayoutParams(
                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                    ),
                                )
                            }
                            attachPullToRefresh(wv) { tab.webView?.reload() }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // Pull-to-refresh spinner overlay (browser-style).
            val offset = pullOffset.value
            if (offset > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = (offset / 3f).dp),
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        tonalElevation = 3.dp,
                    ) {
                        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.5.dp,
                            )
                        }
                    }
                }
            }
            // Floating "download this video" button.
            AnimatedVisibility(
                visible = detectedVideo != null,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            ) {
                ExtendedFloatingActionButton(
                    onClick = { showYt = true },
                    icon = { Icon(AppIcons.Download, "Descargar video") },
                    text = { Text("Descargar") },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                )
            }
        }
    }
    if (showTabs) {
        TabsSheet(
            onDismiss = { showTabs = false },
            onSelect = { TabRegistry.select(it); showTabs = false },
            onClose = { TabRegistry.closeTab(it) },
            onNew = { TabRegistry.openTab(); showTabs = false },
        )
    }
    if (showDownloads) DownloadsDialog { showDownloads = false }
    if (showYt) YouTubeDialog(
        url = detectedVideo ?: tab.url,
        onDismiss = { showYt = false },
        onDownload = { opt, name ->
            val label = opt.quality
            val videoUrl = detectedVideo ?: tab.url
            if (YouTubeExtractor.parseId(opt.url) != null || opt.resolution == "audio" ||
                YouTubeExtractor.parseId(videoUrl) != null
            ) {
                DownloadsRegistry.add(DownloadEntry(name, "Descargando", label))
                scope.launch {
                    runCatching { YouTubeExtractor.download(context, opt, name) }
                        .onSuccess { DownloadsRegistry.update(name, "Completado", it.absolutePath) }
                        .onFailure { DownloadsRegistry.update(name, "Error", it.message ?: "") }
                }
            } else {
                nx.browser.browser.BrowserDownloader.start(
                    context, opt.url, null, null, null
                )
            }
            showYt = false
        },
    )
    longPress?.let { lp ->
        LongPressMenu(
            lp = lp,
            onDismiss = { TabRegistry.longPress.value = null },
            onOpenNewTab = { TabRegistry.longPress.value = null; TabRegistry.openTab(lp.url) },
            onOpenHere = { TabRegistry.longPress.value = null; tab.webView?.loadUrl(lp.url) },
            onCopy = {
                TabRegistry.longPress.value = null
                runCatching {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("url", lp.url))
                }
            },
            onDownload = {
                TabRegistry.longPress.value = null
                nx.browser.browser.BrowserDownloader.start(
                    context, lp.url, null, null, null
                )
            },
            onShare = { TabRegistry.longPress.value = null; onOpenExternal(lp.url) },
        )
    }
}

@Composable
private fun LongPressMenu(
    lp: TabRegistry.LongPress,
    onDismiss: () -> Unit,
    onOpenNewTab: () -> Unit,
    onOpenHere: () -> Unit,
    onCopy: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit,
) {
    val title = when (lp.kind) {
        TabRegistry.LongPress.Kind.LINK -> "Enlace"
        TabRegistry.LongPress.Kind.IMAGE -> "Imagen"
        TabRegistry.LongPress.Kind.MEDIA -> "Video/audio"
        TabRegistry.LongPress.Kind.GENERIC -> "Elemento"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(lp.url.take(120), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3)
                Spacer(Modifier.height(8.dp))
                if (lp.kind == TabRegistry.LongPress.Kind.LINK) {
                    ListItem(headlineContent = { Text("Abrir en pestaña nueva") },
                        modifier = Modifier.clickable(onClick = onOpenNewTab))
                    ListItem(headlineContent = { Text("Abrir aquí") },
                        modifier = Modifier.clickable(onClick = onOpenHere))
                }
                if (lp.kind == TabRegistry.LongPress.Kind.IMAGE ||
                    lp.kind == TabRegistry.LongPress.Kind.MEDIA ||
                    lp.kind == TabRegistry.LongPress.Kind.LINK
                ) {
                    ListItem(headlineContent = { Text("Descargar") },
                        modifier = Modifier.clickable(onClick = onDownload))
                }
                ListItem(headlineContent = { Text("Copiar enlace") },
                    modifier = Modifier.clickable(onClick = onCopy))
                ListItem(headlineContent = { Text("Compartir") },
                    modifier = Modifier.clickable(onClick = onShare))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

/** Pull-down-to-refresh: single-finger only, ignores pinch-zoom. Shows a spinner like a browser. */
private val pullOffset = mutableStateOf(0f)

private fun attachPullToRefresh(wv: WebView, onRefresh: () -> Unit) {
    var startY = 0f
    var startX = 0f
    var tracking = false
    var triggered = false
    val threshold = 200f
    wv.setOnTouchListener { _, ev ->
        when (ev.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> {
                startY = ev.y; startX = ev.x; triggered = false
                tracking = wv.scrollY <= 0
            }
            android.view.MotionEvent.ACTION_POINTER_DOWN -> {
                tracking = false; triggered = false; pullOffset.value = 0f
            }
            android.view.MotionEvent.ACTION_MOVE -> {
                if (!tracking || triggered) return@setOnTouchListener false
                val dy = ev.y - startY
                val dx = kotlin.math.abs(ev.x - startX)
                if (dx > kotlin.math.abs(dy)) { tracking = false; pullOffset.value = 0f; return@setOnTouchListener false }
                if (!wv.canScrollVertically(-1)) {
                    pullOffset.value = (dy.coerceIn(0f, threshold * 2f))
                } else {
                    pullOffset.value = 0f
                }
                if (dy > threshold && wv.scrollY <= 0 && !wv.canScrollVertically(-1)) {
                    triggered = true
                    onRefresh()
                }
            }
            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                tracking = false; triggered = false
                pullOffset.value = 0f
            }
        }
        false
    }
}

@Composable
private fun OmniboxBar(
    url: String,
    secure: Boolean,
    incognito: Boolean,
    title: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.height(44.dp).clickable(onClick = onClick),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                when {
                    incognito -> AppIcons.VisibilityOff
                    secure -> Icons.Default.Lock
                    else -> Icons.Default.Warning
                },
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = when {
                    incognito -> MaterialTheme.colorScheme.tertiary
                    secure -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.error
                },
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = if (url == TabRegistry.HOME || url == "about:blank")
                    "Buscar o escribir dirección"
                else title.ifEmpty { Url.prettyHost(url) },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = if (url == TabRegistry.HOME || url == "about:blank")
                    MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun OmniEditField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            placeholder = { Text("Buscar o escribir dirección") },
            leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(20.dp)) },
            trailingIcon = {
                if (value.isNotEmpty()) IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Default.Close, "Borrar", Modifier.size(18.dp))
                }
            },
            shape = MaterialTheme.shapes.extraLarge,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onSubmit() }),
        )
        TextButton(onClick = onCancel) { Text("Cancelar") }
    }
}

@Composable
private fun BottomBar(
    canGoBack: Boolean,
    canGoForward: Boolean,
    tabCount: Int,
    incognito: Boolean,
    adblockCount: Long,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onHome: () -> Unit,
    onTabs: () -> Unit,
    onMenu: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        IconButton(onClick = onBack, enabled = canGoBack) {
            Icon(Icons.Default.ArrowBack, "Atrás")
        }
        IconButton(onClick = onForward, enabled = canGoForward) {
            Icon(Icons.Default.ArrowForward, "Adelante")
        }
        IconButton(onClick = onHome) { Icon(Icons.Default.Home, "Inicio") }
        Box {
            IconButton(onClick = onTabs) { Icon(AppIcons.Tab, "Pestañas") }
            Badge(
                containerColor = if (incognito) MaterialTheme.colorScheme.tertiary
                else MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.TopEnd),
            ) { Text("$tabCount") }
        }
        Box {
            IconButton(onClick = onMenu) { Icon(Icons.Default.MoreVert, "Menú") }
            if (adblockCount > 0) {
                Badge(
                    containerColor = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.TopEnd),
                ) { Text(if (adblockCount > 999) "999+" else "$adblockCount") }
            }
        }
    }
}

@Composable
private fun FindBar(
    onFind: (String) -> Unit,
    onClose: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
) {
    var q by remember { mutableStateOf("") }
    Surface(tonalElevation = 2.dp) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = q, onValueChange = { q = it; onFind(it) },
                modifier = Modifier.weight(1f), singleLine = true,
                placeholder = { Text("Buscar en la página") },
                leadingIcon = { Icon(AppIcons.FindInPage, null, Modifier.size(20.dp)) },
                shape = MaterialTheme.shapes.extraLarge,
            )
            IconButton(onClick = onPrev) { Icon(Icons.Default.KeyboardArrowUp, "Anterior") }
            IconButton(onClick = onNext) { Icon(Icons.Default.KeyboardArrowDown, "Siguiente") }
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Cerrar") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabsSheet(
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
    onClose: (Int) -> Unit,
    onNew: () -> Unit,
) {
    val tabs = TabRegistry.tabsSnapshot()
    val active = TabRegistry.activeIndex.value
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Pestañas (${tabs.size})", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            FilledTonalButton(onClick = onNew) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Nueva")
            }
        }
        LazyColumn(Modifier.heightIn(max = 480.dp)) {
            items(tabs.size) { i ->
                val t = tabs[i]
                ListItem(
                    modifier = Modifier.clickable { onSelect(i) },
                    leadingContent = {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = if (i == active) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(40.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("${i + 1}", fontWeight = FontWeight.Bold)
                            }
                        }
                    },
                    headlineContent = {
                        Text(t.title.ifEmpty { Url.prettyHost(t.url) }.take(60), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = {
                        Text(Url.prettyHost(t.url).take(50), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    trailingContent = {
                        IconButton(onClick = { onClose(i) }) { Icon(Icons.Default.Close, "Cerrar") }
                    },
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DownloadsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val items = DownloadsRegistry.items
    LaunchedEffect(Unit) {
        while (true) {
            DownloadsRegistry.refreshProgress()
            kotlinx.coroutines.delay(700)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(AppIcons.Download, null) },
        title = { Text("Descargas") },
        text = {
            if (items.isEmpty()) {
                Text("Aún no hay descargas.")
            } else {
                Column {
                    LazyColumn(Modifier.heightIn(max = 400.dp)) {
                        items(items.size) { i ->
                            val d = items[i]
                            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(d.name.take(48), fontWeight = FontWeight.Medium,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            if (d.progress in 0..100 && d.state == "Descargando")
                                                "${d.state} · ${d.progress}%"
                                            else "${d.state} · ${d.detail.take(36)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    if (d.state == "Completado") {
                                        IconButton(onClick = { openDownloaded(context, d) }) {
                                            Icon(AppIcons.Download, "Abrir", Modifier.size(18.dp))
                                        }
                                    }
                                    IconButton(onClick = { DownloadsRegistry.remove(i) }) {
                                        Icon(Icons.Default.Close, "Quitar", Modifier.size(18.dp))
                                    }
                                }
                                if (d.progress in 0..99 && d.state == "Descargando") {
                                    LinearProgressIndicator(
                                        progress = { d.progress / 100f },
                                        modifier = Modifier.fillMaxWidth().height(3.dp),
                                    )
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        dismissButton = {
            if (items.any { it.state == "Completado" || it.state.startsWith("Error") })
                TextButton(onClick = { DownloadsRegistry.clearFinished() }) { Text("Limpiar") }
        },
    )
}

/** Shown when the device WebView is missing, broken or a different provider failed. */
@Composable
private fun WebViewUnavailableNotice() {
    val context = LocalContext.current
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.fillMaxSize().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Default.Warning, null, Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(16.dp))
            Text("WebView no disponible", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "NexBrowser usa el motor WebView del sistema. Instálalo o actívalo para poder navegar.\n\n" +
                    "Prueba una de estas opciones:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = {
                runCatching {
                    context.startActivity(
                        android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("market://details?id=com.google.android.webview"),
                        )
                    )
                }
            }) { Text("Instalar Android System WebView") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {
                runCatching {
                    context.startActivity(
                        android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("market://details?id=com.android.chrome"),
                        )
                    )
                }
            }) { Text("Instalar / activar Chrome") }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = {
                runCatching {
                    context.startActivity(
                        android.content.Intent(android.provider.Settings.ACTION_WEBVIEW_SETTINGS)
                    )
                }
            }) { Text("Abrir ajustes de WebView") }
        }
    }
}

/** Opens a finished download with the system viewer (Via-style "open file"). */
private fun openDownloaded(context: Context, d: DownloadEntry) {
    runCatching {
        val uri: android.net.Uri = if (d.path.isNotBlank() && java.io.File(d.path).exists()) {
            androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", java.io.File(d.path)
            )
        } else if (d.downloadId >= 0) {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
            dm.getUriForDownloadedFile(d.downloadId)
        } else {
            return
        }
        val mime = android.webkit.MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(d.name.substringAfterLast('.', "").lowercase())
            ?: "*/*"
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

/** Full-screen settings & menu page (no swipe drawer). */
@Composable
private fun SettingsScreen(
    context: Context,
    onBack: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenTabs: () -> Unit,
    onNewTab: () -> Unit,
    onToggleIncognito: () -> Unit,
    onOpenExternal: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var js by remember { mutableStateOf(BrowserSettings.javaScriptEnabled) }
    var https by remember { mutableStateOf(BrowserSettings.httpsOnly) }
    var adblock by remember { mutableStateOf(BrowserSettings.adblockEnabled) }
    var thirdPc by remember { mutableStateOf(BrowserSettings.blockThirdPartyCookies) }
    var dnt by remember { mutableStateOf(BrowserSettings.doNotTrack) }
    var desktop by remember { mutableStateOf(BrowserSettings.desktopMode) }
    var images by remember { mutableStateOf(BrowserSettings.imageBlocking) }
    var engine by remember { mutableStateOf(BrowserSettings.searchEngine) }
    var clearing by remember { mutableStateOf(false) }
    var engineOpen by remember { mutableStateOf(false) }
    var shieldsTick by remember { mutableStateOf(0) }
    val incognito by TabRegistry.incognito

    fun currentHost(): String? {
        shieldsTick
        val u = TabRegistry.current()?.url ?: return null
        runCatching { java.net.URI(u).host?.lowercase()?.takeIf { it.isNotEmpty() } }
            .getOrNull()?.let { return it }
        val s = u.substringAfter("://", "").substringBefore('/').substringBefore('?')
            .substringAfter('@').substringBefore(':').lowercase()
        return s.takeIf { it.isNotEmpty() && it.contains('.') }
    }

    Scaffold(
        topBar = {
            Surface(tonalElevation = 2.dp) {
                Column {
                    Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.Close, "Cerrar menú", modifier = Modifier.size(24.dp))
                        }
                        Text(
                            "NexBrowser",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        },
        bottomBar = { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            item {
                Column(Modifier.padding(20.dp)) {
                    Icon(AppIcons.Compas, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Bloqueados: ${AdblockEngine.blockedCount} anuncios",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                val curHost = currentHost()
                if (curHost != null) {
                    val shieldsOn = BrowserSettings.shieldsEnabledFor(curHost)
                    ToggleRow(
                        AppIcons.Block,
                        "Escudo en este sitio",
                        shieldsOn,
                        curHost + if (shieldsOn) " · activo" else " · desactivado",
                    ) {
                        BrowserSettings.toggleShieldsFor(curHost)
                        TabRegistry.current()?.webView?.reload()
                        shieldsTick++
                    }
                }
            }
            item { DrawerItem(Icons.Default.Add, "Nueva pestaña", onNewTab) }
            item { DrawerItem(AppIcons.Tab, "Pestañas (${TabRegistry.count()})", onOpenTabs) }
            item { DrawerItem(AppIcons.Download, "Descargas (${DownloadsRegistry.items.size})", onOpenDownloads) }
            item { DrawerItem(AppIcons.LockOpen, if (incognito) "Salir de incógnito" else "Incógnito", onToggleIncognito) }
            item { DrawerItem(Icons.Default.Share, "Compartir página", onOpenExternal) }
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }

            item { SectionLabel("Privacidad y bloqueo") }
            item {
                ToggleRow(AppIcons.Block, "Bloqueo agresivo de anuncios", adblock, "Red + cosmético + scriptlets") {
                    adblock = it; BrowserSettings.updateAdblock(it)
                }
            }
            item {
                TextButton(
                    onClick = { scope.launch { clearing = true; runCatching { FilterListRepo.updateIfStale(true) }; clearing = false } },
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    Icon(AppIcons.RefreshIcon, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                    Text(if (clearing) "Actualizando…" else "Actualizar filtros (${AdblockEngine.ruleCount()} reglas)")
                }
            }
            item { ToggleRow(Icons.Default.Lock, "Solo HTTPS", https, "Fuerza conexiones seguras") { https = it; BrowserSettings.updateHttpsOnly(it) } }
            item { ToggleRow(AppIcons.Cookie, "Bloquear cookies de terceros", thirdPc, null) { thirdPc = it; BrowserSettings.updateBlockThirdPartyCookies(it) } }
            item { ToggleRow(AppIcons.VisibilityOff, "Do Not Track", dnt, "Pide a los sitios no rastrear") { dnt = it; BrowserSettings.updateDoNotTrack(it) } }

            item { SectionLabel("Contenido") }
            item { ToggleRow(AppIcons.Code, "JavaScript", js, "Desactivar puede romper sitios") { js = it; BrowserSettings.updateJavaScript(it); TabRegistry.current()?.applySettings() } }
            item { ToggleRow(AppIcons.Image, "Cargar imágenes", !images, "Desactivar ahorra datos") { images = !it; BrowserSettings.updateImageBlocking(images); TabRegistry.current()?.applySettings() } }
            item { ToggleRow(AppIcons.DesktopWindows, "Modo escritorio", desktop, "Sitio de escritorio por defecto") { desktop = it; BrowserSettings.updateDesktopMode(it); TabRegistry.current()?.applySettings() } }

            item { SectionLabel("Apariencia") }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(AppIcons.DarkMode, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(14.dp))
                    Text("Tema", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    // null = sistema, false = claro, true = oscuro
                    TextButton(onClick = {
                        BrowserSettings.updateDarkMode(
                            when (BrowserSettings.darkMode) {
                                null -> false
                                false -> true
                                else -> null
                            }
                        )
                    }) {
                        Text(
                            when (BrowserSettings.darkMode) {
                                null -> "Sistema"; false -> "Claro"; else -> "Oscuro"
                            }
                        )
                    }
                }
            }

            item { SectionLabel("Búsqueda") }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Search, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(14.dp))
                    Text("Motor", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Box {
                        TextButton(onClick = { engineOpen = true }) {
                            Text(engine.replaceFirstChar { it.uppercase() })
                        }
                        DropdownMenu(expanded = engineOpen, onDismissRequest = { engineOpen = false }) {
                            BrowserSettings.searchEngines.forEach { (id, _) ->
                                DropdownMenuItem(
                                    text = { Text(id.replaceFirstChar { it.uppercase() }) },
                                    onClick = { engine = id; BrowserSettings.updateSearchEngine(id); engineOpen = false },
                                )
                            }
                        }
                    }
                }
            }

            item { SectionLabel("Datos") }
            item {
                TextButton(
                    onClick = { BrowserSettings.clearBrowsingData(context) },
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    Icon(AppIcons.DeleteSweep, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                    Text("Borrar datos de navegación")
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun DrawerItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit,
) {
    NavigationDrawerItem(
        label = { Text(text) },
        icon = { Icon(icon, null, Modifier.size(22.dp)) },
        selected = false,
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text, style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 4.dp),
    )
}

@Composable
private fun ToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    checked: Boolean,
    subtitle: String?,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun YouTubeDialog(
    url: String,
    onDismiss: () -> Unit,
    onDownload: (nx.browser.youtube.StreamOption, String) -> Unit,
) {
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var meta by remember { mutableStateOf<nx.browser.youtube.VideoMeta?>(null) }
    var options by remember { mutableStateOf<List<nx.browser.youtube.StreamOption>>(emptyList()) }
    val isYouTube = nx.browser.youtube.YouTubeExtractor.parseId(url) != null

    LaunchedEffect(url) {
        loading = true
        if (isYouTube) {
            runCatching { YouTubeExtractor.extract(url) }
                .onSuccess { (m, o) -> meta = m; options = o; loading = false }
                .onFailure { error = it.message ?: "Error"; loading = false }
        } else {
            // Generic media: offer the URL as a direct download.
            val name = url.substringBefore('?').substringAfterLast('/').ifBlank { "media" }
            options = listOf(
                nx.browser.youtube.StreamOption(
                    quality = name,
                    url = url,
                    isAudio = false,
                    resolution = "media",
                )
            )
            loading = false
        }
    }

    val videos = options.filter { !it.isAudio }
    val audios = options.filter { it.isAudio }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(AppIcons.Download, null) },
        title = { Text(if (isYouTube) "Descargar video" else "Descargar archivo") },
        text = {
            when {
                loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp)); Spacer(Modifier.width(12.dp)); Text("Analizando…")
                }
                error != null -> Text("No se pudo extraer:\n$error")
                else -> Column {
                    Text(meta?.title ?: url.take(60), maxLines = 2, style = MaterialTheme.typography.titleSmall)
                    meta?.uploader?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        if (videos.isNotEmpty()) {
                            item { SectionLabel(if (isYouTube) "Video" else "Archivo") }
                            items(videos.size) { i ->
                                val o = videos[i]
                                ListItem(
                                    modifier = Modifier.clickable { onDownload(o, meta?.title ?: "video") },
                                    leadingContent = { Icon(AppIcons.Videocam, null) },
                                    headlineContent = { Text(o.quality) },
                                )
                            }
                        }
                        if (audios.isNotEmpty()) {
                            item { SectionLabel("Audio") }
                            items(audios.size) { i ->
                                val o = audios[i]
                                ListItem(
                                    modifier = Modifier.clickable { onDownload(o, meta?.title ?: "audio") },
                                    leadingContent = { Icon(AppIcons.MusicNote, null) },
                                    headlineContent = { Text(o.quality) },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}
