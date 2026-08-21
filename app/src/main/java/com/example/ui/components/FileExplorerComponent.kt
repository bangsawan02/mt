package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.FileItem
import com.example.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FilePanelColumn(
    title: String,
    currentPath: String,
    files: List<FileItem>,
    isActive: Boolean,
    onNavigateUp: () -> Unit,
    onFileClick: (FileItem) -> Unit,
    onDelete: (FileItem) -> Unit,
    onPathClick: () -> Unit,
    onOpenArchive: ((FileItem) -> Unit)? = null,
    onExtractTo: ((FileItem, String) -> Unit)? = null,
    onCompressToZip: ((FileItem) -> Unit)? = null,
    onOpenHexEditor: ((FileItem) -> Unit)? = null,
    onShowChecksum: ((FileItem) -> Unit)? = null,
    onSignApk: ((FileItem) -> Unit)? = null,
    onAddBookmark: ((String) -> Unit)? = null,
    oppositePath: String = "",
    modifier: Modifier = Modifier
) {
    var filterQuery by remember { mutableStateOf("") }
    var itemToOptions by remember { mutableStateOf<FileItem?>(null) }
    var itemToDelete by remember { mutableStateOf<FileItem?>(null) }

    val filteredFiles = remember(files, filterQuery) {
        if (filterQuery.isEmpty()) files
        else files.filter { it.name.contains(filterQuery, ignoreCase = true) }
    }

    // Prepend parent folder ".." virtual item if not at root directory "/"
    val displayFiles = remember(filteredFiles, currentPath) {
        if (currentPath != "/" && currentPath.isNotEmpty()) {
            val parentPath = File(currentPath).parent ?: "/"
            val parentItem = FileItem(
                name = "..",
                path = parentPath,
                isDirectory = true,
                size = 0,
                lastModified = 0
            )
            listOf(parentItem) + filteredFiles
        } else {
            filteredFiles
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White) // MT Manager classic clean white background
    ) {
        // Filter Bar (Compact & Elegant)
        if (filterQuery.isNotEmpty() || files.size > 25) {
            OutlinedTextField(
                value = filterQuery,
                onValueChange = { filterQuery = it },
                placeholder = { Text(stringResource(R.string.filter_directory), fontSize = 11.sp, color = Color.Gray) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.Gray
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                    .height(38.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF1976D2),
                    unfocusedBorderColor = Color(0xFFE0E0E0),
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color(0xFFFAFAFA)
                ),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, color = Color.Black)
            )
        }

        // File List
        Box(modifier = Modifier.weight(1f)) {
            if (displayFiles.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (files.isEmpty()) {
                            stringResource(R.string.empty_directory)
                        } else {
                            stringResource(R.string.no_matching_files)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(displayFiles) { file ->
                        FileItemView(
                            file = file,
                            onClick = { onFileClick(file) },
                            onLongClick = {
                                if (file.name != "..") {
                                    itemToOptions = file
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Context Options Dialog
    if (itemToOptions != null) {
        val file = itemToOptions!!
        AlertDialog(
            onDismissRequest = { itemToOptions = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (file.isDirectory) Icons.Default.Folder else if (file.isArchive) Icons.Default.FolderZip else Icons.Default.InsertDriveFile,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (file.isArchive) {
                        ListItem(
                            headlineContent = { Text("Buka Isi Arsip") },
                            leadingContent = { Icon(Icons.Default.Visibility, contentDescription = null, tint = Color(0xFF1976D2)) },
                            modifier = Modifier.clickable {
                                val target = file
                                itemToOptions = null
                                onOpenArchive?.invoke(target)
                            }
                        )
                        ListItem(
                            headlineContent = { Text("Ekstrak ke Folder Ini") },
                            leadingContent = { Icon(Icons.Default.Unarchive, contentDescription = null, tint = Color(0xFF388E3C)) },
                            modifier = Modifier.clickable {
                                val target = file
                                itemToOptions = null
                                onExtractTo?.invoke(target, currentPath)
                            }
                        )
                        if (oppositePath.isNotEmpty() && oppositePath != currentPath) {
                            ListItem(
                                headlineContent = { Text("Ekstrak ke Panel Sebelah") },
                                supportingContent = { Text(oppositePath, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 10.sp) },
                                leadingContent = { Icon(Icons.Default.DriveFileMove, contentDescription = null, tint = Color(0xFF00796B)) },
                                modifier = Modifier.clickable {
                                    val target = file
                                    itemToOptions = null
                                    onExtractTo?.invoke(target, oppositePath)
                                }
                            )
                        }
                    } else if (file.isImage) {
                        ListItem(
                            headlineContent = { Text("Lihat / Sunting Foto") },
                            leadingContent = { Icon(Icons.Default.Image, contentDescription = null, tint = Color(0xFFFF9800)) },
                            modifier = Modifier.clickable {
                                val target = file
                                itemToOptions = null
                                onFileClick(target)
                            }
                        )
                        ListItem(
                            headlineContent = { Text("Kompres ke ZIP (.zip)") },
                            leadingContent = { Icon(Icons.Default.FolderZip, contentDescription = null, tint = Color(0xFFF57C00)) },
                            modifier = Modifier.clickable {
                                val target = file
                                itemToOptions = null
                                onCompressToZip?.invoke(target)
                            }
                        )
                    } else {
                        ListItem(
                            headlineContent = { Text("Kompres ke ZIP (.zip)") },
                            leadingContent = { Icon(Icons.Default.FolderZip, contentDescription = null, tint = Color(0xFFF57C00)) },
                            modifier = Modifier.clickable {
                                val target = file
                                itemToOptions = null
                                onCompressToZip?.invoke(target)
                            }
                        )
                    }

                    // Hex Editor & Checksum Options for all regular files
                    if (!file.isDirectory) {
                        ListItem(
                            headlineContent = { Text("Buka di Hex Editor") },
                            leadingContent = { Icon(Icons.Default.Code, contentDescription = null, tint = Color(0xFF5C6BC0)) },
                            modifier = Modifier.clickable {
                                val target = file
                                itemToOptions = null
                                onOpenHexEditor?.invoke(target)
                            }
                        )

                        ListItem(
                            headlineContent = { Text("Lihat Checksum & Hash") },
                            leadingContent = { Icon(Icons.Default.Fingerprint, contentDescription = null, tint = Color(0xFF00897B)) },
                            modifier = Modifier.clickable {
                                val target = file
                                itemToOptions = null
                                onShowChecksum?.invoke(target)
                            }
                        )

                        if (file.isApk) {
                            ListItem(
                                headlineContent = { Text("Tanda Tangani APK (Sign APK)") },
                                leadingContent = { Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = Color(0xFF2E7D32)) },
                                modifier = Modifier.clickable {
                                    val target = file
                                    itemToOptions = null
                                    onSignApk?.invoke(target)
                                }
                            )
                        }
                    }

                    HorizontalDivider(color = Color(0xFFEEEEEE), modifier = Modifier.padding(vertical = 4.dp))

                    ListItem(
                        headlineContent = { Text("Hapus", color = MaterialTheme.colorScheme.error) },
                        leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        modifier = Modifier.clickable {
                            val target = file
                            itemToOptions = null
                            itemToDelete = target
                        }
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { itemToOptions = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (itemToDelete != null) {
        val item = itemToDelete!!
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text(stringResource(R.string.delete_item), color = Color.Black) },
            text = { Text(stringResource(R.string.delete_confirm, item.name), color = Color.Black) },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete(item)
                        itemToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun FileItemView(
    file: FileItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    // Folder icons are dark gray / charcoal, file icons have high contrast colors
    val iconColor = when {
        file.name == ".." -> Color(0xFF2C2C2C)
        file.isDirectory -> Color(0xFF2C2C2C) // Dark gray folder matching the image
        file.isApk -> Color(0xFF4CAF50)      // Android green
        file.isArchive -> Color(0xFFE65100)  // Archive Amber/Orange
        file.isImage -> Color(0xFFFF9800)    // Image orange
        else -> Color(0xFF1976D2)            // File blue
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Elegant custom Canvas-drawn icons
            FileIcon(
                file = file,
                color = iconColor,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(10.dp))
            
            // Text Column
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color.Black,
                        fontWeight = if (file.isDirectory) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 14.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                // Show timestamp below name for all items except ".."
                if (file.name != "..") {
                    val subtext = if (file.isDirectory) {
                        formatLastModified(file.lastModified)
                    } else {
                        "${formatLastModified(file.lastModified)}   ${formatSize(file.size)}"
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

            // Symbolic link / arrow indicators matching the screen
            if (file.name != ".." && file.isDirectory && (file.name == "bin" || file.name == "bugreports" || file.name == "etc")) {
                Text(
                    text = "->",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(end = 4.dp)
                )
            }
        }
    }
}

private fun formatLastModified(timestamp: Long): String {
    if (timestamp <= 0) return "09-01-01 07:00"
    return try {
        val sdf = SimpleDateFormat("yy-MM-dd HH:mm", Locale.US)
        sdf.format(Date(timestamp))
    } catch (e: Exception) {
        "09-01-01 07:00"
    }
}

private fun formatSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    if (digitGroups < 0 || digitGroups >= units.size) return "$size B"
    return String.format(Locale.US, "%.1f%s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

@Composable
fun FileIcon(
    file: FileItem,
    color: Color,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.Canvas(modifier = modifier.size(24.dp)) {
        when {
            file.name == ".." || file.isDirectory -> {
                // Folder Shape matching standard material
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(2f.dp.toPx(), 4f.dp.toPx())
                    lineTo(8f.dp.toPx(), 4f.dp.toPx())
                    lineTo(10f.dp.toPx(), 7f.dp.toPx())
                    lineTo(22f.dp.toPx(), 7f.dp.toPx())
                    lineTo(22f.dp.toPx(), 20f.dp.toPx())
                    lineTo(2f.dp.toPx(), 20f.dp.toPx())
                    close()
                }
                drawPath(path, color = color)
            }
            file.isApk -> {
                // Bugdroid shape
                drawArc(
                    color = color,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = true,
                    size = androidx.compose.ui.geometry.Size(16f.dp.toPx(), 16f.dp.toPx()),
                    topLeft = androidx.compose.ui.geometry.Offset(4f.dp.toPx(), 6f.dp.toPx())
                )
                drawLine(
                    color = color,
                    start = androidx.compose.ui.geometry.Offset(7f.dp.toPx(), 7f.dp.toPx()),
                    end = androidx.compose.ui.geometry.Offset(4f.dp.toPx(), 2f.dp.toPx()),
                    strokeWidth = 2f.dp.toPx()
                )
                drawLine(
                    color = color,
                    start = androidx.compose.ui.geometry.Offset(17f.dp.toPx(), 7f.dp.toPx()),
                    end = androidx.compose.ui.geometry.Offset(20f.dp.toPx(), 2f.dp.toPx()),
                    strokeWidth = 2f.dp.toPx()
                )
                drawCircle(
                    color = Color.White,
                    radius = 1.5f.dp.toPx(),
                    center = androidx.compose.ui.geometry.Offset(9f.dp.toPx(), 11f.dp.toPx())
                )
                drawCircle(
                    color = Color.White,
                    radius = 1.5f.dp.toPx(),
                    center = androidx.compose.ui.geometry.Offset(15f.dp.toPx(), 11f.dp.toPx())
                )
            }
            file.isArchive -> {
                // Archive / ZIP box shape with zipper accents
                val boxPath = androidx.compose.ui.graphics.Path().apply {
                    moveTo(3f.dp.toPx(), 3f.dp.toPx())
                    lineTo(21f.dp.toPx(), 3f.dp.toPx())
                    lineTo(21f.dp.toPx(), 21f.dp.toPx())
                    lineTo(3f.dp.toPx(), 21f.dp.toPx())
                    close()
                }
                drawPath(boxPath, color = color)

                // Zipper teeth / stripe
                drawLine(
                    color = Color.White,
                    start = androidx.compose.ui.geometry.Offset(12f.dp.toPx(), 3f.dp.toPx()),
                    end = androidx.compose.ui.geometry.Offset(12f.dp.toPx(), 15f.dp.toPx()),
                    strokeWidth = 2f.dp.toPx()
                )
                for (y in 4..12 step 3) {
                    val yPx = y.toFloat().dp.toPx()
                    drawLine(
                        color = Color.White,
                        start = androidx.compose.ui.geometry.Offset(9f.dp.toPx(), yPx),
                        end = androidx.compose.ui.geometry.Offset(15f.dp.toPx(), yPx),
                        strokeWidth = 1.5f.dp.toPx()
                    )
                }
                // Zipper pull
                drawRoundRect(
                    color = Color.White,
                    topLeft = androidx.compose.ui.geometry.Offset(10f.dp.toPx(), 14f.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(4f.dp.toPx(), 5f.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(1f.dp.toPx(), 1f.dp.toPx())
                )
            }
            file.isImage -> {
                // Landscape frame with sun and mountains
                drawRoundRect(
                    color = color,
                    topLeft = androidx.compose.ui.geometry.Offset(2f.dp.toPx(), 4f.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(20f.dp.toPx(), 16f.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f.dp.toPx(), 2f.dp.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f.dp.toPx())
                )
                drawCircle(
                    color = color,
                    radius = 2.5f.dp.toPx(),
                    center = androidx.compose.ui.geometry.Offset(7f.dp.toPx(), 9f.dp.toPx())
                )
                val mountain = androidx.compose.ui.graphics.Path().apply {
                    moveTo(4f.dp.toPx(), 18f.dp.toPx())
                    lineTo(11f.dp.toPx(), 11f.dp.toPx())
                    lineTo(16f.dp.toPx(), 16f.dp.toPx())
                    lineTo(20f.dp.toPx(), 12f.dp.toPx())
                    lineTo(20f.dp.toPx(), 18f.dp.toPx())
                    close()
                }
                drawPath(mountain, color = color)
            }
            else -> {
                // Sheet of paper with folded corner
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(4f.dp.toPx(), 2f.dp.toPx())
                    lineTo(16f.dp.toPx(), 2f.dp.toPx())
                    lineTo(20f.dp.toPx(), 6f.dp.toPx())
                    lineTo(20f.dp.toPx(), 22f.dp.toPx())
                    lineTo(4f.dp.toPx(), 22f.dp.toPx())
                    close()
                }
                drawPath(path, color = color)
                val cornerPath = androidx.compose.ui.graphics.Path().apply {
                    moveTo(16f.dp.toPx(), 2f.dp.toPx())
                    lineTo(16f.dp.toPx(), 6f.dp.toPx())
                    lineTo(20f.dp.toPx(), 6f.dp.toPx())
                    close()
                }
                drawPath(cornerPath, color = color.copy(alpha = 0.6f))
            }
        }
    }
}

