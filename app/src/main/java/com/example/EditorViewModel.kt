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

data class ImageMetadata(
    val fileName: String,
    val filePath: String,
    val width: Int,
    val height: Int,
    val fileSize: Long,
    val mimeType: String,
    val lastModified: Long,
    val cameraModel: String? = null,
    val dateTimeOriginal: String? = null,
    val iso: String? = null
)

enum class PanelType { LEFT, RIGHT }

sealed interface ActiveView {
    object Explorer : ActiveView
    data class TextEditor(val filePath: String, val isNewFile: Boolean = false) : ActiveView
    data class CompareView(val fileAPath: String, val fileBPath: String) : ActiveView
    data class ApkInspector(val apkPath: String, val currentApkPath: String = "") : ActiveView
    data class PhotoEditor(val filePath: String) : ActiveView
    data class ArchiveViewer(val archivePath: String, val currentInternalPath: String = "") : ActiveView
    data class HexEditor(val filePath: String) : ActiveView
    data class VideoPlayer(val filePath: String) : ActiveView
    object AppManager : ActiveView
}

data class FileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val isApk: Boolean = false,
    val isImage: Boolean = false,
    val isVideo: Boolean = false,
    val isArchive: Boolean = false
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

    // Multi-Select State
    private val _selectedLeftFiles = MutableStateFlow<Set<String>>(emptySet())
    val selectedLeftFiles: StateFlow<Set<String>> = _selectedLeftFiles.asStateFlow()

    private val _selectedRightFiles = MutableStateFlow<Set<String>>(emptySet())
    val selectedRightFiles: StateFlow<Set<String>> = _selectedRightFiles.asStateFlow()

    private val _isMultiSelectMode = MutableStateFlow(false)
    val isMultiSelectMode: StateFlow<Boolean> = _isMultiSelectMode.asStateFlow()

    fun toggleFileSelection(panel: PanelType, filePath: String) {
        _activePanel.value = panel
        if (panel == PanelType.LEFT) {
            val current = _selectedLeftFiles.value.toMutableSet()
            if (current.contains(filePath)) {
                current.remove(filePath)
            } else {
                current.add(filePath)
            }
            _selectedLeftFiles.value = current
            if (current.isNotEmpty()) {
                _isMultiSelectMode.value = true
            } else if (_selectedRightFiles.value.isEmpty()) {
                _isMultiSelectMode.value = false
            }
        } else {
            val current = _selectedRightFiles.value.toMutableSet()
            if (current.contains(filePath)) {
                current.remove(filePath)
            } else {
                current.add(filePath)
            }
            _selectedRightFiles.value = current
            if (current.isNotEmpty()) {
                _isMultiSelectMode.value = true
            } else if (_selectedLeftFiles.value.isEmpty()) {
                _isMultiSelectMode.value = false
            }
        }
    }

    fun selectAll(panel: PanelType) {
        _activePanel.value = panel
        val files = if (panel == PanelType.LEFT) _leftFiles.value else _rightFiles.value
        val validPaths = files.filter { it.name != ".." }.map { it.path }.toSet()
        if (panel == PanelType.LEFT) {
            _selectedLeftFiles.value = validPaths
        } else {
            _selectedRightFiles.value = validPaths
        }
        if (validPaths.isNotEmpty()) {
            _isMultiSelectMode.value = true
        }
    }

    fun invertSelection(panel: PanelType) {
        _activePanel.value = panel
        val files = if (panel == PanelType.LEFT) _leftFiles.value else _rightFiles.value
        val validPaths = files.filter { it.name != ".." }.map { it.path }.toSet()
        val current = if (panel == PanelType.LEFT) _selectedLeftFiles.value else _selectedRightFiles.value
        val inverted = validPaths.subtract(current)
        if (panel == PanelType.LEFT) {
            _selectedLeftFiles.value = inverted
        } else {
            _selectedRightFiles.value = inverted
        }
        if (inverted.isNotEmpty() || (if (panel == PanelType.LEFT) _selectedRightFiles.value else _selectedLeftFiles.value).isNotEmpty()) {
            _isMultiSelectMode.value = true
        } else {
            _isMultiSelectMode.value = false
        }
    }

    fun clearSelection(panel: PanelType) {
        if (panel == PanelType.LEFT) {
            _selectedLeftFiles.value = emptySet()
        } else {
            _selectedRightFiles.value = emptySet()
        }
        if (_selectedLeftFiles.value.isEmpty() && _selectedRightFiles.value.isEmpty()) {
            _isMultiSelectMode.value = false
        }
    }

    fun clearAllSelections() {
        _selectedLeftFiles.value = emptySet()
        _selectedRightFiles.value = emptySet()
        _isMultiSelectMode.value = false
    }

    fun setMultiSelectMode(enabled: Boolean) {
        _isMultiSelectMode.value = enabled
        if (!enabled) {
            clearAllSelections()
        }
    }

    // Path history stacks
    private val leftHistory = mutableListOf<String>()
    private var leftHistoryIndex = -1
    private val rightHistory = mutableListOf<String>()
    private var rightHistoryIndex = -1

    fun addToHistory(panel: PanelType, path: String) {
        if (path.isEmpty()) return
        if (panel == PanelType.LEFT) {
            if (leftHistoryIndex >= 0 && leftHistoryIndex < leftHistory.size && leftHistory[leftHistoryIndex] == path) return
            while (leftHistory.size > leftHistoryIndex + 1) {
                leftHistory.removeAt(leftHistory.size - 1)
            }
            leftHistory.add(path)
            leftHistoryIndex = leftHistory.size - 1
        } else {
            if (rightHistoryIndex >= 0 && rightHistoryIndex < rightHistory.size && rightHistory[rightHistoryIndex] == path) return
            while (rightHistory.size > rightHistoryIndex + 1) {
                rightHistory.removeAt(rightHistory.size - 1)
            }
            rightHistory.add(path)
            rightHistoryIndex = rightHistory.size - 1
        }
    }

    fun canNavigateBack(panel: PanelType): Boolean {
        return if (panel == PanelType.LEFT) leftHistoryIndex > 0 else rightHistoryIndex > 0
    }

    fun canNavigateForward(panel: PanelType): Boolean {
        return if (panel == PanelType.LEFT) leftHistoryIndex < leftHistory.size - 1 else rightHistoryIndex < rightHistory.size - 1
    }

    fun navigateBack(panel: PanelType) {
        if (panel == PanelType.LEFT) {
            if (leftHistoryIndex > 0) {
                leftHistoryIndex--
                _leftPath.value = leftHistory[leftHistoryIndex]
                loadFiles(PanelType.LEFT)
            }
        } else {
            if (rightHistoryIndex > 0) {
                rightHistoryIndex--
                _rightPath.value = rightHistory[rightHistoryIndex]
                loadFiles(PanelType.RIGHT)
            }
        }
    }

    fun navigateForward(panel: PanelType) {
        if (panel == PanelType.LEFT) {
            if (leftHistoryIndex < leftHistory.size - 1) {
                leftHistoryIndex++
                _leftPath.value = leftHistory[leftHistoryIndex]
                loadFiles(PanelType.LEFT)
            }
        } else {
            if (rightHistoryIndex < rightHistory.size - 1) {
                rightHistoryIndex++
                _rightPath.value = rightHistory[rightHistoryIndex]
                loadFiles(PanelType.RIGHT)
            }
        }
    }

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
    
    private val _operationProgress = MutableStateFlow<Float?>(null)
    val operationProgress: StateFlow<Float?> = _operationProgress.asStateFlow()

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

    // Archive Viewer & Extractor State
    private val _currentArchivePath = MutableStateFlow("")
    val currentArchivePath: StateFlow<String> = _currentArchivePath.asStateFlow()

    private val _archiveInternalPath = MutableStateFlow("")
    val archiveInternalPath: StateFlow<String> = _archiveInternalPath.asStateFlow()

    private val _archiveEntries = MutableStateFlow<List<ArchiveEntryItem>>(emptyList())
    val archiveEntries: StateFlow<List<ArchiveEntryItem>> = _archiveEntries.asStateFlow()

    private val _archiveLoading = MutableStateFlow(false)
    val archiveLoading: StateFlow<Boolean> = _archiveLoading.asStateFlow()

    private val _archiveErrorMessage = MutableStateFlow<String?>(null)
    val archiveErrorMessage: StateFlow<String?> = _archiveErrorMessage.asStateFlow()

    private val _archiveProgressState = MutableStateFlow<ArchiveProgressState?>(null)
    val archiveProgressState: StateFlow<ArchiveProgressState?> = _archiveProgressState.asStateFlow()

    private val _archivePreviewText = MutableStateFlow<String?>(null)
    val archivePreviewText: StateFlow<String?> = _archivePreviewText.asStateFlow()

    private val _archivePreviewEntryName = MutableStateFlow<String?>(null)
    val archivePreviewEntryName: StateFlow<String?> = _archivePreviewEntryName.asStateFlow()

    private var isArchiveOperationCancelled = false


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
        addToHistory(PanelType.LEFT, startDir)
        addToHistory(PanelType.RIGHT, startDir)

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

    fun isImageFile(name: String): Boolean {
        val ext = name.lowercase().substringAfterLast(".", "")
        return ext in listOf("png", "jpg", "jpeg", "webp", "bmp", "gif")
    }

    fun isVideoFile(name: String): Boolean {
        val ext = name.lowercase().substringAfterLast(".", "")
        return ext in listOf("mp4", "mkv", "webm", "avi", "3gp", "mov", "flv", "ts", "wmv", "m4v", "mpg", "mpeg")
    }

    // Bookmarks & Quick Access
    private val _bookmarks = MutableStateFlow<List<String>>(
        listOf(
            Environment.getExternalStorageDirectory().absolutePath,
            File(Environment.getExternalStorageDirectory(), "Download").absolutePath,
            File(Environment.getExternalStorageDirectory(), "DCIM").absolutePath,
            File(Environment.getExternalStorageDirectory(), "Pictures").absolutePath,
            File(Environment.getExternalStorageDirectory(), "Documents").absolutePath,
            "/system",
            "/data/app",
            "/"
        )
    )
    val bookmarks: StateFlow<List<String>> = _bookmarks.asStateFlow()

    fun addBookmark(path: String) {
        if (!_bookmarks.value.contains(path)) {
            _bookmarks.value = _bookmarks.value + path
        }
    }

    fun removeBookmark(path: String) {
        _bookmarks.value = _bookmarks.value.filter { it != path }
    }

    fun openHexEditor(filePath: String) {
        _activeView.value = ActiveView.HexEditor(filePath)
    }

    fun openAppManager() {
        _activeView.value = ActiveView.AppManager
    }

    fun openPhotoEditor(filePath: String) {
        _activeView.value = ActiveView.PhotoEditor(filePath)
    }

    fun openVideoPlayer(filePath: String) {
        _activeView.value = ActiveView.VideoPlayer(filePath)
    }

    fun loadPath(panel: PanelType, path: String) {
        if (panel == PanelType.LEFT) {
            _leftPath.value = path
            addToHistory(PanelType.LEFT, path)
            loadFiles(PanelType.LEFT)
        } else {
            _rightPath.value = path
            addToHistory(PanelType.RIGHT, path)
            loadFiles(PanelType.RIGHT)
        }
    }

    fun loadFiles(panel: PanelType) {
        viewModelScope.launch(Dispatchers.IO) {
            val path = if (panel == PanelType.LEFT) _leftPath.value else _rightPath.value
            val isRoot = _isRootEnabled.value
 
            val items = mutableListOf<FileItem>()
            try {
                // Coba pembacaan langsung via Java API terlebih dahulu (Sangat Cepat & Efisien)
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
                                isApk = f.name.lowercase().endsWith(".apk"),
                                isImage = isImageFile(f.name),
                                isVideo = isVideoFile(f.name),
                                isArchive = ArchiveManager.isArchiveFile(f.name)
                            )
                        )
                    }
                } else if (isRoot) {
                    // Fallback ke Root shell hanya jika Java API mengembalikan null (Permission Denied).
                    // Menggunakan perintah tunggal `ls -ap` untuk mendapatkan status folder secara instan 
                    // tanpa perlu mengeksekusi shell berulang-ulang untuk setiap item.
                    val res = RootUtils.executeCommand("ls -ap \"$path\"", true)
                    if (res.success && res.output.isNotEmpty()) {
                        val lines = res.output.split("\n")
                        for (line in lines) {
                            var name = line.trim()
                            if (name == "." || name == "..") continue
                            if (name.isEmpty()) continue
 
                            val isDir = name.endsWith("/")
                            if (isDir) {
                                name = name.substring(0, name.length - 1)
                            }
                            val fullPath = if (path.endsWith("/")) "$path$name" else "$path/$name"
 
                            items.add(
                                FileItem(
                                    name = name,
                                    path = fullPath,
                                    isDirectory = isDir,
                                    size = 0L,
                                    lastModified = 0L,
                                    isApk = name.lowercase().endsWith(".apk"),
                                    isImage = isImageFile(name),
                                    isVideo = isVideoFile(name),
                                    isArchive = ArchiveManager.isArchiveFile(name)
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                // Gagal membaca
            }
 
            // Urutkan folder di atas, diikuti oleh file secara alfabetis
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
 
    fun selectFileItem(panel: PanelType, item: FileItem) {
        _activePanel.value = panel // Otomatis jadikan panel ini sebagai panel aktif
        if (item.isDirectory) {
            val targetPath = item.path
            if (panel == PanelType.LEFT) {
                _leftPath.value = targetPath
            } else {
                _rightPath.value = targetPath
            }
            addToHistory(panel, targetPath)
            loadFiles(panel)
        } else {
            if (item.isApk) {
                openApkInspector(item.path)
            } else if (item.isArchive) {
                openArchiveViewer(item.path)
            } else if (item.isImage) {
                openPhotoEditor(item.path)
            } else if (item.isVideo) {
                openVideoPlayer(item.path)
            } else {
                openTextEditor(item.path)
            }
        }
    }

    fun navigateUp(panel: PanelType) {
        _activePanel.value = panel // Otomatis jadikan panel ini sebagai panel aktif
        val currentPath = if (panel == PanelType.LEFT) _leftPath.value else _rightPath.value
        val parentFile = File(currentPath).parentFile
        if (parentFile != null) {
            val targetPath = parentFile.absolutePath
            if (panel == PanelType.LEFT) {
                _leftPath.value = targetPath
            } else {
                _rightPath.value = targetPath
            }
            addToHistory(panel, targetPath)
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
                    val res = RootUtils.executeCommand(cmd, true)
                    if (!res.success) {
                        _appLogs.value += "\n[Error Buat File/Folder] ${res.error}\n"
                    }
                } else {
                    val ok = if (isFolder) {
                        target.mkdirs()
                    } else {
                        target.createNewFile()
                    }
                    if (!ok && !target.exists()) {
                        _appLogs.value += "\n[Error Buat File/Folder] Gagal membuat: ${target.name}\n"
                    }
                }
            } catch (e: Exception) {
                _appLogs.value += "\n[Error Buat File/Folder] ${e.localizedMessage ?: e.message}\n"
            }
            loadFiles(panel)
        }
    }

    fun renameFileItem(panel: PanelType, item: FileItem, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val trimmedName = newName.trim()
            if (trimmedName.isEmpty() || trimmedName == item.name) return@launch
            val oldFile = File(item.path)
            val parentDir = oldFile.parentFile ?: return@launch
            val newFile = File(parentDir, trimmedName)
            val isRoot = _isRootEnabled.value

            try {
                if (isRoot) {
                    val res = RootUtils.executeCommand("mv \"${oldFile.absolutePath}\" \"${newFile.absolutePath}\"", true)
                    if (!res.success) {
                        _appLogs.value += "\n[Error Ubah Nama] ${res.error}\n"
                    }
                } else {
                    val ok = oldFile.renameTo(newFile)
                    if (!ok) {
                        _appLogs.value += "\n[Error Ubah Nama] Gagal mengubah nama ke $trimmedName\n"
                    }
                }
            } catch (e: Exception) {
                _appLogs.value += "\n[Error Ubah Nama] ${e.localizedMessage ?: e.message}\n"
            }
            loadFiles(panel)
        }
    }

    fun copyFileItem(item: FileItem, targetDirPath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val isRoot = _isRootEnabled.value
            val res = FileManager.copyFileItem(File(item.path), File(targetDirPath), isRoot, _operationProgress)
            if (!res.success) {
                _appLogs.value += "\n[Error Salin File] ${res.error}\n"
            }
            refreshAll()
        }
    }

    fun moveFileItem(item: FileItem, targetDirPath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val isRoot = _isRootEnabled.value
            val res = FileManager.moveFileItem(File(item.path), File(targetDirPath), isRoot, _operationProgress)
            if (!res.success) {
                _appLogs.value += "\n[Error Pindah File] ${res.error}\n"
            }
            refreshAll()
        }
    }

    fun deleteFileItem(panel: PanelType, item: FileItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val isRoot = _isRootEnabled.value
            val res = FileManager.deleteFileItem(File(item.path), isRoot, _operationProgress)
            if (!res.success) {
                _appLogs.value += "\n[Error Hapus File] ${res.error}\n"
            }
            loadFiles(panel)
        }
    }

    // --- BATCH MULTI-SELECT OPERATIONS ---

    fun copySelectedFiles(panel: PanelType, targetDirPath: String) {
        val selectedPaths = (if (panel == PanelType.LEFT) _selectedLeftFiles.value else _selectedRightFiles.value).toList()
        if (selectedPaths.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            val isRoot = _isRootEnabled.value
            for (path in selectedPaths) {
                val res = FileManager.copyFileItem(File(path), File(targetDirPath), isRoot, _operationProgress)
                if (!res.success) {
                    _appLogs.value += "\n[Error Salin File $path] ${res.error}\n"
                }
            }
            withContext(Dispatchers.Main) {
                clearSelection(panel)
                refreshAll()
            }
        }
    }

    fun moveSelectedFiles(panel: PanelType, targetDirPath: String) {
        val selectedPaths = (if (panel == PanelType.LEFT) _selectedLeftFiles.value else _selectedRightFiles.value).toList()
        if (selectedPaths.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            val isRoot = _isRootEnabled.value
            for (path in selectedPaths) {
                val res = FileManager.moveFileItem(File(path), File(targetDirPath), isRoot, _operationProgress)
                if (!res.success) {
                    _appLogs.value += "\n[Error Pindah File $path] ${res.error}\n"
                }
            }
            withContext(Dispatchers.Main) {
                clearSelection(panel)
                refreshAll()
            }
        }
    }

    fun deleteSelectedFiles(panel: PanelType) {
        val selectedPaths = (if (panel == PanelType.LEFT) _selectedLeftFiles.value else _selectedRightFiles.value).toList()
        if (selectedPaths.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            val isRoot = _isRootEnabled.value
            for (path in selectedPaths) {
                val res = FileManager.deleteFileItem(File(path), isRoot, _operationProgress)
                if (!res.success) {
                    _appLogs.value += "\n[Error Hapus File $path] ${res.error}\n"
                }
            }
            withContext(Dispatchers.Main) {
                clearSelection(panel)
                loadFiles(panel)
            }
        }
    }

    fun compressSelectedFiles(panel: PanelType, targetZipPath: String) {
        val selectedPaths = (if (panel == PanelType.LEFT) _selectedLeftFiles.value else _selectedRightFiles.value).toList()
        if (selectedPaths.isEmpty()) return
        compressFilesToZip(selectedPaths, targetZipPath)
        clearSelection(panel)
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
                    val file = File(filePath)
                    if (file.canRead()) {
                        content = file.readText(Charsets.UTF_8)
                    } else if (_isRootEnabled.value) {
                        val res = RootUtils.executeCommand("cat \"$filePath\"", true)
                        content = res.output
                    } else {
                        content = file.readText(Charsets.UTF_8)
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

    fun saveEditorFile(filePath: String, closeAfterSave: Boolean = false, onComplete: ((Boolean, String?) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val content = _editorContent.value
            val isRoot = _isRootEnabled.value
            var success = true
            var errorMsg: String? = null
            val context = getApplication<Application>()
            try {
                val file = File(filePath)
                if (!isRoot && (file.canWrite() || (!file.exists() && file.parentFile?.canWrite() == true))) {
                    java.io.BufferedWriter(java.io.OutputStreamWriter(java.io.FileOutputStream(file), Charsets.UTF_8), 8192).use { writer ->
                        writer.write(content)
                    }
                } else if (isRoot) {
                    // Safe atomic write via temp cache file and root cp
                    val tempFile = File(context.cacheDir, "editor_temp_${System.currentTimeMillis()}.tmp")
                    java.io.BufferedWriter(java.io.OutputStreamWriter(java.io.FileOutputStream(tempFile), Charsets.UTF_8), 8192).use { writer ->
                        writer.write(content)
                    }
                    val res = RootUtils.executeCommand("cp \"${tempFile.absolutePath}\" \"$filePath\" && chmod 666 \"$filePath\"", true)
                    tempFile.delete()
                    if (res.exitCode != 0) {
                        success = false
                        errorMsg = res.output.ifEmpty { res.error }
                    }
                } else {
                    java.io.BufferedWriter(java.io.OutputStreamWriter(java.io.FileOutputStream(file), Charsets.UTF_8), 8192).use { writer ->
                        writer.write(content)
                    }
                }
            } catch (e: Exception) {
                success = false
                errorMsg = e.localizedMessage ?: "Gagal menyimpan file"
            }
            withContext(Dispatchers.Main) {
                refreshAll()
                onComplete?.invoke(success, errorMsg)
                if (closeAfterSave && success) {
                    navigateToExplorer()
                }
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
                val entries = ApkParser.listApkEntries(File(apkPath))
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
                val entryBytes = ApkParser.extractEntryBytes(File(apkPath), entry.name)

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
                val dexBytes = ApkParser.extractEntryBytes(apkFile, entryName) ?: throw Exception("Could not find $entryName in APK")
                
                val modifiedDexBytes = ApkParser.writeDexStringInPlace(dexBytes, dexString, newValue)
                
                val modified = mapOf(entryName to modifiedDexBytes)
                ApkParser.signApkWithEntries(apkFile, modified)
                
                val updatedDexBytes = ApkParser.extractEntryBytes(apkFile, entryName) ?: throw Exception("Could not reload $entryName")
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
                val dexBytes = ApkParser.extractEntryBytes(File(apkPath), entryName) ?: throw Exception("Could not read $entryName")
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
                val dexBytes = ApkParser.extractEntryBytes(apkFile, entryName) ?: throw Exception("Could not find $entryName in APK")

                // Assemble the new bytecode
                val strings = ApkParser.parseDexStrings(dexBytes)
                val classes = ApkParser.parseDexClasses(dexBytes)
                val newBytecode = ApkParser.assembleInstructions(smaliLines, strings, classes)

                val modifiedDexBytes = ApkParser.writeDexMethodBytecode(dexBytes, dexMethod, newBytecode)

                val modified = mapOf(entryName to modifiedDexBytes)
                ApkParser.signApkWithEntries(apkFile, modified)

                val updatedDexBytes = ApkParser.extractEntryBytes(apkFile, entryName) ?: throw Exception("Could not reload $entryName")
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

    // Archive Viewer & Extractor Methods
    fun getOppositePanelPath(): String {
        return if (_activePanel.value == PanelType.LEFT) _rightPath.value else _leftPath.value
    }

    fun openArchiveViewer(archivePath: String, internalPath: String = "") {
        _currentArchivePath.value = archivePath
        _archiveInternalPath.value = internalPath
        _activeView.value = ActiveView.ArchiveViewer(archivePath, internalPath)
        loadArchiveEntries(archivePath, internalPath)
    }

    fun navigateArchiveInternal(internalPath: String) {
        _archiveInternalPath.value = internalPath
        val archivePath = _currentArchivePath.value
        _activeView.value = ActiveView.ArchiveViewer(archivePath, internalPath)
        loadArchiveEntries(archivePath, internalPath)
    }

    fun navigateArchiveUp() {
        val current = _archiveInternalPath.value
        if (current.isEmpty()) {
            navigateToExplorer()
            return
        }

        val trimmed = current.trimEnd('/')
        val lastSlash = trimmed.lastIndexOf('/')
        val parentPath = if (lastSlash >= 0) trimmed.substring(0, lastSlash + 1) else ""
        navigateArchiveInternal(parentPath)
    }

    fun reloadArchiveEntries() {
        val archivePath = _currentArchivePath.value
        val internalPath = _archiveInternalPath.value
        if (archivePath.isNotEmpty()) {
            loadArchiveEntries(archivePath, internalPath)
        }
    }

    private fun loadArchiveEntries(archivePath: String, internalPath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _archiveLoading.value = true
            _archiveErrorMessage.value = null
            try {
                val archiveFile = File(archivePath)
                val (items, error) = ArchiveManager.listArchiveEntries(archiveFile, internalPath)
                withContext(Dispatchers.Main) {
                    _archiveEntries.value = items
                    _archiveErrorMessage.value = error
                    _archiveLoading.value = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _archiveEntries.value = emptyList()
                    _archiveErrorMessage.value = "Error: ${e.localizedMessage ?: e.message}"
                    _archiveLoading.value = false
                }
            }
        }
    }

    fun previewArchiveEntryText(archivePath: String, entryPath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val text = ArchiveManager.readArchiveEntryText(File(archivePath), entryPath)
            withContext(Dispatchers.Main) {
                _archivePreviewEntryName.value = entryPath.substringAfterLast('/')
                _archivePreviewText.value = text
            }
        }
    }

    fun closeArchivePreview() {
        _archivePreviewText.value = null
        _archivePreviewEntryName.value = null
    }

    fun cancelArchiveOperation() {
        isArchiveOperationCancelled = true
    }

    fun extractArchiveAll(archivePath: String, targetDirPath: String) {
        isArchiveOperationCancelled = false
        viewModelScope.launch(Dispatchers.IO) {
            _archiveProgressState.value = ArchiveProgressState(
                isRunning = true,
                title = "Mengekstrak Seluruh Arsip...",
                currentFile = "Mempersiapkan ekstraksi...",
                currentCount = 0,
                totalCount = 1,
                percent = 0f
            )

            val archiveFile = File(archivePath)
            val targetDir = File(targetDirPath)

            val (success, message) = ArchiveManager.extractArchive(
                archiveFile = archiveFile,
                targetDir = targetDir,
                entriesToExtract = null,
                onProgress = { current, total, currentFile ->
                    val percent = if (total > 0) current.toFloat() / total.toFloat() else 0f
                    _archiveProgressState.value = ArchiveProgressState(
                        isRunning = true,
                        title = "Mengekstrak Arsip (${(percent * 100).toInt()}%)...",
                        currentFile = currentFile,
                        currentCount = current,
                        totalCount = total,
                        percent = percent
                    )
                },
                isCancelled = { isArchiveOperationCancelled }
            )

            withContext(Dispatchers.Main) {
                _archiveProgressState.value = null
                _appLogs.value += "\n[Arsip Ekstrak] $message\n"
                refreshAll()
            }
        }
    }

    fun extractArchiveEntry(archivePath: String, entryPath: String, targetDirPath: String) {
        isArchiveOperationCancelled = false
        viewModelScope.launch(Dispatchers.IO) {
            _archiveProgressState.value = ArchiveProgressState(
                isRunning = true,
                title = "Mengekstrak Item...",
                currentFile = entryPath,
                currentCount = 0,
                totalCount = 1,
                percent = 0f
            )

            val archiveFile = File(archivePath)
            val targetDir = File(targetDirPath)

            val (success, message) = ArchiveManager.extractArchive(
                archiveFile = archiveFile,
                targetDir = targetDir,
                entriesToExtract = listOf(entryPath),
                onProgress = { current, total, currentFile ->
                    val percent = if (total > 0) current.toFloat() / total.toFloat() else 0f
                    _archiveProgressState.value = ArchiveProgressState(
                        isRunning = true,
                        title = "Mengekstrak Item...",
                        currentFile = currentFile,
                        currentCount = current,
                        totalCount = total,
                        percent = percent
                    )
                },
                isCancelled = { isArchiveOperationCancelled }
            )

            withContext(Dispatchers.Main) {
                _archiveProgressState.value = null
                _appLogs.value += "\n[Arsip Ekstrak] $message\n"
                refreshAll()
            }
        }
    }

    fun compressFilesToZip(sourcePaths: List<String>, targetZipPath: String) {
        isArchiveOperationCancelled = false
        viewModelScope.launch(Dispatchers.IO) {
            _archiveProgressState.value = ArchiveProgressState(
                isRunning = true,
                title = "Mengompresi ke ZIP...",
                currentFile = "Menganalisis file...",
                currentCount = 0,
                totalCount = 1,
                percent = 0f
            )

            val sourceFiles = sourcePaths.map { File(it) }
            val destinationZip = File(targetZipPath)

            val (success, message) = ArchiveManager.compressFilesToZip(
                sourceFiles = sourceFiles,
                destinationZip = destinationZip,
                onProgress = { current, total, currentFile ->
                    val percent = if (total > 0) current.toFloat() / total.toFloat() else 0f
                    _archiveProgressState.value = ArchiveProgressState(
                        isRunning = true,
                        title = "Mengompresi (${(percent * 100).toInt()}%)...",
                        currentFile = currentFile,
                        currentCount = current,
                        totalCount = total,
                        percent = percent
                    )
                },
                isCancelled = { isArchiveOperationCancelled }
            )

            withContext(Dispatchers.Main) {
                _archiveProgressState.value = null
                _appLogs.value += "\n[Arsip Kompresi] $message\n"
                refreshAll()
            }
        }
    }
}
