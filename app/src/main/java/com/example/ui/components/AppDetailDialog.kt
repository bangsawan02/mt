package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.EditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class DetailTab(val label: String) {
    OVERVIEW("Ringkasan"),
    COMPONENTS("Komponen"),
    PERMISSIONS("Izin"),
    ACTIONS("Tindakan")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailDialog(
    app: InstalledAppItem,
    isRootEnabled: Boolean,
    viewModel: EditorViewModel,
    onDismiss: () -> Unit,
    onAppStatusChanged: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedTab by remember { mutableStateOf(DetailTab.OVERVIEW) }
    var components by remember { mutableStateOf<List<AppComponentItem>>(emptyList()) }
    var permissions by remember { mutableStateOf<List<AppPermissionItem>>(emptyList()) }
    var signatureInfo by remember { mutableStateOf<AppSignatureInfo?>(null) }
    var cacheSizeBytes by remember { mutableStateOf<Long?>(null) }
    var isAppRunning by remember { mutableStateOf(app.isRunning) }
    var isLoadingDetails by remember { mutableStateOf(true) }

    var selectedComponentTypeFilter by remember { mutableStateOf<ComponentType?>(null) }
    var componentSearchQuery by remember { mutableStateOf("") }
    var isExecutingAction by remember { mutableStateOf(false) }

    // Load rich details on background thread
    LaunchedEffect(app.packageName) {
        isLoadingDetails = true
        withContext(Dispatchers.IO) {
            val cmps = AppManagerUtils.loadAppComponents(context, app.packageName)
            val perms = AppManagerUtils.loadAppPermissions(context, app.packageName)
            val sig = AppManagerUtils.loadAppSignature(context, app.packageName)
            val cSize = if (isRootEnabled) AppManagerUtils.getAppCacheSize(app.packageName, true) else null
            val runningSet = AppManagerUtils.getRunningPackages(context, isRootEnabled)
            withContext(Dispatchers.Main) {
                components = cmps
                permissions = perms
                signatureInfo = sig
                cacheSizeBytes = cSize
                isAppRunning = runningSet.contains(app.packageName)
                isLoadingDetails = false
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header ala 3C Toolbox
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (app.iconBitmap != null) {
                                Image(
                                    bitmap = app.iconBitmap.asImageBitmap(),
                                    contentDescription = app.appName,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFFE0E0E0)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Android, contentDescription = null, tint = Color(0xFF4CAF50))
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.appName,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = app.packageName,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 2.dp)
                                ) {
                                    BadgePill(
                                        text = if (app.isSystemApp) "SISTEM" else "PENGGUNA",
                                        bgColor = if (app.isSystemApp) Color(0xFFD32F2F) else Color(0xFF388E3C)
                                    )
                                    BadgePill(
                                        text = if (app.isEnabled) "AKTIF" else "BEKU (DISABLED)",
                                        bgColor = if (app.isEnabled) Color(0xFF1976D2) else Color(0xFFE64A19)
                                    )
                                    BadgePill(
                                        text = "UID ${app.uid}",
                                        bgColor = Color(0xFF5D4037)
                                    )
                                    BadgePill(
                                        text = if (isAppRunning) "BERJALAN" else "IDLE",
                                        bgColor = if (isAppRunning) Color(0xFF00897B) else Color(0xFF757575)
                                    )
                                }
                            }

                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "Tutup")
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Tab Row
                        PrimaryTabRow(
                            selectedTabIndex = selectedTab.ordinal,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            DetailTab.values().forEach { tab ->
                                Tab(
                                    selected = selectedTab == tab,
                                    onClick = { selectedTab = tab },
                                    text = {
                                        Text(
                                            text = tab.label,
                                            fontSize = 12.sp,
                                            fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                )
                            }
                        }
                    }
                }

                // Tab Content
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (isLoadingDetails) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Menganalisis komponen & metadata...", fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                    } else {
                        when (selectedTab) {
                            DetailTab.OVERVIEW -> OverviewTab(app, signatureInfo, isAppRunning, cacheSizeBytes, context)
                            DetailTab.COMPONENTS -> ComponentsTab(
                                components = components,
                                selectedType = selectedComponentTypeFilter,
                                searchQuery = componentSearchQuery,
                                isRootEnabled = isRootEnabled,
                                onStopService = { serviceName ->
                                    coroutineScope.launch {
                                        val res = withContext(Dispatchers.IO) {
                                            AppManagerUtils.stopSpecificService(app.packageName, serviceName, isRootEnabled)
                                        }
                                        Toast.makeText(context, res.second, Toast.LENGTH_SHORT).show()
                                        onAppStatusChanged()
                                    }
                                },
                                onTypeChange = { selectedComponentTypeFilter = it },
                                onSearchChange = { componentSearchQuery = it }
                            )
                            DetailTab.PERMISSIONS -> PermissionsTab(permissions)
                            DetailTab.ACTIONS -> ActionsTab(
                                app = app,
                                isRootEnabled = isRootEnabled,
                                isExecuting = isExecutingAction,
                                onExecute = { action ->
                                    coroutineScope.launch {
                                        isExecutingAction = true
                                        try {
                                            action()
                                        } finally {
                                            isExecutingAction = false
                                        }
                                    }
                                },
                                viewModel = viewModel,
                                context = context,
                                onDismiss = onDismiss,
                                onStatusChanged = onAppStatusChanged
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverviewTab(
    app: InstalledAppItem,
    signature: AppSignatureInfo?,
    isAppRunning: Boolean,
    cacheSizeBytes: Long?,
    context: Context
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            SectionHeader(title = "Spesifikasi Paket & Status")
        }
        item {
            InfoCard {
                InfoRow("Status Proses", if (isAppRunning) "Sedang Berjalan (Active)" else "Idle / Standby")
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = Color(0xFFF0F0F0))
                InfoRow("Versi Aplikasi", "${app.versionName} (Build ${app.versionCode})")
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = Color(0xFFF0F0F0))
                InfoRow("Target SDK", "Android ${getAndroidName(app.targetSdkVersion)} (API ${app.targetSdkVersion})")
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = Color(0xFFF0F0F0))
                InfoRow("Min SDK", if (app.minSdkVersion > 0) "Android ${getAndroidName(app.minSdkVersion)} (API ${app.minSdkVersion})" else "Tidak ditentukan")
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = Color(0xFFF0F0F0))
                InfoRow("Ukuran Total APK", AppManagerUtils.formatBytes(app.fileSize))
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = Color(0xFFF0F0F0))
                InfoRow("Ukuran Cache", if (cacheSizeBytes != null && cacheSizeBytes > 0) AppManagerUtils.formatBytes(cacheSizeBytes) else "0 B / Tidak tersedia")
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = Color(0xFFF0F0F0))
                InfoRow("Split APKs", if (app.splitApks.isEmpty()) "Single monolithic APK" else "${app.splitApks.size} Split APKs")
            }
        }

        item {
            SectionHeader(title = "Jalur Penyimpanan & Sistem")
        }
        item {
            InfoCard {
                InfoRow("APK Source Path", app.sourceDir, isMonospace = true)
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = Color(0xFFF0F0F0))
                InfoRow("Data Path", app.dataDir, isMonospace = true)
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = Color(0xFFF0F0F0))
                InfoRow("UID Sistem", "${app.uid}")
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = Color(0xFFF0F0F0))
                InfoRow("Pertama Dipasang", AppManagerUtils.formatDateTime(app.firstInstallTime))
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = Color(0xFFF0F0F0))
                InfoRow("Terakhir Diperbarui", AppManagerUtils.formatDateTime(app.lastUpdateTime))
            }
        }

        if (app.splitApks.isNotEmpty()) {
            item {
                SectionHeader(title = "Daftar Split APK (${app.splitApks.size})")
            }
            items(app.splitApks) { splitPath ->
                val fileName = splitPath.substringAfterLast('/')
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Extension, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(fileName, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }

        if (signature != null) {
            item {
                SectionHeader(title = "Sidik Jari Sertifikat Signing")
            }
            item {
                InfoCard {
                    SignRow("SHA-256", signature.sha256, context)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = Color(0xFFF0F0F0))
                    SignRow("SHA-1", signature.sha1, context)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = Color(0xFFF0F0F0))
                    SignRow("MD5", signature.md5, context)
                }
            }
        }
    }
}

