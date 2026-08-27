package com.example.ui.components
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.CommonUtils
import com.example.EditorViewModel
import com.example.PanelType
import com.example.ImageMetadata
import com.example.R
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksDrawer(
    viewModel: EditorViewModel,
    activePanel: PanelType,
    leftPath: String,
    rightPath: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
                        modifier = Modifier.clickable {
                            viewModel.loadPath(activePanel, bPath)
                            onDismiss()
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun GoToLineDialog(
    lineCount: Int,
    initialInput: String = "",
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var input by remember { mutableStateOf(initialInput) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FormatListNumbered, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Lompat ke Baris")
            }
        },
        text = {
            Column {
                Text(
                    text = "Masukkan nomor baris target (1 - $lineCount):",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.filter { char -> char.isDigit() } },
                    singleLine = true,
                    placeholder = { Text("misal: 25") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val targetLine = input.toIntOrNull()
                    if (targetLine != null && targetLine in 1..lineCount) {
                        onConfirm(targetLine)
                    }
                }
            ) {
                Text("Lompat")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}

@Composable
fun UnsavedChangesDialog(
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFF9800))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Simpan Perubahan?")
            }
        },
        text = {
            Text("Ada perubahan pada berkas ini yang belum disimpan. Apakah Anda ingin menyimpannya sekarang?")
        },
        confirmButton = {
            Button(
                onClick = onSave
            ) {
                Text("Simpan & Keluar")
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = onDiscard
                ) {
                    Text("Buang Perubahan", color = Color(0xFFE53935))
                }
                TextButton(onClick = onDismiss) {
                    Text("Batal")
                }
            }
        }
    )
}

@Composable
fun PhotoInfoDialog(
    metadata: ImageMetadata,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Detail Berkas Foto", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InfoRow("Nama Berkas", metadata.fileName)
                InfoRow("Lokasi", metadata.filePath)
                InfoRow("Dimensi", "${metadata.width} × ${metadata.height} piksel (${String.format("%.1f", (metadata.width.toDouble() * metadata.height) / 1000000.0)} MP)")
                InfoRow("Ukuran Berkas", CommonUtils.formatFileSize(metadata.fileSize))
                InfoRow("Format MIME", metadata.mimeType)
                InfoRow("Terakhir Diubah", CommonUtils.formatTimestamp(metadata.lastModified))

                if (!metadata.cameraModel.isNullOrBlank()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    InfoRow("Kamera", metadata.cameraModel)
                }
                if (!metadata.dateTimeOriginal.isNullOrBlank()) {
                    InfoRow("Tanggal Pengambilan", metadata.dateTimeOriginal)
                }
                if (!metadata.iso.isNullOrBlank()) {
                    InfoRow("ISO", metadata.iso)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup")
            }
        }
    )
}

@Composable
fun RootMountDialog(
    activePath: String,
    viewModel: EditorViewModel,
    onDismiss: () -> Unit
) {
    val rootCheckState by viewModel.rootCheckState.collectAsStateWithLifecycle()
    val partitionRwState by viewModel.partitionRwState.collectAsStateWithLifecycle()
    val rootOperationLogs by viewModel.rootOperationLogs.collectAsStateWithLifecycle()

    LaunchedEffect(Unit, activePath) {
        viewModel.checkPartitionStatus(activePath)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
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
                        logScrollState.scrollTo(logScrollState.maxValue)
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
            TextButton(onClick = onDismiss) {
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

@Composable
fun TerminalDialog(
    activePath: String,
    viewModel: EditorViewModel,
    onDismiss: () -> Unit
) {
    var commandInput by remember { mutableStateOf("") }
    val terminalLogs by viewModel.terminalLogs.collectAsStateWithLifecycle()
    val isRoot by viewModel.terminalIsRoot.collectAsStateWithLifecycle()

    AlertDialog(
        onDismissRequest = onDismiss,
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
                        scrollState.scrollTo(scrollState.maxValue)
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
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Run", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
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
