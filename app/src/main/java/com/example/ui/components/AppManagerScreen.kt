package com.example.ui.components
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.EditorViewModel
import com.example.R
import com.example.RootUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

data class InstalledAppItem(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val sourceDir: String,
    val isSystemApp: Boolean,
    val icon: Drawable?,
    val fileSize: Long
)

enum class AppFilter {
    ALL, USER, SYSTEM
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppManagerScreen(
    viewModel: EditorViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isRootEnabled by viewModel.isRootEnabled.collectAsStateWithLifecycle()

    var appList by remember { mutableStateOf<List<InstalledAppItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var filterType by remember { mutableStateOf(AppFilter.USER) }
    var selectedAppForDetails by remember { mutableStateOf<InstalledAppItem?>(null) }
    var isExtracting by remember { mutableStateOf(false) }

    // Load installed apps
    LaunchedEffect(Unit) {
        isLoading = true
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val packages = pm.getInstalledPackages(PackageManager.GET_META_DATA)
            val items = packages.mapNotNull { pkg ->
                try {
                    val appInfo = pkg.applicationInfo ?: return@mapNotNull null
                    val appName = pm.getApplicationLabel(appInfo).toString()
                    val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    val sourceApk = appInfo.sourceDir
                    val file = File(sourceApk)
                    val size = if (file.exists()) file.length() else 0L

                    InstalledAppItem(
                        appName = appName,
                        packageName = pkg.packageName,
                        versionName = pkg.versionName ?: "1.0",
                        versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                            pkg.longVersionCode
                        } else {
                            @Suppress("DEPRECATION")
                            pkg.versionCode.toLong()
                        },
                        sourceDir = sourceApk,
                        isSystemApp = isSystem,
                        icon = try { pm.getApplicationIcon(appInfo) } catch (_: Exception) { null },
                        fileSize = size
                    )
                } catch (e: Exception) {
                    null
                }
            }.sortedBy { it.appName.lowercase() }

            withContext(Dispatchers.Main) {
                appList = items
                isLoading = false
            }
        }
    }

    val filteredApps = remember(appList, searchQuery, filterType) {
        appList.filter { app ->
            val matchesFilter = when (filterType) {
                AppFilter.ALL -> true
                AppFilter.USER -> !app.isSystemApp
                AppFilter.SYSTEM -> app.isSystemApp
            }
            val matchesQuery = searchQuery.isEmpty() ||
                    app.appName.contains(searchQuery, ignoreCase = true) ||
                    app.packageName.contains(searchQuery, ignoreCase = true)
            matchesFilter && matchesQuery
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Manajer Aplikasi (App Extractor)",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Total: ${filteredApps.size} Aplikasi",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateToExplorer() }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
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
            // Search Bar & Filter Chips
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Cari nama aplikasi atau paket...", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Hapus")
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = filterType == AppFilter.USER,
                            onClick = { filterType = AppFilter.USER },
                            label = { Text("Aplikasi Pengguna", fontSize = 11.sp) },
                            leadingIcon = { if (filterType == AppFilter.USER) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) else null }
                        )
                        FilterChip(
                            selected = filterType == AppFilter.SYSTEM,
                            onClick = { filterType = AppFilter.SYSTEM },
                            label = { Text("Sistem", fontSize = 11.sp) },
                            leadingIcon = { if (filterType == AppFilter.SYSTEM) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) else null }
                        )
                        FilterChip(
                            selected = filterType == AppFilter.ALL,
                            onClick = { filterType = AppFilter.ALL },
                            label = { Text("Semua", fontSize = 11.sp) },
                            leadingIcon = { if (filterType == AppFilter.ALL) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) else null }
                        )
                    }
                }
            }

            if (isLoading) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Memindai aplikasi terpasang...", color = Color.Gray, fontSize = 13.sp)
                    }
                }
            } else if (filteredApps.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Tidak ada aplikasi yang cocok", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        AppListItem(
                            app = app,
                            onClick = { selectedAppForDetails = app },
                            onExtract = {
                                extractAppApk(app, isRootEnabled, viewModel, context)
                            },
                            onInspect = {
                                viewModel.openApkInspector(app.sourceDir)
                            }
                        )
                        HorizontalDivider(color = Color(0xFFEEEEEE))
                    }
                }
            }
        }
    }

    // App Detail Dialog
    if (selectedAppForDetails != null) {
        val app = selectedAppForDetails!!
        AlertDialog(
            onDismissRequest = { selectedAppForDetails = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (app.icon != null) {
                        Image(
                            bitmap = app.icon.toBitmap(48, 48).asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                    Column {
                        Text(
                            text = app.appName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (app.isSystemApp) "Aplikasi Sistem" else "Aplikasi Pengguna",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (app.isSystemApp) Color(0xFFD32F2F) else Color(0xFF388E3C)
                        )
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Package: ${app.packageName}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("Versi: ${app.versionName} (${app.versionCode})", fontSize = 12.sp)
                    Text("Ukuran APK: ${formatAppSize(app.fileSize)}", fontSize = 12.sp)
                    Text("Path Asli: ${app.sourceDir}", fontSize = 11.sp, color = Color.DarkGray)
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val pkg = app.packageName
                            selectedAppForDetails = null
                            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:$pkg")
                            }
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Gagal membuka info aplikasi", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("Info Sistem")
                    }

                    Button(
                        onClick = {
                            selectedAppForDetails = null
                            extractAppApk(app, isRootEnabled, viewModel, context)
                        }
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Ekstrak APK")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedAppForDetails = null }) {
                    Text("Tutup")
                }
            }
        )
    }
}

