package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.CommonUtils
import com.example.EditorViewModel
import com.example.FileItem
import com.example.PanelType
import com.example.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Layout modes for the Dual-Panel File Manager:
 * - HORIZONTAL: Side-by-side with resizable vertical divider.
 * - VERTICAL: Top-and-bottom with resizable horizontal divider (ideal for portrait phone screens).
 * - SINGLE_TAB: Full-screen active panel with top tabs ("Panel Kiri" | "Panel Kanan").
 */
enum class DualPanelLayoutMode(val label: String, val icon: ImageVector) {
    HORIZONTAL("Kiri - Kanan", Icons.Default.ViewColumn),
    VERTICAL("Atas - Bawah", Icons.Default.ViewStream),
    SINGLE_TAB("Tab Penuh", Icons.Default.Tab)
}

/**
 * Main Interface: Dual-Panel Native Storage & DocumentFile Browser.
 *
 * Enhanced with:
 * 1. Adaptive orientation & Resizable Splitter (Horizontal, Vertical, Single-Tab).
 * 2. Sync navigation between panels.
 * 3. Fast folder history per panel.
 * 4. Image, Video & APK thumbnail preview.
 * 5. Asynchronous folder size & item count calculation.
 * 6. Quick SAF shortcuts for Android/data and Android/obb.
 * 7. 1-tap quick transfer button between panels.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoublePanelView(
    viewModel: EditorViewModel,
    onShowChecksum: (FileItem) -> Unit,
    onSignApk: (FileItem) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 1. Query available storage volumes natively via StorageManager
    var storageVolumes by remember { mutableStateOf<List<StorageVolumeItem>>(emptyList()) }
    LaunchedEffect(Unit) {
        storageVolumes = StorageManagerUtils.queryStorageVolumes(context)
    }

    // Active Panel State from ViewModel
    val activePanel by viewModel.activePanel.collectAsStateWithLifecycle()

    // Layout configuration: Mode & Splitter Ratio
    var layoutMode by remember { mutableStateOf(DualPanelLayoutMode.HORIZONTAL) }
    var splitRatio by remember { mutableFloatStateOf(0.5f) }

    // 2. Left Panel State
    var leftVolume by remember { mutableStateOf<StorageVolumeItem?>(null) }
    var leftBreadcrumbs by remember { mutableStateOf<List<DocumentBreadcrumb>>(emptyList()) }
    var leftItems by remember { mutableStateOf<List<DocumentItem>>(emptyList()) }
    var leftSelectedUris by remember { mutableStateOf<Set<Uri>>(emptySet()) }
    var leftSearchQuery by remember { mutableStateOf("") }
    var leftCategory by remember { mutableStateOf(DocFilterCategory.ALL) }
    var leftSortMode by remember { mutableStateOf(DocSortMode.NAME_ASC) }
    var leftLoading by remember { mutableStateOf(false) }
    var leftHistory by remember { mutableStateOf<List<DocumentBreadcrumb>>(emptyList()) }

    // 3. Right Panel State
    var rightVolume by remember { mutableStateOf<StorageVolumeItem?>(null) }
    var rightBreadcrumbs by remember { mutableStateOf<List<DocumentBreadcrumb>>(emptyList()) }
    var rightItems by remember { mutableStateOf<List<DocumentItem>>(emptyList()) }
    var rightSelectedUris by remember { mutableStateOf<Set<Uri>>(emptySet()) }
    var rightSearchQuery by remember { mutableStateOf("") }
    var rightCategory by remember { mutableStateOf(DocFilterCategory.ALL) }
    var rightSortMode by remember { mutableStateOf(DocSortMode.NAME_ASC) }
    var rightLoading by remember { mutableStateOf(false) }
    var rightHistory by remember { mutableStateOf<List<DocumentBreadcrumb>>(emptyList()) }

    // Multi-Select Mode
    var isMultiSelectMode by remember { mutableStateOf(false) }

    // Pick Volume Bottom Sheets
    var showLeftVolumeSheet by remember { mutableStateOf(false) }
    var showRightVolumeSheet by remember { mutableStateOf(false) }

    // Dialog States
    var showCreateDialog by remember { mutableStateOf(false) }
    var createIsFolder by remember { mutableStateOf(false) }
    var createNameInput by remember { mutableStateOf("") }

    var itemToRename by remember { mutableStateOf<DocumentItem?>(null) }
    var renameNewName by remember { mutableStateOf("") }

    var itemToDelete by remember { mutableStateOf<DocumentItem?>(null) }
    var showDeleteSelectedDialog by remember { mutableStateOf(false) }

    var itemForDetails by remember { mutableStateOf<DocumentItem?>(null) }
    var showCompressDialog by remember { mutableStateOf(false) }
    var compressZipName by remember { mutableStateOf("") }

    // Track which panel triggered the SAF picker for Android/data or Android/obb
    var safPickerTargetPanel by remember { mutableStateOf(PanelType.LEFT) }

    // Helper functions to load items for Left and Right panels
    fun loadLeftCurrentDir() {
        val currentDoc = leftBreadcrumbs.lastOrNull()?.doc ?: return
        val currentRealPath = leftBreadcrumbs.lastOrNull()?.realPath
        if (currentRealPath != null) {
            viewModel.loadPath(PanelType.LEFT, currentRealPath)
        }
        // Add to history
        val crumb = leftBreadcrumbs.lastOrNull()
        if (crumb != null) {
            leftHistory = (listOf(crumb) + leftHistory.filter { it.doc.uri != crumb.doc.uri }).take(10)
        }
        leftLoading = true
        coroutineScope.launch {
            val items = withContext(Dispatchers.IO) {
                try {
                    val rawFiles = currentDoc.listFiles()
                    rawFiles.mapNotNull { doc ->
                        val name = doc.name ?: return@mapNotNull null
                        if (name.startsWith(".") && name != "..") return@mapNotNull null
                        val childRealPath = if (currentRealPath != null) File(currentRealPath, name).absolutePath else null
                        DocumentItem(
                            doc = doc,
                            name = name,
                            isDirectory = doc.isDirectory,
                            isFile = doc.isFile,
                            size = if (doc.isDirectory) 0L else doc.length(),
                            lastModified = doc.lastModified(),
                            mimeType = doc.type ?: "",
                            canRead = doc.canRead(),
                            canWrite = doc.canWrite(),
                            uri = doc.uri,
                            realPath = childRealPath
                        )
                    }
                } catch (e: Exception) {
                    emptyList()
                }
            }
            leftItems = items
            leftLoading = false
        }
    }

    fun loadRightCurrentDir() {
        val currentDoc = rightBreadcrumbs.lastOrNull()?.doc ?: return
        val currentRealPath = rightBreadcrumbs.lastOrNull()?.realPath
        if (currentRealPath != null) {
            viewModel.loadPath(PanelType.RIGHT, currentRealPath)
        }
        // Add to history
        val crumb = rightBreadcrumbs.lastOrNull()
        if (crumb != null) {
            rightHistory = (listOf(crumb) + rightHistory.filter { it.doc.uri != crumb.doc.uri }).take(10)
        }
        rightLoading = true
        coroutineScope.launch {
            val items = withContext(Dispatchers.IO) {
                try {
                    val rawFiles = currentDoc.listFiles()
                    rawFiles.mapNotNull { doc ->
                        val name = doc.name ?: return@mapNotNull null
                        if (name.startsWith(".") && name != "..") return@mapNotNull null
                        val childRealPath = if (currentRealPath != null) File(currentRealPath, name).absolutePath else null
                        DocumentItem(
                            doc = doc,
                            name = name,
                            isDirectory = doc.isDirectory,
                            isFile = doc.isFile,
                            size = if (doc.isDirectory) 0L else doc.length(),
                            lastModified = doc.lastModified(),
                            mimeType = doc.type ?: "",
                            canRead = doc.canRead(),
                            canWrite = doc.canWrite(),
                            uri = doc.uri,
                            realPath = childRealPath
                        )
                    }
                } catch (e: Exception) {
                    emptyList()
                }
            }
            rightItems = items
            rightLoading = false
        }
    }

    // Initialize Left and Right panels when storage volumes are detected
    LaunchedEffect(storageVolumes) {
        if (storageVolumes.isNotEmpty()) {
            if (leftVolume == null) {
                val prim = storageVolumes.firstOrNull { it.isPrimary } ?: storageVolumes.first()
                leftVolume = prim
                val rootFile = prim.rootFile ?: Environment.getExternalStorageDirectory()
                val doc = DocumentFile.fromFile(rootFile)
                leftBreadcrumbs = listOf(DocumentBreadcrumb(prim.title, doc, rootFile.absolutePath))
                loadLeftCurrentDir()
            }
            if (rightVolume == null) {
                val sec = storageVolumes.firstOrNull { !it.isPrimary } ?: storageVolumes.first()
                rightVolume = sec
                val rootFile = sec.rootFile ?: Environment.getExternalStorageDirectory()
                val doc = DocumentFile.fromFile(rootFile)
                rightBreadcrumbs = listOf(DocumentBreadcrumb(sec.title, doc, rootFile.absolutePath))
                loadRightCurrentDir()
            }
        }
    }

    // SAF Tree Launchers for picking arbitrary directory on Left / Right
    val leftSafLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {}
            val doc = DocumentFile.fromTreeUri(context, treeUri)
            if (doc != null) {
                leftBreadcrumbs = listOf(DocumentBreadcrumb(doc.name ?: "SAF Folder", doc, null))
                loadLeftCurrentDir()
                Toast.makeText(context, "Membuka folder SAF di Panel Kiri", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val rightSafLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {}
            val doc = DocumentFile.fromTreeUri(context, treeUri)
            if (doc != null) {
                rightBreadcrumbs = listOf(DocumentBreadcrumb(doc.name ?: "SAF Folder", doc, null))
                loadRightCurrentDir()
                Toast.makeText(context, "Membuka folder SAF di Panel Kanan", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // SAF Activity Launcher for Android/data or Android/obb
    val androidSpecialSafLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {}
            val doc = DocumentFile.fromTreeUri(context, uri)
            if (doc != null) {
                val label = doc.name ?: "Android Folder"
                if (safPickerTargetPanel == PanelType.LEFT) {
                    leftBreadcrumbs = listOf(DocumentBreadcrumb(label, doc, null))
                    loadLeftCurrentDir()
                } else {
                    rightBreadcrumbs = listOf(DocumentBreadcrumb(label, doc, null))
                    loadRightCurrentDir()
                }
                Toast.makeText(context, "Membuka $label di ${if (safPickerTargetPanel == PanelType.LEFT) "Panel Kiri" else "Panel Kanan"}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Synchronize Navigation between panels
    fun syncNavigation() {
        if (activePanel == PanelType.LEFT) {
            if (leftBreadcrumbs.isNotEmpty()) {
                rightVolume = leftVolume
                rightBreadcrumbs = leftBreadcrumbs
                loadRightCurrentDir()
                Toast.makeText(context, "Panel Kanan disamakan ke lokasi Panel Kiri", Toast.LENGTH_SHORT).show()
            }
        } else {
            if (rightBreadcrumbs.isNotEmpty()) {
                leftVolume = rightVolume
                leftBreadcrumbs = rightBreadcrumbs
                loadLeftCurrentDir()
                Toast.makeText(context, "Panel Kiri disamakan ke lokasi Panel Kanan", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Cross-panel Copy / Move actions
    fun copyItemToOpposite(item: DocumentItem, targetPanel: PanelType) {
        val targetDoc = if (targetPanel == PanelType.RIGHT) rightBreadcrumbs.lastOrNull()?.doc else leftBreadcrumbs.lastOrNull()?.doc
        if (targetDoc == null) {
            Toast.makeText(context, "Panel tujuan belum siap", Toast.LENGTH_SHORT).show()
            return
        }
        coroutineScope.launch {
            Toast.makeText(context, "Menyalin ${item.name} ke panel sebelah...", Toast.LENGTH_SHORT).show()
            val ok = StorageManagerUtils.copyDocumentRecursively(context, item.doc, targetDoc)
            if (ok) {
                Toast.makeText(context, "Berhasil disalin ke panel sebelah", Toast.LENGTH_SHORT).show()
                if (targetPanel == PanelType.RIGHT) loadRightCurrentDir() else loadLeftCurrentDir()
            } else {
                Toast.makeText(context, "Gagal menyalin item", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun moveItemToOpposite(item: DocumentItem, targetPanel: PanelType) {
        val targetDoc = if (targetPanel == PanelType.RIGHT) rightBreadcrumbs.lastOrNull()?.doc else leftBreadcrumbs.lastOrNull()?.doc
        if (targetDoc == null) {
            Toast.makeText(context, "Panel tujuan belum siap", Toast.LENGTH_SHORT).show()
            return
        }
        coroutineScope.launch {
            Toast.makeText(context, "Memindahkan ${item.name} ke panel sebelah...", Toast.LENGTH_SHORT).show()
            val ok = StorageManagerUtils.moveDocumentRecursively(context, item.doc, targetDoc)
            if (ok) {
                Toast.makeText(context, "Berhasil dipindahkan ke panel sebelah", Toast.LENGTH_SHORT).show()
                loadLeftCurrentDir()
                loadRightCurrentDir()
            } else {
                Toast.makeText(context, "Gagal memindahkan item", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun copySelectedToOpposite() {
        val isLeft = activePanel == PanelType.LEFT
        val selectedUris = if (isLeft) leftSelectedUris else rightSelectedUris
        val sourceItems = (if (isLeft) leftItems else rightItems).filter { selectedUris.contains(it.uri) }
        val targetDoc = if (isLeft) rightBreadcrumbs.lastOrNull()?.doc else leftBreadcrumbs.lastOrNull()?.doc

        if (sourceItems.isEmpty() || targetDoc == null) return

        coroutineScope.launch {
            Toast.makeText(context, "Menyalin ${sourceItems.size} item ke panel sebelah...", Toast.LENGTH_SHORT).show()
            var successCount = 0
            for (item in sourceItems) {
                if (StorageManagerUtils.copyDocumentRecursively(context, item.doc, targetDoc)) {
                    successCount++
                }
            }
            Toast.makeText(context, "Selesai: $successCount dari ${sourceItems.size} disalin", Toast.LENGTH_SHORT).show()
            if (isLeft) {
                leftSelectedUris = emptySet()
                loadRightCurrentDir()
            } else {
                rightSelectedUris = emptySet()
                loadLeftCurrentDir()
            }
            isMultiSelectMode = false
        }
    }

    fun moveSelectedToOpposite() {
        val isLeft = activePanel == PanelType.LEFT
        val selectedUris = if (isLeft) leftSelectedUris else rightSelectedUris
        val sourceItems = (if (isLeft) leftItems else rightItems).filter { selectedUris.contains(it.uri) }
        val targetDoc = if (isLeft) rightBreadcrumbs.lastOrNull()?.doc else leftBreadcrumbs.lastOrNull()?.doc

        if (sourceItems.isEmpty() || targetDoc == null) return

        coroutineScope.launch {
            Toast.makeText(context, "Memindahkan ${sourceItems.size} item ke panel sebelah...", Toast.LENGTH_SHORT).show()
            var successCount = 0
            for (item in sourceItems) {
                if (StorageManagerUtils.moveDocumentRecursively(context, item.doc, targetDoc)) {
                    successCount++
                }
            }
            Toast.makeText(context, "Selesai: $successCount dari ${sourceItems.size} dipindahkan", Toast.LENGTH_SHORT).show()
            leftSelectedUris = emptySet()
            rightSelectedUris = emptySet()
            isMultiSelectMode = false
            loadLeftCurrentDir()
            loadRightCurrentDir()
        }
    }

    // MAIN DUAL PANEL LAYOUT
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
    ) {
        // TOP CONTROL BAR: Layout mode switch, Sync navigation, and Panel Swap
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFFFFFFFF),
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Active panel badge
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1976D2))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (activePanel == PanelType.LEFT) "Aktif: Panel Kiri" else "Aktif: Panel Kanan",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                }

                // Quick Header Actions
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Sync Navigation Button
                    IconButton(
                        onClick = { syncNavigation() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "Samakan Lokasi Panel",
                            tint = Color(0xFF1976D2),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Layout Mode Switcher Dropdown
                    var showLayoutMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(
                            onClick = { showLayoutMenu = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = layoutMode.icon,
                                contentDescription = "Mode Tampilan",
                                tint = Color(0xFF475569),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showLayoutMenu,
                            onDismissRequest = { showLayoutMenu = false },
                            modifier = Modifier.background(Color.White)
                        ) {
                            DualPanelLayoutMode.values().forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(mode.label, fontSize = 12.sp) },
                                    leadingIcon = {
                                        Icon(
                                            mode.icon,
                                            contentDescription = null,
                                            tint = if (layoutMode == mode) Color(0xFF1976D2) else Color.Gray,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    onClick = {
                                        layoutMode = mode
                                        showLayoutMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // PANELS RENDERER BASED ON SELECTED LAYOUT MODE
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (layoutMode) {
                DualPanelLayoutMode.HORIZONTAL -> {
                    // SIDE-BY-SIDE with Resizable Splitter
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val totalWidth = maxWidth
                        val leftWidth = (totalWidth * splitRatio - 4.dp).coerceAtLeast(80.dp)

                        Row(modifier = Modifier.fillMaxSize()) {
                            // Left Panel
                            Box(
                                modifier = Modifier
                                    .width(leftWidth)
                                    .fillMaxHeight()
                                    .clickable { viewModel.setActivePanel(PanelType.LEFT) }
                                    .border(
                                        width = if (activePanel == PanelType.LEFT) 1.5.dp else 0.5.dp,
                                        color = if (activePanel == PanelType.LEFT) Color(0xFF1976D2) else Color(0xFFE2E8F0)
                                    )
                            ) {
                                StoragePanelColumn(
                                    panelTitle = "Panel Kiri",
                                    isActive = activePanel == PanelType.LEFT,
                                    volume = leftVolume,
                                    breadcrumbs = leftBreadcrumbs,
                                    history = leftHistory,
                                    items = leftItems,
                                    selectedUris = leftSelectedUris,
                                    searchQuery = leftSearchQuery,
                                    category = leftCategory,
                                    sortMode = leftSortMode,
                                    isLoading = leftLoading,
                                    isMultiSelectMode = isMultiSelectMode,
                                    onActivate = { viewModel.setActivePanel(PanelType.LEFT) },
                                    onOpenVolumePicker = { showLeftVolumeSheet = true },
                                    onNavigateUp = {
                                        if (leftBreadcrumbs.size > 1) {
                                            leftBreadcrumbs = leftBreadcrumbs.dropLast(1)
                                            loadLeftCurrentDir()
                                        }
                                    },
                                    onBreadcrumbClick = { index ->
                                        leftBreadcrumbs = leftBreadcrumbs.take(index + 1)
                                        loadLeftCurrentDir()
                                    },
                                    onSelectHistory = { crumb ->
                                        leftBreadcrumbs = listOf(crumb)
                                        loadLeftCurrentDir()
                                    },
                                    onSearchQueryChange = { leftSearchQuery = it },
                                    onCategoryChange = { leftCategory = it },
                                    onSortModeChange = { leftSortMode = it },
                                    onItemClick = { item ->
                                        if (isMultiSelectMode) {
                                            leftSelectedUris = if (leftSelectedUris.contains(item.uri)) {
                                                leftSelectedUris - item.uri
                                            } else {
                                                leftSelectedUris + item.uri
                                            }
                                        } else if (item.isDirectory) {
                                            leftBreadcrumbs = leftBreadcrumbs + DocumentBreadcrumb(item.name, item.doc, item.realPath)
                                            loadLeftCurrentDir()
                                        } else {
                                            StorageManagerUtils.openDocumentItem(
                                                context, viewModel, item,
                                                onShowChecksum = { f -> onShowChecksum(f) },
                                                onSignApk = { f -> onSignApk(f) }
                                            )
                                        }
                                    },
                                    onToggleSelect = { item ->
                                        leftSelectedUris = if (leftSelectedUris.contains(item.uri)) {
                                            leftSelectedUris - item.uri
                                        } else {
                                            leftSelectedUris + item.uri
                                        }
                                        if (leftSelectedUris.isNotEmpty()) isMultiSelectMode = true
                                    },
                                    onCopyToOpposite = { item -> copyItemToOpposite(item, PanelType.RIGHT) },
                                    onMoveToOpposite = { item -> moveItemToOpposite(item, PanelType.RIGHT) },
                                    onRename = { item ->
                                        itemToRename = item
                                        renameNewName = item.name
                                    },
                                    onDelete = { item -> itemToDelete = item },
                                    onDetails = { item -> itemForDetails = item },
                                    onOpenAsText = { item ->
                                        val path = item.realPath ?: item.uri.path
                                        if (path != null) viewModel.openTextEditor(path)
                                    },
                                    onOpenAsHex = { item ->
                                        val path = item.realPath ?: item.uri.path
                                        if (path != null) viewModel.openHexEditor(path)
                                    },
                                    onShowChecksum = { item ->
                                        val path = item.realPath ?: item.uri.path
                                        if (path != null) onShowChecksum(FileItem(item.name, path, false, item.size, item.lastModified))
                                    },
                                    onSignApk = { item ->
                                        val path = item.realPath ?: item.uri.path
                                        if (path != null) onSignApk(FileItem(item.name, path, false, item.size, item.lastModified))
                                    }
                                )
                            }

                            // Vertical Resizable Splitter Handle
                            Box(
                                modifier = Modifier
                                    .width(8.dp)
                                    .fillMaxHeight()
                                    .background(Color(0xFFE2E8F0))
                                    .pointerInput(totalWidth) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            val newRatio = splitRatio + (dragAmount.x / totalWidth.toPx())
                                            splitRatio = newRatio.coerceIn(0.2f, 0.8f)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(36.dp)
                                        .clip(RoundedCornerShape(1.5.dp))
                                        .background(Color(0xFF94A3B8))
                                )
                            }

                            // Right Panel
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable { viewModel.setActivePanel(PanelType.RIGHT) }
                                    .border(
                                        width = if (activePanel == PanelType.RIGHT) 1.5.dp else 0.5.dp,
                                        color = if (activePanel == PanelType.RIGHT) Color(0xFF1976D2) else Color(0xFFE2E8F0)
                                    )
                            ) {
                                StoragePanelColumn(
                                    panelTitle = "Panel Kanan",
                                    isActive = activePanel == PanelType.RIGHT,
                                    volume = rightVolume,
                                    breadcrumbs = rightBreadcrumbs,
                                    history = rightHistory,
                                    items = rightItems,
                                    selectedUris = rightSelectedUris,
                                    searchQuery = rightSearchQuery,
                                    category = rightCategory,
                                    sortMode = rightSortMode,
                                    isLoading = rightLoading,
                                    isMultiSelectMode = isMultiSelectMode,
                                    onActivate = { viewModel.setActivePanel(PanelType.RIGHT) },
                                    onOpenVolumePicker = { showRightVolumeSheet = true },
                                    onNavigateUp = {
                                        if (rightBreadcrumbs.size > 1) {
                                            rightBreadcrumbs = rightBreadcrumbs.dropLast(1)
                                            loadRightCurrentDir()
                                        }
                                    },
                                    onBreadcrumbClick = { index ->
                                        rightBreadcrumbs = rightBreadcrumbs.take(index + 1)
                                        loadRightCurrentDir()
                                    },
                                    onSelectHistory = { crumb ->
                                        rightBreadcrumbs = listOf(crumb)
                                        loadRightCurrentDir()
                                    },
                                    onSearchQueryChange = { rightSearchQuery = it },
                                    onCategoryChange = { rightCategory = it },
                                    onSortModeChange = { rightSortMode = it },
                                    onItemClick = { item ->
                                        if (isMultiSelectMode) {
                                            rightSelectedUris = if (rightSelectedUris.contains(item.uri)) {
                                                rightSelectedUris - item.uri
                                            } else {
                                                rightSelectedUris + item.uri
                                            }
                                        } else if (item.isDirectory) {
                                            rightBreadcrumbs = rightBreadcrumbs + DocumentBreadcrumb(item.name, item.doc, item.realPath)
                                            loadRightCurrentDir()
                                        } else {
                                            StorageManagerUtils.openDocumentItem(
                                                context, viewModel, item,
                                                onShowChecksum = { f -> onShowChecksum(f) },
                                                onSignApk = { f -> onSignApk(f) }
                                            )
                                        }
                                    },
                                    onToggleSelect = { item ->
                                        rightSelectedUris = if (rightSelectedUris.contains(item.uri)) {
                                            rightSelectedUris - item.uri
                                        } else {
                                            rightSelectedUris + item.uri
                                        }
                                        if (rightSelectedUris.isNotEmpty()) isMultiSelectMode = true
                                    },
                                    onCopyToOpposite = { item -> copyItemToOpposite(item, PanelType.LEFT) },
                                    onMoveToOpposite = { item -> moveItemToOpposite(item, PanelType.LEFT) },
                                    onRename = { item ->
                                        itemToRename = item
                                        renameNewName = item.name
                                    },
                                    onDelete = { item -> itemToDelete = item },
                                    onDetails = { item -> itemForDetails = item },
                                    onOpenAsText = { item ->
                                        val path = item.realPath ?: item.uri.path
                                        if (path != null) viewModel.openTextEditor(path)
                                    },
                                    onOpenAsHex = { item ->
                                        val path = item.realPath ?: item.uri.path
                                        if (path != null) viewModel.openHexEditor(path)
                                    },
                                    onShowChecksum = { item ->
                                        val path = item.realPath ?: item.uri.path
                                        if (path != null) onShowChecksum(FileItem(item.name, path, false, item.size, item.lastModified))
                                    },
                                    onSignApk = { item ->
                                        val path = item.realPath ?: item.uri.path
                                        if (path != null) onSignApk(FileItem(item.name, path, false, item.size, item.lastModified))
                                    }
                                )
                            }
                        }
                    }
                }

                DualPanelLayoutMode.VERTICAL -> {
                    // TOP-AND-BOTTOM with Resizable Splitter
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val totalHeight = maxHeight
                        val topHeight = (totalHeight * splitRatio - 4.dp).coerceAtLeast(80.dp)

                        Column(modifier = Modifier.fillMaxSize()) {
                            // Top Panel (Left Panel logic)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(topHeight)
                                    .clickable { viewModel.setActivePanel(PanelType.LEFT) }
                                    .border(
                                        width = if (activePanel == PanelType.LEFT) 1.5.dp else 0.5.dp,
                                        color = if (activePanel == PanelType.LEFT) Color(0xFF1976D2) else Color(0xFFE2E8F0)
                                    )
                            ) {
                                StoragePanelColumn(
                                    panelTitle = "Panel Atas (Kiri)",
                                    isActive = activePanel == PanelType.LEFT,
                                    volume = leftVolume,
                                    breadcrumbs = leftBreadcrumbs,
                                    history = leftHistory,
                                    items = leftItems,
                                    selectedUris = leftSelectedUris,
                                    searchQuery = leftSearchQuery,
                                    category = leftCategory,
                                    sortMode = leftSortMode,
                                    isLoading = leftLoading,
                                    isMultiSelectMode = isMultiSelectMode,
                                    onActivate = { viewModel.setActivePanel(PanelType.LEFT) },
                                    onOpenVolumePicker = { showLeftVolumeSheet = true },
                                    onNavigateUp = {
                                        if (leftBreadcrumbs.size > 1) {
                                            leftBreadcrumbs = leftBreadcrumbs.dropLast(1)
                                            loadLeftCurrentDir()
                                        }
                                    },
                                    onBreadcrumbClick = { index ->
                                        leftBreadcrumbs = leftBreadcrumbs.take(index + 1)
                                        loadLeftCurrentDir()
                                    },
                                    onSelectHistory = { crumb ->
                                        leftBreadcrumbs = listOf(crumb)
                                        loadLeftCurrentDir()
                                    },
                                    onSearchQueryChange = { leftSearchQuery = it },
                                    onCategoryChange = { leftCategory = it },
                                    onSortModeChange = { leftSortMode = it },
                                    onItemClick = { item ->
                                        if (isMultiSelectMode) {
                                            leftSelectedUris = if (leftSelectedUris.contains(item.uri)) {
                                                leftSelectedUris - item.uri
                                            } else {
                                                leftSelectedUris + item.uri
                                            }
                                        } else if (item.isDirectory) {
                                            leftBreadcrumbs = leftBreadcrumbs + DocumentBreadcrumb(item.name, item.doc, item.realPath)
                                            loadLeftCurrentDir()
                                        } else {
                                            StorageManagerUtils.openDocumentItem(
                                                context, viewModel, item,
                                                onShowChecksum = { f -> onShowChecksum(f) },
                                                onSignApk = { f -> onSignApk(f) }
                                            )
                                        }
                                    },
                                    onToggleSelect = { item ->
                                        leftSelectedUris = if (leftSelectedUris.contains(item.uri)) {
                                            leftSelectedUris - item.uri
                                        } else {
                                            leftSelectedUris + item.uri
                                        }
                                        if (leftSelectedUris.isNotEmpty()) isMultiSelectMode = true
                                    },
                                    onCopyToOpposite = { item -> copyItemToOpposite(item, PanelType.RIGHT) },
                                    onMoveToOpposite = { item -> moveItemToOpposite(item, PanelType.RIGHT) },
                                    onRename = { item ->
                                        itemToRename = item
                                        renameNewName = item.name
                                    },
                                    onDelete = { item -> itemToDelete = item },
                                    onDetails = { item -> itemForDetails = item },
                                    onOpenAsText = { item ->
                                        val path = item.realPath ?: item.uri.path
                                        if (path != null) viewModel.openTextEditor(path)
                                    },
                                    onOpenAsHex = { item ->
                                        val path = item.realPath ?: item.uri.path
                                        if (path != null) viewModel.openHexEditor(path)
                                    },
                                    onShowChecksum = { item ->
                                        val path = item.realPath ?: item.uri.path
                                        if (path != null) onShowChecksum(FileItem(item.name, path, false, item.size, item.lastModified))
                                    },
                                    onSignApk = { item ->
                                        val path = item.realPath ?: item.uri.path
                                        if (path != null) onSignApk(FileItem(item.name, path, false, item.size, item.lastModified))
                                    }
                                )
                            }

                            // Horizontal Resizable Splitter Handle
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .background(Color(0xFFE2E8F0))
                                    .pointerInput(totalHeight) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            val newRatio = splitRatio + (dragAmount.y / totalHeight.toPx())
                                            splitRatio = newRatio.coerceIn(0.2f, 0.8f)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(36.dp)
                                        .height(3.dp)
                                        .clip(RoundedCornerShape(1.5.dp))
                                        .background(Color(0xFF94A3B8))
                                )
                            }

                            // Bottom Panel (Right Panel logic)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .clickable { viewModel.setActivePanel(PanelType.RIGHT) }
                                    .border(
                                        width = if (activePanel == PanelType.RIGHT) 1.5.dp else 0.5.dp,
                                        color = if (activePanel == PanelType.RIGHT) Color(0xFF1976D2) else Color(0xFFE2E8F0)
                                    )
                            ) {
                                StoragePanelColumn(
                                    panelTitle = "Panel Bawah (Kanan)",
                                    isActive = activePanel == PanelType.RIGHT,
                                    volume = rightVolume,
                                    breadcrumbs = rightBreadcrumbs,
                                    history = rightHistory,
                                    items = rightItems,
                                    selectedUris = rightSelectedUris,
                                    searchQuery = rightSearchQuery,
                                    category = rightCategory,
                                    sortMode = rightSortMode,
                                    isLoading = rightLoading,
                                    isMultiSelectMode = isMultiSelectMode,
                                    onActivate = { viewModel.setActivePanel(PanelType.RIGHT) },
                                    onOpenVolumePicker = { showRightVolumeSheet = true },
                                    onNavigateUp = {
                                        if (rightBreadcrumbs.size > 1) {
                                            rightBreadcrumbs = rightBreadcrumbs.dropLast(1)
                                            loadRightCurrentDir()
                                        }
                                    },
                                    onBreadcrumbClick = { index ->
                                        rightBreadcrumbs = rightBreadcrumbs.take(index + 1)
                                        loadRightCurrentDir()
                                    },
                                    onSelectHistory = { crumb ->
                                        rightBreadcrumbs = listOf(crumb)
                                        loadRightCurrentDir()
                                    },
                                    onSearchQueryChange = { rightSearchQuery = it },
                                    onCategoryChange = { rightCategory = it },
                                    onSortModeChange = { rightSortMode = it },
                                    onItemClick = { item ->
                                        if (isMultiSelectMode) {
                                            rightSelectedUris = if (rightSelectedUris.contains(item.uri)) {
                                                rightSelectedUris - item.uri
                                            } else {
                                                rightSelectedUris + item.uri
                                            }
                                        } else if (item.isDirectory) {
                                            rightBreadcrumbs = rightBreadcrumbs + DocumentBreadcrumb(item.name, item.doc, item.realPath)
                                            loadRightCurrentDir()
                                        } else {
                                            StorageManagerUtils.openDocumentItem(
                                                context, viewModel, item,
                                                onShowChecksum = { f -> onShowChecksum(f) },
                                                onSignApk = { f -> onSignApk(f) }
                                            )
                                        }
                                    },
                                    onToggleSelect = { item ->
                                        rightSelectedUris = if (rightSelectedUris.contains(item.uri)) {
                                            rightSelectedUris - item.uri
                                        } else {
                                            rightSelectedUris + item.uri
                                        }
                                        if (rightSelectedUris.isNotEmpty()) isMultiSelectMode = true
                                    },
                                    onCopyToOpposite = { item -> copyItemToOpposite(item, PanelType.LEFT) },
                                    onMoveToOpposite = { item -> moveItemToOpposite(item, PanelType.LEFT) },
                                    onRename = { item ->
                                        itemToRename = item
                                        renameNewName = item.name
                                    },
                                    onDelete = { item -> itemToDelete = item },
                                    onDetails = { item -> itemForDetails = item },
                                    onOpenAsText = { item ->
                                        val path = item.realPath ?: item.uri.path
                                        if (path != null) viewModel.openTextEditor(path)
                                    },
                                    onOpenAsHex = { item ->
                                        val path = item.realPath ?: item.uri.path
                                        if (path != null) viewModel.openHexEditor(path)
                                    },
                                    onShowChecksum = { item ->
                                        val path = item.realPath ?: item.uri.path
                                        if (path != null) onShowChecksum(FileItem(item.name, path, false, item.size, item.lastModified))
                                    },
                                    onSignApk = { item ->
                                        val path = item.realPath ?: item.uri.path
                                        if (path != null) onSignApk(FileItem(item.name, path, false, item.size, item.lastModified))
                                    }
                                )
                            }
                        }
                    }
                }

                DualPanelLayoutMode.SINGLE_TAB -> {
                    // FULL-SCREEN SINGLE PANEL WITH TABS
                    Column(modifier = Modifier.fillMaxSize()) {
                        TabRow(
                            selectedTabIndex = if (activePanel == PanelType.LEFT) 0 else 1,
                            containerColor = Color(0xFFF1F5F9),
                            contentColor = Color(0xFF1976D2),
                            modifier = Modifier.height(38.dp)
                        ) {
                            Tab(
                                selected = activePanel == PanelType.LEFT,
                                onClick = { viewModel.setActivePanel(PanelType.LEFT) },
                                text = {
                                    Text(
                                        text = "Panel Kiri (${leftBreadcrumbs.lastOrNull()?.title ?: "Root"})",
                                        fontSize = 12.sp,
                                        fontWeight = if (activePanel == PanelType.LEFT) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            )
                            Tab(
                                selected = activePanel == PanelType.RIGHT,
                                onClick = { viewModel.setActivePanel(PanelType.RIGHT) },
                                text = {
                                    Text(
                                        text = "Panel Kanan (${rightBreadcrumbs.lastOrNull()?.title ?: "Root"})",
                                        fontSize = 12.sp,
                                        fontWeight = if (activePanel == PanelType.RIGHT) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            )
                        }

                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            if (activePanel == PanelType.LEFT) {
                                StoragePanelColumn(
                                    panelTitle = "Panel Kiri (Penuh)",
                                    isActive = true,
                                    volume = leftVolume,
                                    breadcrumbs = leftBreadcrumbs,
                                    history = leftHistory,
                                    items = leftItems,
                                    selectedUris = leftSelectedUris,
                                    searchQuery = leftSearchQuery,
                                    category = leftCategory,
                                    sortMode = leftSortMode,
                                    isLoading = leftLoading,
                                    isMultiSelectMode = isMultiSelectMode,
                                    onActivate = { viewModel.setActivePanel(PanelType.LEFT) },
                                    onOpenVolumePicker = { showLeftVolumeSheet = true },
                                    onNavigateUp = {
                                        if (leftBreadcrumbs.size > 1) {
                                            leftBreadcrumbs = leftBreadcrumbs.dropLast(1)
                                            loadLeftCurrentDir()
                                        }
                                    },
                                    onBreadcrumbClick = { index ->
                                        leftBreadcrumbs = leftBreadcrumbs.take(index + 1)
                                        loadLeftCurrentDir()
                                    },
                                    onSelectHistory = { crumb ->
                                        leftBreadcrumbs = listOf(crumb)
                                        loadLeftCurrentDir()
                                    },
                                    onSearchQueryChange = { leftSearchQuery = it },
                                    onCategoryChange = { leftCategory = it },
                                    onSortModeChange = { leftSortMode = it },
                                    onItemClick = { item ->
                                        if (isMultiSelectMode) {
                                            leftSelectedUris = if (leftSelectedUris.contains(item.uri)) {
                                                leftSelectedUris - item.uri
                                            } else {
                                                leftSelectedUris + item.uri
                                            }
                                        } else if (item.isDirectory) {
                                            leftBreadcrumbs = leftBreadcrumbs + DocumentBreadcrumb(item.name, item.doc, item.realPath)
                                            loadLeftCurrentDir()
                                        } else {
                                            StorageManagerUtils.openDocumentItem(
                                                context, viewModel, item,
                                                onShowChecksum = { f -> onShowChecksum(f) },
                                                onSignApk = { f -> onSignApk(f) }
                                            )
                                        }
                                    },
                                    onToggleSelect = { item ->
                                        leftSelectedUris = if (leftSelectedUris.contains(item.uri)) {
                                            leftSelectedUris - item.uri
                                        } else {
                                            leftSelectedUris + item.uri
                                        }
                                        if (leftSelectedUris.isNotEmpty()) isMultiSelectMode = true
                                    },
                                    onCopyToOpposite = { item -> copyItemToOpposite(item, PanelType.RIGHT) },
                                    onMoveToOpposite = { item -> moveItemToOpposite(item, PanelType.RIGHT) },
                                    onRename = { item ->
                                        itemToRename = item
                                        renameNewName = item.name
                                    },
                                    onDelete = { item -> itemToDelete = item },
                                    onDetails = { item -> itemForDetails = item },
                                    onOpenAsText = { item ->
                                        val path = item.realPath ?: item.uri.path
                                        if (path != null) viewModel.openTextEditor(path)
                                    },
                                    onOpenAsHex = { item ->
                                        val path = item.realPath ?: item.uri.path
                                        if (path != null) viewModel.openHexEditor(path)
                                    },
                                    onShowChecksum = { item ->
                                        val path = item.realPath ?: item.uri.path
                                        if (path != null) onShowChecksum(FileItem(item.name, path, false, item.size, item.lastModified))
                                    },
                                    onSignApk = { item ->
                                        val path = item.realPath ?: item.uri.path
                                        if (path != null) onSignApk(FileItem(item.name, path, false, item.size, item.lastModified))
                                    }
                                )
                            } else {
                                StoragePanelColumn(
                                    panelTitle = "Panel Kanan (Penuh)",
                                    isActive = true,
                                    volume = rightVolume,
                                    breadcrumbs = rightBreadcrumbs,
                                    history = rightHistory,
                                    items = rightItems,
                                    selectedUris = rightSelectedUris,
                                    searchQuery = rightSearchQuery,
                                    category = rightCategory,
                                    sortMode = rightSortMode,
                                    isLoading = rightLoading,
                                    isMultiSelectMode = isMultiSelectMode,
                                    onActivate = { viewModel.setActivePanel(PanelType.RIGHT) },
                                    onOpenVolumePicker = { showRightVolumeSheet = true },
                                    onNavigateUp = {
                                        if (rightBreadcrumbs.size > 1) {
                                            rightBreadcrumbs = rightBreadcrumbs.dropLast(1)
                                            loadRightCurrentDir()
                                        }
                                    },
                                    onBreadcrumbClick = { index ->
                                        rightBreadcrumbs = rightBreadcrumbs.take(index + 1)
                                        loadRightCurrentDir()
                                    },
                                    onSelectHistory = { crumb ->
                                        rightBreadcrumbs = listOf(crumb)
                                        loadRightCurrentDir()
                                    },
                                    onSearchQueryChange = { rightSearchQuery = it },
                                    onCategoryChange = { rightCategory = it },
                                    onSortModeChange = { rightSortMode = it },
                                    onItemClick = { item ->
                                        if (isMultiSelectMode) {
                                            rightSelectedUris = if (rightSelectedUris.contains(item.uri)) {
                                                rightSelectedUris - item.uri
                                            } else {
                                                rightSelectedUris + item.uri
                                            }
                                        } else if (item.isDirectory) {
                                            rightBreadcrumbs = rightBreadcrumbs + DocumentBreadcrumb(item.name, item.doc, item.realPath)
                                            loadRightCurrentDir()
                                        } else {
                                            StorageManagerUtils.openDocumentItem(
                                                context, viewModel, item,
                                                onShowChecksum = { f -> onShowChecksum(f) },
                                                onSignApk = { f -> onSignApk(f) }
                                            )
                                        }
                                    },
                                    onToggleSelect = { item ->
                                        rightSelectedUris = if (rightSelectedUris.contains(item.uri)) {
                                            rightSelectedUris - item.uri
                                        } else {
                                            rightSelectedUris + item.uri
                                        }
                                        if (rightSelectedUris.isNotEmpty()) isMultiSelectMode = true
                                    },
                                    onCopyToOpposite = { item -> copyItemToOpposite(item, PanelType.LEFT) },
                                    onMoveToOpposite = { item -> moveItemToOpposite(item, PanelType.LEFT) },
                                    onRename = { item ->
                                        itemToRename = item
                                        renameNewName = item.name
                                    },
                                    onDelete = { item -> itemToDelete = item },
                                    onDetails = { item -> itemForDetails = item },
                                    onOpenAsText = { item ->
                                        val path = item.realPath ?: item.uri.path
                                        if (path != null) viewModel.openTextEditor(path)
                                    },
                                    onOpenAsHex = { item ->
                                        val path = item.realPath ?: item.uri.path
                                        if (path != null) viewModel.openHexEditor(path)
                                    },
                                    onShowChecksum = { item ->
                                        val path = item.realPath ?: item.uri.path
                                        if (path != null) onShowChecksum(FileItem(item.name, path, false, item.size, item.lastModified))
                                    },
                                    onSignApk = { item ->
                                        val path = item.realPath ?: item.uri.path
                                        if (path != null) onSignApk(FileItem(item.name, path, false, item.size, item.lastModified))
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // BOTTOM ACTION BAR
        val currentSelectedCount = if (activePanel == PanelType.LEFT) leftSelectedUris.size else rightSelectedUris.size

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = if (isMultiSelectMode) Color(0xFF0F172A) else Color(0xFFFFFFFF),
            shadowElevation = 8.dp
        ) {
            if (isMultiSelectMode) {
                // Multi-select actions bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$currentSelectedCount Terpilih",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(start = 4.dp)
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = { copySelectedToOpposite() },
                            enabled = currentSelectedCount > 0
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Salin ke Sebelah", tint = Color(0xFF90CAF9))
                        }
                        IconButton(
                            onClick = { moveSelectedToOpposite() },
                            enabled = currentSelectedCount > 0
                        ) {
                            Icon(Icons.Default.DriveFileMove, contentDescription = "Pindahkan ke Sebelah", tint = Color(0xFFFFCC80))
                        }
                        IconButton(
                            onClick = {
                                compressZipName = "arsip_bundle.zip"
                                showCompressDialog = true
                            },
                            enabled = currentSelectedCount > 0
                        ) {
                            Icon(Icons.Default.FolderZip, contentDescription = "Kompres ZIP", tint = Color(0xFF80CBC4))
                        }
                        IconButton(
                            onClick = { showDeleteSelectedDialog = true },
                            enabled = currentSelectedCount > 0
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = Color(0xFFEF5350))
                        }
                        IconButton(
                            onClick = {
                                leftSelectedUris = emptySet()
                                rightSelectedUris = emptySet()
                                isMultiSelectMode = false
                            }
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Batal", tint = Color.LightGray)
                        }
                    }
                }
            } else {
                // Standard Dual-Panel Bottom Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Navigate Back / History
                    IconButton(
                        onClick = {
                            if (activePanel == PanelType.LEFT) {
                                if (leftBreadcrumbs.size > 1) {
                                    leftBreadcrumbs = leftBreadcrumbs.dropLast(1)
                                    loadLeftCurrentDir()
                                }
                            } else {
                                if (rightBreadcrumbs.size > 1) {
                                    rightBreadcrumbs = rightBreadcrumbs.dropLast(1)
                                    loadRightCurrentDir()
                                }
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = Color(0xFF475569))
                    }

                    // Create New Folder / File
                    FilledTonalButton(
                        onClick = {
                            createNameInput = ""
                            createIsFolder = true
                            showCreateDialog = true
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFFE2E8F0)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF1E293B))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Buat", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1E293B))
                    }

                    // Toggle Multi-Select Mode
                    IconButton(
                        onClick = {
                            isMultiSelectMode = !isMultiSelectMode
                            if (!isMultiSelectMode) {
                                leftSelectedUris = emptySet()
                                rightSelectedUris = emptySet()
                            }
                        }
                    ) {
                        Icon(Icons.Default.Checklist, contentDescription = "Multi-Pilih", tint = Color(0xFF475569))
                    }

                    // Swap Active Panel
                    IconButton(
                        onClick = {
                            val newPanel = if (activePanel == PanelType.LEFT) PanelType.RIGHT else PanelType.LEFT
                            viewModel.setActivePanel(newPanel)
                        }
                    ) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = "Tukar Panel Aktif", tint = Color(0xFF1976D2))
                    }

                    // Navigate Up to Parent
                    IconButton(
                        onClick = {
                            if (activePanel == PanelType.LEFT) {
                                if (leftBreadcrumbs.size > 1) {
                                    leftBreadcrumbs = leftBreadcrumbs.dropLast(1)
                                    loadLeftCurrentDir()
                                }
                            } else {
                                if (rightBreadcrumbs.size > 1) {
                                    rightBreadcrumbs = rightBreadcrumbs.dropLast(1)
                                    loadRightCurrentDir()
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = "Naik ke Induk", tint = Color(0xFF475569))
                    }
                }
            }
        }
    }

    // 4. STORAGE VOLUME SELECTOR BOTTOM SHEET (Left Panel)
    if (showLeftVolumeSheet) {
        ModalBottomSheet(
            onDismissRequest = { showLeftVolumeSheet = false },
            containerColor = Color(0xFF1E293B)
        ) {
            VolumePickerSheetContent(
                volumes = storageVolumes,
                selectedVolume = leftVolume,
                panelTitle = "Panel Kiri",
                onSelectVolume = { vol ->
                    leftVolume = vol
                    val root = vol.rootFile ?: Environment.getExternalStorageDirectory()
                    val doc = DocumentFile.fromFile(root)
                    leftBreadcrumbs = listOf(DocumentBreadcrumb(vol.title, doc, root.absolutePath))
                    loadLeftCurrentDir()
                    showLeftVolumeSheet = false
                },
                onPickSafTree = {
                    showLeftVolumeSheet = false
                    leftSafLauncher.launch(null)
                },
                onPickSpecialSaf = { subDir ->
                    showLeftVolumeSheet = false
                    safPickerTargetPanel = PanelType.LEFT
                    try {
                        val intent = StorageManagerUtils.getAndroidFolderSAFIntent(subDir)
                        androidSpecialSafLauncher.launch(intent)
                    } catch (e: Exception) {
                        leftSafLauncher.launch(null)
                    }
                }
            )
        }
    }

    // 5. STORAGE VOLUME SELECTOR BOTTOM SHEET (Right Panel)
    if (showRightVolumeSheet) {
        ModalBottomSheet(
            onDismissRequest = { showRightVolumeSheet = false },
            containerColor = Color(0xFF1E293B)
        ) {
            VolumePickerSheetContent(
                volumes = storageVolumes,
                selectedVolume = rightVolume,
                panelTitle = "Panel Kanan",
                onSelectVolume = { vol ->
                    rightVolume = vol
                    val root = vol.rootFile ?: Environment.getExternalStorageDirectory()
                    val doc = DocumentFile.fromFile(root)
                    rightBreadcrumbs = listOf(DocumentBreadcrumb(vol.title, doc, root.absolutePath))
                    loadRightCurrentDir()
                    showRightVolumeSheet = false
                },
                onPickSafTree = {
                    showRightVolumeSheet = false
                    rightSafLauncher.launch(null)
                },
                onPickSpecialSaf = { subDir ->
                    showRightVolumeSheet = false
                    safPickerTargetPanel = PanelType.RIGHT
                    try {
                        val intent = StorageManagerUtils.getAndroidFolderSAFIntent(subDir)
                        androidSpecialSafLauncher.launch(intent)
                    } catch (e: Exception) {
                        rightSafLauncher.launch(null)
                    }
                }
            )
        }
    }

    // 6. CREATE FOLDER / FILE DIALOG (using DocumentFile API)
    if (showCreateDialog) {
        val targetPanelTitle = if (activePanel == PanelType.LEFT) "Panel Kiri" else "Panel Kanan"
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = {
                Text(
                    text = if (createIsFolder) "Buat Folder Baru ($targetPanelTitle)" else "Buat Berkas Teks Baru ($targetPanelTitle)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
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
                        value = createNameInput,
                        onValueChange = { createNameInput = it },
                        label = { Text(if (createIsFolder) "Nama Folder" else "Nama Berkas (mis: catatan.txt)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = createNameInput.trim()
                        if (name.isNotEmpty()) {
                            val activeBreadcrumbs = if (activePanel == PanelType.LEFT) leftBreadcrumbs else rightBreadcrumbs
                            val currentDoc = activeBreadcrumbs.lastOrNull()?.doc
                            if (currentDoc != null) {
                                coroutineScope.launch {
                                    val created = withContext(Dispatchers.IO) {
                                        if (createIsFolder) {
                                            currentDoc.createDirectory(name)
                                        } else {
                                            val finalName = if (name.contains(".")) name else "$name.txt"
                                            currentDoc.createFile("text/plain", finalName)
                                        }
                                    }
                                    if (created != null) {
                                        Toast.makeText(context, "Berhasil dibuat via DocumentFile", Toast.LENGTH_SHORT).show()
                                        if (activePanel == PanelType.LEFT) loadLeftCurrentDir() else loadRightCurrentDir()
                                    } else {
                                        Toast.makeText(context, "Gagal membuat item", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            showCreateDialog = false
                        }
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

    // 7. RENAME ITEM DIALOG (using DocumentFile.renameTo)
    if (itemToRename != null) {
        val item = itemToRename!!
        AlertDialog(
            onDismissRequest = { itemToRename = null },
            title = { Text("Ganti Nama", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameNewName,
                    onValueChange = { renameNewName = it },
                    label = { Text("Nama Baru") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newName = renameNewName.trim()
                        if (newName.isNotEmpty() && newName != item.name) {
                            coroutineScope.launch {
                                val ok = withContext(Dispatchers.IO) {
                                    item.doc.renameTo(newName)
                                }
                                if (ok) {
                                    Toast.makeText(context, "Nama berhasil diubah", Toast.LENGTH_SHORT).show()
                                    loadLeftCurrentDir()
                                    loadRightCurrentDir()
                                } else {
                                    Toast.makeText(context, "Gagal mengubah nama", Toast.LENGTH_SHORT).show()
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

    // 8. DELETE ITEM DIALOG (using DocumentFile.delete)
    if (itemToDelete != null) {
        val item = itemToDelete!!
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Hapus Item", fontWeight = FontWeight.Bold) },
            text = {
                Text("Apakah Anda yakin ingin menghapus '${item.name}' secara permanen?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            val ok = withContext(Dispatchers.IO) {
                                item.doc.delete()
                            }
                            if (ok) {
                                Toast.makeText(context, "Item berhasil dihapus", Toast.LENGTH_SHORT).show()
                                loadLeftCurrentDir()
                                loadRightCurrentDir()
                            } else {
                                Toast.makeText(context, "Gagal menghapus item", Toast.LENGTH_SHORT).show()
                            }
                        }
                        itemToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
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

    // 9. DELETE SELECTED ITEMS DIALOG
    if (showDeleteSelectedDialog) {
        val count = if (activePanel == PanelType.LEFT) leftSelectedUris.size else rightSelectedUris.size
        AlertDialog(
            onDismissRequest = { showDeleteSelectedDialog = false },
            title = { Text("Hapus $count Item Terpilih", fontWeight = FontWeight.Bold) },
            text = {
                Text("Semua $count item yang dipilih akan dihapus secara permanen menggunakan DocumentFile API.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val isLeft = activePanel == PanelType.LEFT
                        val selectedUris = if (isLeft) leftSelectedUris else rightSelectedUris
                        val itemsToDelete = (if (isLeft) leftItems else rightItems).filter { selectedUris.contains(it.uri) }

                        coroutineScope.launch {
                            var deletedCount = 0
                            withContext(Dispatchers.IO) {
                                for (it in itemsToDelete) {
                                    if (it.doc.delete()) deletedCount++
                                }
                            }
                            Toast.makeText(context, "$deletedCount item berhasil dihapus", Toast.LENGTH_SHORT).show()
                            if (isLeft) leftSelectedUris = emptySet() else rightSelectedUris = emptySet()
                            isMultiSelectMode = false
                            loadLeftCurrentDir()
                            loadRightCurrentDir()
                        }
                        showDeleteSelectedDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Hapus Semua")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSelectedDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    // 10. COMPRESS SELECTED ITEMS TO ZIP DIALOG
    if (showCompressDialog) {
        AlertDialog(
            onDismissRequest = { showCompressDialog = false },
            title = { Text("Kompres ke Berkas ZIP", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Buat satu arsip ZIP berisi semua item yang dipilih:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = compressZipName,
                        onValueChange = { compressZipName = it },
                        label = { Text("Nama Berkas ZIP") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val isLeft = activePanel == PanelType.LEFT
                        val selectedUris = if (isLeft) leftSelectedUris else rightSelectedUris
                        val itemsToZip = (if (isLeft) leftItems else rightItems).filter { selectedUris.contains(it.uri) }
                        val currentDir = (if (isLeft) leftBreadcrumbs else rightBreadcrumbs).lastOrNull()?.realPath

                        if (currentDir != null && itemsToZip.isNotEmpty()) {
                            val paths = itemsToZip.mapNotNull { it.realPath }
                            val targetZip = File(currentDir, if (compressZipName.endsWith(".zip")) compressZipName else "$compressZipName.zip").absolutePath
                            viewModel.compressFilesToZip(paths, targetZip)
                            Toast.makeText(context, "Mengompres ke $targetZip", Toast.LENGTH_SHORT).show()
                            if (isLeft) leftSelectedUris = emptySet() else rightSelectedUris = emptySet()
                            isMultiSelectMode = false
                        } else {
                            Toast.makeText(context, "Lokasi tidak mendukung kompresi ZIP langsung", Toast.LENGTH_SHORT).show()
                        }
                        showCompressDialog = false
                    }
                ) {
                    Text("Kompres")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCompressDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    // 11. ITEM DETAILS DIALOG (DocumentFile metadata + Asynchronous Folder Size Calculation)
    if (itemForDetails != null) {
        val item = itemForDetails!!
        val dateFormatted = remember(item.lastModified) {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(item.lastModified))
        }
        var folderStats by remember(item.uri) { mutableStateOf<Pair<Long, Int>?>(null) }
        var isCalculatingSize by remember(item.uri) { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { itemForDetails = null },
            title = { Text("Detail ${if (item.isDirectory) "Folder" else "Berkas"}", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Nama: ${item.name}", fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
                    Text("Tipe: ${if (item.isDirectory) "Direktori / Folder" else item.mimeType.ifEmpty { "Berkas Data" }}", fontSize = 12.sp)

                    if (!item.isDirectory) {
                        Text("Ukuran: ${CommonUtils.formatFileSize(item.size)} (${item.size} bytes)", fontSize = 12.sp)
                    } else {
                        if (folderStats != null) {
                            Text(
                                text = "Total Ukuran: ${CommonUtils.formatFileSize(folderStats!!.first)} (${folderStats!!.second} berkas)",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1976D2)
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isCalculatingSize) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 1.5.dp, color = Color(0xFF1976D2))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Menghitung isi folder...", fontSize = 11.sp, color = Color.Gray)
                                } else {
                                    FilledTonalButton(
                                        onClick = {
                                            isCalculatingSize = true
                                            coroutineScope.launch {
                                                val stats = StorageManagerUtils.calculateFolderSize(item.doc)
                                                folderStats = stats
                                                isCalculatingSize = false
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Icon(Icons.Default.Calculate, contentDescription = null, modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Hitung Ukuran & Berkas", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }

                    Text("Terakhir Diubah: $dateFormatted", fontSize = 12.sp)
                    Text("Izin: ${if (item.canWrite) "Dapat Dibaca & Ditulis (R/W)" else "Hanya Baca (R/O)"}", fontSize = 12.sp)
                    if (item.realPath != null) {
                        Text("Path Fisik: ${item.realPath}", fontSize = 11.sp, color = Color.DarkGray, fontFamily = FontFamily.Monospace)
                    }
                    Text("URI: ${item.uri}", fontSize = 10.5.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
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

/**
 * Single Panel Column Composable (used by both Left Panel & Right Panel).
 */
