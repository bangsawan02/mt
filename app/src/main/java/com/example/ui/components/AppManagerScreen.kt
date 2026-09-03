package com.example.ui.components

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.EditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 3C Toolbox-style Application Manager Screen.
 * Provides rich package telemetry, components inspection, batch backup, and system controls.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppManagerScreen(
    viewModel: EditorViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isRootEnabled by viewModel.isRootEnabled.collectAsStateWithLifecycle()

    var allApps by remember { mutableStateOf<List<InstalledAppItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var filterType by remember { mutableStateOf(AppFilter.USER) }
    var sortOrder by remember { mutableStateOf(AppSort.NAME_ASC) }

    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedPackages = remember { mutableStateListOf<String>() }

    var selectedAppForDetails by remember { mutableStateOf<InstalledAppItem?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }

    // Batch progress state
    var batchProgressMessage by remember { mutableStateOf<String?>(null) }

    // Load installed apps
    fun reloadApps() {
        isLoading = true
        coroutineScope.launch(Dispatchers.IO) {
            val apps = AppManagerUtils.loadInstalledApps(context)
            withContext(Dispatchers.Main) {
                allApps = apps
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        reloadApps()
    }

    // Intercept back button if in multi-select mode
    BackHandler(enabled = isSelectionMode) {
        isSelectionMode = false
        selectedPackages.clear()
    }

    // Computed Stats
    val stats = remember(allApps) {
        var users = 0
        var systems = 0
        var disabled = 0
        var running = 0
        var totalBytes = 0L
        for (app in allApps) {
            if (app.isSystemApp) systems++ else users++
            if (!app.isEnabled) disabled++
            if (app.isRunning) running++
            totalBytes += app.fileSize
        }
        AppStats(
            totalCount = allApps.size,
            userCount = users,
            systemCount = systems,
            disabledCount = disabled,
            runningCount = running,
            totalStorageBytes = totalBytes
        )
    }

    // Filter & Sort
    val displayedApps = remember(allApps, searchQuery, filterType, sortOrder) {
        val filtered = allApps.filter { app ->
            val matchFilter = when (filterType) {
                AppFilter.ALL -> true
                AppFilter.USER -> !app.isSystemApp
                AppFilter.SYSTEM -> app.isSystemApp
                AppFilter.DISABLED -> !app.isEnabled
                AppFilter.RUNNING -> app.isRunning
            }
            val matchQuery = searchQuery.isEmpty() ||
                    app.appName.contains(searchQuery, ignoreCase = true) ||
                    app.packageName.contains(searchQuery, ignoreCase = true)
            matchFilter && matchQuery
        }

        when (sortOrder) {
            AppSort.NAME_ASC -> filtered.sortedBy { it.appName.lowercase() }
            AppSort.NAME_DESC -> filtered.sortedByDescending { it.appName.lowercase() }
            AppSort.SIZE_DESC -> filtered.sortedByDescending { it.fileSize }
            AppSort.SIZE_ASC -> filtered.sortedBy { it.fileSize }
            AppSort.UPDATE_TIME_DESC -> filtered.sortedByDescending { it.lastUpdateTime }
            AppSort.INSTALL_TIME_DESC -> filtered.sortedByDescending { it.firstInstallTime }
            AppSort.TARGET_SDK_DESC -> filtered.sortedByDescending { it.targetSdkVersion }
            AppSort.UID_ASC -> filtered.sortedBy { it.uid }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (isSelectionMode) "${selectedPackages.size} Terpilih" else "3C App Manager",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (isSelectionMode) "Operasi Massal Aktif" else "Menampilkan ${displayedApps.size} dari ${allApps.size} Aplikasi",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isSelectionMode) {
                            isSelectionMode = false
                            selectedPackages.clear()
                        } else {
                            viewModel.navigateToExplorer()
                        }
                    }) {
                        Icon(
                            imageVector = if (isSelectionMode) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali"
                        )
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        IconButton(onClick = {
                            if (selectedPackages.size == displayedApps.size) {
                                selectedPackages.clear()
                            } else {
                                selectedPackages.clear()
                                selectedPackages.addAll(displayedApps.map { it.packageName })
                            }
                        }) {
                            Icon(
                                imageVector = if (selectedPackages.size == displayedApps.size) Icons.Default.Deselect else Icons.Default.SelectAll,
                                contentDescription = "Pilih Semua"
                            )
                        }
                    } else {
                        // Multi select toggle
                        IconButton(onClick = { isSelectionMode = true }) {
                            Icon(Icons.Default.Checklist, contentDescription = "Pilih Banyak")
                        }
                        // Sort action
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Urutkan")
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                AppSort.values().forEach { sort ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (sortOrder == sort) {
                                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                }
                                                Text(sort.label, fontSize = 13.sp)
                                            }
                                        },
                                        onClick = {
                                            sortOrder = sort
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }
                        // Refresh action
                        IconButton(onClick = { reloadApps() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Muat Ulang")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFFFAFAFA))
        ) {
            // 1. Telemetry / Stats Header ala 3C Toolbox
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    // 1. Telemetry / Stats Header ala 3C Toolbox
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HeaderMetricPill("Pengguna", "${stats.userCount}", Color(0xFF2E7D32), onClick = { filterType = AppFilter.USER })
                        HeaderMetricPill("Sistem", "${stats.systemCount}", Color(0xFFC62828), onClick = { filterType = AppFilter.SYSTEM })
                        HeaderMetricPill("Berjalan", "${stats.runningCount}", Color(0xFF00897B), onClick = { filterType = AppFilter.RUNNING })
                        HeaderMetricPill("Nonaktif", "${stats.disabledCount}", Color(0xFFE65100), onClick = { filterType = AppFilter.DISABLED })
                        HeaderMetricPill("Total APK", AppManagerUtils.formatBytes(stats.totalStorageBytes), Color(0xFF1565C0), onClick = { filterType = AppFilter.ALL })
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 2. Search Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Cari nama aplikasi, paket, atau UID...", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Hapus", modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 3. Filter Chips ala 3C Toolbox
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = filterType == AppFilter.USER,
                            onClick = { filterType = AppFilter.USER },
                            label = { Text("Pengguna (${stats.userCount})", fontSize = 11.sp) },
                            leadingIcon = { if (filterType == AppFilter.USER) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) else null }
                        )
                        FilterChip(
                            selected = filterType == AppFilter.SYSTEM,
                            onClick = { filterType = AppFilter.SYSTEM },
                            label = { Text("Sistem (${stats.systemCount})", fontSize = 11.sp) },
                            leadingIcon = { if (filterType == AppFilter.SYSTEM) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) else null }
                        )
                        FilterChip(
                            selected = filterType == AppFilter.RUNNING,
                            onClick = { filterType = AppFilter.RUNNING },
                            label = { Text("Berjalan (${stats.runningCount})", fontSize = 11.sp) },
                            leadingIcon = { if (filterType == AppFilter.RUNNING) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) else null }
                        )
                        FilterChip(
                            selected = filterType == AppFilter.DISABLED,
                            onClick = { filterType = AppFilter.DISABLED },
                            label = { Text("Nonaktif (${stats.disabledCount})", fontSize = 11.sp) },
                            leadingIcon = { if (filterType == AppFilter.DISABLED) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) else null }
                        )
                        FilterChip(
                            selected = filterType == AppFilter.ALL,
                            onClick = { filterType = AppFilter.ALL },
                            label = { Text("Semua (${stats.totalCount})", fontSize = 11.sp) },
                            leadingIcon = { if (filterType == AppFilter.ALL) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) else null }
                        )
                    }
                }
            }

            // 4. Main App List
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Memindai paket dan dependensi sistem...", color = Color.Gray, fontSize = 13.sp)
                        }
                    }
                } else if (displayedApps.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Tidak ada aplikasi yang cocok dengan kriteria", color = Color.Gray, fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(displayedApps, key = { it.packageName }) { app ->
                            val isSelected = selectedPackages.contains(app.packageName)
                            ToolboxAppItem(
                                app = app,
                                isSelectionMode = isSelectionMode,
                                isSelected = isSelected,
                                onSelectToggle = {
                                    if (isSelected) selectedPackages.remove(app.packageName)
                                    else selectedPackages.add(app.packageName)
                                },
                                onClick = {
                                    if (isSelectionMode) {
                                        if (isSelected) selectedPackages.remove(app.packageName)
                                        else selectedPackages.add(app.packageName)
                                    } else {
                                        selectedAppForDetails = app
                                    }
                                },
                                onLongClick = {
                                    if (!isSelectionMode) {
                                        isSelectionMode = true
                                        selectedPackages.add(app.packageName)
                                    }
                                },
                                onQuickLaunch = {
                                    val ok = AppManagerUtils.launchApp(context, app.packageName)
                                    if (!ok) Toast.makeText(context, "Aplikasi tidak dapat dibuka secara langsung", Toast.LENGTH_SHORT).show()
                                },
                                onQuickExtract = {
                                    coroutineScope.launch(Dispatchers.IO) {
                                        val (success, path) = AppManagerUtils.extractAppApk(context, app, isRootEnabled)
                                        withContext(Dispatchers.Main) {
                                            if (success) {
                                                Toast.makeText(context, "APK diekstrak ke:\n$path", Toast.LENGTH_SHORT).show()
                                                viewModel.refreshAll()
                                            } else {
                                                Toast.makeText(context, "Gagal: $path", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                },
                                onQuickKillServices = if (app.isRunning) {
                                    {
                                        coroutineScope.launch(Dispatchers.IO) {
                                            val res = AppManagerUtils.killRunningServices(context, app.packageName, isRootEnabled)
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(context, res.second, Toast.LENGTH_SHORT).show()
                                                reloadApps()
                                            }
                                        }
                                    }
                                } else null,
                                onQuickClearCache = {
                                    coroutineScope.launch(Dispatchers.IO) {
                                        val res = AppManagerUtils.clearAppCache(context, app.packageName, isRootEnabled)
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, res.second, Toast.LENGTH_SHORT).show()
                                            reloadApps()
                                        }
                                    }
                                }
                            )
                            HorizontalDivider(color = Color(0xFFEEEEEE), thickness = 0.8.dp)
                        }
                    }
                }

                // 5. Floating Batch Operations Bar (Muncul saat multi-select aktif)
                androidx.compose.animation.AnimatedVisibility(
                    visible = isSelectionMode && selectedPackages.isNotEmpty(),
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                ) {
                    BatchActionBar(
                        selectedCount = selectedPackages.size,
                        isRootEnabled = isRootEnabled,
                        onBatchBackup = {
                            coroutineScope.launch(Dispatchers.IO) {
                                batchProgressMessage = "Mengekstrak ${selectedPackages.size} APK..."
                                val selectedApps = allApps.filter { selectedPackages.contains(it.packageName) }
                                var successCount = 0
                                for (target in selectedApps) {
                                    val (ok, _) = AppManagerUtils.extractAppApk(context, target, isRootEnabled)
                                    if (ok) successCount++
                                }
                                withContext(Dispatchers.Main) {
                                    batchProgressMessage = null
                                    Toast.makeText(context, "Berhasil mengekstrak $successCount dari ${selectedApps.size} APK ke folder Backups", Toast.LENGTH_LONG).show()
                                    isSelectionMode = false
                                    selectedPackages.clear()
                                    viewModel.refreshAll()
                                }
                            }
                        },
                        onBatchKillServices = {
                            coroutineScope.launch(Dispatchers.IO) {
                                batchProgressMessage = "Menghentikan layanan latar untuk ${selectedPackages.size} aplikasi..."
                                val pkgs = selectedPackages.toList()
                                val (_, msg) = AppManagerUtils.killBatchRunningServices(context, pkgs, isRootEnabled)
                                withContext(Dispatchers.Main) {
                                    batchProgressMessage = null
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    isSelectionMode = false
                                    selectedPackages.clear()
                                    reloadApps()
                                }
                            }
                        },
                        onBatchClearCache = {
                            coroutineScope.launch(Dispatchers.IO) {
                                batchProgressMessage = "Membersihkan cache untuk ${selectedPackages.size} aplikasi..."
                                val pkgs = selectedPackages.toList()
                                val (_, msg) = AppManagerUtils.clearBatchAppCache(context, pkgs, isRootEnabled)
                                withContext(Dispatchers.Main) {
                                    batchProgressMessage = null
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    isSelectionMode = false
                                    selectedPackages.clear()
                                    reloadApps()
                                }
                            }
                        },
                        onBatchFreeze = {
                            coroutineScope.launch(Dispatchers.IO) {
                                batchProgressMessage = "Membekukan aplikasi terpilih..."
                                val pkgs = selectedPackages.toList()
                                for (p in pkgs) {
                                    AppManagerUtils.freezeApp(p, isRootEnabled)
                                }
                                withContext(Dispatchers.Main) {
                                    batchProgressMessage = null
                                    Toast.makeText(context, "Selesai memproses pembekuan aplikasi", Toast.LENGTH_SHORT).show()
                                    isSelectionMode = false
                                    selectedPackages.clear()
                                    reloadApps()
                                }
                            }
                        },
                        onBatchUnfreeze = {
                            coroutineScope.launch(Dispatchers.IO) {
                                batchProgressMessage = "Mengaktifkan aplikasi terpilih..."
                                val pkgs = selectedPackages.toList()
                                for (p in pkgs) {
                                    AppManagerUtils.unfreezeApp(p, isRootEnabled)
                                }
                                withContext(Dispatchers.Main) {
                                    batchProgressMessage = null
                                    Toast.makeText(context, "Selesai mengaktifkan aplikasi", Toast.LENGTH_SHORT).show()
                                    isSelectionMode = false
                                    selectedPackages.clear()
                                    reloadApps()
                                }
                            }
                        },
                        onBatchUninstall = {
                            val count = selectedPackages.size
                            if (isRootEnabled) {
                                coroutineScope.launch(Dispatchers.IO) {
                                    batchProgressMessage = "Mencopot $count aplikasi via Root..."
                                    val pkgs = selectedPackages.toList()
                                    for (p in pkgs) {
                                        AppManagerUtils.uninstallApp(context, p, true)
                                    }
                                    withContext(Dispatchers.Main) {
                                        batchProgressMessage = null
                                        Toast.makeText(context, "Selesai mencopot $count aplikasi", Toast.LENGTH_SHORT).show()
                                        isSelectionMode = false
                                        selectedPackages.clear()
                                        reloadApps()
                                    }
                                }
                            } else {
                                Toast.makeText(context, "Pencopotan massal instan memerlukan akses Root", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onCancel = {
                            isSelectionMode = false
                            selectedPackages.clear()
                        }
                    )
                }
            }
        }
    }

    // App Detail Dialog 3C Toolbox
    if (selectedAppForDetails != null) {
        AppDetailDialog(
            app = selectedAppForDetails!!,
            isRootEnabled = isRootEnabled,
            viewModel = viewModel,
            onDismiss = { selectedAppForDetails = null },
            onAppStatusChanged = {
                reloadApps()
            }
        )
    }

    // Batch Progress Modal Dialog
    if (batchProgressMessage != null) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Memproses Operasi Massal", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(batchProgressMessage ?: "", fontSize = 13.sp)
                }
            },
            confirmButton = {}
        )
    }
}

