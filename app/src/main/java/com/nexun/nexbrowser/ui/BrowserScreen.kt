package com.nexun.nexbrowser.ui

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.nexun.nexbrowser.adblock.AdblockEngine
import com.nexun.nexbrowser.adblock.FilterListRepo
import com.nexun.nexbrowser.browser.BrowserSettings
import com.nexun.nexbrowser.browser.TabRegistry
import com.nexun.nexbrowser.download.DownloadEntry
import com.nexun.nexbrowser.download.DownloadsRegistry
import com.nexun.nexbrowser.util.Url
import com.nexun.nexbrowser.youtube.YouTubeExtractor
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

    var editing by remember { mutableStateOf(false) }
    var omnibox by remember { mutableStateOf("") }
    var showTabs by remember { mutableStateOf(false) }
    var showDownloads by remember { mutableStateOf(false) }
    var showYt by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf<String?>(null) }

    val drawerState = rememberDrawerState(DrawerValue.Closed)

    val secure = Url.isSecure(tab.url)

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SettingsDrawer(
                context = context,
                onDismiss = { scope.launch { drawerState.close() } },
                onOpenDownloads = {
                    scope.launch { drawerState.close() }
                    showDownloads = true
                },
                onOpenTabs = {
                    scope.launch { drawerState.close() }
                    showTabs = true
                },
                onNewTab = {
                    scope.launch { drawerState.close() }
                    TabRegistry.openTab()
                },
                onToggleIncognito = {
                    scope.launch { drawerState.close() }
                    TabRegistry.setIncognito(!incognito)
                },
                onOpenExternal = { onOpenExternal(tab.url) },
            )
        },
    ) {
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
                            onMenu = { scope.launch { drawerState.open() } },
                        )
                        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                    }
                }
            },
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                AndroidView(
                    factory = { ctx -> tab.ensureWebView(ctx, incognito) as WebView },
                    update = { wv -> (wv.parent as? ViewGroup)?.removeView(wv) },
                    modifier = Modifier.fillMaxSize(),
                )
                // Floating "download this video" button, Brave/YT-Downloader style.
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
            DownloadsRegistry.add(DownloadEntry(name, "Descargando", label))
            scope.launch {
                runCatching { YouTubeExtractor.download(context, opt, name) }
                    .onSuccess { DownloadsRegistry.update(name, "Completado", it.absolutePath) }
                    .onFailure { DownloadsRegistry.update(name, "Error", it.message ?: "") }
            }
            showYt = false
        },
    )
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
    val items = DownloadsRegistry.items
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(AppIcons.Download, null) },
        title = { Text("Descargas") },
        text = {
            if (items.isEmpty()) Text("Aún no hay descargas.")
            else LazyColumn(Modifier.heightIn(max = 400.dp)) {
                items(items.size) { i ->
                    val d = items[i]
                    ListItem(
                        headlineContent = { Text(d.name.take(50)) },
                        supportingContent = { Text("${d.state} · ${d.detail.take(40)}") },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}

/** Left-side settings drawer: replaces the old modal settings dialog. */
@Composable
private fun SettingsDrawer(
    context: Context,
    onDismiss: () -> Unit,
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
    val incognito by TabRegistry.incognito

    ModalDrawerSheet(Modifier.widthIn(max = 320.dp)) {
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Column(Modifier.padding(20.dp)) {
                    Icon(AppIcons.Compas, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Text("NexBrowser", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Bloqueados: ${AdblockEngine.blockedCount} anuncios",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
                ToggleRow(AppIcons.Block, "Bloqueo agresivo de anuncios", adblock, "Brave-style: red + cosmético + scriptlets") {
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
                    TextButton(onClick = {
                        BrowserSettings.updateDarkMode(
                            when (BrowserSettings.darkMode) {
                                true -> false
                                false -> null
                                else -> true
                            }
                        )
                    }) {
                        Text(
                            when (BrowserSettings.darkMode) {
                                true -> "Claro"; false -> "Sistema"; else -> "Oscuro"
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
    onDownload: (com.nexun.nexbrowser.youtube.StreamOption, String) -> Unit,
) {
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var meta by remember { mutableStateOf<com.nexun.nexbrowser.youtube.VideoMeta?>(null) }
    var options by remember { mutableStateOf<List<com.nexun.nexbrowser.youtube.StreamOption>>(emptyList()) }

    LaunchedEffect(url) {
        loading = true
        runCatching { YouTubeExtractor.extract(url) }
            .onSuccess { (m, o) -> meta = m; options = o; loading = false }
            .onFailure { error = it.message ?: "Error"; loading = false }
    }

    val videos = options.filter { !it.isAudio }
    val audios = options.filter { it.isAudio }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(AppIcons.Download, null) },
        title = { Text("Descargar video") },
        text = {
            when {
                loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp)); Spacer(Modifier.width(12.dp)); Text("Analizando video…")
                }
                error != null -> Text("No se pudo extraer:\n$error")
                else -> Column {
                    Text(meta?.title ?: "", maxLines = 2, style = MaterialTheme.typography.titleSmall)
                    Text(meta?.uploader ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        if (videos.isNotEmpty()) {
                            item { SectionLabel("Video") }
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