@Composable
fun StoragePanelColumn(
    panelTitle: String,
    isActive: Boolean,
    volume: StorageVolumeItem?,
    breadcrumbs: List<DocumentBreadcrumb>,
    history: List<DocumentBreadcrumb>,
    items: List<DocumentItem>,
    selectedUris: Set<Uri>,
    searchQuery: String,
    category: DocFilterCategory,
    sortMode: DocSortMode,
    isLoading: Boolean,
    isMultiSelectMode: Boolean,
    onActivate: () -> Unit,
    onOpenVolumePicker: () -> Unit,
    onNavigateUp: () -> Unit,
    onBreadcrumbClick: (Int) -> Unit,
    onSelectHistory: (DocumentBreadcrumb) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onCategoryChange: (DocFilterCategory) -> Unit,
    onSortModeChange: (DocSortMode) -> Unit,
    onItemClick: (DocumentItem) -> Unit,
    onToggleSelect: (DocumentItem) -> Unit,
    onCopyToOpposite: (DocumentItem) -> Unit,
    onMoveToOpposite: (DocumentItem) -> Unit,
    onRename: (DocumentItem) -> Unit,
    onDelete: (DocumentItem) -> Unit,
    onDetails: (DocumentItem) -> Unit,
    onOpenAsText: (DocumentItem) -> Unit,
    onOpenAsHex: (DocumentItem) -> Unit,
    onShowChecksum: (DocumentItem) -> Unit,
    onSignApk: (DocumentItem) -> Unit
) {
    var showHistoryMenu by remember { mutableStateOf(false) }

    // Filter and Sort Items
    val filteredItems = remember(items, searchQuery, category, sortMode) {
        items
            .filter { item ->
                if (searchQuery.isEmpty()) true
                else item.name.contains(searchQuery, ignoreCase = true)
            }
            .filter { item ->
                when (category) {
                    DocFilterCategory.ALL -> true
                    DocFilterCategory.FOLDERS -> item.isDirectory
                    DocFilterCategory.DOCUMENTS -> item.isCodeOrText
                    DocFilterCategory.APKS -> item.isApk
                    DocFilterCategory.IMAGES -> item.isImage
                    DocFilterCategory.VIDEOS -> item.isVideo
                    DocFilterCategory.AUDIO -> item.isAudio
                    DocFilterCategory.ARCHIVES -> item.isArchive
                }
            }
            .sortedWith { a, b ->
                if (a.isDirectory && !b.isDirectory) -1
                else if (!a.isDirectory && b.isDirectory) 1
                else when (sortMode) {
                    DocSortMode.NAME_ASC -> a.name.compareTo(b.name, ignoreCase = true)
                    DocSortMode.NAME_DESC -> b.name.compareTo(a.name, ignoreCase = true)
                    DocSortMode.SIZE_DESC -> b.size.compareTo(a.size)
                    DocSortMode.SIZE_ASC -> a.size.compareTo(b.size)
                    DocSortMode.DATE_DESC -> b.lastModified.compareTo(a.lastModified)
                    DocSortMode.DATE_ASC -> a.lastModified.compareTo(b.lastModified)
                }
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // PANEL HEADER (Volume Card & Active Indicator)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onActivate() },
            color = if (isActive) Color(0xFFF0F7FF) else Color(0xFFF8FAFC)
        ) {
            Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Panel label & Active dot
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isActive) Color(0xFF1976D2) else Color(0xFF94A3B8))
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = panelTitle,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 11.5.sp,
                            color = if (isActive) Color(0xFF1976D2) else Color(0xFF64748B)
                        )
                    }

                    // Storage Volume Picker Button (StorageManager)
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isActive) Color(0xFFE0EFFF) else Color(0xFFE2E8F0))
                            .clickable { onOpenVolumePicker() }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (volume?.isRemovable == true) Icons.Default.SdCard else Icons.Default.Storage,
                            contentDescription = null,
                            tint = Color(0xFF1976D2),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = volume?.title?.take(12) ?: "Storage",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF0F172A),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = Color(0xFF475569),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                // Storage Free / Total Bar (if available)
                if (volume != null && volume.totalBytes > 0) {
                    val usedPercent = 1f - (volume.freeBytes.toFloat() / volume.totalBytes.toFloat()).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { usedPercent },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp)
                            .padding(top = 3.dp),
                        color = if (usedPercent > 0.9f) Color(0xFFEF4444) else Color(0xFF1976D2),
                        trackColor = Color(0xFFE2E8F0)
                    )
                }
            }
        }

        HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 1.dp)

        // BREADCRUMB NAVIGATION & HISTORY ROW
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFFAFAFA))
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Parent UP button
            IconButton(
                onClick = onNavigateUp,
                modifier = Modifier.size(24.dp),
                enabled = breadcrumbs.size > 1
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowUpward,
                    contentDescription = "Up",
                    modifier = Modifier.size(14.dp),
                    tint = if (breadcrumbs.size > 1) Color(0xFF1976D2) else Color(0xFFCBD5E1)
                )
            }

            // Quick Folder History Button
            Box {
                IconButton(
                    onClick = { showHistoryMenu = true },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "Riwayat Folder",
                        modifier = Modifier.size(14.dp),
                        tint = if (history.isNotEmpty()) Color(0xFF1976D2) else Color(0xFFCBD5E1)
                    )
                }

                DropdownMenu(
                    expanded = showHistoryMenu,
                    onDismissRequest = { showHistoryMenu = false },
                    modifier = Modifier.background(Color.White)
                ) {
                    Text(
                        text = "Riwayat Folder Terakhir",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                    HorizontalDivider(color = Color(0xFFF1F5F9))

                    if (history.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("Belum ada riwayat", fontSize = 11.sp, color = Color.Gray) },
                            onClick = { showHistoryMenu = false }
                        )
                    } else {
                        history.forEach { hist ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(hist.title, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Color.Black)
                                        if (hist.realPath != null) {
                                            Text(hist.realPath, fontSize = 9.5.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                },
                                leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null, tint = Color(0xFFFFA000), modifier = Modifier.size(15.dp)) },
                                onClick = {
                                    showHistoryMenu = false
                                    onSelectHistory(hist)
                                }
                            )
                        }
                    }
                }
            }

            // Horizontally scrollable breadcrumbs
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                breadcrumbs.forEachIndexed { index, crumb ->
                    if (index > 0) {
                        Text(
                            text = " / ",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(horizontal = 1.dp)
                        )
                    }
                    Text(
                        text = crumb.title,
                        fontSize = 11.sp,
                        fontWeight = if (index == breadcrumbs.lastIndex) FontWeight.Bold else FontWeight.Normal,
                        color = if (index == breadcrumbs.lastIndex) Color(0xFF0F172A) else Color(0xFF1976D2),
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onBreadcrumbClick(index) }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }

        HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 0.5.dp)

        // SEARCH & FILTER BAR
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Cari di folder ini...", fontSize = 11.sp, color = Color.Gray) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Gray)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { onSearchQueryChange("") },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(12.dp), tint = Color.Gray)
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 3.dp)
                .height(36.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF1976D2),
                unfocusedBorderColor = Color(0xFFE2E8F0),
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color(0xFFF8FAFC)
            ),
            textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = Color.Black)
        )

        // QUICK CATEGORY FILTER CHIPS ROW
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp, vertical = 1.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DocFilterCategory.values().forEach { cat ->
                val isCatSelected = category == cat
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onCategoryChange(cat) },
                    color = if (isCatSelected) Color(0xFF1976D2) else Color(0xFFF1F5F9)
                ) {
                    Text(
                        text = cat.label,
                        fontSize = 10.sp,
                        fontWeight = if (isCatSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isCatSelected) Color.White else Color(0xFF475569),
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }
        }

        // STATS & PERMISSION INDICATOR
        val folderCount = remember(filteredItems) { filteredItems.count { it.isDirectory } }
        val fileCount = remember(filteredItems) { filteredItems.count { !it.isDirectory } }
        val canWriteCurrent = breadcrumbs.lastOrNull()?.doc?.canWrite() ?: false

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF8FAFC))
                .padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$folderCount Folder • $fileCount Berkas",
                fontSize = 10.sp,
                color = Color(0xFF64748B)
            )
            Surface(
                shape = RoundedCornerShape(3.dp),
                color = if (canWriteCurrent) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
            ) {
                Text(
                    text = if (canWriteCurrent) "R/W" else "R/O",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (canWriteCurrent) Color(0xFF2E7D32) else Color(0xFFE65100),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }

        HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 0.5.dp)

        // FILE & FOLDER LIST with Thumbnails and 1-tap Transfer
        Box(modifier = Modifier.weight(1f)) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = Color(0xFF1976D2))
                }
            } else if (filteredItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (items.isEmpty()) "Folder kosong" else "Tidak ada berkas yang sesuai",
                        fontSize = 11.5.sp,
                        color = Color.Gray
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(filteredItems, key = { it.uri.toString() + "_" + it.name }) { item ->
                        val isSelected = selectedUris.contains(item.uri)
                        DocumentItemCompactRow(
                            item = item,
                            isSelected = isSelected,
                            isMultiSelectMode = isMultiSelectMode,
                            onClick = { onItemClick(item) },
                            onToggleSelect = { onToggleSelect(item) },
                            onCopyToOpposite = { onCopyToOpposite(item) },
                            onMoveToOpposite = { onMoveToOpposite(item) },
                            onRename = { onRename(item) },
                            onDelete = { onDelete(item) },
                            onDetails = { onDetails(item) },
                            onOpenAsText = { onOpenAsText(item) },
                            onOpenAsHex = { onOpenAsHex(item) },
                            onShowChecksum = { onShowChecksum(item) },
                            onSignApk = { onSignApk(item) }
                        )
                        HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

/**
 * Compact File/Folder Row Component tailored for Dual-Panel View.
 * Displays Thumbnail for image/video/APK, and quick transfer button.
 */
@Composable
fun DocumentItemCompactRow(
    item: DocumentItem,
    isSelected: Boolean,
    isMultiSelectMode: Boolean,
    onClick: () -> Unit,
    onToggleSelect: () -> Unit,
    onCopyToOpposite: () -> Unit,
    onMoveToOpposite: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDetails: () -> Unit,
    onOpenAsText: () -> Unit,
    onOpenAsHex: () -> Unit,
    onShowChecksum: () -> Unit,
    onSignApk: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    val dateStr = remember(item.lastModified) {
        if (item.lastModified > 0) {
            SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date(item.lastModified))
        } else ""
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) Color(0xFFE0F2FE) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Multi-select Checkbox
        if (isMultiSelectMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelect() },
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
        }

        // Thumbnail Preview for Images, Videos, APKs or fallback icon
        DocumentThumbnailView(
            item = item,
            modifier = Modifier.size(22.dp)
        )

        Spacer(modifier = Modifier.width(6.dp))

        // File Name & Details
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.5.sp,
                    fontWeight = if (item.isDirectory) FontWeight.SemiBold else FontWeight.Normal,
                    color = Color(0xFF0F172A)
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!item.isDirectory && item.size > 0) {
                    Text(
                        text = CommonUtils.formatFileSize(item.size),
                        fontSize = 9.5.sp,
                        color = Color(0xFF64748B)
                    )
                    Text(text = " • ", fontSize = 9.5.sp, color = Color(0xFFCBD5E1))
                }
                if (dateStr.isNotEmpty()) {
                    Text(
                        text = dateStr,
                        fontSize = 9.5.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
        }

        // 1-Tap Quick Copy to Opposite Panel Button
        IconButton(
            onClick = onCopyToOpposite,
            modifier = Modifier.size(22.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Forward,
                contentDescription = "Salin Cepat ke Sebelah",
                tint = Color(0xFF1976D2),
                modifier = Modifier.size(14.dp)
            )
        }

        // 3-Dots Action Menu Button
        Box {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(22.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Menu",
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(14.dp)
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(Color.White)
            ) {
                DropdownMenuItem(
                    text = { Text("Salin ke Panel Sebelah", fontSize = 12.sp, color = Color.Black) },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color(0xFF1976D2), modifier = Modifier.size(16.dp)) },
                    onClick = {
                        showMenu = false
                        onCopyToOpposite()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Pindahkan ke Panel Sebelah", fontSize = 12.sp, color = Color.Black) },
                    leadingIcon = { Icon(Icons.Default.DriveFileMove, contentDescription = null, tint = Color(0xFFFF9800), modifier = Modifier.size(16.dp)) },
                    onClick = {
                        showMenu = false
                        onMoveToOpposite()
                    }
                )

                if (!item.isDirectory) {
                    DropdownMenuItem(
                        text = { Text("Buka Sebagai Teks", fontSize = 12.sp, color = Color.Black) },
                        leadingIcon = { Icon(Icons.Default.Code, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp)) },
                        onClick = {
                            showMenu = false
                            onOpenAsText()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Buka Sebagai Hex", fontSize = 12.sp, color = Color.Black) },
                        leadingIcon = { Icon(Icons.Default.DataObject, contentDescription = null, tint = Color(0xFF00ACC1), modifier = Modifier.size(16.dp)) },
                        onClick = {
                            showMenu = false
                            onOpenAsHex()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Periksa Checksum", fontSize = 12.sp, color = Color.Black) },
                        leadingIcon = { Icon(Icons.Default.Tag, contentDescription = null, tint = Color(0xFF7B1FA2), modifier = Modifier.size(16.dp)) },
                        onClick = {
                            showMenu = false
                            onShowChecksum()
                        }
                    )
                    if (item.isApk) {
                        DropdownMenuItem(
                            text = { Text("Tanda Tangani APK", fontSize = 12.sp, color = Color.Black) },
                            leadingIcon = { Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp)) },
                            onClick = {
                                showMenu = false
                                onSignApk()
                            }
                        )
                    }
                }

                HorizontalDivider(color = Color(0xFFF1F5F9))

                DropdownMenuItem(
                    text = { Text("Ganti Nama", fontSize = 12.sp, color = Color.Black) },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = Color(0xFF546E7A), modifier = Modifier.size(16.dp)) },
                    onClick = {
                        showMenu = false
                        onRename()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Detail Dokumen", fontSize = 12.sp, color = Color.Black) },
                    leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF1976D2), modifier = Modifier.size(16.dp)) },
                    onClick = {
                        showMenu = false
                        onDetails()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Hapus", fontSize = 12.sp, color = Color(0xFFEF4444)) },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp)) },
                    onClick = {
                        showMenu = false
                        onDelete()
                    }
                )
            }
        }
    }
}

