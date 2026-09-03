package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.documentfile.provider.DocumentFile
import com.example.EditorViewModel
import com.example.FileItem
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Data class representing a storage volume detected by StorageManager.
 */
data class StorageVolumeItem(
    val id: String,
    val title: String,
    val isPrimary: Boolean,
    val isRemovable: Boolean,
    val state: String,
    val rootFile: File?,
    val totalBytes: Long,
    val freeBytes: Long
)

/**
 * Breadcrumb entry in the DocumentFile hierarchy.
 */
data class DocumentBreadcrumb(
    val title: String,
    val doc: DocumentFile,
    val realPath: String? = null
)

/**
 * Wrapper for individual DocumentFile items in a directory.
 */
data class DocumentItem(
    val doc: DocumentFile,
    val name: String,
    val isDirectory: Boolean,
    val isFile: Boolean,
    val size: Long,
    val lastModified: Long,
    val mimeType: String,
    val canRead: Boolean,
    val canWrite: Boolean,
    val uri: Uri,
    val realPath: String? = null
) {
    val isApk: Boolean
        get() = name.endsWith(".apk", ignoreCase = true)

    val isImage: Boolean
        get() = mimeType.startsWith("image/") || name.endsWithAny(".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp", ".svg")

    val isVideo: Boolean
        get() = mimeType.startsWith("video/") || name.endsWithAny(".mp4", ".mkv", ".avi", ".mov", ".webm", ".3gp", ".flv")

    val isAudio: Boolean
        get() = mimeType.startsWith("audio/") || name.endsWithAny(".mp3", ".wav", ".ogg", ".flac", ".m4a", ".aac")

    val isArchive: Boolean
        get() = name.endsWithAny(".zip", ".tar", ".gz", ".7z", ".rar", ".bz2", ".xz", ".apk")

    val isCodeOrText: Boolean
        get() = mimeType.startsWith("text/") || name.endsWithAny(
            ".txt", ".json", ".xml", ".kt", ".java", ".smali",
            ".html", ".css", ".js", ".md", ".sh", ".py", ".c", ".cpp", ".h", ".log", ".gradle", ".properties"
        )

    private fun String.endsWithAny(vararg extensions: String): Boolean {
        return extensions.any { this.endsWith(it, ignoreCase = true) }
    }
}

enum class DocSortMode(val label: String) {
    NAME_ASC("Nama (A-Z)"),
    NAME_DESC("Nama (Z-A)"),
    SIZE_DESC("Ukuran (Besar)"),
    SIZE_ASC("Ukuran (Kecil)"),
    DATE_DESC("Waktu (Baru)"),
    DATE_ASC("Waktu (Lama)")
}

enum class DocFilterCategory(val label: String) {
    ALL("Semua"),
    FOLDERS("Folder"),
    DOCUMENTS("Teks"),
    APKS("APK"),
    IMAGES("Gambar"),
    VIDEOS("Video"),
    AUDIO("Audio"),
    ARCHIVES("Arsip")
}

object StorageManagerUtils {

    /**
     * Query available storage volumes using Android's StorageManager API natively.
     */
    fun queryStorageVolumes(context: Context): List<StorageVolumeItem> {
        val volumesList = mutableListOf<StorageVolumeItem>()
        try {
            val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
            if (storageManager != null) {
                val volumes = storageManager.storageVolumes
                for (volume in volumes) {
                    val isPrimary = volume.isPrimary
                    val isRemovable = volume.isRemovable
                    val state = volume.state
                    val title = volume.getDescription(context)
                    val id = volume.uuid ?: if (isPrimary) "primary" else "removable_${volumesList.size}"

                    var rootFile: File? = null
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        rootFile = volume.directory
                    }

                    if (rootFile == null && isPrimary) {
                        rootFile = Environment.getExternalStorageDirectory()
                    }

                    var total = 0L
                    var free = 0L
                    if (rootFile != null && rootFile.exists()) {
                        try {
                            val stat = StatFs(rootFile.absolutePath)
                            total = stat.totalBytes
                            free = stat.availableBytes
                        } catch (_: Exception) {}
                    }

                    volumesList.add(
                        StorageVolumeItem(
                            id = id,
                            title = title,
                            isPrimary = isPrimary,
                            isRemovable = isRemovable,
                            state = state,
                            rootFile = rootFile,
                            totalBytes = total,
                            freeBytes = free
                        )
                    )
                }
            }
        } catch (_: Exception) {}

        // Fallback to Primary Shared Storage if empty
        if (volumesList.isEmpty()) {
            val primaryDir = Environment.getExternalStorageDirectory()
            var total = 0L
            var free = 0L
            if (primaryDir.exists()) {
                try {
                    val stat = StatFs(primaryDir.absolutePath)
                    total = stat.totalBytes
                    free = stat.availableBytes
                } catch (_: Exception) {}
            }
            volumesList.add(
                StorageVolumeItem(
                    id = "primary",
                    title = "Penyimpanan Utama (Internal)",
                    isPrimary = true,
                    isRemovable = false,
                    state = Environment.MEDIA_MOUNTED,
                    rootFile = primaryDir,
                    totalBytes = total,
                    freeBytes = free
                )
            )
        }

