package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import com.example.CommonUtils
import com.example.EditorViewModel
import com.example.R
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Data classes and utility methods moved to StorageManagerUtils.kt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NativeStorageBrowserScreen(
    viewModel: EditorViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 1. Detect Storage Volumes using StorageManager
    var storageVolumes by remember { mutableStateOf<List<StorageVolumeItem>>(emptyList()) }
    var selectedVolume by remember { mutableStateOf<StorageVolumeItem?>(null) }
    var showVolumePickerSheet by remember { mutableStateOf(false) }

    fun refreshStorageVolumes() {
        val detected = queryStorageVolumes(context)
        storageVolumes = detected
        if (selectedVolume == null || !detected.any { it.id == selectedVolume?.id }) {
            selectedVolume = detected.firstOrNull { it.isPrimary } ?: detected.firstOrNull()
        }
    }

    LaunchedEffect(Unit) {
        refreshStorageVolumes()
    }

    // 2. Navigation State for DocumentFile
    var breadcrumbs by remember { mutableStateOf<List<DocumentBreadcrumb>>(emptyList()) }
    val currentDoc = breadcrumbs.lastOrNull()?.doc

    var items by remember { mutableStateOf<List<DocumentItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // 3. Search, Filter, and Sort states
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(DocFilterCategory.ALL) }
    var sortMode by remember { mutableStateOf(DocSortMode.NAME_ASC) }
    var showSortMenu by remember { mutableStateOf(false) }

    // 4. Action Dialog States
    var showCreateDialog by remember { mutableStateOf(false) }
    var createIsFolder by remember { mutableStateOf(true) }
    var newItemName by remember { mutableStateOf("") }

    var itemToRename by remember { mutableStateOf<DocumentItem?>(null) }
    var renameNewName by remember { mutableStateOf("") }

    var itemToDelete by remember { mutableStateOf<DocumentItem?>(null) }
    var itemForDetails by remember { mutableStateOf<DocumentItem?>(null) }

    // Load items for the current DocumentFile
    fun loadCurrentDirectory() {
        val targetDoc = currentDoc ?: return
        isLoading = true
        errorMessage = null

        coroutineScope.launch {
            try {
                val list = withContext(Dispatchers.IO) {
                    val rawFiles = targetDoc.listFiles()
                    rawFiles.map { docFile ->
                        val name = docFile.name ?: "Tanpa Nama"
                        val isDir = docFile.isDirectory
                        val isF = docFile.isFile
                        val size = if (isF) docFile.length() else 0L
                        val lastMod = docFile.lastModified()
                        val mime = docFile.type ?: ""
                        val canR = docFile.canRead()
                        val canW = docFile.canWrite()
                        val uri = docFile.uri

                        DocumentItem(
                            doc = docFile,
                            name = name,
                            isDirectory = isDir,
                            isFile = isF,
                            size = size,
                            lastModified = lastMod,
                            mimeType = mime,
                            canRead = canR,
                            canWrite = canW,
                            uri = uri
                        )
                    }
                }
                items = list
                isLoading = false
            } catch (e: Exception) {
                errorMessage = "Gagal memuat direktori: ${e.localizedMessage}"
                isLoading = false
            }
        }
    }

    // Set root when volume is selected or changes
    LaunchedEffect(selectedVolume) {
        val vol = selectedVolume
        if (vol != null && vol.rootFile != null) {
            val rootDoc = DocumentFile.fromFile(vol.rootFile)
            breadcrumbs = listOf(DocumentBreadcrumb(vol.title, rootDoc))
            loadCurrentDirectory()
        }
    }

    // Reload when breadcrumbs change
    LaunchedEffect(breadcrumbs.size) {
        if (breadcrumbs.isNotEmpty()) {
            loadCurrentDirectory()
        }
    }

    // SAF Document Tree Launcher
    val safTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (_: Exception) {
                // Ignore if persist fails
            }

            val docTree = DocumentFile.fromTreeUri(context, uri)
            if (docTree != null) {
                val treeTitle = docTree.name ?: "SAF Tree (${uri.lastPathSegment ?: "Folder"})"
                breadcrumbs = listOf(DocumentBreadcrumb(treeTitle, docTree))
                loadCurrentDirectory()
                Toast.makeText(context, "Membuka folder SAF: $treeTitle", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Back button behavior
    val canGoUp = breadcrumbs.size > 1
    BackHandler(enabled = true) {
        if (isSearchActive) {
            isSearchActive = false
            searchQuery = ""
        } else if (canGoUp) {
            breadcrumbs = breadcrumbs.dropLast(1)
        } else {
            onNavigateBack()
        }
    }

    // Filter & sort list
    val filteredItems = remember(items, searchQuery, selectedCategory, sortMode) {
        var result = items

        // 1. Search Query
        if (searchQuery.isNotBlank()) {
            result = result.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }

        // 2. Category Filter
        result = when (selectedCategory) {
            DocFilterCategory.ALL -> result
            DocFilterCategory.FOLDERS -> result.filter { it.isDirectory }
            DocFilterCategory.DOCUMENTS -> result.filter { !it.isDirectory && it.isCodeOrText }
            DocFilterCategory.IMAGES -> result.filter { !it.isDirectory && it.isImage }
            DocFilterCategory.VIDEOS -> result.filter { !it.isDirectory && it.isVideo }
            DocFilterCategory.AUDIO -> result.filter { !it.isDirectory && it.isAudio }
            DocFilterCategory.APKS -> result.filter { !it.isDirectory && it.isApk }
            DocFilterCategory.ARCHIVES -> result.filter { !it.isDirectory && it.isArchive }
        }

        // 3. Sorting (directories always on top)
        result.sortedWith { a, b ->
            if (a.isDirectory && !b.isDirectory) -1
            else if (!a.isDirectory && b.isDirectory) 1
            else {
                when (sortMode) {
                    DocSortMode.NAME_ASC -> a.name.compareTo(b.name, ignoreCase = true)
                    DocSortMode.NAME_DESC -> b.name.compareTo(a.name, ignoreCase = true)
                    DocSortMode.SIZE_DESC -> b.size.compareTo(a.size)
                    DocSortMode.SIZE_ASC -> a.size.compareTo(b.size)
                    DocSortMode.DATE_DESC -> b.lastModified.compareTo(a.lastModified)
                    DocSortMode.DATE_ASC -> a.lastModified.compareTo(b.lastModified)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchActive) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Cari berkas di folder...", color = Color.LightGray, fontSize = 14.sp) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = Color(0xFF64B5F6),
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("storage_search_input")
                        )
                    } else {
                        Column {
                            Text(
                                text = "Storage & DocumentFile",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 15.sp
                                )
                            )
                            Text(
                                text = selectedVolume?.title ?: "Penyimpanan Android",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color(0xFF90CAF9),
                                    fontSize = 11.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (isSearchActive) {
                                isSearchActive = false
                                searchQuery = ""
                            } else if (canGoUp) {
                                breadcrumbs = breadcrumbs.dropLast(1)
                            } else {
                                onNavigateBack()
                            }
                        },
                        modifier = Modifier.testTag("storage_browser_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    if (isSearchActive) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Bersihkan", tint = Color.White)
                        }
                    } else {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Cari", tint = Color.White)
                        }

                        IconButton(onClick = { showVolumePickerSheet = true }) {
                            Icon(
                                imageVector = Icons.Default.Storage,
                                contentDescription = "Pilih Volume Penyimpanan",
                                tint = Color(0xFF64B5F6)
                            )
                        }

                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = "Urutkan",
                                    tint = Color.White
                                )
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                DocSortMode.values().forEach { mode ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                RadioButton(
                                                    selected = sortMode == mode,
                                                    onClick = null
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(mode.label, fontSize = 13.sp)
                                            }
                                        },
                                        onClick = {
                                            sortMode = mode
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        IconButton(onClick = { loadCurrentDirectory() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Muat Ulang", tint = Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF151D2A)
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    createIsFolder = true
                    newItemName = ""
                    showCreateDialog = true
                },
                containerColor = Color(0xFF1976D2),
                contentColor = Color.White,
                modifier = Modifier
                    .navigationBarsPadding()
                    .testTag("storage_browser_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Buat Folder/Berkas")
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F172A))
                .padding(innerPadding)
        ) {
            // Volume Header Card
            val vol = selectedVolume
            if (vol != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (vol.isRemovable) Icons.Default.SdCard else Icons.Default.Storage,
                                    contentDescription = null,
                                    tint = if (vol.isRemovable) Color(0xFFFFB74D) else Color(0xFF64B5F6),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = vol.title,
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    )
                                    Text(
                                        text = if (vol.isPrimary) "Penyimpanan Utama (Internal)" else "Penyimpanan Eksternal",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = Color.LightGray,
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                            }

                            FilledTonalButton(
                                onClick = { safTreeLauncher.launch(null) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = Color(0xFF2E3A52),
                                    contentColor = Color(0xFF90CAF9)
                                )
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Pilih SAF Tree", fontSize = 11.sp)
                            }
                        }

                        if (vol.totalBytes > 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            val usedBytes = (vol.totalBytes - vol.freeBytes).coerceAtLeast(0L)
                            val fraction = (usedBytes.toFloat() / vol.totalBytes.toFloat()).coerceIn(0f, 1f)

                            LinearProgressIndicator(
                                progress = { fraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = if (fraction > 0.9f) Color(0xFFEF5350) else Color(0xFF42A5F5),
                                trackColor = Color(0xFF334155)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Terpakai: ${CommonUtils.formatFileSize(usedBytes)}",
                                    fontSize = 10.5.sp,
                                    color = Color.LightGray
                                )
                                Text(
                                    text = "Total: ${CommonUtils.formatFileSize(vol.totalBytes)} (Bebas: ${CommonUtils.formatFileSize(vol.freeBytes)})",
                                    fontSize = 10.5.sp,
                                    color = Color.LightGray
                                )
                            }
                        }
                    }
                }
            }

            // Breadcrumbs Navigation Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF131B2A),
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (canGoUp) {
                        IconButton(
                            onClick = { breadcrumbs = breadcrumbs.dropLast(1) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowUpward,
                                contentDescription = "Naik ke Direktori Induk",
                                tint = Color(0xFF64B5F6),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    breadcrumbs.forEachIndexed { index, crumb ->
                        val isLast = index == breadcrumbs.size - 1
                        Text(
                            text = crumb.title,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                                color = if (isLast) Color(0xFF90CAF9) else Color.White
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable {
                                    if (!isLast) {
                                        breadcrumbs = breadcrumbs.take(index + 1)
                                    }
                                }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        )

                        if (!isLast) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Filter Chips Row
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(DocFilterCategory.values()) { category ->
                    val isSelected = selectedCategory == category
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCategory = category },
                        label = { Text(category.label, fontSize = 11.5.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF1976D2),
                            selectedLabelColor = Color.White,
                            containerColor = Color(0xFF1E293B),
                            labelColor = Color.LightGray
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = Color(0xFF334155),
                            selectedBorderColor = Color(0xFF64B5F6)
                        ),
                        modifier = Modifier.height(30.dp)
                    )
                }
            }

            // Stats info line
            val folderCount = remember(filteredItems) { filteredItems.count { it.isDirectory } }
            val fileCount = remember(filteredItems) { filteredItems.count { !it.isDirectory } }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$folderCount Folder, $fileCount Berkas",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray, fontSize = 11.sp)
                )

                val canWrite = currentDoc?.canWrite() ?: false
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (canWrite) Color(0xFF1B5E20) else Color(0xFF4A148C),
                    modifier = Modifier.padding(start = 6.dp)
                ) {
                    Text(
                        text = if (canWrite) "R/W (Bisa Ditulis)" else "R/O (Hanya Baca)",
                        color = Color.White,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            HorizontalDivider(color = Color(0xFF1E293B), thickness = 1.dp, modifier = Modifier.padding(top = 4.dp))

            // Main Content: List, Loading, or Empty state
            Box(modifier = Modifier.fillMaxSize()) {
                if (isLoading) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = Color(0xFF64B5F6))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Memindai berkas via DocumentFile...", color = Color.LightGray, fontSize = 13.sp)
                    }
                } else if (errorMessage != null) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFEF5350), modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(errorMessage ?: "", color = Color.White, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { loadCurrentDirectory() }) {
                            Text("Coba Lagi")
                        }
                    }
                } else if (filteredItems.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Color(0xFF475569), modifier = Modifier.size(56.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "Tidak ada berkas yang cocok dengan pencarian" else "Direktori ini kosong",
                            color = Color.LightGray,
                            fontSize = 13.5.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("document_files_list"),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(filteredItems, key = { it.uri.toString() + "_" + it.name }) { item ->
                            DocumentItemRow(
                                item = item,
                                onClick = {
                                    if (item.isDirectory) {
                                        breadcrumbs = breadcrumbs + DocumentBreadcrumb(item.name, item.doc)
                                    } else {
                                        openDocumentItem(context, viewModel, item)
                                    }
                                },
                                onInfoClick = { itemForDetails = item },
                                onRenameClick = {
                                    itemToRename = item
                                    renameNewName = item.name
                                },
                                onDeleteClick = { itemToDelete = item }
                            )
                        }
                    }
                }
            }
        }
    }

    // 5. Volume Selector Bottom Sheet
    if (showVolumePickerSheet) {
        ModalBottomSheet(
            onDismissRequest = { showVolumePickerSheet = false },
            containerColor = Color(0xFF1E293B)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .navigationBarsPadding()
            ) {
                Text(
                    text = "Pilih Volume Penyimpanan (StorageManager)",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
                Text(
                    text = "Daftar media penyimpanan lokal yang terpasang pada perangkat",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.LightGray, fontSize = 11.sp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                storageVolumes.forEach { volItem ->
                    val isSelected = selectedVolume?.id == volItem.id
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable {
                                selectedVolume = volItem
                                showVolumePickerSheet = false
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) Color(0xFF2E3A52) else Color(0xFF131B2A)
                        ),
                        border = if (isSelected) CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF64B5F6))) else null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (volItem.isRemovable) Icons.Default.SdCard else Icons.Default.Storage,
                                contentDescription = null,
                                tint = if (volItem.isRemovable) Color(0xFFFFB74D) else Color(0xFF64B5F6),
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = volItem.title,
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                )
                                Text(
                                    text = "${if (volItem.isPrimary) "Internal" else "Eksternal"} • Status: ${volItem.state}",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color.LightGray,
                                        fontSize = 11.sp
                                    )
                                )
                                if (volItem.totalBytes > 0) {
                                    Text(
                                        text = "Bebas: ${CommonUtils.formatFileSize(volItem.freeBytes)} dari ${CommonUtils.formatFileSize(volItem.totalBytes)}",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = Color(0xFF90CAF9),
                                            fontSize = 10.5.sp
                                        )
                                    )
                                }
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Terpilih",
                                    tint = Color(0xFF64B5F6)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        showVolumePickerSheet = false
                        safTreeLauncher.launch(null)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF90CAF9))
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Buka Direktori / Folder SAF Bebas")
                }
            }
        }
    }

    // 6. Create Folder / File Dialog
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = {
                Text(
                    text = if (createIsFolder) "Buat Folder Baru" else "Buat Berkas Baru",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = createIsFolder,
                            onClick = { createIsFolder = true },
                            label = { Text("Folder") },
                            leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) }
                        )
                        FilterChip(
                            selected = !createIsFolder,
                            onClick = { createIsFolder = false },
                            label = { Text("Berkas Teks") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null) }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = newItemName,
                        onValueChange = { newItemName = it },
                        label = { Text("Nama") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("create_item_name_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = newItemName.trim()
                        if (name.isNotEmpty() && currentDoc != null) {
                            coroutineScope.launch {
                                val success = withContext(Dispatchers.IO) {
                                    try {
                                        if (createIsFolder) {
                                            currentDoc.createDirectory(name) != null
                                        } else {
                                            val mime = if (name.endsWith(".json")) "application/json"
                                            else if (name.endsWith(".xml")) "application/xml"
                                            else "text/plain"
                                            currentDoc.createFile(mime, name) != null
                                        }
                                    } catch (e: Exception) {
                                        false
                                    }
                                }
                                if (success) {
                                    Toast.makeText(context, "Berhasil dibuat: $name", Toast.LENGTH_SHORT).show()
                                    loadCurrentDirectory()
                                } else {
                                    Toast.makeText(context, "Gagal membuat item", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        showCreateDialog = false
                    }
                ) {
                    Text("Buat")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    // 7. Rename Dialog
    if (itemToRename != null) {
        val target = itemToRename!!
        AlertDialog(
            onDismissRequest = { itemToRename = null },
            title = { Text("Ganti Nama", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Ubah nama untuk: ${target.name}", fontSize = 12.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = renameNewName,
                        onValueChange = { renameNewName = it },
                        label = { Text("Nama Baru") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newName = renameNewName.trim()
                        if (newName.isNotEmpty() && newName != target.name) {
                            coroutineScope.launch {
                                val success = withContext(Dispatchers.IO) {
                                    try {
                                        target.doc.renameTo(newName)
                                    } catch (_: Exception) {
                                        false
                                    }
                                }
                                if (success) {
                                    Toast.makeText(context, "Berhasil mengganti nama", Toast.LENGTH_SHORT).show()
                                    loadCurrentDirectory()
                                } else {
                                    Toast.makeText(context, "Gagal mengganti nama", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        itemToRename = null
                    }
                ) {
                    Text("Simpan")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToRename = null }) {
                    Text("Batal")
                }
            }
        )
    }

    // 8. Delete Confirmation Dialog
    if (itemToDelete != null) {
        val target = itemToDelete!!
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Hapus Berkas / Folder?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Apakah Anda yakin ingin menghapus '${target.name}' via DocumentFile API? Tindakan ini tidak dapat dibatalkan.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            val success = withContext(Dispatchers.IO) {
                                try {
                                    target.doc.delete()
                                } catch (_: Exception) {
                                    false
                                }
                            }
                            if (success) {
                                Toast.makeText(context, "Berhasil dihapus: ${target.name}", Toast.LENGTH_SHORT).show()
                                loadCurrentDirectory()
                            } else {
                                Toast.makeText(context, "Gagal menghapus item", Toast.LENGTH_SHORT).show()
                            }
                        }
                        itemToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) {
                    Text("Hapus")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("Batal")
                }
            }
        )
    }

    // 9. Document Details Dialog
    if (itemForDetails != null) {
        val target = itemForDetails!!
        AlertDialog(
            onDismissRequest = { itemForDetails = null },
            title = { Text("Detail Dokumen", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    DetailRow("Nama", target.name)
                    DetailRow("Tipe", if (target.isDirectory) "Direktori / Folder" else target.mimeType.ifEmpty { "Berkas Biner" })
                    if (!target.isDirectory) {
                        DetailRow("Ukuran", "${CommonUtils.formatFileSize(target.size)} (${target.size} bytes)")
                    }
                    DetailRow("Terakhir Diubah", CommonUtils.formatTimestamp(target.lastModified))
                    DetailRow("Izin", "${if (target.canRead) "Baca" else "-"} / ${if (target.canWrite) "Tulis" else "-"}")
                    DetailRow("URI", target.uri.toString())
                }
            },
            confirmButton = {
                TextButton(onClick = { itemForDetails = null }) {
                    Text("Tutup")
                }
            }
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(text = label, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.Gray)
        Text(
            text = value,
            fontSize = 12.5.sp,
            fontFamily = FontFamily.Monospace,
            color = Color.White,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DocumentItemRow(
    item: DocumentItem,
    onClick: () -> Unit,
    onInfoClick: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var showItemMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon
        val (icon, tint) = getDocumentIcon(item)
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Name and meta
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!item.isDirectory) {
                    Text(
                        text = CommonUtils.formatFileSize(item.size),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFF90CAF9),
                            fontSize = 11.sp
                        )
                    )
                    Text(
                        text = " • ",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                    )
                }

                Text(
                    text = CommonUtils.formatTimestamp(item.lastModified),
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.Gray,
                        fontSize = 11.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // More options menu
        Box {
            IconButton(
                onClick = { showItemMenu = true },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Opsi",
                    tint = Color.LightGray,
                    modifier = Modifier.size(20.dp)
                )
            }

            DropdownMenu(
                expanded = showItemMenu,
                onDismissRequest = { showItemMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Detail & Info") },
                    leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                    onClick = {
                        showItemMenu = false
                        onInfoClick()
                    }
                )

                DropdownMenuItem(
                    text = { Text("Ganti Nama") },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    onClick = {
                        showItemMenu = false
                        onRenameClick()
                    }
                )

                DropdownMenuItem(
                    text = { Text("Hapus", color = Color(0xFFEF5350)) },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF5350)) },
                    onClick = {
                        showItemMenu = false
                        onDeleteClick()
                    }
                )
            }
        }
    }
}