/**
 * Storage Volume Picker Bottom Sheet Content for selecting detected StorageManager volumes,
 * custom SAF folder, or quick shortcuts to Android/data and Android/obb.
 */
@Composable
fun VolumePickerSheetContent(
    volumes: List<StorageVolumeItem>,
    selectedVolume: StorageVolumeItem?,
    panelTitle: String,
    onSelectVolume: (StorageVolumeItem) -> Unit,
    onPickSafTree: () -> Unit,
    onPickSpecialSaf: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .navigationBarsPadding()
    ) {
        Text(
            text = "Pilih Media Penyimpanan ($panelTitle)",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        )
        Text(
            text = "Terdeteksi melalui native StorageManager & SAF",
            style = MaterialTheme.typography.bodySmall.copy(color = Color.LightGray, fontSize = 11.sp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        volumes.forEach { vol ->
            val isSelected = selectedVolume?.id == vol.id
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { onSelectVolume(vol) },
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
                        imageVector = if (vol.isRemovable) Icons.Default.SdCard else Icons.Default.Storage,
                        contentDescription = null,
                        tint = if (vol.isRemovable) Color(0xFFFFB74D) else Color(0xFF64B5F6),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = vol.title,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                        Text(
                            text = "${if (vol.isPrimary) "Penyimpanan Utama" else "Penyimpanan Eksternal"} • Status: ${vol.state}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color.LightGray,
                                fontSize = 11.sp
                            )
                        )
                        if (vol.totalBytes > 0) {
                            Text(
                                text = "Bebas: ${CommonUtils.formatFileSize(vol.freeBytes)} dari ${CommonUtils.formatFileSize(vol.totalBytes)}",
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

        // SPECIAL ANDROID/DATA AND ANDROID/OBB SHORTCUTS
        Text(
            text = "Akses Khusus Direktori Sistem (SAF)",
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF94A3B8),
            modifier = Modifier.padding(vertical = 4.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { onPickSpecialSaf("data") },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF81D4FA))
            ) {
                Icon(Icons.Default.FolderSpecial, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Android/data", fontSize = 11.sp)
            }
            OutlinedButton(
                onClick = { onPickSpecialSaf("obb") },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFCC80))
            ) {
                Icon(Icons.Default.FolderZip, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Android/obb", fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        OutlinedButton(
            onClick = onPickSafTree,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF90CAF9))
        ) {
            Icon(Icons.Default.FolderOpen, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Buka Folder SAF Bebas (OpenDocumentTree)")
        }
    }
}