@Composable
private fun ToolboxAppItem(
    app: InstalledAppItem,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onSelectToggle: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onQuickLaunch: () -> Unit,
    onQuickExtract: () -> Unit,
    onQuickKillServices: (() -> Unit)? = null,
    onQuickClearCache: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Multi-select Checkbox or Icon
        if (isSelectionMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onSelectToggle() },
                modifier = Modifier.padding(end = 8.dp)
            )
        }

        // App Icon
        if (app.iconBitmap != null) {
            Image(
                bitmap = app.iconBitmap.asImageBitmap(),
                contentDescription = app.appName,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFE0E0E0)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Android, contentDescription = null, tint = Color(0xFF4CAF50))
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Center Content: Name, Package, Technical Pills ala 3C Toolbox
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color.Black),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (app.splitApks.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xFFE1F5FE))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text("APKS", fontSize = 8.sp, color = Color(0xFF0288D1), fontWeight = FontWeight.Bold)
                    }
                }
            }

            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color(0xFF616161)),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Technical Specs Row (3C Toolbox signature look)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp)
            ) {
                Text(
                    text = "v${app.versionName}",
                    fontSize = 10.sp,
                    color = Color(0xFF757575),
                    fontWeight = FontWeight.Medium
                )
                Text("•", fontSize = 10.sp, color = Color.LightGray)
                Text(
                    text = AppManagerUtils.formatBytes(app.fileSize),
                    fontSize = 10.sp,
                    color = Color(0xFF1976D2),
                    fontWeight = FontWeight.SemiBold
                )
                Text("•", fontSize = 10.sp, color = Color.LightGray)
                Text(
                    text = "API ${app.targetSdkVersion}",
                    fontSize = 10.sp,
                    color = Color(0xFF388E3C),
                    fontWeight = FontWeight.Medium
                )
                if (app.isRunning) {
                    Text("•", fontSize = 10.sp, color = Color.LightGray)
                    Text(
                        text = "BERJALAN",
                        fontSize = 10.sp,
                        color = Color(0xFF00897B),
                        fontWeight = FontWeight.Bold
                    )
                }
                if (!app.isEnabled) {
                    Text("•", fontSize = 10.sp, color = Color.LightGray)
                    Text(
                        text = "BEKU",
                        fontSize = 10.sp,
                        color = Color(0xFFD32F2F),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Action Buttons
        if (!isSelectionMode) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (app.isRunning && onQuickKillServices != null) {
                    IconButton(onClick = onQuickKillServices, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.StopCircle,
                            contentDescription = "Hentikan Layanan Latar",
                            tint = Color(0xFFE53935),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                if (onQuickClearCache != null) {
                    IconButton(onClick = onQuickClearCache, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.CleaningServices,
                            contentDescription = "Bersihkan Cache",
                            tint = Color(0xFFFB8C00),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                if (app.isEnabled) {
                    IconButton(onClick = onQuickLaunch, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Buka",
                            tint = Color(0xFF1976D2),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                IconButton(onClick = onQuickExtract, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Ekstrak APK",
                        tint = Color(0xFF2E7D32),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderMetricPill(label: String, value: String, accentColor: Color, onClick: (() -> Unit)? = null) {
    Surface(
        color = accentColor.copy(alpha = 0.08f),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.2f)),
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = accentColor)
            Text(label, fontSize = 9.sp, color = Color.DarkGray)
        }
    }
}

@Composable
private fun BatchActionBar(
    selectedCount: Int,
    isRootEnabled: Boolean,
    onBatchBackup: () -> Unit,
    onBatchKillServices: () -> Unit,
    onBatchClearCache: () -> Unit,
    onBatchFreeze: () -> Unit,
    onBatchUnfreeze: () -> Unit,
    onBatchUninstall: () -> Unit,
    onCancel: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp),
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "$selectedCount Aplikasi",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Aksi Massal",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                // Batch Kill Running Services
                IconButton(onClick = onBatchKillServices) {
                    Icon(Icons.Default.StopCircle, contentDescription = "Hentikan Layanan Latar", tint = Color(0xFFE53935))
                }

                // Batch Clear Cache
                IconButton(onClick = onBatchClearCache) {
                    Icon(Icons.Default.CleaningServices, contentDescription = "Bersihkan Cache", tint = Color(0xFFFB8C00))
                }

                // Batch Backup
                IconButton(onClick = onBatchBackup) {
                    Icon(Icons.Default.Download, contentDescription = "Batch Backup", tint = Color(0xFF2E7D32))
                }

                if (isRootEnabled) {
                    // Batch Freeze
                    IconButton(onClick = onBatchFreeze) {
                        Icon(Icons.Default.AcUnit, contentDescription = "Batch Freeze", tint = Color(0xFF0288D1))
                    }
                    // Batch Defrost
                    IconButton(onClick = onBatchUnfreeze) {
                        Icon(Icons.Default.LockOpen, contentDescription = "Batch Defrost", tint = Color(0xFF388E3C))
                    }
                    // Batch Uninstall
                    IconButton(onClick = onBatchUninstall) {
                        Icon(Icons.Default.DeleteForever, contentDescription = "Batch Uninstall", tint = Color(0xFFD32F2F))
                    }
                }

                IconButton(onClick = onCancel) {
                    Icon(Icons.Default.Close, contentDescription = "Batal", tint = Color.Gray)
                }
            }
        }
    }
}
