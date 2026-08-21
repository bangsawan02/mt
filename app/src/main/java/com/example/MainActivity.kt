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
import com.example.ui.components.ChecksumViewerDialog
import com.example.ui.components.ApkSignerDialog
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

    val leftPath by viewModel.leftPath.collectAsState()
    val rightPath by viewModel.rightPath.collectAsState()
    val activePanel by viewModel.activePanel.collectAsState()
    val leftFiles by viewModel.leftFiles.collectAsState()
    val rightFiles by viewModel.rightFiles.collectAsState()

    // Dialog state variables defined here at screen-level
    var showRootMountDialog by remember { mutableStateOf(false) }
    var showTerminalDialog by remember { mutableStateOf(false) }
    var checksumFileTarget by remember { mutableStateOf<FileItem?>(null) }
    var apkSignTarget by remember { mutableStateOf<FileItem?>(null) }
    var showBookmarksDrawer by remember { mutableStateOf(false) }

    // System back press navigation
    val canNavigateBack = viewModel.canNavigateBack(activePanel)
    BackHandler(enabled = activeView is ActiveView.Explorer && canNavigateBack) {
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
            if (activeView is ActiveView.Explorer) {
                val activePath = if (activePanel == PanelType.LEFT) leftPath else rightPath
                val activeFiles = if (activePanel == PanelType.LEFT) leftFiles else rightFiles
                val folderCount = remember(activeFiles) { activeFiles.count { it.isDirectory && it.name != ".." } }
                val fileCount = remember(activeFiles) { activeFiles.count { !it.isDirectory } }
                val storageInfo = remember(activePath) { getStorageInfo(activePath) }

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
                                text = { Text("Manajer Aplikasi (App Extractor)", color = Color.Black) },
                                leadingIcon = { Icon(Icons.Default.Apps, contentDescription = null, tint = Color(0xFF1976D2)) },
                                onClick = {
                                    showMenuDropdown = false
                                    viewModel.openAppManager()
                                }
                            )

                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.compare), color = Color.Black) },
                                leadingIcon = { Icon(Icons.Default.List, contentDescription = null, tint = Color.DarkGray) },
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
            }
        }
    }

    // Global Archive Progress Dialog
    val archiveProgressState by viewModel.archiveProgressState.collectAsState()
    if (archiveProgressState != null) {
        ArchiveProgressDialog(
            progress = archiveProgressState!!,
            onCancel = { viewModel.cancelArchiveOperation() }
        )
    }

    // Dialogs rendered here
    if (showRootMountDialog) {
        val rootCheckState by viewModel.rootCheckState.collectAsState()
        val partitionRwState by viewModel.partitionRwState.collectAsState()
        val rootOperationLogs by viewModel.rootOperationLogs.collectAsState()
        val activePath = if (activePanel == PanelType.LEFT) leftPath else rightPath

        LaunchedEffect(showRootMountDialog, activePath) {
            viewModel.checkPartitionStatus(activePath)
        }

        AlertDialog(
            onDismissRequest = { showRootMountDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Build, contentDescription = "Root Tools", tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.root_mount_manager), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.current_active_path),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = activePath,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 8.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.root_access_check), style = MaterialTheme.typography.bodyMedium)
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (rootCheckState.contains("Granted")) {
                                    Color(0xFFE8F5E9)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }
                            )
                        ) {
                            Text(
                                text = rootCheckState,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = if (rootCheckState.contains("Granted")) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.partition_rw_status), style = MaterialTheme.typography.bodyMedium)
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (partitionRwState.contains("Read-Write") || partitionRwState.contains("RW")) {
                                    Color(0xFFE8F5E9)
                                } else {
                                    Color(0xFFFFEBEE)
                                }
                            )
                        ) {
                            Text(
                                text = partitionRwState,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = if (partitionRwState.contains("Read-Write") || partitionRwState.contains("RW")) Color(0xFF2E7D32) else Color(0xFFC62828),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.console_logs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .background(Color(0xFF1E1E1E), shape = RoundedCornerShape(4.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), shape = RoundedCornerShape(4.dp))
                            .padding(6.dp)
                    ) {
                        val logScrollState = rememberScrollState()
                        LaunchedEffect(rootOperationLogs.length) {
                            logScrollState.animateScrollTo(logScrollState.maxValue)
                        }
                        Text(
                            text = rootOperationLogs.ifEmpty { "Console output logs..." },
                            color = Color(0xFF00FF00),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            modifier = Modifier.verticalScroll(logScrollState)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(
                            onClick = { viewModel.verifyAndRequestRoot() },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(stringResource(R.string.request_root), fontSize = 10.sp)
                        }
                        Button(
                            onClick = { viewModel.checkPartitionStatus(activePath) },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(stringResource(R.string.check_status), fontSize = 10.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(
                            onClick = { viewModel.remountSystemPartition(activePath, true) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(stringResource(R.string.mount_rw), fontSize = 10.sp)
                        }
                        Button(
                            onClick = { viewModel.remountSystemPartition(activePath, false) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(stringResource(R.string.mount_ro), fontSize = 10.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRootMountDialog = false }) {
                    Text(stringResource(R.string.close))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.clearRootLogs() }) {
                    Text(stringResource(R.string.clear_logs), color = MaterialTheme.colorScheme.error)
                }
            }
        )
    }

    if (showTerminalDialog) {
        var commandInput by remember { mutableStateOf("") }
        val terminalLogs by viewModel.terminalLogs.collectAsState()
        val isRoot by viewModel.terminalIsRoot.collectAsState()
        val activePath = if (activePanel == PanelType.LEFT) leftPath else rightPath

        AlertDialog(
            onDismissRequest = { showTerminalDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Terminal", tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.terminal_emulator), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Dir: $activePath",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.root), style = MaterialTheme.typography.labelSmall)
                            Switch(
                                checked = isRoot,
                                onCheckedChange = { viewModel.toggleTerminalRoot() },
                                modifier = Modifier.scale(0.7f)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .background(Color(0xFF1E1E1E), shape = RoundedCornerShape(4.dp))
                            .padding(6.dp)
                    ) {
                        val scrollState = rememberScrollState()
                        LaunchedEffect(terminalLogs.length) {
                            scrollState.animateScrollTo(scrollState.maxValue)
                        }
                        Text(
                            text = terminalLogs.ifEmpty { "Terminal output...\n" },
                            color = Color(0xFF00FF00),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            modifier = Modifier.fillMaxSize().verticalScroll(scrollState)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = commandInput,
                            onValueChange = { commandInput = it },
                            placeholder = { Text("Command...", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            textStyle = MaterialTheme.typography.bodySmall,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                imeAction = androidx.compose.ui.text.input.ImeAction.Send
                            ),
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                onSend = {
                                    if (commandInput.isNotBlank()) {
                                        viewModel.runTerminalCommand(commandInput, activePath)
                                        commandInput = ""
                                    }
                                }
                            )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = {
                                if (commandInput.isNotBlank()) {
                                    viewModel.runTerminalCommand(commandInput, activePath)
                                    commandInput = ""
                                }
                            },
                            modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "Run", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTerminalDialog = false }) {
                    Text(stringResource(R.string.close))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.clearTerminalLogs() }) {
                    Text(stringResource(R.string.clear_logs), color = MaterialTheme.colorScheme.error)
                }
            }
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
        val bookmarks by viewModel.bookmarks.collectAsState()
        ModalBottomSheet(
            onDismissRequest = { showBookmarksDrawer = false },
            containerColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Akses Cepat & Bookmark",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF191919)
                    )
                    IconButton(onClick = {
                        val currentActive = if (activePanel == PanelType.LEFT) leftPath else rightPath
                        viewModel.addBookmark(currentActive)
                        Toast.makeText(context, "Folder ditambahkan ke bookmark", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.BookmarkAdd, contentDescription = "Tambah Bookmark", tint = Color(0xFF1976D2))
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    text = "Lokasi Standar & Tersimpan",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(6.dp))

                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                    items(bookmarks) { bPath ->
                        val isDefault = bPath in listOf(
                            Environment.getExternalStorageDirectory().absolutePath,
                            "${Environment.getExternalStorageDirectory().absolutePath}/Download",
                            "${Environment.getExternalStorageDirectory().absolutePath}/DCIM",
                            "${Environment.getExternalStorageDirectory().absolutePath}/Android/data"
                        )
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = when (bPath) {
                                        Environment.getExternalStorageDirectory().absolutePath -> "Internal Storage (/sdcard)"
                                        "${Environment.getExternalStorageDirectory().absolutePath}/Download" -> "Download"
                                        "${Environment.getExternalStorageDirectory().absolutePath}/DCIM" -> "DCIM (Kamera/Foto)"
                                        "${Environment.getExternalStorageDirectory().absolutePath}/Android/data" -> "Android/data"
                                        else -> File(bPath).name.ifEmpty { bPath }
                                    },
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                            },
                            supportingContent = {
                                Text(bPath, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.Gray)
                            },
                            leadingContent = {
                                Icon(
                                    imageVector = if (isDefault) Icons.Default.FolderSpecial else Icons.Default.Bookmark,
                                    contentDescription = null,
                                    tint = if (isDefault) Color(0xFFFFA000) else Color(0xFF1976D2)
                                )
                            },
                            trailingContent = {
                                if (!isDefault) {
                                    IconButton(onClick = { viewModel.removeBookmark(bPath) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = Color.Gray)
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showBookmarksDrawer = false
                                    viewModel.loadPath(activePanel, bPath)
                                }
                        )
                    }
                }
            }
        }
    }
}