@Composable
private fun AppListItem(
    app: InstalledAppItem,
    onClick: () -> Unit,
    onExtract: () -> Unit,
    onInspect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (app.icon != null) {
            Image(
                bitmap = app.icon.toBitmap(48, 48).asImageBitmap(),
                contentDescription = app.appName,
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(8.dp))
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
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color.Black),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF616161), fontSize = 11.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "v${app.versionName} • ${formatAppSize(app.fileSize)}",
                style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF9E9E9E), fontSize = 10.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // Inspect Button
            IconButton(onClick = onInspect, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Search, contentDescription = "Bedah APK", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }

            // Extract Button
            IconButton(onClick = onExtract, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.FileDownload, contentDescription = "Ekstrak APK", tint = Color(0xFF2E7D32), modifier = Modifier.size(18.dp))
            }
        }
    }
}

private fun extractAppApk(
    app: InstalledAppItem,
    isRoot: Boolean,
    viewModel: EditorViewModel,
    context: Context
) {
    val targetDir = File(android.os.Environment.getExternalStorageDirectory(), "Download")
    if (!targetDir.exists()) targetDir.mkdirs()

    val sanitizedName = app.appName.replace("[^a-zA-Z0-9_.-]".toRegex(), "_")
    val destFile = File(targetDir, "${sanitizedName}_v${app.versionName}.apk")

    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
        try {
            val srcFile = File(app.sourceDir)
            if (srcFile.canRead()) {
                FileInputStream(srcFile).use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
            } else if (isRoot) {
                val cmd = "cp \"${app.sourceDir}\" \"${destFile.absolutePath}\" && chmod 666 \"${destFile.absolutePath}\""
                val res = RootUtils.executeCommand(cmd, true)
                if (!res.success) throw Exception(res.error)
            } else {
                throw Exception("Tidak memiliki izin membaca file APK sistem")
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(context, "APK berhasil diekstrak ke:\n${destFile.absolutePath}", Toast.LENGTH_LONG).show()
                viewModel.refreshAll()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Gagal mengekstrak APK: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}

private fun formatAppSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val mb = bytes / (1024.0 * 1024.0)
    return String.format(java.util.Locale.US, "%.1f MB", mb)
}
