package com.example.ui.components
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ArchiveEntryItem
import com.example.ArchiveProgressState
import com.example.EditorViewModel
import com.example.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveViewerScreen(
    archivePath: String,
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    val archiveFile = remember(archivePath) { File(archivePath) }

    val currentInternalPath by viewModel.archiveInternalPath.collectAsStateWithLifecycle()
    val entries by viewModel.archiveEntries.collectAsStateWithLifecycle()
    val isLoading by viewModel.archiveLoading.collectAsStateWithLifecycle()
    val errorMsg by viewModel.archiveErrorMessage.collectAsStateWithLifecycle()
    val progressState by viewModel.archiveProgressState.collectAsStateWithLifecycle()

    val previewEntryText by viewModel.archivePreviewText.collectAsStateWithLifecycle()
    val previewEntryName by viewModel.archivePreviewEntryName.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var showExtractAllDialog by remember { mutableStateOf(false) }
    var itemToExtract by remember { mutableStateOf<ArchiveEntryItem?>(null) }

    val oppositePath = viewModel.getOppositePanelPath()

    val filteredEntries = remember(entries, searchQuery) {
        if (searchQuery.isEmpty()) entries
        else entries.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    val displayEntries = remember(filteredEntries, currentInternalPath) {
        if (currentInternalPath.isNotEmpty()) {
            val parentItem = ArchiveEntryItem(
                name = "..",
                fullEntryPath = "",
                isDirectory = true,
                size = 0L,
                compressedSize = 0L,
                lastModified = 0L
            )
            listOf(parentItem) + filteredEntries
        } else {
            filteredEntries
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.FolderZip,
                                contentDescription = "Arsip",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = archiveFile.name,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = if (currentInternalPath.isEmpty()) "root /" else "root / $currentInternalPath",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (currentInternalPath.isNotEmpty()) {
                                viewModel.navigateArchiveUp()
                            } else {
                                viewModel.navigateToExplorer()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali"
                        )
                    }
                },
                actions = {
                    FilledTonalButton(
                        onClick = { showExtractAllDialog = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Unarchive,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Ekstrak Semua",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.White)
        ) {
            // Search / Filter Bar inside Archive
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Cari file di dalam arsip...", fontSize = 12.sp, color = Color.Gray) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "Hapus Pencarian")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .height(44.dp),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, color = Color.Black),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color(0xFFE0E0E0),
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color(0xFFFAFAFA)
                )
            )

            // Content Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (isLoading) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Membaca struktur arsip...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray
                        )
                    }
                } else if (errorMsg != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = errorMsg ?: "Terjadi kesalahan",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.reloadArchiveEntries() }) {
                            Text("Coba Lagi")
                        }
                    }
                } else if (displayEntries.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Folder arsip ini kosong",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(displayEntries) { item ->
                            ArchiveItemRow(
                                item = item,
                                onClick = {
                                    if (item.name == "..") {
                                        viewModel.navigateArchiveUp()
                                    } else if (item.isDirectory) {
                                        viewModel.navigateArchiveInternal(item.fullEntryPath)
                                    } else {
                                        // Cek apakah teks/kode untuk preview langsung
                                        if (isTextOrCodeEntry(item.name)) {
                                            viewModel.previewArchiveEntryText(archivePath, item.fullEntryPath)
                                        } else {
                                            itemToExtract = item
                                        }
                                    }
                                },
                                onExtractClick = {
                                    if (item.name != "..") {
                                        itemToExtract = item
                                    }
                                },
                                onPreviewClick = {
                                    if (!item.isDirectory && item.name != "..") {
                                        viewModel.previewArchiveEntryText(archivePath, item.fullEntryPath)
                                    }
                                }
                            )
                            HorizontalDivider(color = Color(0xFFEEEEEE), thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
    }

    // Extract All Dialog
    if (showExtractAllDialog) {
        var targetPathInput by remember { mutableStateOf(oppositePath.ifEmpty { archiveFile.parent ?: "/sdcard" }) }

        AlertDialog(
            onDismissRequest = { showExtractAllDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Unarchive, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Ekstrak Seluruh Arsip", style = MaterialTheme.typography.titleMedium)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Arsip: ${archiveFile.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.DarkGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Folder Tujuan Ekstraksi:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = targetPathInput,
                        onValueChange = { targetPathInput = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AssistChip(
                            onClick = { targetPathInput = oppositePath.ifEmpty { archiveFile.parent ?: "/sdcard" } },
                            label = { Text("Panel Sebelah", fontSize = 11.sp) }
                        )
                        AssistChip(
                            onClick = { targetPathInput = "${archiveFile.parent}/${archiveFile.nameWithoutExtension}" },
                            label = { Text("Folder Baru", fontSize = 11.sp) }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showExtractAllDialog = false
                        viewModel.extractArchiveAll(archivePath, targetPathInput)
                    }
                ) {
                    Text("Mulai Ekstrak")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExtractAllDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    // Extract Single Item Dialog
    if (itemToExtract != null) {
        val entryItem = itemToExtract!!
        var targetPathInput by remember { mutableStateOf(oppositePath.ifEmpty { archiveFile.parent ?: "/sdcard" }) }

        AlertDialog(
            onDismissRequest = { itemToExtract = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.FileDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (entryItem.isDirectory) "Ekstrak Folder" else "Ekstrak Berkas",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Item: ${entryItem.name}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Ukuran: ${formatArchiveSize(entryItem.size)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Folder Tujuan:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = targetPathInput,
                        onValueChange = { targetPathInput = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val path = entryItem.fullEntryPath
                        itemToExtract = null
                        viewModel.extractArchiveEntry(archivePath, path, targetPathInput)
                    }
                ) {
                    Text("Ekstrak")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToExtract = null }) {
                    Text("Batal")
                }
            }
        )
    }

    // Text Preview Dialog
    if (previewEntryText != null) {
        val textContent = previewEntryText!!
        val entryName = previewEntryName ?: "Berkas"

        AlertDialog(
            onDismissRequest = { viewModel.closeArchivePreview() },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = entryName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(textContent))
                        }
                    ) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Salin Teks")
                    }
                }
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 150.dp, max = 400.dp)
                        .background(Color(0xFF1E1E1E), shape = RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    val verticalScroll = rememberScrollState()
                    val horizontalScroll = rememberScrollState()
                    Text(
                        text = textContent,
                        color = Color(0xFFD4D4D4),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(verticalScroll)
                            .horizontalScroll(horizontalScroll)
                    )
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.closeArchivePreview() }) {
                    Text("Tutup")
                }
            }
        )
    }

    // Progress Dialog (Extraction / Compression)
    if (progressState != null && progressState!!.isRunning) {
        ArchiveProgressDialog(
            progress = progressState!!,
            onCancel = { viewModel.cancelArchiveOperation() }
        )
    }
}

