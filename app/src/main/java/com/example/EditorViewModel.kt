package com.example

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import java.io.File

enum class PanelType { LEFT, RIGHT }

sealed interface ActiveView {
    object Explorer : ActiveView
    data class TextEditor(val filePath: String, val isNewFile: Boolean = false) : ActiveView
    data class CompareView(val fileAPath: String, val fileBPath: String) : ActiveView
    data class ApkInspector(val apkPath: String, val currentApkPath: String = "") : ActiveView
}

data class FileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val isApk: Boolean = false
)

data class ComparisonLine(
    val lineNumber: Int,
    val textA: String?,
    val textB: String?,
    val type: LineDiffType
)

enum class LineDiffType { MATCH, DIFFERENT, ONLY_A, ONLY_B }

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val _isRootEnabled = MutableStateFlow(false)
    val isRootEnabled: StateFlow<Boolean> = _isRootEnabled.asStateFlow()

    private val _isRootAvailable = MutableStateFlow(false)
    val isRootAvailable: StateFlow<Boolean> = _isRootAvailable.asStateFlow()

    // Left panel path & files
    private val _leftPath = MutableStateFlow("")
    val leftPath: StateFlow<String> = _leftPath.asStateFlow()

    private val _leftFiles = MutableStateFlow<List<FileItem>>(emptyList())
    val leftFiles: StateFlow<List<FileItem>> = _leftFiles.asStateFlow()

    // Right panel path & files
    private val _rightPath = MutableStateFlow("")
    val rightPath: StateFlow<String> = _rightPath.asStateFlow()

    private val _rightFiles = MutableStateFlow<List<FileItem>>(emptyList())
    val rightFiles: StateFlow<List<FileItem>> = _rightFiles.asStateFlow()

    // Selected panel for operations
    private val _activePanel = MutableStateFlow(PanelType.LEFT)
    val activePanel: StateFlow<PanelType> = _activePanel.asStateFlow()

    // Screen View navigation
    private val _activeView = MutableStateFlow<ActiveView>(ActiveView.Explorer)
    val activeView: StateFlow<ActiveView> = _activeView.asStateFlow()

    // Text Editor State
    private val _editorContent = MutableStateFlow("")
    val editorContent: StateFlow<String> = _editorContent.asStateFlow()

    private val _editorTitle = MutableStateFlow("")
    val editorTitle: StateFlow<String> = _editorTitle.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // File Comparison State
    private val _comparisonLines = MutableStateFlow<List<ComparisonLine>>(emptyList())
    val comparisonLines: StateFlow<List<ComparisonLine>> = _comparisonLines.asStateFlow()

    // APK Inspector State
    private val _apkEntries = MutableStateFlow<List<ApkEntry>>(emptyList())
    val apkEntries: StateFlow<List<ApkEntry>> = _apkEntries.asStateFlow()

    private val _apkInspectorContent = MutableStateFlow<String?>(null)
    val apkInspectorContent: StateFlow<String?> = _apkInspectorContent.asStateFlow()

    private val _apkInspectorTitle = MutableStateFlow("")
    val apkInspectorTitle: StateFlow<String> = _apkInspectorTitle.asStateFlow()

    private val _selectedApkEntry = MutableStateFlow<ApkEntry?>(null)
    val selectedApkEntry: StateFlow<ApkEntry?> = _selectedApkEntry.asStateFlow()

    private val _dexStrings = MutableStateFlow<List<DexString>>(emptyList())
    val dexStrings: StateFlow<List<DexString>> = _dexStrings.asStateFlow()

    private val _dexClasses = MutableStateFlow<List<DexClass>>(emptyList())
    val dexClasses: StateFlow<List<DexClass>> = _dexClasses.asStateFlow()

    private val _dexMethods = MutableStateFlow<List<DexMethod>>(emptyList())
    val dexMethods: StateFlow<List<DexMethod>> = _dexMethods.asStateFlow()

    private val _appLogs = MutableStateFlow("")
    val appLogs: StateFlow<String> = _appLogs.asStateFlow()
    private var logcatJob: kotlinx.coroutines.Job? = null

    private val _rootCheckState = MutableStateFlow("Not Checked")
    val rootCheckState: StateFlow<String> = _rootCheckState.asStateFlow()

    private val _partitionRwState = MutableStateFlow("Not Checked")
    val partitionRwState: StateFlow<String> = _partitionRwState.asStateFlow()

    private val _rootOperationLogs = MutableStateFlow("")
    val rootOperationLogs: StateFlow<String> = _rootOperationLogs.asStateFlow()

    private val _terminalLogs = MutableStateFlow("")
    val terminalLogs: StateFlow<String> = _terminalLogs.asStateFlow()

    private val _terminalIsRoot = MutableStateFlow(false)
    val terminalIsRoot: StateFlow<Boolean> = _terminalIsRoot.asStateFlow()


    init {
        // Initialize with default paths
        val extDir = Environment.getExternalStorageDirectory().absolutePath
        val fallbacks = listOf(extDir, "/sdcard", "/system", "/")
        var startDir = "/"
        for (f in fallbacks) {
            val file = File(f)
            if (file.exists() && file.canRead()) {
                startDir = f
                break
            }
        }

        _leftPath.value = startDir
        _rightPath.value = startDir

        viewModelScope.launch {
            _isRootAvailable.value = RootUtils.isRootAvailable()
            loadFiles(PanelType.LEFT)
            loadFiles(PanelType.RIGHT)
        }
        startLogcatCapture()
    }

    private fun startLogcatCapture() {
        logcatJob?.cancel()
        logcatJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                // Clear existing logcat
                Runtime.getRuntime().exec("logcat -c").waitFor()
                
                // Read continuous logcat output for this process
                val pid = android.os.Process.myPid().toString()
                val process = Runtime.getRuntime().exec("logcat --pid=$pid")
                val bufferedReader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
                
                var line: String?
                while (this.isActive) {
                    line = bufferedReader.readLine()
                    if (line != null) {
                        val currentLog = _appLogs.value
                        var newLog = currentLog + line + "\n"
                        if (newLog.length > 50000) {
                            newLog = newLog.substring(newLog.length - 25000)
                        }
                        _appLogs.value = newLog
                    } else {
                        kotlinx.coroutines.delay(200)
                    }
                }
            } catch (e: Exception) {
                _appLogs.value += "Error capturing logcat: ${e.message}\n"
            }
        }
    }

    fun clearLogs() {
        _appLogs.value = ""
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Runtime.getRuntime().exec("logcat -c")
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    fun setRootEnabled(enabled: Boolean) {
        _isRootEnabled.value = enabled
        refreshAll()
    }

    fun setActivePanel(panel: PanelType) {
        _activePanel.value = panel
    }

    fun navigateToExplorer() {
        _activeView.value = ActiveView.Explorer
        _apkInspectorContent.value = null
    }

    fun refreshAll() {
        loadFiles(PanelType.LEFT)
        loadFiles(PanelType.RIGHT)
    }

    fun loadFiles(panel: PanelType) {
        viewModelScope.launch(Dispatchers.IO) {
            val path = if (panel == PanelType.LEFT) _leftPath.value else _rightPath.value
            val isRoot = _isRootEnabled.value

            val items = mutableListOf<FileItem>()
            try {
                if (isRoot) {
                    // Fetch files via root command `ls -la`
                    val res = RootUtils.executeCommand("ls -a \"$path\"", true)
                    if (res.success && res.output.isNotEmpty()) {
                        val lines = res.output.split("\n")
                        for (line in lines) {
                            val name = line.trim()
                            if (name == "." || name == "..") continue
                            if (name.isEmpty()) continue

                            val fullPath = if (path.endsWith("/")) "$path$name" else "$path/$name"
                            
                            // Check if directory or file using simple shell test [ -d path ]
                            val dirCheck = RootUtils.executeCommand("[ -d \"$fullPath\" ]", true)
                            val isDir = dirCheck.exitCode == 0

                            items.add(
                                FileItem(
                                    name = name,
                                    path = fullPath,
                                    isDirectory = isDir,
                                    size = 0L,
                                    lastModified = 0L,
                                    isApk = name.lowercase().endsWith(".apk")
                                )
                            )
                        }
                    } else {
                        // Fallback to normal file list
                        fallbackFileList(path, items)
                    }
                } else {
                    fallbackFileList(path, items)
                }
            } catch (e: Exception) {
                // Handle error
            }

            // Sort directories first, then alphabetically
            val sorted = items.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))

            withContext(Dispatchers.Main) {
                if (panel == PanelType.LEFT) {
                    _leftFiles.value = sorted
                } else {
                    _rightFiles.value = sorted
                }
            }
        }
    }

    private fun fallbackFileList(path: String, items: MutableList<FileItem>) {
        val dir = File(path)
        val files = dir.listFiles()
        if (files != null) {
            for (f in files) {
                items.add(
                    FileItem(
                        name = f.name,
                        path = f.absolutePath,
                        isDirectory = f.isDirectory,
                        size = if (f.isDirectory) 0 else f.length(),
                        lastModified = f.lastModified(),
                        isApk = f.name.lowercase().endsWith(".apk")
                    )
                )
            }
        }
    }

    fun selectFileItem(panel: PanelType, item: FileItem) {
        if (item.isDirectory) {
            if (panel == PanelType.LEFT) {
                _leftPath.value = item.path
            } else {
                _rightPath.value = item.path
            }
            loadFiles(panel)
        } else {
            if (item.isApk) {
                openApkInspector(item.path)
            } else {
                openTextEditor(item.path)
            }
        }
    }

    fun navigateUp(panel: PanelType) {
        val currentPath = if (panel == PanelType.LEFT) _leftPath.value else _rightPath.value
        val parentFile = File(currentPath).parentFile
        if (parentFile != null) {
            if (panel == PanelType.LEFT) {
                _leftPath.value = parentFile.absolutePath
            } else {
                _rightPath.value = parentFile.absolutePath
            }
            loadFiles(panel)
        }
    }

    // --- FILE CREATION & DELETION (MT Manager style) ---
    fun createNewFileOrDir(panel: PanelType, name: String, isFolder: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val dirPath = if (panel == PanelType.LEFT) _leftPath.value else _rightPath.value
            val target = File(dirPath, name)
            val isRoot = _isRootEnabled.value

            try {
                if (isRoot) {
                    val cmd = if (isFolder) "mkdir -p \"${target.absolutePath}\"" else "touch \"${target.absolutePath}\""
                    RootUtils.executeCommand(cmd, true)
                } else {
                    if (isFolder) {
                        target.mkdirs()
                    } else {
                        target.createNewFile()
                    }
                }
            } catch (e: Exception) {
                // Ignore or log
            }
            loadFiles(panel)
        }
    }

    fun deleteFileItem(panel: PanelType, item: FileItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val isRoot = _isRootEnabled.value
            try {
                if (isRoot) {
                    RootUtils.executeCommand("rm -rf \"${item.path}\"", true)
                } else {
                    val file = File(item.path)
                    if (file.isDirectory) {
                        file.deleteRecursively()
                    } else {
                        file.delete()
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
            loadFiles(panel)
        }
    }

    // --- TEXT EDITOR OPERATIONS ---
    fun openTextEditor(filePath: String, isNewFile: Boolean = false) {
        _editorTitle.value = File(filePath).name
        _searchQuery.value = ""
        _activeView.value = ActiveView.TextEditor(filePath, isNewFile)

        viewModelScope.launch(Dispatchers.IO) {
            var content = ""
            if (!isNewFile) {
                try {
                    val isRoot = _isRootEnabled.value
                    if (isRoot) {
                        val res = RootUtils.executeCommand("cat \"$filePath\"", true)
                        content = res.output
                    } else {
                        content = File(filePath).readText()
                    }
                } catch (e: Exception) {
                    content = "Error reading file: ${e.localizedMessage}"
                }
            }
            withContext(Dispatchers.Main) {
                _editorContent.value = content
            }
        }
    }

    fun updateEditorContent(content: String) {
        _editorContent.value = content
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun saveEditorFile(filePath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val content = _editorContent.value
            val isRoot = _isRootEnabled.value
            try {
                if (isRoot) {
                    // Write via echo or shell redirection (safely handling newlines)
                    val escapedContent = content.replace("'", "'\\''")
                    RootUtils.executeCommand("echo -n '$escapedContent' > \"$filePath\"", true)
                } else {
                    File(filePath).writeText(content)
                }
            } catch (e: Exception) {
                // Fail silently or handle
            }
            withContext(Dispatchers.Main) {
                refreshAll()
                navigateToExplorer()
            }
        }
    }

    // --- SIDE-BY-SIDE FILE COMPARISON ---
    fun startComparison() {
        // Find selected files in both left and right panel directory
        val leftSelected = _leftFiles.value.firstOrNull { !it.isDirectory }
        val rightSelected = _rightFiles.value.firstOrNull { !it.isDirectory }

        if (leftSelected != null && rightSelected != null) {
            openCompareView(leftSelected.path, rightSelected.path)
        }
    }

    fun openCompareView(fileAPath: String, fileBPath: String) {
        _activeView.value = ActiveView.CompareView(fileAPath, fileBPath)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val isRoot = _isRootEnabled.value
                val textA = if (isRoot) RootUtils.executeCommand("cat \"$fileAPath\"", true).output else File(fileAPath).readText()
                val textB = if (isRoot) RootUtils.executeCommand("cat \"$fileBPath\"", true).output else File(fileBPath).readText()

                val linesA = textA.split("\n")
                val linesB = textB.split("\n")

                val lines = mutableListOf<ComparisonLine>()
                val maxLines = maxOf(linesA.size, linesB.size)

                for (i in 0 until maxLines) {
                    val lineA = linesA.getOrNull(i)
                    val lineB = linesB.getOrNull(i)

                    val type = when {
                        lineA == lineB -> LineDiffType.MATCH
                        lineA != null && lineB != null -> LineDiffType.DIFFERENT
                        lineA != null -> LineDiffType.ONLY_A
                        else -> LineDiffType.ONLY_B
                    }

                    lines.add(
                        ComparisonLine(
                            lineNumber = i + 1,
                            textA = lineA,
                            textB = lineB,
                            type = type
                        )
                    )
                }

                withContext(Dispatchers.Main) {
                    _comparisonLines.value = lines
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    // --- APK INSPECTOR (Real DEX, XML, ARSC Parsing) ---
    fun openApkInspector(apkPath: String) {
        _activeView.value = ActiveView.ApkInspector(apkPath)
        _apkInspectorTitle.value = File(apkPath).name
        copyApkSignature(apkPath)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bytes = File(apkPath).readBytes()
                val entries = ApkParser.listApkEntries(bytes)
                withContext(Dispatchers.Main) {
                    _apkEntries.value = entries
                }
            } catch (e: Exception) {
                // Error
            }
        }
    }

    fun copyApkSignature(apkPath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val signatureInfo = try {
                val context = getApplication<Application>()
                val pm = context.packageManager
                val flags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
                } else {
                    @Suppress("DEPRECATION")
                    android.content.pm.PackageManager.GET_SIGNATURES
                }
                val packageInfo = pm.getPackageArchiveInfo(apkPath, flags)
                val signatures = if (packageInfo != null) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        packageInfo.signingInfo?.apkContentsSigners
                    } else {
                        @Suppress("DEPRECATION")
                        packageInfo.signatures
                    }
                } else null

                if (signatures != null && signatures.isNotEmpty()) {
                    val sb = java.lang.StringBuilder()
                    for ((index, sig) in signatures.withIndex()) {
                        val certBytes = sig.toByteArray()
                        val md5 = getDigest(certBytes, "MD5")
                        val sha1 = getDigest(certBytes, "SHA-1")
                        val sha256 = getDigest(certBytes, "SHA-256")
                        sb.append("Signature #$index:\n")
                        sb.append("MD5: $md5\n")
                        sb.append("SHA-1: $sha1\n")
                        sb.append("SHA-256: $sha256\n\n")
                    }
                    sb.toString().trim()
                } else {
                    parseSignatureFromMetaInf(apkPath)
                }
            } catch (e: Exception) {
                "Failed to parse signature: ${e.localizedMessage}"
            }

            withContext(Dispatchers.Main) {
                val context = getApplication<Application>()
                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("APK Signature", signatureInfo)
                clipboard.setPrimaryClip(clip)
                android.widget.Toast.makeText(context, "APK Signature copied to clipboard!", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun getDigest(bytes: ByteArray, algorithm: String): String {
        return try {
            val md = java.security.MessageDigest.getInstance(algorithm)
            val digest = md.digest(bytes)
            digest.joinToString(":") { String.format("%02X", it) }
        } catch (e: Exception) {
            "N/A"
        }
    }

    private fun parseSignatureFromMetaInf(apkPath: String): String {
        try {
            val apkFile = File(apkPath)
            if (!apkFile.exists()) return "File does not exist"
            val bytes = apkFile.readBytes()
            val bais = java.io.ByteArrayInputStream(bytes)
            val zis = java.util.zip.ZipInputStream(bais)
            var entry = zis.nextEntry
            while (entry != null) {
                val name = entry.name.uppercase()
                if (name.startsWith("META-INF/") && (name.endsWith(".RSA") || name.endsWith(".DSA") || name.endsWith(".EC"))) {
                    val certBytes = zis.readBytes()
                    val md5 = getDigest(certBytes, "MD5")
                    val sha1 = getDigest(certBytes, "SHA-1")
                    val sha256 = getDigest(certBytes, "SHA-256")
                    zis.close()
                    return "Signature (META-INF ${entry.name}):\nMD5: $md5\nSHA-1: $sha1\nSHA-256: $sha256"
                }
                entry = zis.nextEntry
            }
            zis.close()
        } catch (e: Exception) {
            return "Failed to parse META-INF: ${e.localizedMessage}"
        }
        return "No signature found in PackageArchiveInfo or META-INF."
    }

    fun inspectApkEntry(apkPath: String, entry: ApkEntry) {
        _selectedApkEntry.value = entry
        _apkInspectorTitle.value = "${File(apkPath).name} -> ${entry.name}"
        _apkInspectorContent.value = "Decompiling/Parsing: ${entry.name}...\nPlease wait..."

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val apkBytes = File(apkPath).readBytes()
                val entryBytes = ApkParser.extractEntryBytes(apkBytes, entry.name)

                val result = if (entryBytes == null) {
                    "Error: Could not extract entry bytes."
                } else {
                    val name = entry.name.lowercase()
                    when {
                        name.endsWith("androidmanifest.xml") || (name.endsWith(".xml") && entryBytes.size > 8 && entryBytes[0].toInt() == 3) -> {
                            // Decompile Binary XML
                            ApkParser.decompileBinaryXml(entryBytes)
                        }
                        name.endsWith(".dex") -> {
                            // Parse DEX classes, headers, and strings
                            val classes = ApkParser.parseDexClasses(entryBytes)
                            val strings = ApkParser.parseDexStrings(entryBytes)
                            _dexClasses.value = classes
                            _dexStrings.value = strings
                            ApkParser.parseDexHeader(entryBytes)
                        }
                        name.endsWith(".arsc") -> {
                            // Parse resources table
                            ApkParser.parseArscHeader(entryBytes)
                        }
                        // General text file
                        name.endsWith(".xml") || name.endsWith(".json") || name.endsWith(".txt") || name.endsWith(".properties") -> {
                            String(entryBytes, Charsets.UTF_8)
                        }
                        else -> {
                            "Binary File: ${entry.name}\nSize: ${entryBytes.size} bytes\n\nNo specialized text decompiler available for this type. Supports decompiling Binary XML, DEX Headers, ARSC Headers, and general Text."
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    _apkInspectorContent.value = result
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _apkInspectorContent.value = "Failed to parse entry: ${e.localizedMessage}"
                }
            }
        }
    }

    fun closeApkEntryInspector() {
        _apkInspectorContent.value = null
        _selectedApkEntry.value = null
        _dexStrings.value = emptyList()
        _dexClasses.value = emptyList()
        _dexMethods.value = emptyList()
    }

    fun saveDexString(apkPath: String, entryName: String, dexString: DexString, newValue: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val apkFile = File(apkPath)
                val apkBytes = apkFile.readBytes()
                val dexBytes = ApkParser.extractEntryBytes(apkBytes, entryName) ?: throw Exception("Could not find $entryName in APK")
                
                val modifiedDexBytes = ApkParser.writeDexStringInPlace(dexBytes, dexString, newValue)
                
                val modified = mapOf(entryName to modifiedDexBytes)
                ApkParser.signApkWithEntries(apkFile, modified)
                
                val updatedDexBytes = ApkParser.extractEntryBytes(apkFile.readBytes(), entryName) ?: throw Exception("Could not reload $entryName")
                val classes = ApkParser.parseDexClasses(updatedDexBytes)
                val strings = ApkParser.parseDexStrings(updatedDexBytes)
                val headerText = ApkParser.parseDexHeader(updatedDexBytes)
                
                withContext(Dispatchers.Main) {
                    _dexClasses.value = classes
                    _dexStrings.value = strings
                    _apkInspectorContent.value = headerText
                    
                    val context = getApplication<Application>()
                    android.widget.Toast.makeText(context, "DEX string updated and APK re-signed successfully!", android.widget.Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val context = getApplication<Application>()
                    android.widget.Toast.makeText(context, "Failed to update DEX string: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun saveDexClass(apkPath: String, entryName: String, dexClass: DexClass, newValue: String) {
        val dexString = DexString(
            index = dexClass.stringIndex,
            offset = dexClass.stringOffset,
            value = dexClass.name,
            byteLength = dexClass.byteLength
        )
        saveDexString(apkPath, entryName, dexString, newValue)
    }

    fun loadClassMethods(apkPath: String, entryName: String, dexClass: DexClass) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val apkBytes = File(apkPath).readBytes()
                val dexBytes = ApkParser.extractEntryBytes(apkBytes, entryName) ?: throw Exception("Could not read $entryName")
                val methods = ApkParser.decompileClassMethods(dexBytes, dexClass)
                withContext(Dispatchers.Main) {
                    _dexMethods.value = methods
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _dexMethods.value = emptyList()
                    val context = getApplication<Application>()
                    android.widget.Toast.makeText(context, "Error loading methods: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun saveDexMethod(apkPath: String, entryName: String, currentClass: DexClass, dexMethod: DexMethod, smaliLines: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val apkFile = File(apkPath)
                val apkBytes = apkFile.readBytes()
                val dexBytes = ApkParser.extractEntryBytes(apkBytes, entryName) ?: throw Exception("Could not find $entryName in APK")

                // Assemble the new bytecode
                val strings = ApkParser.parseDexStrings(dexBytes)
                val classes = ApkParser.parseDexClasses(dexBytes)
                val newBytecode = ApkParser.assembleInstructions(smaliLines, strings, classes)

                val modifiedDexBytes = ApkParser.writeDexMethodBytecode(dexBytes, dexMethod, newBytecode)

                val modified = mapOf(entryName to modifiedDexBytes)
                ApkParser.signApkWithEntries(apkFile, modified)

                val updatedDexBytes = ApkParser.extractEntryBytes(apkFile.readBytes(), entryName) ?: throw Exception("Could not reload $entryName")
                val updatedClasses = ApkParser.parseDexClasses(updatedDexBytes)
                val updatedStrings = ApkParser.parseDexStrings(updatedDexBytes)
                val headerText = ApkParser.parseDexHeader(updatedDexBytes)

                val updatedClass = updatedClasses.find { it.typeIdx == currentClass.typeIdx } ?: currentClass
                val updatedMethods = ApkParser.decompileClassMethods(updatedDexBytes, updatedClass)

                withContext(Dispatchers.Main) {
                    _dexClasses.value = updatedClasses
                    _dexStrings.value = updatedStrings
                    _dexMethods.value = updatedMethods
                    _apkInspectorContent.value = headerText

                    val context = getApplication<Application>()
                    android.widget.Toast.makeText(context, "DEX method bytecode updated and APK re-signed successfully!", android.widget.Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val context = getApplication<Application>()
                    android.widget.Toast.makeText(context, "Failed to update DEX method: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun repairApkSignature(apkPath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                ApkParser.signApkWithEntries(File(apkPath))
                withContext(Dispatchers.Main) {
                    val context = getApplication<Application>()
                    android.widget.Toast.makeText(context, "APK Signature repaired and re-signed!", android.widget.Toast.LENGTH_LONG).show()
                    openApkInspector(apkPath)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val context = getApplication<Application>()
                    android.widget.Toast.makeText(context, "Failed to repair: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun saveApkEntry(apkPath: String, entryName: String, newContent: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val modified = mapOf(entryName to newContent.toByteArray(Charsets.UTF_8))
                ApkParser.signApkWithEntries(File(apkPath), modified)
                withContext(Dispatchers.Main) {
                    val context = getApplication<Application>()
                    android.widget.Toast.makeText(context, "File updated and APK successfully re-signed!", android.widget.Toast.LENGTH_LONG).show()
                    _apkInspectorContent.value = null
                    _selectedApkEntry.value = null
                    openApkInspector(apkPath)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val context = getApplication<Application>()
                    android.widget.Toast.makeText(context, "Failed to save entry: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun verifyAndRequestRoot() {
        viewModelScope.launch(Dispatchers.IO) {
            _rootCheckState.value = "Checking..."
            _rootOperationLogs.value += "[*] Verifying root access...\n"
            
            val isAvail = RootUtils.isRootAvailable()
            _isRootAvailable.value = isAvail
            if (!isAvail) {
                _rootCheckState.value = "su binary not found"
                _rootOperationLogs.value += "[-] Error: su binary is missing on this device.\n"
                return@launch
            }
            
            val res = RootUtils.executeCommand("id", true)
            if (res.success && (res.output.contains("uid=0") || res.output.contains("root"))) {
                _rootCheckState.value = "Granted (UID 0)"
                _isRootEnabled.value = true
                _rootOperationLogs.value += "[+] Root access GRANTED successfully:\n${res.output}\n"
                refreshAll()
            } else {
                _rootCheckState.value = "Denied / Not Granted"
                _rootOperationLogs.value += "[-] Root access DENIED or failed: ${res.error}\n"
            }
        }
    }

    fun checkPartitionStatus(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _partitionRwState.value = "Checking..."
            _rootOperationLogs.value += "[*] Checking partition status for path: $path\n"
            
            // Try to find the mount options for this path's mountpoint
            val mountsRes = RootUtils.executeCommand("cat /proc/mounts", false)
            var foundMountLine = ""
            if (mountsRes.success) {
                val lines = mountsRes.output.split("\n")
                // Best matching mount point
                var bestMatch = ""
                for (line in lines) {
                    val parts = line.split(" ")
                    if (parts.size >= 4) {
                        val mountPoint = parts[1]
                        if (path.startsWith(mountPoint) && mountPoint.length > bestMatch.length) {
                            bestMatch = mountPoint
                            foundMountLine = line
                        }
                    }
                }
            }
            
            _rootOperationLogs.value += "[*] Parent mount point determined: ${if (foundMountLine.isNotEmpty()) foundMountLine else "Unknown"}\n"
            
            // Check write capability with root test file if root enabled
            val isRoot = _isRootEnabled.value
            var actuallyWritable = false
            if (isRoot) {
                val testFile = if (path.endsWith("/")) "${path}test_rw_mount" else "$path/test_rw_mount"
                _rootOperationLogs.value += "[*] Performing touch write test as root: $testFile\n"
                val touchRes = RootUtils.executeCommand("touch \"$testFile\" && rm -f \"$testFile\"", true)
                if (touchRes.success) {
                    actuallyWritable = true
                    _rootOperationLogs.value += "[+] Write test succeeded! Path is WRITABLE via Root.\n"
                } else {
                    _rootOperationLogs.value += "[-] Write test failed! Path is READ-ONLY or write-protected: ${touchRes.error}\n"
                }
            } else {
                val file = File(path)
                actuallyWritable = file.canWrite()
                _rootOperationLogs.value += "[*] Standard write check: canWrite() = $actuallyWritable\n"
            }
            
            if (actuallyWritable) {
                _partitionRwState.value = "Read-Write (RW)"
            } else {
                _partitionRwState.value = "Read-Only (RO)"
            }
        }
    }

    fun remountSystemPartition(path: String, makeWriteable: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            _rootOperationLogs.value += "[*] Requesting partition remount for path: $path (as ${if (makeWriteable) "RW" else "RO"})\n"
            
            val isAvail = RootUtils.isRootAvailable()
            if (!isAvail) {
                _rootOperationLogs.value += "[-] Error: cannot remount, root is not available.\n"
                return@launch
            }
            
            // Find appropriate mount point
            var mountPoint = "/system"
            if (path.startsWith("/vendor")) {
                mountPoint = "/vendor"
            } else if (path.startsWith("/product")) {
                mountPoint = "/product"
            } else if (path.startsWith("/odm")) {
                mountPoint = "/odm"
            } else if (path == "/" || !path.startsWith("/system")) {
                mountPoint = "/"
            }
            
            val opt = if (makeWriteable) "rw,remount" else "ro,remount"
            val cmd = "mount -o $opt $mountPoint"
            _rootOperationLogs.value += "[*] Executing root mount command: $cmd\n"
            
            val res = RootUtils.executeCommand(cmd, true)
            if (res.success) {
                _rootOperationLogs.value += "[+] Remount command succeeded!\n"
            } else {
                _rootOperationLogs.value += "[-] Remount command failed: ${res.error}\n"
                _rootOperationLogs.value += "[*] Attempting fallback mount command format: mount -o remount,$opt $mountPoint\n"
                val fallbackCmd = "mount -o remount,$opt $mountPoint"
                val resFallback = RootUtils.executeCommand(fallbackCmd, true)
                if (resFallback.success) {
                    _rootOperationLogs.value += "[+] Fallback remount succeeded!\n"
                } else {
                    _rootOperationLogs.value += "[-] Fallback remount failed: ${resFallback.error}\n"
                }
            }
            
            checkPartitionStatus(path)
            refreshAll()
        }
    }

    fun clearRootLogs() {
        _rootOperationLogs.value = ""
    }

    fun toggleTerminalRoot() {
        _terminalIsRoot.value = !_terminalIsRoot.value
    }

    fun runTerminalCommand(command: String, workingDir: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val isRoot = _terminalIsRoot.value
            val prompt = if (isRoot) "#" else "$"
            _terminalLogs.value += "\n$workingDir $prompt $command\n"
            
            val fullCommand = "cd \"$workingDir\" && $command"
            val res = RootUtils.executeCommand(fullCommand, isRoot)
            
            if (res.output.isNotEmpty()) {
                _terminalLogs.value += "${res.output}\n"
            }
            if (!res.success && res.error.isNotEmpty()) {
                _terminalLogs.value += "Error: ${res.error}\n"
            }
        }
    }

    fun clearTerminalLogs() {
        _terminalLogs.value = ""
    }
}