private fun getDocumentIcon(item: DocumentItem): Pair<ImageVector, Color> {
    return when {
        item.isDirectory -> Pair(Icons.Default.Folder, Color(0xFFFFA000))
        item.isApk -> Pair(Icons.Default.Android, Color(0xFF4CAF50))
        item.isImage -> Pair(Icons.Default.Image, Color(0xFFAB47BC))
        item.isVideo -> Pair(Icons.Default.Movie, Color(0xFFE53935))
        item.isAudio -> Pair(Icons.Default.MusicNote, Color(0xFF00ACC1))
        item.isArchive -> Pair(Icons.Default.FolderZip, Color(0xFF00897B))
        item.isCodeOrText -> Pair(Icons.Default.Code, Color(0xFF1E88E5))
        else -> Pair(Icons.AutoMirrored.Filled.InsertDriveFile, Color(0xFF78909C))
    }
}

/**
 * Open DocumentFile based on its mimeType or file extension.
 */
private fun openDocumentItem(context: Context, viewModel: EditorViewModel, item: DocumentItem) {
    try {
        // Check if there is an underlying file path
        val uriPath = item.uri.path
        val possibleFile = if (uriPath != null && item.uri.scheme == "file") File(uriPath) else null

        if (possibleFile != null && possibleFile.exists()) {
            val absolutePath = possibleFile.absolutePath
            when {
                item.isApk -> viewModel.openApkInspector(absolutePath)
                item.isImage -> viewModel.openPhotoEditor(absolutePath)
                item.isVideo -> viewModel.openVideoPlayer(absolutePath)
                item.isCodeOrText -> viewModel.openTextEditor(absolutePath)
                item.isArchive -> viewModel.openArchiveViewer(absolutePath)
                else -> {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(item.uri, item.mimeType.ifEmpty { "*/*" })
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Buka dengan..."))
                }
            }
        } else {
            // General SAF intent view
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(item.uri, item.mimeType.ifEmpty { "*/*" })
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Buka dengan..."))
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Tidak dapat membuka berkas: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Query available storage volumes using Android's StorageManager API natively.
 */
private fun queryStorageVolumes(context: Context): List<StorageVolumeItem> {
    val volumesList = mutableListOf<StorageVolumeItem>()
    try {
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager

        if (storageManager != null) {
            val volumes = storageManager.storageVolumes
            for (volume in volumes) {
                val isPrimary = volume.isPrimary
                val isRemovable = volume.isRemovable
                val state = volume.state
                val title = volume.getDescription(context)
                val id = volume.uuid ?: if (isPrimary) "primary" else "removable_${volumesList.size}"

                var rootFile: File? = null
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    rootFile = volume.directory
                }

                if (rootFile == null && isPrimary) {
                    rootFile = Environment.getExternalStorageDirectory()
                }

                var total = 0L
                var free = 0L
                if (rootFile != null && rootFile.exists()) {
                    try {
                        val stat = StatFs(rootFile.absolutePath)
                        total = stat.totalBytes
                        free = stat.availableBytes
                    } catch (_: Exception) {}
                }

                volumesList.add(
                    StorageVolumeItem(
                        id = id,
                        title = title,
                        isPrimary = isPrimary,
                        isRemovable = isRemovable,
                        state = state,
                        rootFile = rootFile,
                        totalBytes = total,
                        freeBytes = free
                    )
                )
            }
        }
    } catch (_: Exception) {
    }

    // Fallback if no volumes found
    if (volumesList.isEmpty()) {
        val primaryDir = Environment.getExternalStorageDirectory()
        var total = 0L
        var free = 0L
        if (primaryDir.exists()) {
            try {
                val stat = StatFs(primaryDir.absolutePath)
                total = stat.totalBytes
                free = stat.availableBytes
            } catch (_: Exception) {}
        }
        volumesList.add(
            StorageVolumeItem(
                id = "primary",
                title = "Penyimpanan Bersama Internal",
                isPrimary = true,
                isRemovable = false,
                state = Environment.MEDIA_MOUNTED,
                rootFile = primaryDir,
                totalBytes = total,
                freeBytes = free
            )
        )
    }

    return volumesList
}