        return volumesList
    }

    /**
     * Get Icon and color tint for a DocumentItem.
     */
    fun getDocumentIcon(item: DocumentItem): Pair<ImageVector, Color> {
        return when {
            item.isDirectory -> Pair(Icons.Default.Folder, Color(0xFFFFA000))
            item.isApk -> Pair(Icons.Default.Android, Color(0xFF4CAF50))
            item.isImage -> Pair(Icons.Default.Image, Color(0xFFAB47BC))
            item.isVideo -> Pair(Icons.Default.Movie, Color(0xFFE53935))
            item.isAudio -> Pair(Icons.Default.MusicNote, Color(0xFF00ACC1))
            item.isArchive -> Pair(Icons.Default.FolderZip, Color(0xFF00897B))
            item.isCodeOrText -> Pair(Icons.Default.Code, Color(0xFF1E88E5))
            else -> Pair(Icons.AutoMirrored.Filled.InsertDriveFile, Color(0xFF78909C))
        }
    }

    /**
     * Open DocumentItem using the app's internal editors/viewers if available,
     * or fallback to system intent chooser.
     */
    fun openDocumentItem(
        context: Context,
        viewModel: EditorViewModel,
        item: DocumentItem,
        onShowChecksum: ((FileItem) -> Unit)? = null,
        onSignApk: ((FileItem) -> Unit)? = null
    ) {
        try {
            val realPath = item.realPath ?: (if (item.uri.scheme == "file") item.uri.path else null)
            val fileObj = if (realPath != null) File(realPath) else null

            if (fileObj != null && fileObj.exists()) {
                val absPath = fileObj.absolutePath
                when {
                    item.isApk -> viewModel.openApkInspector(absPath)
                    item.isImage -> viewModel.openPhotoEditor(absPath)
                    item.isVideo -> viewModel.openVideoPlayer(absPath)
                    item.isCodeOrText -> viewModel.openTextEditor(absPath)
                    item.isArchive -> viewModel.openArchiveViewer(absPath)
                    else -> {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(item.uri, item.mimeType.ifEmpty { "*/*" })
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Buka dengan..."))
                    }
                }
            } else {
                // If it's a SAF URI without a direct filesystem path:
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(item.uri, item.mimeType.ifEmpty { "*/*" })
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Buka dengan..."))
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Tidak dapat membuka berkas: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Copy a DocumentFile to target directory DocumentFile recursively via ContentResolver/Streams.
     */
    suspend fun copyDocumentRecursively(
        context: Context,
        source: DocumentFile,
        targetDir: DocumentFile
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val name = source.name ?: "item"

            if (source.isDirectory) {
                val newSubDir = targetDir.createDirectory(name) ?: return@withContext false
                val children = source.listFiles()
                for (child in children) {
                    val success = copyDocumentRecursively(context, child, newSubDir)
                    if (!success) return@withContext false
                }
                true
            } else {
                val mime = source.type ?: "application/octet-stream"
                val newFile = targetDir.createFile(mime, name) ?: return@withContext false

                resolver.openInputStream(source.uri)?.use { input ->
                    resolver.openOutputStream(newFile.uri)?.use { output ->
                        input.copyTo(output)
                    }
                }
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Move a DocumentFile to target directory DocumentFile (copy then delete).
     */
    suspend fun moveDocumentRecursively(
        context: Context,
        source: DocumentFile,
        targetDir: DocumentFile
    ): Boolean = withContext(Dispatchers.IO) {
        val copied = copyDocumentRecursively(context, source, targetDir)
        if (copied) {
            try {
                source.delete()
                true
            } catch (_: Exception) {
                true
            }
        } else {
            false
        }
    }

    /**
     * Asynchronously calculate total folder size and recursive file count.
     */
    suspend fun calculateFolderSize(folder: DocumentFile): Pair<Long, Int> = withContext(Dispatchers.IO) {
        var totalBytes = 0L
        var fileCount = 0

        fun scan(dir: DocumentFile) {
            try {
                val files = dir.listFiles()
                for (file in files) {
                    if (file.isDirectory) {
                        scan(file)
                    } else {
                        fileCount++
                        totalBytes += file.length()
                    }
                }
            } catch (_: Exception) {}
        }

        scan(folder)
        Pair(totalBytes, fileCount)
    }

    /**
     * Build SAF Tree Intent targeting specific Android/data or Android/obb directories.
     */
    fun getAndroidFolderSAFIntent(subDir: String): Intent {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val encodedPath = "primary%3AAndroid%2F$subDir"
            val uri = Uri.parse("content://com.android.externalstorage.documents/document/$encodedPath")
            intent.putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, uri)
        }
        intent.addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
            Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
        )
        return intent
    }
}
