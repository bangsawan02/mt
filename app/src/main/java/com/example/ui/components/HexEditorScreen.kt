package com.example.ui.components

import android.content.Context
import android.widget.Toast
import java.util.Locale
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.EditorViewModel
import com.example.RootUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

private const val BYTES_PER_ROW = 16
private const val MAX_VIEW_BYTES = 512 * 1024 // 512 KB per chunk to stay lightning fast & memory safe

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HexEditorScreen(
    filePath: String,
    viewModel: EditorViewModel
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val isRootEnabled by viewModel.isRootEnabled.collectAsState()

    var fileData by remember { mutableStateOf<ByteArray?>(null) }
    var originalData by remember { mutableStateOf<ByteArray?>(null) }
    var fileSize by remember { mutableStateOf(0L) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }

    // Selected byte index for editing / inspecting
    var selectedByteIndex by remember { mutableStateOf<Int?>(null) }
    var showEditByteDialog by remember { mutableStateOf(false) }
    var editByteHexInput by remember { mutableStateOf("") }
    var editByteAsciiInput by remember { mutableStateOf("") }

    // Search state
    var showSearchDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isHexSearch by remember { mutableStateOf(true) }
    var searchResultIndices by remember { mutableStateOf<List<Int>>(emptyList()) }
    var currentResultPointer by remember { mutableStateOf(0) }

    // Jump to offset
    var showGotoDialog by remember { mutableStateOf(false) }
    var gotoOffsetInput by remember { mutableStateOf("") }

    val listState = rememberLazyListState()

    // Load file bytes
    LaunchedEffect(filePath, isRootEnabled) {
        isLoading = true
        withContext(Dispatchers.IO) {
            try {
                val file = File(filePath)
                var targetFile = file
                if (isRootEnabled && !file.canRead()) {
                    val temp = File(context.cacheDir, "hex_temp_${System.currentTimeMillis()}.bin")
                    val cmd = "cp \"$filePath\" \"${temp.absolutePath}\" && chmod 777 \"${temp.absolutePath}\""
                    val res = RootUtils.executeCommand(cmd, true)
                    if (res.success && temp.exists()) {
                        targetFile = temp
                    }
                }

                fileSize = targetFile.length()
                val bytesToRead = fileSize.coerceAtMost(MAX_VIEW_BYTES.toLong()).toInt()
                val raf = RandomAccessFile(targetFile, "r")
                val buffer = ByteArray(bytesToRead)
                raf.readFully(buffer)
                raf.close()

                fileData = buffer
                originalData = buffer.copyOf()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Gagal membaca berkas biner: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                isLoading = false
            }
        }
    }

    val totalRows = remember(fileData) {
        val size = fileData?.size ?: 0
        if (size == 0) 0 else (size + BYTES_PER_ROW - 1) / BYTES_PER_ROW
    }

    val hasModifications = remember(fileData, originalData) {
        if (fileData == null || originalData == null) false
        else !fileData!!.contentEquals(originalData!!)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = File(filePath).name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Ukuran: ${formatBytes(fileSize)} ${if (fileSize > MAX_VIEW_BYTES) "(Pratinjau 512KB)" else ""}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateToExplorer() }) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    // Search
                    IconButton(onClick = { showSearchDialog = true }) {
                        Icon(Icons.Default.Search, contentDescription = "Cari Hex/Teks")
                    }

                    // Goto Offset
                    IconButton(onClick = { showGotoDialog = true }) {
                        Icon(Icons.Default.Directions, contentDescription = "Lompat ke Offset")
                    }

                    // Save
                    IconButton(
                        onClick = {
                            if (fileData != null && hasModifications) {
                                isSaving = true
                                coroutineScope.launch(Dispatchers.IO) {
                                    try {
                                        val file = File(filePath)
                                        if (isRootEnabled && !file.canWrite()) {
                                            val temp = File(context.cacheDir, "hex_save_${System.currentTimeMillis()}.bin")
                                            temp.writeBytes(fileData!!)
                                            val cmd = "cp \"${temp.absolutePath}\" \"$filePath\" && chmod 666 \"$filePath\""
                                            RootUtils.executeCommand(cmd, true)
                                            temp.delete()
                                        } else {
                                            file.writeBytes(fileData!!)
                                        }
                                        originalData = fileData!!.copyOf()
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "Perubahan biner berhasil disimpan!", Toast.LENGTH_SHORT).show()
                                            viewModel.refreshAll()
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "Gagal menyimpan: ${e.message}", Toast.LENGTH_LONG).show()
                                        }
                                    } finally {
                                        isSaving = false
                                    }
                                }
                            }
                        },
                        enabled = hasModifications && !isSaving
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "Simpan",
                            tint = if (hasModifications) MaterialTheme.colorScheme.primary else Color.Gray
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                )
            )
        }
    ) { innerPadding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Membaca byte berkas...", color = Color.Gray, fontSize = 13.sp)
                }
            }
        } else if (fileData == null || fileData!!.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("Berkas biner kosong atau tidak dapat diakses", color = Color.Gray)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(Color(0xFF1E1E1E)) // Dark code editor feel
            ) {
                // Header Bar with column labels
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF252526))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Offset",
                        color = Color(0xFF9CDCFE),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.width(68.dp)
                    )
                    Text(
                        text = "00 01 02 03 04 05 06 07  08 09 0A 0B 0C 0D 0E 0F",
                        color = Color(0xFFCE9178),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "Decoded Text",
                        color = Color(0xFF4EC9B0),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.width(105.dp),
                        textAlign = TextAlign.End
                    )
                }

                HorizontalDivider(color = Color(0xFF333333))

                // Scrollable Hex View Grid
                val bytes = fileData!!
                val orig = originalData ?: bytes

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    items(totalRows) { rowIndex ->
                        val startOffset = rowIndex * BYTES_PER_ROW
                        val rowByteCount = (bytes.size - startOffset).coerceAtMost(BYTES_PER_ROW)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (rowIndex % 2 == 0) Color(0xFF1E1E1E) else Color(0xFF181818))
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Offset (e.g. 000000A0)
                            Text(
                                text = String.format("%08X", startOffset),
                                color = Color(0xFF569CD6),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                modifier = Modifier.width(68.dp)
                            )

                            // Hex columns (16 bytes per line)
                            Row(modifier = Modifier.weight(1f)) {
                                for (i in 0 until BYTES_PER_ROW) {
                                    val byteIndex = startOffset + i
                                    if (i == 8) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    if (i < rowByteCount) {
                                        val b = bytes[byteIndex]
                                        val isModified = byteIndex < orig.size && b != orig[byteIndex]
                                        val isSelected = selectedByteIndex == byteIndex
                                        val isMatch = searchResultIndices.contains(byteIndex)

                                        val bgColor = when {
                                            isSelected -> Color(0xFF264F78)
                                            isMatch -> Color(0xFF6A5A1B)
                                            isModified -> Color(0xFF5A1E1E)
                                            else -> Color.Transparent
                                        }

                                        val textColor = when {
                                            isModified -> Color(0xFFFF5252)
                                            isMatch -> Color(0xFFFFEB3B)
                                            b.toInt() == 0 -> Color(0xFF606060)
                                            else -> Color(0xFFD4D4D4)
                                        }

                                        Text(
                                            text = String.format("%02X", b),
                                            color = textColor,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            fontWeight = if (isModified || isSelected) FontWeight.Bold else FontWeight.Normal,
                                            modifier = Modifier
                                                .background(bgColor, RoundedCornerShape(2.dp))
                                                .padding(horizontal = 2.dp)
                                                .clickable {
                                                    selectedByteIndex = byteIndex
                                                    editByteHexInput = String.format("%02X", b)
                                                    editByteAsciiInput = if (b in 32..126) b.toInt().toChar().toString() else "."
                                                    showEditByteDialog = true
                                                }
                                        )
                                    } else {
                                        Text(
                                            text = "  ",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(horizontal = 2.dp)
                                        )
                                    }
                                }
                            }

                            // ASCII decoded text column
                            val asciiText = buildString {
                                for (i in 0 until rowByteCount) {
                                    val b = bytes[startOffset + i]
                                    if (b in 32..126) {
                                        append(b.toInt().toChar())
                                    } else {
                                        append('·')
                                    }
                                }
                            }

                            Text(
                                text = asciiText,
                                color = Color(0xFF98C379),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                maxLines = 1,
                                modifier = Modifier
                                    .width(105.dp)
                                    .padding(start = 4.dp),
                                textAlign = TextAlign.Start
                            )
                        }
                    }
                }

                // Bottom Status Bar
                Surface(
                    color = Color(0xFF007ACC),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (selectedByteIndex != null) {
                                val idx = selectedByteIndex!!
                                val b = bytes[idx].toInt() and 0xFF
                                "Offset: 0x${String.format("%X", idx)} (${idx}) | Nilai: 0x${String.format("%02X", b)} ($b, '${if (b in 32..126) b.toChar() else "."}')"
                            } else {
                                "Pilih sembarang byte untuk menyunting"
                            },
                            color = Color.White,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (hasModifications) {
                            Text(
                                text = "TERMODIFIKASI",
                                color = Color(0xFFFFD54F),
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }

    // Edit Single Byte Dialog
    if (showEditByteDialog && selectedByteIndex != null && fileData != null) {
        val idx = selectedByteIndex!!
        val currentByte = fileData!![idx]

        AlertDialog(
            onDismissRequest = { showEditByteDialog = false },
            title = {
                Text(
                    text = "Sunting Byte (Offset: 0x${String.format("%X", idx)})",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editByteHexInput,
                        onValueChange = { input ->
                            val clean = input.filter { it.isDigit() || it.lowercaseChar() in 'a'..'f' }.take(2).uppercase()
                            editByteHexInput = clean
                            if (clean.isNotEmpty()) {
                                try {
                                    val byteVal = clean.toInt(16)
                                    editByteAsciiInput = if (byteVal in 32..126) byteVal.toChar().toString() else "."
                                } catch (_: Exception) {}
                            }
                        },
                        label = { Text("Nilai Heksadesimal (Hex)") },
                        placeholder = { Text("Contoh: FF, 00, 7F") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = editByteAsciiInput,
                        onValueChange = { input ->
                            if (input.isNotEmpty()) {
                                val char = input.last()
                                editByteAsciiInput = char.toString()
                                editByteHexInput = String.format("%02X", char.code)
                            }
                        },
                        label = { Text("Karakter ASCII") },
                        placeholder = { Text("1 karakter") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        try {
                            val newByteVal = editByteHexInput.toInt(16).toByte()
                            val updated = fileData!!.copyOf()
                            updated[idx] = newByteVal
                            fileData = updated
                            showEditByteDialog = false
                        } catch (e: Exception) {
                            Toast.makeText(context, "Format hex tidak valid", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Terapkan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditByteDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    // Jump to Offset Dialog
    if (showGotoDialog) {
        AlertDialog(
            onDismissRequest = { showGotoDialog = false },
            title = { Text("Lompat ke Offset", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = gotoOffsetInput,
                    onValueChange = { gotoOffsetInput = it },
                    label = { Text("Offset (Hex atau Desimal)") },
                    placeholder = { Text("Contoh: 0x1A40 atau 6720") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val input = gotoOffsetInput.trim()
                        val offsetLong = if (input.startsWith("0x", ignoreCase = true)) {
                            input.substring(2).toLongOrNull(16)
                        } else {
                            input.toLongOrNull()
                        }

                        if (offsetLong != null && fileData != null && offsetLong in 0 until fileData!!.size) {
                            val targetRow = (offsetLong / BYTES_PER_ROW).toInt()
                            selectedByteIndex = offsetLong.toInt()
                            coroutineScope.launch {
                                listState.scrollToItem(targetRow)
                            }
                            showGotoDialog = false
                        } else {
                            Toast.makeText(context, "Offset di luar jangkauan!", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Lompat")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGotoDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    // Search Dialog
    if (showSearchDialog) {
        AlertDialog(
            onDismissRequest = { showSearchDialog = false },
            title = { Text("Pencarian Byte / Teks", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = isHexSearch,
                            onClick = { isHexSearch = true },
                            label = { Text("Hex (e.g. 50 4B 03 04)") }
                        )
                        FilterChip(
                            selected = !isHexSearch,
                            onClick = { isHexSearch = false },
                            label = { Text("Teks (ASCII)") }
                        )
                    }

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text(if (isHexSearch) "Pola Hex" else "Kata Kunci Teks") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val query = searchQuery.trim()
                        if (query.isNotEmpty() && fileData != null) {
                            val bytes = fileData!!
                            val matches = mutableListOf<Int>()
                            if (isHexSearch) {
                                val cleanHex = query.replace(" ", "").replace(",", "")
                                if (cleanHex.length % 2 == 0) {
                                    val targetBytes = ByteArray(cleanHex.length / 2)
                                    for (i in targetBytes.indices) {
                                        targetBytes[i] = cleanHex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
                                    }
                                    matches.addAll(findByteMatches(bytes, targetBytes))
                                }
                            } else {
                                val targetBytes = query.toByteArray(Charsets.UTF_8)
                                matches.addAll(findByteMatches(bytes, targetBytes))
                            }

                            searchResultIndices = matches
                            if (matches.isNotEmpty()) {
                                currentResultPointer = 0
                                val firstMatch = matches[0]
                                selectedByteIndex = firstMatch
                                coroutineScope.launch {
                                    listState.scrollToItem(firstMatch / BYTES_PER_ROW)
                                }
                                Toast.makeText(context, "Ditemukan ${matches.size} kecocokan", Toast.LENGTH_SHORT).show()
                                showSearchDialog = false
                            } else {
                                Toast.makeText(context, "Tidak ditemukan kecocokan", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) {
                    Text("Cari")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSearchDialog = false }) {
                    Text("Tutup")
                }
            }
        )
    }
}

private fun findByteMatches(source: ByteArray, target: ByteArray): List<Int> {
    if (target.isEmpty() || source.size < target.size) return emptyList()
    val matches = mutableListOf<Int>()
    for (i in 0..source.size - target.size) {
        var found = true
        for (j in target.indices) {
            if (source[i + j] != target[j]) {
                found = false
                break
            }
        }
        if (found) matches.add(i)
    }
    return matches
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
    val pre = "KMGTPE"[exp - 1]
    return String.format(Locale.US, "%.1f %sB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
}