@Composable
fun ArchiveProgressDialog(
    progress: ArchiveProgressState,
    onCancel: () -> Unit
) {
    if (!progress.isRunning) return

    AlertDialog(
        onDismissRequest = { /* Modal tidak bisa ditutup sembarangan saat proses */ },
        title = {
            Text(
                text = progress.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = progress.currentFile,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.DarkGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { progress.percent },
                    modifier = Modifier.fillMaxWidth().height(8.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${progress.currentCount} / ${progress.totalCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                    Text(
                        text = "${(progress.percent * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onCancel,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Batalkan")
            }
        }
    )
}

@Composable
private fun ArchiveItemRow(
    item: ArchiveEntryItem,
    onClick: () -> Unit,
    onExtractClick: () -> Unit,
    onPreviewClick: () -> Unit
) {
    val isText = remember(item.name) { isTextOrCodeEntry(item.name) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            val iconTint = when {
                item.name == ".." -> Color(0xFF2C2C2C)
                item.isDirectory -> Color(0xFF1976D2)
                isText -> Color(0xFF4CAF50)
                item.name.lowercase().endsWith(".png") || item.name.lowercase().endsWith(".jpg") || item.name.lowercase().endsWith(".webp") -> Color(0xFFFF9800)
                item.name.lowercase().endsWith(".dex") -> Color(0xFF9C27B0)
                else -> Color(0xFF607D8B)
            }

            Icon(
                imageVector = when {
                    item.name == ".." || item.isDirectory -> Icons.Default.Folder
                    isText -> Icons.Default.Code
                    item.name.lowercase().endsWith(".dex") -> Icons.Default.Memory
                    else -> Icons.AutoMirrored.Filled.InsertDriveFile
                },
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            // Details Column
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color.Black,
                        fontWeight = if (item.isDirectory) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 13.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (item.name != "..") {
                    val subtext = if (item.isDirectory) {
                        "${formatDate(item.lastModified)}   ${item.childCount} item"
                    } else {
                        val ratio = if (item.size > 0) {
                            val r = ((1.0 - (item.compressedSize.toDouble() / item.size.toDouble())) * 100.0).toInt()
                            if (r >= 0) " (${r}%)" else ""
                        } else ""
                        "${formatDate(item.lastModified)}   ${formatArchiveSize(item.size)}$ratio"
                    }

                    Text(
                        text = subtext,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF757575),
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Action buttons
            if (item.name != "..") {
                if (!item.isDirectory && isText) {
                    IconButton(
                        onClick = onPreviewClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = "Pratinjau Teks",
                            tint = Color(0xFF1976D2),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onExtractClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = "Ekstrak",
                        tint = Color(0xFF555555),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

private fun isTextOrCodeEntry(name: String): Boolean {
    val lower = name.lowercase()
    return lower.endsWith(".txt") ||
            lower.endsWith(".xml") ||
            lower.endsWith(".json") ||
            lower.endsWith(".md") ||
            lower.endsWith(".prop") ||
            lower.endsWith(".properties") ||
            lower.endsWith(".smali") ||
            lower.endsWith(".java") ||
            lower.endsWith(".kt") ||
            lower.endsWith(".html") ||
            lower.endsWith(".css") ||
            lower.endsWith(".js") ||
            lower.endsWith(".sh") ||
            lower.endsWith(".rc") ||
            lower.endsWith(".cfg") ||
            lower.endsWith(".ini") ||
            lower.endsWith(".mf") ||
            lower.endsWith(".sf")
}

private fun formatArchiveSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    if (digitGroups < 0 || digitGroups >= units.size) return "$size B"
    return String.format(Locale.US, "%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

private fun formatDate(timestamp: Long): String {
    if (timestamp <= 0) return "09-01-01 07:00"
    return try {
        val sdf = SimpleDateFormat("yy-MM-dd HH:mm", Locale.US)
        sdf.format(Date(timestamp))
    } catch (e: Exception) {
        "09-01-01 07:00"
    }
}
