package com.example
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect

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
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.FilePanelColumn
import com.example.ui.components.CodeEditorScreen
import com.example.ui.components.PhotoEditorView
import com.example.ui.components.ArchiveViewerScreen
import com.example.ui.components.ArchiveProgressDialog
import com.example.ui.components.HexEditorScreen
import com.example.ui.components.VideoPlayerScreen
import com.example.ui.components.AppManagerScreen
import com.example.ui.components.NativeStorageBrowserScreen
import com.example.ui.components.ChecksumViewerDialog
import com.example.ui.components.ApkSignerDialog
import com.example.ui.components.RootMountDialog
import com.example.ui.components.TerminalDialog
import com.example.ui.components.DoublePanelView
import com.example.ui.components.CompareViewScreen
import com.example.ui.components.ApkInspectorScreen
import com.example.ui.components.BookmarksDrawer
import com.example.ui.theme.MyApplicationTheme
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: EditorViewModel = viewModel()
            val themeMode by viewModel.themeModeFlow.collectAsStateWithLifecycle(initialValue = "System")
            val darkTheme = when (themeMode) {
                "Dark" -> true
                "Light" -> false
                else -> isSystemInDarkTheme()
            }
            MyApplicationTheme(darkTheme = darkTheme) {
                MainAppScreen(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(viewModel: EditorViewModel = viewModel()) {
    val context = LocalContext.current
    val activeView by viewModel.activeView.collectAsStateWithLifecycle()
    val isRootEnabled by viewModel.isRootEnabled.collectAsStateWithLifecycle()
    val isRootAvailable by viewModel.isRootAvailable.collectAsStateWithLifecycle()

    val leftPath by viewModel.leftPath.collectAsStateWithLifecycle()
    val rightPath by viewModel.rightPath.collectAsStateWithLifecycle()
    val activePanel by viewModel.activePanel.collectAsStateWithLifecycle()
    val leftFiles by viewModel.leftFiles.collectAsStateWithLifecycle()
    val rightFiles by viewModel.rightFiles.collectAsStateWithLifecycle()
    val selectedLeftFiles by viewModel.selectedLeftFiles.collectAsStateWithLifecycle()
    val selectedRightFiles by viewModel.selectedRightFiles.collectAsStateWithLifecycle()
    val isMultiSelectMode by viewModel.isMultiSelectMode.collectAsStateWithLifecycle()

    val activeSelectedFiles = if (activePanel == PanelType.LEFT) selectedLeftFiles else selectedRightFiles
    val selectedCount = activeSelectedFiles.size

    // Dialog state variables defined here at screen-level
    var showRootMountDialog by remember { mutableStateOf(false) }
    var showTerminalDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    val themeMode by viewModel.themeModeFlow.collectAsStateWithLifecycle(initialValue = "System")
    var checksumFileTarget by remember { mutableStateOf<FileItem?>(null) }
    var apkSignTarget by remember { mutableStateOf<FileItem?>(null) }
    var showBookmarksDrawer by remember { mutableStateOf(false) }

    // System back press navigation
    BackHandler(enabled = activeView is ActiveView.Explorer && isMultiSelectMode) {
        viewModel.clearAllSelections()
    }

    val canNavigateBack = viewModel.canNavigateBack(activePanel)
    BackHandler(enabled = activeView is ActiveView.Explorer && !isMultiSelectMode && canNavigateBack) {
        viewModel.navigateBack(activePanel)
    }

    BackHandler(enabled = activeView is ActiveView.CompareView) {
        viewModel.navigateToExplorer()
    }

    BackHandler(enabled = activeView is ActiveView.PhotoEditor) {
        viewModel.navigateToExplorer()
    }

    BackHandler(enabled = activeView is ActiveView.HexEditor) {
        viewModel.navigateToExplorer()
    }

    BackHandler(enabled = activeView is ActiveView.VideoPlayer) {
        viewModel.navigateToExplorer()
    }

    BackHandler(enabled = activeView is ActiveView.AppManager) {
        viewModel.navigateToExplorer()
    }

    BackHandler(enabled = activeView is ActiveView.StorageBrowser) {
        viewModel.navigateToExplorer()
    }

    BackHandler(enabled = activeView is ActiveView.ArchiveViewer) {
        viewModel.navigateArchiveUp()
    }

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

    LifecycleResumeEffect(Unit) {
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
        if (granted && !hasPermissions) viewModel.refreshAll()
        hasPermissions = granted
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            if (activeView is ActiveView.Explorer) {
                if (isMultiSelectMode) {
                    TopAppBar(
                        title = {
                            Text(
                                text = if (selectedCount > 0) stringResource(R.string.selected_count, selectedCount) else stringResource(R.string.multi_select),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 16.sp
                                )
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { viewModel.clearAllSelections() }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.clear_selection),
                                    tint = Color.White
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = { viewModel.selectAll(activePanel) }) {
                                Icon(
                                    imageVector = Icons.Default.SelectAll,
                                    contentDescription = stringResource(R.string.select_all),
                                    tint = Color.White
                                )
                            }
                            IconButton(onClick = { viewModel.invertSelection(activePanel) }) {
                                Icon(
                                    imageVector = Icons.Default.SwapHoriz,
                                    contentDescription = stringResource(R.string.invert_selection),
                                    tint = Color.White
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color(0xFF0D47A1)
                        )
                    )
                } else {
                    val activePath = if (activePanel == PanelType.LEFT) leftPath else rightPath
                    val activeFiles = if (activePanel == PanelType.LEFT) leftFiles else rightFiles
                    val folderCount = remember(activeFiles) { activeFiles.count { it.isDirectory && it.name != ".." } }
                    val fileCount = remember(activeFiles) { activeFiles.count { !it.isDirectory } }
                    val storageInfo = remember(activePath) { CommonUtils.getStorageInfo(activePath) }

                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = activePath,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 15.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Folder: $folderCount File: $fileCount   $storageInfo",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color.LightGray,
                                        fontSize = 10.5.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = { showBookmarksDrawer = true }) {
                                Icon(
                                    imageVector = Icons.Default.Bookmark,
                                    contentDescription = "Akses Cepat & Bookmark",
                                    tint = Color.White
                                )
                            }
                        },
                        actions = {
                            var showMenuDropdown by remember { mutableStateOf(false) }
                            IconButton(onClick = { showMenuDropdown = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More Actions",
                                    tint = Color.White
                                )
                            }

                            DropdownMenu(
                                expanded = showMenuDropdown,
                                onDismissRequest = { showMenuDropdown = false },
                                modifier = Modifier.background(Color.White)
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.multi_select), color = Color.Black) },
                                    leadingIcon = { Icon(Icons.Default.Checklist, contentDescription = null, tint = Color(0xFF1976D2)) },
                                    onClick = {
                                        showMenuDropdown = false
                                        viewModel.setMultiSelectMode(true)
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text("Manajer Aplikasi (App Extractor)", color = Color.Black) },
                                    leadingIcon = { Icon(Icons.Default.Apps, contentDescription = null, tint = Color(0xFF1976D2)) },
                                    onClick = {
                                        showMenuDropdown = false
                                        viewModel.openAppManager()
                                    }
                                )

                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.compare), color = Color.Black) },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, tint = Color.DarkGray) },
                                onClick = {
                                    showMenuDropdown = false
                                    viewModel.startComparison()
                                },
                                enabled = leftFiles.any { !it.isDirectory } && rightFiles.any { !it.isDirectory }
                            )

                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.terminal), color = Color.Black) },
                                leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.DarkGray) },
                                onClick = {
                                    showMenuDropdown = false
                                    showTerminalDialog = true
                                }
                            )

                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.root_mount_manager), color = Color.Black) },
                                leadingIcon = { Icon(Icons.Default.Build, contentDescription = null, tint = Color.DarkGray) },
                                onClick = {
                                    showMenuDropdown = false
                                    showRootMountDialog = true
                                }
                            )

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = if (isRootEnabled) stringResource(R.string.root_on) else stringResource(R.string.root_off),
                                        color = if (isRootEnabled) Color(0xFF2E7D32) else Color.Black
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = if (isRootEnabled) Color(0xFF2E7D32) else Color.DarkGray
                                    )
                                },
                                onClick = {
                                    showMenuDropdown = false
                                    if (isRootAvailable || !isRootEnabled) {
                                        viewModel.setRootEnabled(!isRootEnabled)
                                        Toast.makeText(context, context.getString(R.string.root_updated), Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, context.getString(R.string.root_not_found), Toast.LENGTH_LONG).show()
                                        viewModel.setRootEnabled(false)
                                    }
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Tema Aplikasi ($themeMode)", color = Color.Black) },
                                leadingIcon = { Icon(Icons.Default.Palette, contentDescription = null, tint = Color.DarkGray) },
                                onClick = {
                                    showMenuDropdown = false
                                    showThemeDialog = true
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Refresh", color = Color.Black) },
                                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.DarkGray) },
                                onClick = {
                                    showMenuDropdown = false
                                    viewModel.refreshAll()
                                }
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF191919)
                    )
                )
            }
        }
    },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = activeView,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
                },
                label = "ScreenTransition"
            ) { view ->
                when (view) {
                    is ActiveView.Explorer -> {
                        DoublePanelView(
                            viewModel = viewModel,
                            onShowChecksum = { item -> checksumFileTarget = item },
                            onSignApk = { item -> apkSignTarget = item }
                        )
                    }
                    is ActiveView.TextEditor -> {
                        CodeEditorScreen(
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
                    is ActiveView.PhotoEditor -> {
                        PhotoEditorView(
                            filePath = view.filePath,
                            viewModel = viewModel
                        )
                    }
                    is ActiveView.ArchiveViewer -> {
                        ArchiveViewerScreen(
                            archivePath = view.archivePath,
                            viewModel = viewModel
                        )
                    }
                    is ActiveView.HexEditor -> {
                        HexEditorScreen(
                            filePath = view.filePath,
                            viewModel = viewModel
                        )
                    }
                    is ActiveView.VideoPlayer -> {
                        VideoPlayerScreen(
                            filePath = view.filePath,
                            viewModel = viewModel
                        )
                    }
                    is ActiveView.AppManager -> {
                        AppManagerScreen(
                            viewModel = viewModel
                        )
                    }
                    is ActiveView.StorageBrowser -> {
                        NativeStorageBrowserScreen(
                            viewModel = viewModel,
                            onNavigateBack = { viewModel.navigateToExplorer() }
                        )
                    }
                }
            }

            // Global File Operation Progress Bar (for copy/move/delete)
            val operationProgress by viewModel.operationProgress.collectAsStateWithLifecycle()
            if (operationProgress != null) {
                LinearProgressIndicator(
                    progress = { operationProgress!! },
                    modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }
    }

    // Global Archive Progress Dialog
    val archiveProgressState by viewModel.archiveProgressState.collectAsStateWithLifecycle()
    if (archiveProgressState != null) {
        ArchiveProgressDialog(
            progress = archiveProgressState!!,
            onCancel = { viewModel.cancelArchiveOperation() }
        )
    }

    // Dialogs rendered here
    if (showRootMountDialog) {
        val activePath = if (activePanel == PanelType.LEFT) leftPath else rightPath
        RootMountDialog(
            activePath = activePath,
            viewModel = viewModel,
            onDismiss = { showRootMountDialog = false }
        )
    }

    if (showTerminalDialog) {
        val activePath = if (activePanel == PanelType.LEFT) leftPath else rightPath
        TerminalDialog(
            activePath = activePath,
            viewModel = viewModel,
            onDismiss = { showTerminalDialog = false }
        )
    }

    // Checksum & Hash Dialog
    if (checksumFileTarget != null) {
        ChecksumViewerDialog(
            filePath = checksumFileTarget!!.path,
            isRoot = isRootEnabled,
            onDismiss = { checksumFileTarget = null }
        )
    }

    // Sign APK Dialog
    if (apkSignTarget != null) {
        ApkSignerDialog(
            apkPath = apkSignTarget!!.path,
            viewModel = viewModel,
            onDismiss = { apkSignTarget = null }
        )
    }

    // Bookmarks & Quick Access Bottom Sheet
    if (showBookmarksDrawer) {
        BookmarksDrawer(
            viewModel = viewModel,
            activePanel = activePanel,
            leftPath = leftPath,
            rightPath = rightPath,
            onDismiss = { showBookmarksDrawer = false }
        )
    }

    if (showThemeDialog) {
        val themeOptions = listOf("System", "Light", "Dark")
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Pilih Tema Aplikasi", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column {
                    themeOptions.forEach { option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setThemeMode(option)
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = (themeMode == option),
                                onClick = { viewModel.setThemeMode(option) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = option, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("Selesai")
                }
            }
        )
    }
}