@Composable
private fun ComponentsTab(
    components: List<AppComponentItem>,
    selectedType: ComponentType?,
    searchQuery: String,
    isRootEnabled: Boolean,
    onStopService: (String) -> Unit,
    onTypeChange: (ComponentType?) -> Unit,
    onSearchChange: (String) -> Unit
) {
    val filtered = remember(components, selectedType, searchQuery) {
        components.filter { c ->
            val matchType = selectedType == null || c.type == selectedType
            val matchQuery = searchQuery.isEmpty() || c.name.contains(searchQuery, ignoreCase = true)
            matchType && matchQuery
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Filter bar
        Column(modifier = Modifier.padding(12.dp)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("Cari komponen...", fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.fillMaxWidth().height(46.dp),
                singleLine = true,
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FilterChip(
                    selected = selectedType == null,
                    onClick = { onTypeChange(null) },
                    label = { Text("Semua (${components.size})", fontSize = 10.sp) }
                )
                FilterChip(
                    selected = selectedType == ComponentType.ACTIVITY,
                    onClick = { onTypeChange(if (selectedType == ComponentType.ACTIVITY) null else ComponentType.ACTIVITY) },
                    label = { Text("Acts (${components.count { it.type == ComponentType.ACTIVITY }})", fontSize = 10.sp) }
                )
                FilterChip(
                    selected = selectedType == ComponentType.SERVICE,
                    onClick = { onTypeChange(if (selectedType == ComponentType.SERVICE) null else ComponentType.SERVICE) },
                    label = { Text("Servs (${components.count { it.type == ComponentType.SERVICE }})", fontSize = 10.sp) }
                )
                FilterChip(
                    selected = selectedType == ComponentType.RECEIVER,
                    onClick = { onTypeChange(if (selectedType == ComponentType.RECEIVER) null else ComponentType.RECEIVER) },
                    label = { Text("Rcvs (${components.count { it.type == ComponentType.RECEIVER }})", fontSize = 10.sp) }
                )
                FilterChip(
                    selected = selectedType == ComponentType.PROVIDER,
                    onClick = { onTypeChange(if (selectedType == ComponentType.PROVIDER) null else ComponentType.PROVIDER) },
                    label = { Text("Prvs (${components.count { it.type == ComponentType.PROVIDER }})", fontSize = 10.sp) }
                )
            }
        }

        if (filtered.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Tidak ada komponen yang cocok", color = Color.Gray, fontSize = 12.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filtered) { cmp ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    BadgePill(
                                        text = cmp.type.name,
                                        bgColor = when (cmp.type) {
                                            ComponentType.ACTIVITY -> Color(0xFF1976D2)
                                            ComponentType.SERVICE -> Color(0xFF7B1FA2)
                                            ComponentType.RECEIVER -> Color(0xFFE65100)
                                            ComponentType.PROVIDER -> Color(0xFF00796B)
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    if (cmp.isExported) {
                                        BadgePill(text = "EXPORTED", bgColor = Color(0xFF2E7D32))
                                    }
                                    if (!cmp.isEnabled) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        BadgePill(text = "DISABLED", bgColor = Color(0xFFC62828))
                                    }
                                }

                                if (cmp.type == ComponentType.SERVICE && isRootEnabled) {
                                    FilledTonalButton(
                                        onClick = { onStopService(cmp.name) },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                        modifier = Modifier.height(28.dp),
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = Color(0xFFFFEBEE),
                                            contentColor = Color(0xFFC62828)
                                        )
                                    ) {
                                        Icon(Icons.Default.StopCircle, contentDescription = "Hentikan Layanan", modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Stop", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = cmp.simpleName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = cmp.name,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (cmp.permission != null) {
                                Text(
                                    text = "Izin: ${cmp.permission}",
                                    fontSize = 10.sp,
                                    color = Color(0xFFD32F2F)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionsTab(permissions: List<AppPermissionItem>) {
    val grantedCount = permissions.count { it.isGranted }
    val dangerousCount = permissions.count { it.isDangerous }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                StatMetric("Total Izin", "${permissions.size}")
                StatMetric("Diberikan", "$grantedCount", Color(0xFF2E7D32))
                StatMetric("Berbahaya", "$dangerousCount", Color(0xFFC62828))
            }
        }

        if (permissions.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Aplikasi ini tidak meminta izin tambahan", color = Color.Gray, fontSize = 12.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(permissions) { perm ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (perm.isGranted) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                contentDescription = null,
                                tint = if (perm.isGranted) Color(0xFF2E7D32) else Color(0xFF9E9E9E),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = perm.simpleName,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp
                                    )
                                    if (perm.isDangerous) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        BadgePill(text = "DANGEROUS", bgColor = Color(0xFFC62828))
                                    }
                                }
                                Text(
                                    text = perm.name,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionsTab(
    app: InstalledAppItem,
    isRootEnabled: Boolean,
    isExecuting: Boolean,
    onExecute: (suspend () -> Unit) -> Unit,
    viewModel: EditorViewModel,
    context: Context,
    onDismiss: () -> Unit,
    onStatusChanged: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            SectionHeader(title = "Operasi Standar (Native SDK)")
        }

        item {
            ActionTile(
                icon = Icons.Default.StopCircle,
                title = "Hentikan Layanan Latar (Kill Background Services)",
                description = "Hentikan layanan latar belakang dan proses aplikasi via ActivityManager SDK",
                tint = Color(0xFFE53935),
                enabled = !isExecuting,
                onClick = {
                    onExecute {
                        withContext(Dispatchers.IO) {
                            val res = AppManagerUtils.killRunningServices(context, app.packageName, isRootEnabled)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, res.second, Toast.LENGTH_SHORT).show()
                                onStatusChanged()
                            }
                        }
                    }
                }
            )
        }

        item {
            ActionTile(
                icon = Icons.Default.CleaningServices,
                title = "Bersihkan Cache Aplikasi (Clear Cache)",
                description = "Hapus berkas cache dan direktori temporary tanpa menghapus data / akun",
                tint = Color(0xFFFB8C00),
                enabled = !isExecuting,
                onClick = {
                    onExecute {
                        withContext(Dispatchers.IO) {
                            val res = AppManagerUtils.clearAppCache(context, app.packageName, isRootEnabled)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, res.second, Toast.LENGTH_LONG).show()
                                onStatusChanged()
                            }
                        }
                    }
                }
            )
        }

        item {
            ActionTile(
                icon = Icons.Default.PlayArrow,
                title = "Jalankan Aplikasi",
                description = "Buka activity utama aplikasi ini",
                tint = Color(0xFF1976D2),
                enabled = !isExecuting && app.isEnabled,
                onClick = {
                    val launched = AppManagerUtils.launchApp(context, app.packageName)
                    if (!launched) {
                        Toast.makeText(context, "Aplikasi tidak memiliki launcher intent", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        item {
            ActionTile(
                icon = Icons.Default.Download,
                title = "Ekstrak APK / App Bundle",
                description = "Salin berkas APK lengkap ke folder Download/3C_Backups",
                tint = Color(0xFF2E7D32),
                enabled = !isExecuting,
                onClick = {
                    onExecute {
                        withContext(Dispatchers.IO) {
                            val (success, path) = AppManagerUtils.extractAppApk(context, app, isRootEnabled)
                            withContext(Dispatchers.Main) {
                                if (success) {
                                    Toast.makeText(context, "APK diekstrak ke:\n$path", Toast.LENGTH_LONG).show()
                                    viewModel.refreshAll()
                                } else {
                                    Toast.makeText(context, "Gagal ekstrak: $path", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                }
            )
        }

        item {
            ActionTile(
                icon = Icons.Default.Share,
                title = "Bagikan APK",
                description = "Kirim APK ke aplikasi lain via Android Share Sheet",
                tint = Color(0xFFF57C00),
                enabled = !isExecuting,
                onClick = {
                    try {
                        AppManagerUtils.shareApk(context, app)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Gagal membagikan: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        item {
            ActionTile(
                icon = Icons.Default.Search,
                title = "Bedah APK (Inspector)",
                description = "Analisis isi DEX, Manifest XML, resources & certificates",
                tint = MaterialTheme.colorScheme.primary,
                enabled = !isExecuting,
                onClick = {
                    onDismiss()
                    viewModel.openApkInspector(app.sourceDir)
                }
            )
        }

        item {
            ActionTile(
                icon = Icons.Default.Settings,
                title = "Info Aplikasi Sistem",
                description = "Buka manajemen aplikasi bawaan sistem Android",
                tint = Color(0xFF607D8B),
                enabled = !isExecuting,
                onClick = {
                    AppManagerUtils.openAppDetailsSettings(context, app.packageName)
                }
            )
        }

        item {
            ActionTile(
                icon = Icons.Default.Shop,
                title = "Buka di Google Play Store",
                description = "Kunjungi halaman resmi aplikasi di store",
                tint = Color(0xFF0097A7),
                enabled = !isExecuting,
                onClick = {
                    AppManagerUtils.openInPlayStore(context, app.packageName)
                }
            )
        }

        item {
            SectionHeader(title = "Operasi Tingkat Lanjut & Root (3C Toolbox)")
        }

        item {
            ActionTile(
                icon = Icons.Default.Stop,
                title = "Hentikan Paksa (Force Stop)",
                description = "Hentikan proses background aplikasi seketika (am force-stop)",
                tint = Color(0xFFD32F2F),
                enabled = !isExecuting && isRootEnabled,
                onClick = {
                    onExecute {
                        withContext(Dispatchers.IO) {
                            val res = AppManagerUtils.forceStopApp(app.packageName, isRootEnabled)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, res.second, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            )
        }

        item {
            val isCurrentlyFrozen = !app.isEnabled
            ActionTile(
                icon = if (isCurrentlyFrozen) Icons.Default.LockOpen else Icons.Default.AcUnit,
                title = if (isCurrentlyFrozen) "Aktifkan Kembali (Defrost App)" else "Bekukan Aplikasi (Freeze / Disable)",
                description = if (isCurrentlyFrozen) "Aktifkan kembali aplikasi yang dibekukan (pm enable)" else "Nonaktifkan aplikasi dari sistem tanpa mencopot (pm disable)",
                tint = if (isCurrentlyFrozen) Color(0xFF388E3C) else Color(0xFF0288D1),
                enabled = !isExecuting && isRootEnabled,
                onClick = {
                    onExecute {
                        withContext(Dispatchers.IO) {
                            val res = if (isCurrentlyFrozen) {
                                AppManagerUtils.unfreezeApp(app.packageName, isRootEnabled)
                            } else {
                                AppManagerUtils.freezeApp(app.packageName, isRootEnabled)
                            }
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, res.second, Toast.LENGTH_SHORT).show()
                                onStatusChanged()
                            }
                        }
                    }
                }
            )
        }

        item {
            ActionTile(
                icon = Icons.Default.CleaningServices,
                title = "Bersihkan Data & Cache",
                description = "Hapus total direktori data aplikasi (pm clear)",
                tint = Color(0xFF795548),
                enabled = !isExecuting && isRootEnabled,
                onClick = {
                    onExecute {
                        withContext(Dispatchers.IO) {
                            val res = AppManagerUtils.clearAppData(app.packageName, isRootEnabled)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, res.second, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            )
        }

        item {
            ActionTile(
                icon = Icons.Default.DeleteForever,
                title = "Copot Pemasangan (Uninstall)",
                description = if (isRootEnabled) "Copot bersih aplikasi langsung via Root" else "Buka dialog uninstaller Android",
                tint = Color(0xFFB71C1C),
                enabled = !isExecuting,
                onClick = {
                    val res = AppManagerUtils.uninstallApp(context, app.packageName, isRootEnabled)
                    Toast.makeText(context, res.second, Toast.LENGTH_SHORT).show()
                    if (isRootEnabled && res.first) {
                        onDismiss()
                        onStatusChanged()
                    }
                }
            )
        }
    }
}

@Composable
private fun ActionTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    tint: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        color = if (enabled) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = if (enabled) 0.15f else 0.05f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) tint else Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else Color.Gray
                )
                Text(
                    text = description,
                    fontSize = 11.sp,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else Color.LightGray
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
private fun InfoCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            content = content
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String, isMonospace: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = if (isMonospace) FontFamily.Monospace else FontFamily.Default,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SignRow(algo: String, hash: String, context: Context) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(algo, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            IconButton(
                onClick = {
                    val clip = ClipData.newPlainText(algo, hash)
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
                    Toast.makeText(context, "$algo disalin ke clipboard", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Salin", modifier = Modifier.size(14.dp))
            }
        }
        Text(
            text = hash,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StatMetric(label: String, value: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun BadgePill(text: String, bgColor: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

private fun getAndroidName(apiLevel: Int): String {
    return when (apiLevel) {
        34 -> "14 (UpsideDownCake)"
        33 -> "13 (Tiramisu)"
        32, 31 -> "12 (SnowCone)"
        30 -> "11 (RedVelvetCake)"
        29 -> "10 (Q)"
        28 -> "9.0 (Pie)"
        27, 26 -> "8.0/8.1 (Oreo)"
        25, 24 -> "7.0/7.1 (Nougat)"
        23 -> "6.0 (Marshmallow)"
        22, 21 -> "5.0/5.1 (Lollipop)"
        else -> "API $apiLevel"
    }
}
