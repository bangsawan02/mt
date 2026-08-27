package com.example.ui.components
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.*
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeEditorScreen(
    filePath: String,
    isNewFile: Boolean,
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val content by viewModel.editorContent.collectAsStateWithLifecycle()
    val title by viewModel.editorTitle.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()

    // Text & Selection state
    var textFieldValue by remember { mutableStateOf(TextFieldValue(text = "")) }
    var initialLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(content) {
        if (!initialLoaded || content != textFieldValue.text) {
            textFieldValue = TextFieldValue(text = content)
            initialLoaded = true
        }
    }

    // File extension and auto-detected code syntax language (.js, .jd, .css, .dex, .java, etc.)
    val rawExt = remember(filePath) {
        val name = File(filePath).name
        if (name.contains(".")) name.substringAfterLast(".").lowercase(Locale.getDefault()) else ""
    }
    val fileExt = rawExt
    val detectedLanguage = remember(rawExt, textFieldValue.text) {
        SyntaxHighlighter.detectLanguage(rawExt, textFieldValue.text)
    }

    // Undo / Redo Manager
    val undoRedoManager = remember { UndoRedoManager() }
    LaunchedEffect(filePath) {
        undoRedoManager.clear(content)
    }

    // Editor Configuration & Preferences
    var isSaved by remember { mutableStateOf(true) }
    var isReadOnly by remember { mutableStateOf(false) }
    var showGutter by remember { mutableStateOf(true) }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var replaceQuery by remember { mutableStateOf("") }
    var isCaseSensitive by remember { mutableStateOf(false) }
    var isRegexSearch by remember { mutableStateOf(false) }
    var wordWrap by remember { mutableStateOf(true) }
    var fontSizeSp by remember { mutableIntStateOf(14) }
    var showGoToLineDialog by remember { mutableStateOf(false) }
    var showUnsavedDialog by remember { mutableStateOf(false) }
    var gotoLineInput by remember { mutableStateOf("") }
    var activeMatchIndex by remember { mutableIntStateOf(0) }

    // Synchronized scroll states
    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()

    // Intercept back button if there are unsaved changes
    BackHandler(enabled = !isSaved) {
        showUnsavedDialog = true
    }

    // Helper function to update text and track undo history
    fun updateText(newVal: TextFieldValue) {
        if (isReadOnly) return
        if (newVal.text != textFieldValue.text) {
            undoRedoManager.pushState(textFieldValue.text)
            isSaved = false
        }
        textFieldValue = newVal
    }

    fun insertSymbol(symbol: String) {
        if (isReadOnly) return
        val currentText = textFieldValue.text
        val sel = textFieldValue.selection
        val start = sel.min.coerceIn(0, currentText.length)
        val end = sel.max.coerceIn(0, currentText.length)
        val newText = currentText.substring(0, start) + symbol + currentText.substring(end)
        val newCursor = start + symbol.length
        updateText(
            TextFieldValue(
                text = newText,
                selection = TextRange(newCursor)
            )
        )
    }

    // Fast Line & Character metrics
    val lines = remember(textFieldValue.text) { textFieldValue.text.lines() }
    val lineCount = lines.size
    val charCount = textFieldValue.text.length
    val fileSizeKb = remember(charCount) {
        val kb = charCount.toDouble() / 1024.0
        String.format(Locale.US, "%.1f KB", kb)
    }

    // Cursor position metrics (Line, Column, Selection size)
    val cursorMetrics = remember(textFieldValue.selection, textFieldValue.text) {
        val (line, col) = EditorOperations.calculateCursorMetrics(textFieldValue.text, textFieldValue.selection.start)
        val selLen = textFieldValue.selection.length
        Triple(line, col, selLen)
    }

    // High-Performance Gutter View Line Numbers with Active Line Highlight
    val currentActiveLine = cursorMetrics.first
    val activeLineColor = MaterialTheme.colorScheme.primary
    val inactiveLineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)

    val annotatedGutterText = remember(lineCount, currentActiveLine, activeLineColor, inactiveLineColor) {
        buildAnnotatedString {
            for (i in 1..lineCount) {
                if (i > 1) append("\n")
                if (i == currentActiveLine) {
                    pushStyle(
                        SpanStyle(
                            color = activeLineColor,
                            fontWeight = FontWeight.ExtraBold
                        )
                    )
                    append("$i")
                    pop()
                } else {
                    pushStyle(
                        SpanStyle(
                            color = inactiveLineColor,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    append("$i")
                    pop()
                }
            }
        }
    }

    // Search matches calculation
    val matches = remember(textFieldValue.text, query, isCaseSensitive, isRegexSearch) {
        if (query.isEmpty()) emptyList<Pair<Int, Int>>()
        else {
            val list = mutableListOf<Pair<Int, Int>>()
            try {
                if (isRegexSearch) {
                    val flags = if (isCaseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
                    val regex = Regex(query, flags)
                    regex.findAll(textFieldValue.text).forEach { match ->
                        list.add(Pair(match.range.first, match.range.last + 1))
                    }
                } else {
                    val source = if (isCaseSensitive) textFieldValue.text else textFieldValue.text.lowercase(Locale.getDefault())
                    val target = if (isCaseSensitive) query else query.lowercase(Locale.getDefault())
                    var idx = 0
                    while (idx < source.length) {
                        val found = source.indexOf(target, idx)
                        if (found != -1) {
                            list.add(Pair(found, found + target.length))
                            idx = found + target.length.coerceAtLeast(1)
                        } else break
                    }
                }
            } catch (e: Exception) {
                // Invalid regex
            }
            list
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // --- 1. Top Editor Header Bar ---
        Surface(
            tonalElevation = 2.dp,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = {
                                if (!isSaved) {
                                    showUnsavedDialog = true
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
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 160.dp)
                                )
                                if (detectedLanguage.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = detectedLanguage.uppercase(Locale.getDefault()),
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                if (isReadOnly) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Hanya Baca",
                                        tint = Color(0xFFE91E63),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            Text(
                                text = if (isSaved) "Tersimpan" else "● Belum Disimpan",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSaved) Color(0xFF4CAF50) else Color(0xFFFF9800),
                                fontWeight = if (isSaved) FontWeight.Normal else FontWeight.Bold
                            )
                        }
                    }

                    // Top Action Icons
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Undo
                        IconButton(
                            onClick = {
                                undoRedoManager.undo(textFieldValue.text)?.let { prev ->
                                    textFieldValue = TextFieldValue(text = prev)
                                    isSaved = false
                                }
                            },
                            enabled = undoRedoManager.canUndo() && !isReadOnly
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Undo,
                                contentDescription = "Urungkan (Undo)",
                                tint = if (undoRedoManager.canUndo() && !isReadOnly) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            )
                        }

                        // Redo
                        IconButton(
                            onClick = {
                                undoRedoManager.redo()?.let { next ->
                                    textFieldValue = TextFieldValue(text = next)
                                    isSaved = false
                                }
                            },
                            enabled = undoRedoManager.canRedo() && !isReadOnly
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Redo,
                                contentDescription = "Ulangi (Redo)",
                                tint = if (undoRedoManager.canRedo() && !isReadOnly) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            )
                        }

                        // Format Code
                        IconButton(
                            onClick = {
                                val (success, formatted) = CodeFormatter.format(textFieldValue.text, fileExt)
                                if (success) {
                                    updateText(TextFieldValue(text = formatted))
                                    Toast.makeText(context, "Format kode selesai", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, formatted, Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = !isReadOnly
                        ) {
                            Text(
                                text = "{}",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (!isReadOnly) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            )
                        }

                        // Search Toggle
                        IconButton(onClick = { isSearchExpanded = !isSearchExpanded }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Cari Teks",
                                tint = if (isSearchExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Save Button
                        Button(
                            onClick = {
                                viewModel.updateEditorContent(textFieldValue.text)
                                viewModel.saveEditorFile(filePath, closeAfterSave = false) { success, err ->
                                    if (success) {
                                        isSaved = true
                                        Toast.makeText(context, "File berhasil disimpan", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, err ?: "Gagal menyimpan file", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            enabled = !isReadOnly,
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .testTag("save_file_button")
                        ) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = "Simpan", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Simpan", fontSize = 13.sp)
                        }
                    }
                }

                // Secondary Action Toolbar (Lock, Zoom, Wrap, GoToLine)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Gutter Toggle
                        FilterChip(
                            selected = showGutter,
                            onClick = { showGutter = !showGutter },
                            label = { Text("Gutter", fontSize = 11.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.FormatListNumbered,
                                    contentDescription = "Tampilkan Nomor Baris",
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            modifier = Modifier.height(28.dp)
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        // Word Wrap Toggle
                        FilterChip(
                            selected = wordWrap,
                            onClick = { wordWrap = !wordWrap },
                            label = { Text("Wrap", fontSize = 11.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.WrapText,
                                    contentDescription = "Bungkus Baris",
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            modifier = Modifier.height(28.dp)
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        // Go to Line Button
                        OutlinedButton(
                            onClick = { showGoToLineDialog = true },
                            modifier = Modifier.height(28.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Icon(imageVector = Icons.Default.FormatListNumbered, contentDescription = "Lompat Baris", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Lompat #", fontSize = 11.sp)
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Read-Only Toggle
                        FilterChip(
                            selected = isReadOnly,
                            onClick = {
                                isReadOnly = !isReadOnly
                                val msg = if (isReadOnly) "Mode Hanya Baca diaktifkan" else "Mode Edit diaktifkan"
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            },
                            label = { Text(if (isReadOnly) "Kunci" else "Edit", fontSize = 11.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (isReadOnly) Icons.Default.Lock else Icons.Default.LockOpen,
                                    contentDescription = "Kunci Berkas",
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            modifier = Modifier.height(28.dp)
                        )
                    }

                    // Font Zoom Controls
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${fontSizeSp}sp", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        IconButton(
                            onClick = { if (fontSizeSp > 10) fontSizeSp -= 2 },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Text("A-", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        IconButton(
                            onClick = { if (fontSizeSp < 28) fontSizeSp += 2 },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Text("A+", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // --- 2. Search & Replace Panel ---
        AnimatedVisibility(
            visible = isSearchExpanded,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    // Search Input Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = {
                                viewModel.updateSearchQuery(it)
                                activeMatchIndex = 0
                            },
                            placeholder = { Text("Cari kata kunci...", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(max = 48.dp),
                            textStyle = MaterialTheme.typography.bodyMedium,
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            trailingIcon = {
                                if (query.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Hapus", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        // Match Count
                        Text(
                            text = if (matches.isNotEmpty()) "${(activeMatchIndex + 1).coerceAtMost(matches.size)}/${matches.size}" else "0",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )

                        // Previous Match
                        IconButton(
                            onClick = {
                                if (matches.isNotEmpty()) {
                                    activeMatchIndex = (activeMatchIndex - 1 + matches.size) % matches.size
                                    val (start, end) = matches[activeMatchIndex]
                                    textFieldValue = textFieldValue.copy(
                                        selection = TextRange(start, end)
                                    )
                                }
                            },
                            enabled = matches.isNotEmpty(),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Sebelumnya")
                        }

                        // Next Match
                        IconButton(
                            onClick = {
                                if (matches.isNotEmpty()) {
                                    activeMatchIndex = (activeMatchIndex + 1) % matches.size
                                    val (start, end) = matches[activeMatchIndex]
                                    textFieldValue = textFieldValue.copy(
                                        selection = TextRange(start, end)
                                    )
                                }
                            },
                            enabled = matches.isNotEmpty(),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Berikutnya")
                        }

                        // Case Sensitive Toggle
                        FilterChip(
                            selected = isCaseSensitive,
                            onClick = { isCaseSensitive = !isCaseSensitive },
                            label = { Text("a/A", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                            modifier = Modifier.height(28.dp)
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        // Regex Toggle
                        FilterChip(
                            selected = isRegexSearch,
                            onClick = { isRegexSearch = !isRegexSearch },
                            label = { Text(".*", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                            modifier = Modifier.height(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Replace Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = replaceQuery,
                            onValueChange = { replaceQuery = it },
                            placeholder = { Text("Ganti dengan...", fontSize = 12.sp) },
                            singleLine = true,
                            enabled = !isReadOnly,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(max = 48.dp),
                            textStyle = MaterialTheme.typography.bodyMedium
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        Button(
                            onClick = {
                                if (query.isNotEmpty() && matches.isNotEmpty() && !isReadOnly) {
                                    val safeIndex = activeMatchIndex.coerceIn(0, matches.lastIndex)
                                    val (start, end) = matches[safeIndex]
                                    val currentText = textFieldValue.text
                                    val newText = currentText.substring(0, start) + replaceQuery + currentText.substring(end)
                                    updateText(
                                        TextFieldValue(
                                            text = newText,
                                            selection = TextRange(start + replaceQuery.length)
                                        )
                                    )
                                    Toast.makeText(context, "1 teks diganti", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = query.isNotEmpty() && matches.isNotEmpty() && !isReadOnly,
                            modifier = Modifier.height(36.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text("Ganti", fontSize = 11.sp)
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        OutlinedButton(
                            onClick = {
                                if (query.isNotEmpty() && matches.isNotEmpty() && !isReadOnly) {
                                    val count = matches.size
                                    val newText = if (isRegexSearch) {
                                        val flags = if (isCaseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
                                        textFieldValue.text.replace(Regex(query, flags), replaceQuery)
                                    } else if (isCaseSensitive) {
                                        textFieldValue.text.replace(query, replaceQuery)
                                    } else {
                                        textFieldValue.text.replace(Regex(Regex.escape(query), RegexOption.IGNORE_CASE), replaceQuery)
                                    }
                                    updateText(TextFieldValue(text = newText))
                                    Toast.makeText(context, "$count teks diganti", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = query.isNotEmpty() && matches.isNotEmpty() && !isReadOnly,
                            modifier = Modifier.height(36.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text("Semua", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // --- 3. Main Code Canvas (Optimized Line Numbers + High-Performance Editor) ---
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            val lineHeight = (fontSizeSp * 1.4).sp

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(verticalScrollState)
            ) {
                // High-Performance Interactive Line Numbers Gutter View (With Active Line Highlight)
                AnimatedVisibility(visible = showGutter) {
                    Row(modifier = Modifier.fillMaxHeight()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier
                                .fillMaxHeight()
                                .clickable { showGoToLineDialog = true }
                        ) {
                            Text(
                                text = annotatedGutterText,
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = fontSizeSp.sp,
                                    lineHeight = lineHeight,
                                    textAlign = TextAlign.End
                                ),
                                modifier = Modifier
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                    .widthIn(min = 32.dp)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        )
                    }
                }

                Spacer(modifier = Modifier.width(2.dp))

                // Code Input Area
                val codeModifier = if (wordWrap) {
                    Modifier.weight(1f)
                } else {
                    Modifier
                        .weight(1f)
                        .horizontalScroll(horizontalScrollState)
                }

                OutlinedTextField(
                    value = textFieldValue,
                    onValueChange = { updateText(it) },
                    readOnly = isReadOnly,
                    modifier = codeModifier
                        .padding(vertical = 2.dp, horizontal = 4.dp)
                        .testTag("text_editor_field"),
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = fontSizeSp.sp,
                        lineHeight = lineHeight,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    visualTransformation = CodeSyntaxVisualTransformation(
                        fileExtension = detectedLanguage,
                        searchQuery = query,
                        isCaseSensitive = isCaseSensitive
                    ),
                    placeholder = {
                        Text(
                            text = if (isReadOnly) "Berkas hanya-baca (terkunci)" else "Tulis kode / konten di sini...",
                            fontSize = fontSizeSp.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent
                    )
                )
            }
        }

        // --- 4. Bilah Pintasan Cepat (Smart Action Bar & Coding Symbols) ---
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
            modifier = Modifier.fillMaxWidth()
        ) {
            val quickSymbols = listOf(
                "Tab", "-Tab", "//", "Dup", "{", "}", "(", ")", "[", "]",
                "\"", "'", "<", ">", "=", ";", ":", ",", ".", "+", "-", "*", "/", "\\", "$", "_"
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 4.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                quickSymbols.forEach { sym ->
                    AssistChip(
                        onClick = {
                            when (sym) {
                                "Tab" -> {
                                    val updated = EditorOperations.indentOrUnindent(textFieldValue, unindent = false)
                                    updateText(updated)
                                }
                                "-Tab" -> {
                                    val updated = EditorOperations.indentOrUnindent(textFieldValue, unindent = true)
                                    updateText(updated)
                                }
                                "//" -> {
                                    val updated = EditorOperations.toggleLineComment(textFieldValue, fileExt)
                                    updateText(updated)
                                }
                                "Dup" -> {
                                    val updated = EditorOperations.duplicateCurrentLine(textFieldValue)
                                    updateText(updated)
                                }
                                else -> insertSymbol(sym)
                            }
                        },
                        label = {
                            Text(
                                text = sym,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        },
                        modifier = Modifier
                            .padding(horizontal = 2.dp)
                            .height(30.dp)
                    )
                }
            }
        }

        // --- 5. Bottom Status Bar & File Metrics ---
        Surface(
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val (curLine, curCol, selLen) = cursorMetrics
                val cursorInfo = if (selLen > 0) "Ln $curLine, Col $curCol ($selLen sel)" else "Ln $curLine, Col $curCol"

                Text(
                    text = "$cursorInfo | $lineCount baris | $charCount kar | $fileSizeKb",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isReadOnly) "KUNCI" else "UTF-8",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (isReadOnly) Color(0xFFE91E63) else MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (wordWrap) "WRAP" else "NOWRAP",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }

    // --- 6. Dialogs (Extracted) ---
    if (showGoToLineDialog) {
        GoToLineDialog(
            lineCount = lineCount,
            initialInput = gotoLineInput,
            onDismiss = { showGoToLineDialog = false },
            onConfirm = { targetLine ->
                var offset = 0
                for (i in 0 until (targetLine - 1)) {
                    offset += lines[i].length + 1
                }
                val targetOffset = offset.coerceIn(0, textFieldValue.text.length)
                textFieldValue = textFieldValue.copy(
                    selection = TextRange(targetOffset)
                )
                Toast.makeText(context, "Melompat ke baris $targetLine", Toast.LENGTH_SHORT).show()
                showGoToLineDialog = false
                gotoLineInput = ""
            }
        )
    }

    if (showUnsavedDialog) {
        UnsavedChangesDialog(
            onDismiss = { showUnsavedDialog = false },
            onSave = {
                viewModel.updateEditorContent(textFieldValue.text)
                viewModel.saveEditorFile(filePath, closeAfterSave = true) { success, _ ->
                    if (success) {
                        isSaved = true
                        Toast.makeText(context, "Perubahan disimpan", Toast.LENGTH_SHORT).show()
                    }
                }
                showUnsavedDialog = false
            },
            onDiscard = {
                showUnsavedDialog = false
                viewModel.navigateToExplorer()
            }
        )
    }
}
