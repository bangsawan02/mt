package com.example.ui.components
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.EditorViewModel
import com.example.PanelType
import com.example.FileItem
import com.example.R
import java.io.File

@Composable
fun DoublePanelView(
    viewModel: EditorViewModel,
    onShowChecksum: (FileItem) -> Unit,
    onSignApk: (FileItem) -> Unit
) {
    val context = LocalContext.current
    val activePanel by viewModel.activePanel.collectAsStateWithLifecycle()
    val leftPath by viewModel.leftPath.collectAsStateWithLifecycle()
    val rightPath by viewModel.rightPath.collectAsStateWithLifecycle()
    val leftFiles by viewModel.leftFiles.collectAsStateWithLifecycle()
    val rightFiles by viewModel.rightFiles.collectAsStateWithLifecycle()
    val selectedLeftFiles by viewModel.selectedLeftFiles.collectAsStateWithLifecycle()
    val selectedRightFiles by viewModel.selectedRightFiles.collectAsStateWithLifecycle()
    val isMultiSelectMode by viewModel.isMultiSelectMode.collectAsStateWithLifecycle()

    val activeSelectedFiles = if (activePanel == PanelType.LEFT) selectedLeftFiles else selectedRightFiles
    val selectedCount = activeSelectedFiles.size
    val oppositePath = if (activePanel == PanelType.LEFT) rightPath else leftPath

    var showCreateDialog by remember { mutableStateOf(false) }
    var createIsFolder by remember { mutableStateOf(false) }
    var targetPanelForCreate by remember { mutableStateOf(PanelType.LEFT) }

    var showDeleteSelectedDialog by remember { mutableStateOf(false) }
    var showCompressSelectedDialog by remember { mutableStateOf(false) }

    Scaffold(
        bottomBar = {
            if (isMultiSelectMode) {
                BottomAppBar(
                    containerColor = Color(0xFF1E293B),
                    modifier = Modifier.height(52.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (selectedCount > 0) {
                                    viewModel.copySelectedFiles(activePanel, oppositePath)
                                    Toast.makeText(context, "$selectedCount item disalin", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = selectedCount > 0
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = stringResource(R.string.copy_to_opposite),
                                tint = if (selectedCount > 0) Color.White else Color.Gray
                            )
                        }

                        IconButton(
                            onClick = {
                                if (selectedCount > 0) {
                                    viewModel.moveSelectedFiles(activePanel, oppositePath)
                                    Toast.makeText(context, "$selectedCount item dipindahkan", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = selectedCount > 0
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.DriveFileMove,
                                contentDescription = stringResource(R.string.move_to_opposite),
                                tint = if (selectedCount > 0) Color.White else Color.Gray
                            )
                        }

                        IconButton(
                            onClick = {
                                if (selectedCount > 0) {
                                    showCompressSelectedDialog = true
                                }
                            },
                            enabled = selectedCount > 0
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderZip,
                                contentDescription = stringResource(R.string.compress_zip),
                                tint = if (selectedCount > 0) Color(0xFFFFB74D) else Color.Gray
                            )
                        }

                        IconButton(
                            onClick = {
                                if (selectedCount > 0) {
                                    showDeleteSelectedDialog = true
                                }
                            },
                            enabled = selectedCount > 0
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.delete),
                                tint = if (selectedCount > 0) Color(0xFFEF5350) else Color.Gray
                            )
                        }

                        IconButton(onClick = { viewModel.clearAllSelections() }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.cancel),
                                tint = Color.White
                            )
                        }
                    }
                }
            } else {
                BottomAppBar(
                    containerColor = Color(0xFFECECEC),
                    modifier = Modifier.height(50.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val canBack = viewModel.canNavigateBack(activePanel)
                        IconButton(
                            onClick = { viewModel.navigateBack(activePanel) },
                            enabled = canBack
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back History",
                                tint = if (canBack) Color.Black else Color.LightGray.copy(alpha = 0.5f)
                            )
                        }

                        val canForward = viewModel.canNavigateForward(activePanel)
                        IconButton(
                            onClick = { viewModel.navigateForward(activePanel) },
                            enabled = canForward
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Forward History",
                                tint = if (canForward) Color.Black else Color.LightGray.copy(alpha = 0.5f)
                            )
                        }

                        IconButton(onClick = {
                            targetPanelForCreate = activePanel
                            createIsFolder = false
                            showCreateDialog = true
                        }) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Create Item",
                                tint = Color.Black
                            )
                        }

                        IconButton(onClick = {
                            viewModel.setMultiSelectMode(true)
                        }) {
                            Icon(
                                imageVector = Icons.Default.Checklist,
                                contentDescription = stringResource(R.string.multi_select),
                                tint = Color(0xFF1976D2)
                            )
                        }

                        IconButton(onClick = {
                            viewModel.setActivePanel(if (activePanel == PanelType.LEFT) PanelType.RIGHT else PanelType.LEFT)
                        }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Swap Active Panel",
                                tint = Color.Black
                            )
                        }

                        IconButton(onClick = { viewModel.navigateUp(activePanel) }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Up",
                                tint = Color.Black,
                                modifier = Modifier.rotate(90f)
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.White)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { viewModel.setActivePanel(PanelType.LEFT) }
            ) {
                FilePanelColumn(
                    title = stringResource(R.string.panel_left),
                    currentPath = leftPath,
                    files = leftFiles,
                    isActive = activePanel == PanelType.LEFT,
                    onNavigateUp = { viewModel.navigateUp(PanelType.LEFT) },
                    onFileClick = { item -> viewModel.selectFileItem(PanelType.LEFT, item) },
                    onDelete = { item -> viewModel.deleteFileItem(PanelType.LEFT, item) },
                    onPathClick = { viewModel.setActivePanel(PanelType.LEFT) },
                    onOpenArchive = { item -> viewModel.openArchiveViewer(item.path) },
                    onExtractTo = { item, targetDir -> viewModel.extractArchiveAll(item.path, targetDir) },
                    onCompressToZip = { item ->
                        val targetZip = "${item.path}.zip"
                        viewModel.compressFilesToZip(listOf(item.path), targetZip)
                    },
                    onOpenVideoPlayer = { item -> viewModel.openVideoPlayer(item.path) },
                    onOpenHexEditor = { item -> viewModel.openHexEditor(item.path) },
                    onShowChecksum = { item -> onShowChecksum(item) },
                    onSignApk = { item -> onSignApk(item) },
                    onAddBookmark = { path -> viewModel.addBookmark(path) },
                    onRename = { item, newName -> viewModel.renameFileItem(PanelType.LEFT, item, newName) },
                    onCopy = { item, targetDir -> viewModel.copyFileItem(item, targetDir) },
                    onMove = { item, targetDir -> viewModel.moveFileItem(item, targetDir) },
                    oppositePath = rightPath,
                    selectedPaths = selectedLeftFiles,
                    isMultiSelectMode = isMultiSelectMode,
                    onToggleSelection = { item -> viewModel.toggleFileSelection(PanelType.LEFT, item.path) },
                    onEnterMultiSelect = { item ->
                        viewModel.setMultiSelectMode(true)
                        viewModel.toggleFileSelection(PanelType.LEFT, item.path)
                    }
                )
            }

            VerticalDivider(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp),
                color = Color(0xFFE0E0E0)
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { viewModel.setActivePanel(PanelType.RIGHT) }
            ) {
                FilePanelColumn(
                    title = stringResource(R.string.panel_right),
                    currentPath = rightPath,
                    files = rightFiles,
                    isActive = activePanel == PanelType.RIGHT,
                    onNavigateUp = { viewModel.navigateUp(PanelType.RIGHT) },
                    onFileClick = { item -> viewModel.selectFileItem(PanelType.RIGHT, item) },
                    onDelete = { item -> viewModel.deleteFileItem(PanelType.RIGHT, item) },
                    onPathClick = { viewModel.setActivePanel(PanelType.RIGHT) },
                    onOpenArchive = { item -> viewModel.openArchiveViewer(item.path) },
                    onExtractTo = { item, targetDir -> viewModel.extractArchiveAll(item.path, targetDir) },
                    onCompressToZip = { item ->
                        val targetZip = "${item.path}.zip"
                        viewModel.compressFilesToZip(listOf(item.path), targetZip)
                    },
                    onOpenVideoPlayer = { item -> viewModel.openVideoPlayer(item.path) },
                    onOpenHexEditor = { item -> viewModel.openHexEditor(item.path) },
                    onShowChecksum = { item -> onShowChecksum(item) },
                    onSignApk = { item -> onSignApk(item) },
                    onAddBookmark = { path -> viewModel.addBookmark(path) },
                    onRename = { item, newName -> viewModel.renameFileItem(PanelType.RIGHT, item, newName) },
                    onCopy = { item, targetDir -> viewModel.copyFileItem(item, targetDir) },
                    onMove = { item, targetDir -> viewModel.moveFileItem(item, targetDir) },
                    oppositePath = leftPath,
                    selectedPaths = selectedRightFiles,
                    isMultiSelectMode = isMultiSelectMode,
                    onToggleSelection = { item -> viewModel.toggleFileSelection(PanelType.RIGHT, item.path) },
                    onEnterMultiSelect = { item ->
                        viewModel.setMultiSelectMode(true)
                        viewModel.toggleFileSelection(PanelType.RIGHT, item.path)
                    }
                )
            }
        }
    }

    if (showDeleteSelectedDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteSelectedDialog = false },
            title = { Text(stringResource(R.string.delete_item), color = Color.Black) },
            text = {
                Text(
                    stringResource(R.string.delete_multiple_confirm, selectedCount),
                    color = Color.Black
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSelectedFiles(activePanel)
                        showDeleteSelectedDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSelectedDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showCompressSelectedDialog) {
        val currentDirPath = if (activePanel == PanelType.LEFT) leftPath else rightPath
        val defaultZipName = remember(activeSelectedFiles) {
            val first = activeSelectedFiles.firstOrNull()?.let { File(it).nameWithoutExtension } ?: "archive"
            "${first}_bundle.zip"
        }
        var zipNameInput by remember { mutableStateOf(defaultZipName) }

        AlertDialog(
            onDismissRequest = { showCompressSelectedDialog = false },
            title = { Text(stringResource(R.string.compress_zip), color = Color.Black) },
            text = {
                Column {
                    Text(
                        text = "Kompres $selectedCount item ke dalam satu arsip ZIP:",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.DarkGray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = zipNameInput,
                        onValueChange = { zipNameInput = it },
                        label = { Text("Nama Berkas ZIP") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanName = if (zipNameInput.trim().endsWith(".zip", ignoreCase = true)) {
                            zipNameInput.trim()
                        } else {
                            "${zipNameInput.trim()}.zip"
                        }
                        val targetZipPath = File(currentDirPath, cleanName).absolutePath
                        viewModel.compressSelectedFiles(activePanel, targetZipPath)
                        showCompressSelectedDialog = false
                    }
                ) {
                    Text("Kompres")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCompressSelectedDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showCreateDialog) {
        var inputName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(if (createIsFolder) stringResource(R.string.create_folder) else stringResource(R.string.create_file), color = Color.Black) },
            text = {
                Column {
                    OutlinedTextField(
                        value = inputName,
                        onValueChange = { inputName = it },
                        label = { Text(stringResource(R.string.name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = createIsFolder,
                            onCheckedChange = { createIsFolder = it }
                        )
                        Text(stringResource(R.string.is_directory), color = Color.Black)
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
                    Text(stringResource(R.string.create))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
