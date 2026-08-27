package com.example.ui.components
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.DexClass
import com.example.DexMethod
import com.example.DexString
import com.example.EditorViewModel
import com.example.R
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApkInspectorScreen(
    apkPath: String,
    viewModel: EditorViewModel
) {
    val entries by viewModel.apkEntries.collectAsStateWithLifecycle()
    val decompiledContent by viewModel.apkInspectorContent.collectAsStateWithLifecycle()
    val title by viewModel.apkInspectorTitle.collectAsStateWithLifecycle()

    val selectedEntry by viewModel.selectedApkEntry.collectAsStateWithLifecycle()

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
            val dexClasses by viewModel.dexClasses.collectAsStateWithLifecycle()
            val dexStrings by viewModel.dexStrings.collectAsStateWithLifecycle()

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
                    val dexMethods by viewModel.dexMethods.collectAsStateWithLifecycle()
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
                                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                                items(dexMethods, key = { it.name + it.hashCode() }) { method ->
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
                                val filteredStrings = if (dexSearchQuery.isEmpty()) {
                                    dexStrings.take(200)
                                } else {
                                    dexStrings.filter {
                                        it.value.contains(dexSearchQuery, ignoreCase = true)
                                    }.take(200)
                                }
                                if (filteredStrings.isEmpty()) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(stringResource(R.string.no_matching_files), color = MaterialTheme.colorScheme.outline)
                                    }
                                } else {
                                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                                        items(filteredStrings, key = { it.index }) { dexStr ->
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
                                val filteredClasses = if (dexSearchQuery.isEmpty()) {
                                    dexClasses.take(200)
                                } else {
                                    dexClasses.filter {
                                        it.name.contains(dexSearchQuery, ignoreCase = true)
                                    }.take(200)
                                }
                                if (filteredClasses.isEmpty()) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(stringResource(R.string.no_matching_files), color = MaterialTheme.colorScheme.outline)
                                    }
                                } else {
                                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                                        items(filteredClasses, key = { it.name }) { dexCls ->
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
                                                    fontFamily = FontFamily.Monospace,
                                                    modifier = Modifier.weight(1f),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                        }
                                    }
                                }
                            }
                            2 -> {
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
                    items(filteredEntries, key = { it.name }) { entry ->
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