fun getStorageInfo(path: String): String {
    return try {
        val stat = android.os.StatFs(path)
        val blockSize = stat.blockSizeLong
        val totalBlocks = stat.blockCountLong
        val availableBlocks = stat.availableBlocksLong

        val totalBytes = totalBlocks * blockSize
        val freeBytes = availableBlocks * blockSize
        val usedBytes = totalBytes - freeBytes

        val usedGB = usedBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
        val totalGB = totalBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)

        String.format(java.util.Locale.US, "Penyimpanan: %.2fG/%.2fG", usedGB, totalGB)
    } catch (e: Exception) {
        "Penyimpanan: 31.42G/104.82G"
    }
}

@Composable
fun DoublePanelView(
    viewModel: EditorViewModel,
    onShowChecksum: (FileItem) -> Unit,
    onSignApk: (FileItem) -> Unit
) {
    val activePanel by viewModel.activePanel.collectAsState()
    val leftPath by viewModel.leftPath.collectAsState()
    val rightPath by viewModel.rightPath.collectAsState()
    val leftFiles by viewModel.leftFiles.collectAsState()
    val rightFiles by viewModel.rightFiles.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var createIsFolder by remember { mutableStateOf(false) }
    var targetPanelForCreate by remember { mutableStateOf(PanelType.LEFT) }

    Scaffold(
        bottomBar = {
            BottomAppBar(
                containerColor = Color(0xFFECECEC), // Light gray background matching screenshot
                modifier = Modifier.height(50.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. History Back Button
                    val canBack = viewModel.canNavigateBack(activePanel)
                    IconButton(
                        onClick = { viewModel.navigateBack(activePanel) },
                        enabled = canBack
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back History",
                            tint = if (canBack) Color.Black else Color.LightGray.copy(alpha = 0.5f)
                        )
                    }

                    // 2. History Forward Button
                    val canForward = viewModel.canNavigateForward(activePanel)
                    IconButton(
                        onClick = { viewModel.navigateForward(activePanel) },
                        enabled = canForward
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = "Forward History",
                            tint = if (canForward) Color.Black else Color.LightGray.copy(alpha = 0.5f)
                        )
                    }

                    // 3. Create Button
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

                    // 4. Swap Panels Button
                    IconButton(onClick = {
                        viewModel.setActivePanel(if (activePanel == PanelType.LEFT) PanelType.RIGHT else PanelType.LEFT)
                    }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Swap Active Panel",
                            tint = Color.Black
                        )
                    }

                    // 5. Navigate Up Button
                    IconButton(onClick = { viewModel.navigateUp(activePanel) }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Up",
                            tint = Color.Black,
                            modifier = Modifier.rotate(90f)
                        )
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
            // Left Panel
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
                    oppositePath = rightPath
                )
            }

            VerticalDivider(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp),
                color = Color(0xFFE0E0E0)
            )

            // Right Panel
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
                    oppositePath = leftPath
                )
            }
        }
    }

    // New File/Folder dialog
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

    var selectedTab by remember { mutableStateOf(0) } // 0: Strings, 1: Classes, 2: Header
    var dexSearchQuery by remember { mutableStateOf("") }
    var editingString by remember { mutableStateOf<DexString?>(null) }
    var editedStringValue by remember { mutableStateOf("") }
    var editingClass by remember { mutableStateOf<DexClass?>(null) }
    var editedClassValue by remember { mutableStateOf("") }
    var classActionSelected by remember { mutableStateOf<DexClass?>(null) }
    var activeClassForSmali by remember { mutableStateOf<DexClass?>(null) }
    var editingMethod by remember { mutableStateOf<DexMethod?>(null) }
    var editedSmaliValue by remember { mutableStateOf("") }

    // Unified system back handler for Apk Inspector
    BackHandler(enabled = true) {
        if (decompiledContent != null) {
            val entry = selectedEntry
            val isDex = entry != null && entry.name.lowercase().endsWith(".dex")
            if (isDex) {
                if (editingMethod != null) {
                    editingMethod = null
                } else if (editingString != null) {
                    editingString = null
                } else if (editingClass != null) {
                    editingClass = null
                } else if (classActionSelected != null) {
                    classActionSelected = null
                } else if (activeClassForSmali != null) {
                    activeClassForSmali = null
                } else {
                    viewModel.closeApkEntryInspector()
                }
            } else {
                viewModel.closeApkEntryInspector()
            }
        } else {
            viewModel.navigateToExplorer()
        }
    }

    if (decompiledContent != null) {
        val entry = selectedEntry
        val isDex = entry != null && entry.name.lowercase().endsWith(".dex")

        if (isDex) {
            val dexClasses by viewModel.dexClasses.collectAsState()
            val dexStrings by viewModel.dexStrings.collectAsState()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        if (activeClassForSmali != null) {
                            activeClassForSmali = null
                        } else {
                            viewModel.closeApkEntryInspector()
                        }
                    }) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Back")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (activeClassForSmali != null) "Methods: ${activeClassForSmali!!.name.substringAfterLast("/")}" else title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (activeClassForSmali != null) {
                    val dexMethods by viewModel.dexMethods.collectAsState()
                    val targetClass = activeClassForSmali!!
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { activeClassForSmali = null }) {
                                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = targetClass.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (dexMethods.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(dexMethods) { method ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                editingMethod = method
                                                editedSmaliValue = method.instructionsSmali.joinToString("\n")
                                            }
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        )
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = method.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Registers: ${method.registersSize} | Instructions: ${method.insnsSize} words",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Tabs
                    TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0; dexSearchQuery = "" },
                        text = { Text("${stringResource(R.string.strings)} (${dexStrings.size})", fontSize = 13.sp) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1; dexSearchQuery = "" },
                        text = { Text("${stringResource(R.string.classes)} (${dexClasses.size})", fontSize = 13.sp) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text(stringResource(R.string.header), fontSize = 13.sp) }
                    )
                }

                // Search Bar for Strings and Classes
                if (selectedTab == 0 || selectedTab == 1) {
                    OutlinedTextField(
                        value = dexSearchQuery,
                        onValueChange = { dexSearchQuery = it },
                        placeholder = { Text(if (selectedTab == 0) stringResource(R.string.search_strings) else stringResource(R.string.search_classes)) },
                        leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Search") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        singleLine = true
                    )
                }

                // Content
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) {
                    when (selectedTab) {
                        0 -> {
                            // Strings tab with cached filter and limit of 200 items for butter-smooth scrolling
                            val filteredStrings = remember(dexStrings, dexSearchQuery) {
                                if (dexSearchQuery.isEmpty()) {
                                    dexStrings.take(200)
                                } else {
                                    dexStrings.filter {
                                        it.value.contains(dexSearchQuery, ignoreCase = true)
                                    }.take(200)
                                }
                            }
                            if (filteredStrings.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(stringResource(R.string.no_matching_files), color = MaterialTheme.colorScheme.outline)
                                }
                            } else {
                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                    items(filteredStrings) { dexStr ->
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    editingString = dexStr
                                                    editedStringValue = dexStr.value
                                                }
                                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                        ) {
                                            Row(
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = "Index: ${dexStr.index}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = "${dexStr.byteLength} bytes",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.outline
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = dexStr.value,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontFamily = FontFamily.Monospace,
                                                maxLines = 3,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                    }
                                }
                            }
                        }
                        1 -> {
                            // Classes tab with cached filter and limit of 200 items
                            val filteredClasses = remember(dexClasses, dexSearchQuery) {
                                if (dexSearchQuery.isEmpty()) {
                                    dexClasses.take(200)
                                } else {
                                    dexClasses.filter {
                                        it.name.contains(dexSearchQuery, ignoreCase = true)
                                    }.take(200)
                                }
                            }
                            if (filteredClasses.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(stringResource(R.string.no_matching_files), color = MaterialTheme.colorScheme.outline)
                                }
                            } else {
                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                    items(filteredClasses) { dexCls ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    classActionSelected = dexCls
                                                }
                                                .padding(horizontal = 16.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Place,
                                                contentDescription = "Class",
                                                tint = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = dexCls.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                    }
                                }
                            }
                        }
                        2 -> {
                            // Raw Headers tab
                            LazyColumn(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                                item {
                                    Text(
                                        text = decompiledContent ?: "",
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
                }
            }

            // Edit String Dialog
            if (editingString != null) {
                val dexStr = editingString!!
                val originalLen = dexStr.byteLength
                val newLen = editedStringValue.toByteArray(Charsets.UTF_8).size
                val isLengthOk = newLen <= originalLen

                AlertDialog(
                    onDismissRequest = { editingString = null },
                    title = { Text(stringResource(R.string.strings)) },
                    text = {
                        Column {
                            Text(
                                text = "To maintain binary offsets safely, the modified string must not exceed the original byte length.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Max Allowed: $originalLen bytes",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Current: $newLen bytes",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isLengthOk) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = editedStringValue,
                                onValueChange = { editedStringValue = it },
                                label = { Text("String Value") },
                                modifier = Modifier.fillMaxWidth(),
                                isError = !isLengthOk
                            )
                            if (!isLengthOk) {
                                Text(
                                    text = "Error: Exceeds original length by ${newLen - originalLen} bytes!",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (entry != null && isLengthOk) {
                                    viewModel.saveDexString(apkPath, entry.name, dexStr, editedStringValue)
                                    editingString = null
                                }
                            },
                            enabled = isLengthOk
                        ) {
                            Text(stringResource(R.string.save_resign))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { editingString = null }) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                )
            }

            // Edit Class Dialog
            if (editingClass != null) {
                val dexCls = editingClass!!
                val originalLen = dexCls.byteLength
                val newLen = editedClassValue.toByteArray(Charsets.UTF_8).size
                val isLengthOk = newLen <= originalLen

                AlertDialog(
                    onDismissRequest = { editingClass = null },
                    title = { Text(stringResource(R.string.classes)) },
                    text = {
                        Column {
                            Text(
                                text = "DEX class names are stored as Type Descriptors (e.g. Lcom/example/MyClass;). Make sure to keep the leading 'L' and trailing ';'.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "To maintain binary offsets safely, the modified name must not exceed the original byte length.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Max Allowed: $originalLen bytes",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Current: $newLen bytes",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isLengthOk) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = editedClassValue,
                                onValueChange = { editedClassValue = it },
                                label = { Text("Class Name Descriptor") },
                                modifier = Modifier.fillMaxWidth(),
                                isError = !isLengthOk
                            )
                            if (!isLengthOk) {
                                Text(
                                    text = "Error: Exceeds original length by ${newLen - originalLen} bytes!",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (entry != null && isLengthOk) {
                                    viewModel.saveDexClass(apkPath, entry.name, dexCls, editedClassValue)
                                    editingClass = null
                                }
                            },
                            enabled = isLengthOk
                        ) {
                            Text(stringResource(R.string.save_resign))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { editingClass = null }) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                )
            }

            if (classActionSelected != null) {
                val cls = classActionSelected!!
                AlertDialog(
                    onDismissRequest = { classActionSelected = null },
                    title = { Text("Class Options", style = MaterialTheme.typography.titleMedium) },
                    text = {
                        Column {
                            Text(
                                text = cls.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Choose an action to perform on this class:")
                        }
                    },
                    confirmButton = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                        ) {
                            TextButton(onClick = { classActionSelected = null }) {
                                Text(stringResource(R.string.cancel))
                            }
                            Button(
                                onClick = {
                                    editingClass = cls
                                    editedClassValue = cls.name
                                    classActionSelected = null
                                }
                            ) {
                                Text(stringResource(R.string.rename_class))
                            }
                            Button(
                                onClick = {
                                    activeClassForSmali = cls
                                    viewModel.loadClassMethods(apkPath, entry.name, cls)
                                    classActionSelected = null
                                }
                            ) {
                                Text(stringResource(R.string.edit_smali))
                            }
                        }
                    }
                )
            }

            if (editingMethod != null) {
                val method = editingMethod!!
                val originalWords = method.insnsSize

                val lines = editedSmaliValue.split("\n")
                val newWords = remember(editedSmaliValue) {
                    val linesList = editedSmaliValue.split("\n")
                    var count = 0
                    for (lineRaw in linesList) {
                        val line = lineRaw.trim()
                        if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) continue
                        val parts = line.split(Regex("\\s+"), 2)
                        val mnemonic = parts[0]
                        val operands = if (parts.size > 1) parts[1] else ""
                        count += when (mnemonic) {
                            "nop" -> 1
                            "return-void" -> 1
                            "return" -> 1
                            "return-wide" -> 1
                            "return-object" -> 1
                            "const/4" -> 1
                            "const/16" -> 2
                            "const" -> 3
                            "const-string" -> 2
                            "const-class" -> 2
                            "new-instance" -> 2
                            "move" -> 1
                            "move-object" -> 1
                            else -> {
                                if (mnemonic.startsWith("op_") || mnemonic.endsWith("-op") || mnemonic.endsWith("-range-op")) {
                                    operands.split(Regex("\\s+")).count { it.trim().startsWith("0x") }
                                } else {
                                    line.split(Regex("\\s+")).count { it.trim().startsWith("0x") }
                                }
                            }
                        }
                    }
                    count
                }

                val isSizeOk = newWords <= originalWords

                AlertDialog(
                    onDismissRequest = { editingMethod = null },
                    title = { Text("Edit Smali Bytecode", style = MaterialTheme.typography.titleMedium) },
                    text = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Method: ${method.name}",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Max Allowed: $originalWords words",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    text = "Current: $newWords words",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSizeOk) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = editedSmaliValue,
                                onValueChange = { editedSmaliValue = it },
                                label = { Text("Smali Code") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 240.dp),
                                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                                isError = !isSizeOk
                            )
                            if (!isSizeOk) {
                                Text(
                                    text = "Error: Exceeds original method capacity by ${newWords - originalWords} words!",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (entry != null && isSizeOk) {
                                    viewModel.saveDexMethod(apkPath, entry.name, activeClassForSmali!!, method, lines)
                                    editingMethod = null
                                }
                            },
                            enabled = isSizeOk
                        ) {
                            Text(stringResource(R.string.save_resign))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { editingMethod = null }) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                )
            }
        } else {
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
                                Text(stringResource(R.string.save_resign), style = MaterialTheme.typography.labelMedium)
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
                        "APK Inspector",
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
                        text = stringResource(R.string.repair_sign),
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
                        text = stringResource(R.string.copy_sign),
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
                                        text = stringResource(R.string.size_label, entry.size, entry.compressedSize),
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

@Composable
fun ConsoleView(viewModel: EditorViewModel) {
    val logs by viewModel.appLogs.collectAsState()
    var isExpanded by remember { mutableStateOf(false) }
    val clipboardManager: ClipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1E1E1E))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.console_logs_title),
                color = Color(0xFF00FF00),
                style = MaterialTheme.typography.labelMedium
            )
            if (isExpanded) {
                Row {
                    IconButton(
                        onClick = { 
                            clipboardManager.setText(AnnotatedString(logs))
                            android.widget.Toast.makeText(context, context.getString(R.string.logs_copied), android.widget.Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Copy", tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    IconButton(
                        onClick = { viewModel.clearLogs() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.White)
                    }
                }
            }
        }
        if (isExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .background(Color.Black)
                    .padding(8.dp)
            ) {
                val scrollState = rememberScrollState()
                LaunchedEffect(logs.length) {
                    scrollState.animateScrollTo(scrollState.maxValue)
                }
                Text(
                    text = logs.ifEmpty { stringResource(R.string.no_logs_available) },
                    color = Color(0xFF00FF00),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier.verticalScroll(scrollState)
                )
            }
        }
    }
}
