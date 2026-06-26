package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.MyApplicationTheme
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainAppScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(viewModel: EditorViewModel = viewModel()) {
    val context = LocalContext.current
    val activeView by viewModel.activeView.collectAsState()
    val isRootEnabled by viewModel.isRootEnabled.collectAsState()
    val isRootAvailable by viewModel.isRootAvailable.collectAsState()

    // Permission handling state
    var hasPermissions by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.values.all { it }
        hasPermissions = granted
        if (granted) {
            viewModel.refreshAll()
        }
    }

    LaunchedEffect(Unit) {
        if (!hasPermissions) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    context.startActivity(intent)
                }
                hasPermissions = true
            } else {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = "Logo",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Column {
                            Text(
                                "APK System Editor",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                "MT Manager style tools",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    // Root Toggle Switch
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = if (isRootEnabled) "Root [ON]" else "Root [OFF]",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = if (isRootEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Switch(
                            checked = isRootEnabled,
                            onCheckedChange = {
                                if (isRootAvailable || it) {
                                    viewModel.setRootEnabled(it)
                                    Toast.makeText(context, "Root mode updated", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "su binary not found! Running as standard user.", Toast.LENGTH_LONG).show()
                                    viewModel.setRootEnabled(false)
                                }
                            },
                            modifier = Modifier.scale(0.8f)
                        )
                    }

                    IconButton(
                        onClick = { viewModel.refreshAll() },
                        modifier = Modifier.testTag("refresh_button")
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val view = activeView) {
                is ActiveView.Explorer -> {
                    DoublePanelView(viewModel)
                }
                is ActiveView.TextEditor -> {
                    TextEditorView(
                        filePath = view.filePath,
                        isNewFile = view.isNewFile,
                        viewModel = viewModel
                    )
                }
                is ActiveView.CompareView -> {
                    CompareViewScreen(
                        fileAPath = view.fileAPath,
                        fileBPath = view.fileBPath,
                        viewModel = viewModel
                    )
                }
                is ActiveView.ApkInspector -> {
                    ApkInspectorScreen(
                        apkPath = view.apkPath,
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}

@Composable
fun DoublePanelView(viewModel: EditorViewModel) {
    val activePanel by viewModel.activePanel.collectAsState()
    val leftPath by viewModel.leftPath.collectAsState()
    val rightPath by viewModel.rightPath.collectAsState()
    val leftFiles by viewModel.leftFiles.collectAsState()
    val rightFiles by viewModel.rightFiles.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var createIsFolder by remember { mutableStateOf(false) }
    var targetPanelForCreate by remember { mutableStateOf(PanelType.LEFT) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Dual Toolbar / Utility actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { viewModel.startComparison() },
                enabled = leftFiles.any { !it.isDirectory } && rightFiles.any { !it.isDirectory },
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp)
                    .testTag("compare_files_button"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Icon(imageVector = Icons.Default.List, contentDescription = "Compare")
                Spacer(modifier = Modifier.width(4.dp))
                Text("Compare Panel Files", fontSize = 12.sp)
            }

            Button(
                onClick = {
                    targetPanelForCreate = activePanel
                    createIsFolder = false
                    showCreateDialog = true
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
                    .testTag("create_file_button")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add")
                Spacer(modifier = Modifier.width(4.dp))
                Text("New File / Folder", fontSize = 12.sp)
            }
        }

        HorizontalDivider()

        // Double pane side-by-side layout
        Row(modifier = Modifier.fillMaxSize()) {
            // Left Panel
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .border(
                        border = BorderStroke(
                            width = if (activePanel == PanelType.LEFT) 2.dp else 1.dp,
                            color = if (activePanel == PanelType.LEFT) MaterialTheme.colorScheme.primary else Color.Transparent
                        )
                    )
                    .clickable { viewModel.setActivePanel(PanelType.LEFT) }
            ) {
                FilePanelColumn(
                    title = "Panel A (Left)",
                    currentPath = leftPath,
                    files = leftFiles,
                    isActive = activePanel == PanelType.LEFT,
                    onNavigateUp = { viewModel.navigateUp(PanelType.LEFT) },
                    onItemClick = { item -> viewModel.selectFileItem(PanelType.LEFT, item) },
                    onItemDelete = { item -> viewModel.deleteFileItem(PanelType.LEFT, item) },
                    onPanelSelect = { viewModel.setActivePanel(PanelType.LEFT) }
                )
            }

            VerticalDivider(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp)
            )

            // Right Panel
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .border(
                        border = BorderStroke(
                            width = if (activePanel == PanelType.RIGHT) 2.dp else 1.dp,
                            color = if (activePanel == PanelType.RIGHT) MaterialTheme.colorScheme.primary else Color.Transparent
                        )
                    )
                    .clickable { viewModel.setActivePanel(PanelType.RIGHT) }
            ) {
                FilePanelColumn(
                    title = "Panel B (Right)",
                    currentPath = rightPath,
                    files = rightFiles,
                    isActive = activePanel == PanelType.RIGHT,
                    onNavigateUp = { viewModel.navigateUp(PanelType.RIGHT) },
                    onItemClick = { item -> viewModel.selectFileItem(PanelType.RIGHT, item) },
                    onItemDelete = { item -> viewModel.deleteFileItem(PanelType.RIGHT, item) },
                    onPanelSelect = { viewModel.setActivePanel(PanelType.RIGHT) }
                )
            }
        }
    }

    // New File/Folder dialog
    if (showCreateDialog) {
        var inputName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(if (createIsFolder) "Create New Folder" else "Create New File") },
            text = {
                Column {
                    OutlinedTextField(
                        value = inputName,
                        onValueChange = { inputName = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = createIsFolder,
                            onCheckedChange = { createIsFolder = it }
                        )
                        Text("Is Directory / Folder")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (inputName.isNotBlank()) {
                            viewModel.createNewFileOrDir(targetPanelForCreate, inputName, createIsFolder)
                            showCreateDialog = false
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FilePanelColumn(
    title: String,
    currentPath: String,
    files: List<FileItem>,
    isActive: Boolean,
    onNavigateUp: () -> Unit,
    onItemClick: (FileItem) -> Unit,
    onItemDelete: (FileItem) -> Unit,
    onPanelSelect: () -> Unit
) {
    var itemToDelete by remember { mutableStateOf<FileItem?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isActive) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        // Panel Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )

            IconButton(
                onClick = onNavigateUp,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Up",
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Current Directory Path Breadcrumb
        Text(
            text = currentPath,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.inverseOnSurface)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HorizontalDivider()

        if (files.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Empty directory\n(or partition unreadable)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .padding(2.dp)
            ) {
                items(files) { item ->
                    val fileColor = when {
                        item.isDirectory -> MaterialTheme.colorScheme.primary
                        item.isApk -> Color(0xFF2E7D32) // green for apk
                        else -> MaterialTheme.colorScheme.onSurface
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    onPanelSelect()
                                    onItemClick(item)
                                },
                                onLongClick = {
                                    onPanelSelect()
                                    itemToDelete = item
                                }
                            )
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when {
                                item.isDirectory -> Icons.Default.Home
                                item.isApk -> Icons.Default.PlayArrow
                                else -> Icons.Default.Edit
                            },
                            contentDescription = item.name,
                            tint = fileColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (item.isDirectory) FontWeight.SemiBold else FontWeight.Normal),
                                color = fileColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!item.isDirectory) {
                                Text(
                                    text = "${item.size} bytes",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }

                        IconButton(
                            onClick = { itemToDelete = item },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                }
            }
        }
    }

    if (itemToDelete != null) {
        val item = itemToDelete!!
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Delete item?") },
            text = { Text("Are you sure you want to delete '${item.name}'? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        onItemDelete(item)
                        itemToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun TextEditorView(
    filePath: String,
    isNewFile: Boolean,
    viewModel: EditorViewModel
) {
    val content by viewModel.editorContent.collectAsState()
    val title by viewModel.editorTitle.collectAsState()
    val query by viewModel.searchQuery.collectAsState()

    var textInput by remember { mutableStateOf("") }
    // Initialize textInput once when editor content loads
    LaunchedEffect(content) {
        textInput = content
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Editor toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.navigateToExplorer() }) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 180.dp)
                )
            }

            Button(
                onClick = {
                    viewModel.updateEditorContent(textInput)
                    viewModel.saveEditorFile(filePath)
                },
                modifier = Modifier.testTag("save_file_button")
            ) {
                Icon(imageVector = Icons.Default.Check, contentDescription = "Save")
                Spacer(modifier = Modifier.width(4.dp))
                Text("Save")
            }
        }

        // Search Bar integration inside editor
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.inverseOnSurface)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search icon",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.width(4.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { viewModel.updateSearchQuery(it) },
                placeholder = { Text("Search text...", fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(max = 48.dp),
                textStyle = MaterialTheme.typography.bodySmall
            )

            if (query.isNotEmpty()) {
                val occurrences = textInput.split(query).count() - 1
                Text(
                    text = "$occurrences matches",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 8.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Main code input box
        OutlinedTextField(
            value = textInput,
            onValueChange = { textInput = it },
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .padding(8.dp)
                .testTag("text_editor_field"),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                lineHeight = 20.sp
            ),
            placeholder = { Text("Write content here...") }
        )
    }
}

@Composable
fun CompareViewScreen(
    fileAPath: String,
    fileBPath: String,
    viewModel: EditorViewModel
) {
    val lines by viewModel.comparisonLines.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.navigateToExplorer() }) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Compare Files (Side-by-Side)",
                style = MaterialTheme.typography.titleMedium
            )
        }

        // Headers showing file names
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Left File (A):",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    File(fileAPath).name,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            VerticalDivider(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp)
                    .padding(horizontal = 4.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Right File (B):",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    File(fileBPath).name,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        HorizontalDivider()

        if (lines.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(lines) { line ->
                    val bgColor = when (line.type) {
                        LineDiffType.MATCH -> Color.Transparent
                        LineDiffType.DIFFERENT -> Color(0xFFFFF9C4) // light yellow
                        LineDiffType.ONLY_A -> Color(0xFFFFCDD2) // light red
                        LineDiffType.ONLY_B -> Color(0xFFC8E6C9) // light green
                        else -> Color.Transparent
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(bgColor)
                            .padding(vertical = 4.dp, horizontal = 8.dp)
                    ) {
                        Text(
                            text = "${line.lineNumber}",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.width(30.dp)
                        )

                        // File A side
                        Text(
                            text = line.textA ?: "",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        VerticalDivider(
                            modifier = Modifier
                                .height(16.dp)
                                .width(1.dp)
                                .padding(horizontal = 4.dp)
                        )

                        // File B side
                        Text(
                            text = line.textB ?: "",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                }
            }
        }
    }
}

@Composable
fun ApkInspectorScreen(
    apkPath: String,
    viewModel: EditorViewModel
) {
    val entries by viewModel.apkEntries.collectAsState()
    val decompiledContent by viewModel.apkInspectorContent.collectAsState()
    val title by viewModel.apkInspectorTitle.collectAsState()

    val selectedEntry by viewModel.selectedApkEntry.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var isEditing by remember { mutableStateOf(false) }
    var editedText by remember { mutableStateOf("") }

    if (decompiledContent != null) {
        val entry = selectedEntry
        val isEditable = entry != null && (
            entry.name.endsWith(".txt", ignoreCase = true) ||
            entry.name.endsWith(".json", ignoreCase = true) ||
            entry.name.endsWith(".properties", ignoreCase = true) ||
            entry.name.endsWith(".html", ignoreCase = true) ||
            entry.name.endsWith(".css", ignoreCase = true) ||
            (entry.name.endsWith(".xml", ignoreCase = true) && !entry.name.lowercase().endsWith("androidmanifest.xml"))
        )

        // Detailed Entry Parser View (showing DEX headers, decoded binary xml, etc)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    isEditing = false
                    viewModel.closeApkEntryInspector()
                }) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Back")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (isEditable) {
                    if (isEditing) {
                        Button(
                            onClick = {
                                if (entry != null) {
                                    viewModel.saveApkEntry(apkPath, entry.name, editedText)
                                    isEditing = false
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2E7D32),
                                contentColor = Color.White
                            ),
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = "Save", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save & Sign", style = MaterialTheme.typography.labelMedium)
                        }
                    } else {
                        IconButton(onClick = {
                            editedText = decompiledContent ?: ""
                            isEditing = true
                        }) {
                            Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit File")
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .padding(8.dp)
            ) {
                if (isEditing) {
                    TextField(
                        value = editedText,
                        onValueChange = { editedText = it },
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 18.sp
                        ),
                        modifier = Modifier.fillMaxSize(),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent
                        )
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item {
                            Text(
                                text = decompiledContent!!,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 18.sp
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    } else {
        // Entry List screen (browsing files inside APK)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.navigateToExplorer() }) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "APK Entries Inspector",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        File(apkPath).name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Button(
                    onClick = { viewModel.repairApkSignature(apkPath) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2E7D32),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Repair Sign",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Repair Sign",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                Button(
                    onClick = { viewModel.copyApkSignature(apkPath) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Copy Sign",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Copy Sign",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            // Entry filter search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Filter zip entries...") },
                leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Search") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            )

            HorizontalDivider()

            val filteredEntries = entries.filter {
                it.name.contains(searchQuery, ignoreCase = true)
            }

            if (entries.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(filteredEntries) { entry ->
                        val extension = entry.name.substringAfterLast('.', "").lowercase()
                        val isSpecial = extension == "dex" || extension == "xml" || extension == "arsc"

                        val color = if (isSpecial) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.inspectApkEntry(apkPath, entry) }
                                .padding(vertical = 10.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isSpecial) Icons.Default.Build else Icons.Default.Edit,
                                    contentDescription = "Entry",
                                    tint = color,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = entry.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isSpecial) FontWeight.Bold else FontWeight.Normal
                                        ),
                                        color = color,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Size: ${entry.size} bytes (Compressed: ${entry.compressedSize})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }

                            if (isSpecial) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                    modifier = Modifier.padding(start = 4.dp)
                                ) {
                                    Text(
                                        text = extension.uppercase(),
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}
