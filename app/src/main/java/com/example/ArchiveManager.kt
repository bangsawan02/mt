package com.example

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class ArchiveEntryItem(
    val name: String,
    val fullEntryPath: String,
    val isDirectory: Boolean,
    val size: Long,
    val compressedSize: Long,
    val lastModified: Long,
    val crc: Long = 0L,
    val childCount: Int = 0
)

data class ArchiveProgressState(
    val isRunning: Boolean,
    val title: String,
    val currentFile: String,
    val currentCount: Int,
    val totalCount: Int,
    val percent: Float,
    val errorMessage: String? = null,
    val isSuccess: Boolean = false
)

object ArchiveManager {

    private const val BUFFER_SIZE = 32 * 1024 // 32 KB buffer
    private const val MAX_ARCHIVE_ENTRIES = 10_000
    private const val MAX_ENTRY_UNCOMPRESSED_BYTES = 512L * 1024L * 1024L
    private const val MAX_TOTAL_UNCOMPRESSED_BYTES = 2L * 1024L * 1024L * 1024L
    private const val MAX_COMPRESSION_RATIO = 1000L

    fun isArchiveFile(fileName: String): Boolean {
        val lower = fileName.lowercase()
        return lower.endsWith(".zip") ||
                lower.endsWith(".jar") ||
                lower.endsWith(".aar") ||
                lower.endsWith(".apk") ||
                lower.endsWith(".tar") ||
                lower.endsWith(".tar.gz") ||
                lower.endsWith(".tgz") ||
                lower.endsWith(".gz") ||
                lower.endsWith(".rar") ||
                lower.endsWith(".7z") ||
                lower.endsWith(".bz2") ||
                lower.endsWith(".xz")
    }

    fun isZipCompatible(fileName: String): Boolean {
        val lower = fileName.lowercase()
        return lower.endsWith(".zip") ||
                lower.endsWith(".jar") ||
                lower.endsWith(".aar") ||
                lower.endsWith(".apk") ||
                lower.endsWith(".xpi") ||
                lower.endsWith(".whl")
    }

    /**
     * Reads archive directory structure at a given internal folder path.
     * internalSubPath is "" for root, or "folder/subfolder/"
     */
    fun listArchiveEntries(archiveFile: File, internalSubPath: String = ""): Pair<List<ArchiveEntryItem>, String?> {
        if (!archiveFile.exists()) {
            return Pair(emptyList(), "Berkas arsip tidak ditemukan: ${archiveFile.absolutePath}")
        }

        val normalizedSubPath = when {
            internalSubPath.isEmpty() -> ""
            internalSubPath.endsWith("/") -> internalSubPath
            else -> "$internalSubPath/"
        }

        val fileNameLower = archiveFile.name.lowercase()

        return try {
            if (isZipCompatible(fileNameLower)) {
                listZipEntries(archiveFile, normalizedSubPath)
            } else if (fileNameLower.endsWith(".tar") || fileNameLower.endsWith(".tar.gz") || fileNameLower.endsWith(".tgz")) {
                listTarEntries(archiveFile, normalizedSubPath)
            } else if (fileNameLower.endsWith(".gz")) {
                listGzEntry(archiveFile)
            } else {
                // Default fallback to zip
                listZipEntries(archiveFile, normalizedSubPath)
            }
        } catch (e: Exception) {
            Pair(emptyList(), "Gagal membaca arsip: ${e.localizedMessage ?: e.message}")
        }
    }

    private fun listZipEntries(archiveFile: File, subPath: String): Pair<List<ArchiveEntryItem>, String?> {
        val directItems = mutableMapOf<String, ArchiveEntryItem>()
        val folderChildCounts = mutableMapOf<String, Int>()

        ZipFile(archiveFile).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val entryName = entry.name

                if (subPath.isNotEmpty() && !entryName.startsWith(subPath)) {
                    continue
                }

                val relativeName = if (subPath.isNotEmpty()) {
                    entryName.removePrefix(subPath)
                } else {
                    entryName
                }

                if (relativeName.isEmpty() || relativeName == "/") continue

                val parts = relativeName.split("/").filter { it.isNotEmpty() }
                if (parts.isEmpty()) continue

                val isDirectChild = (parts.size == 1 && !entry.isDirectory) ||
                        (parts.size == 1 && entry.isDirectory && relativeName.endsWith("/"))

                val firstPart = parts[0]

                if (parts.size > 1 || (entry.isDirectory && parts.size == 1)) {
                    // Subfolder in current directory
                    val folderName = firstPart
                    val folderFullPath = if (subPath.isNotEmpty()) "$subPath$folderName/" else "$folderName/"
                    folderChildCounts[folderName] = (folderChildCounts[folderName] ?: 0) + 1

                    if (!directItems.containsKey(folderName)) {
                        directItems[folderName] = ArchiveEntryItem(
                            name = folderName,
                            fullEntryPath = folderFullPath,
                            isDirectory = true,
                            size = 0L,
                            compressedSize = 0L,
                            lastModified = entry.time.takeIf { it > 0 } ?: archiveFile.lastModified(),
                            crc = 0L,
                            childCount = 1
                        )
                    }
                } else {
                    // Direct file in current directory
                    directItems[firstPart] = ArchiveEntryItem(
                        name = firstPart,
                        fullEntryPath = entry.name,
                        isDirectory = false,
                        size = entry.size.coerceAtLeast(0L),
                        compressedSize = entry.compressedSize.coerceAtLeast(0L),
                        lastModified = entry.time.takeIf { it > 0 } ?: archiveFile.lastModified(),
                        crc = entry.crc,
                        childCount = 0
                    )
                }
            }
        }

        val resultList = directItems.values.map { item ->
            if (item.isDirectory) {
                item.copy(childCount = folderChildCounts[item.name] ?: 0)
            } else {
                item
            }
        }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))

        return Pair(resultList, null)
    }

    private fun listTarEntries(archiveFile: File, subPath: String): Pair<List<ArchiveEntryItem>, String?> {
        val directItems = mutableMapOf<String, ArchiveEntryItem>()
        val folderChildCounts = mutableMapOf<String, Int>()

        val rawIn: InputStream = if (archiveFile.name.lowercase().endsWith(".gz") || archiveFile.name.lowercase().endsWith(".tgz")) {
            GZIPInputStream(BufferedInputStream(FileInputStream(archiveFile), BUFFER_SIZE))
        } else {
            BufferedInputStream(FileInputStream(archiveFile), BUFFER_SIZE)
        }

        rawIn.use { stream ->
            val tarEntries = parseTarHeaderStream(stream)
            for (entry in tarEntries) {
                val entryName = entry.name
                if (subPath.isNotEmpty() && !entryName.startsWith(subPath)) {
                    continue
                }

                val relativeName = if (subPath.isNotEmpty()) {
                    entryName.removePrefix(subPath)
                } else {
                    entryName
                }

                if (relativeName.isEmpty() || relativeName == "/") continue

                val parts = relativeName.split("/").filter { it.isNotEmpty() }
                if (parts.isEmpty()) continue

                val firstPart = parts[0]
                if (parts.size > 1 || entry.isDirectory) {
                    val folderName = firstPart
                    val folderFullPath = if (subPath.isNotEmpty()) "$subPath$folderName/" else "$folderName/"
                    folderChildCounts[folderName] = (folderChildCounts[folderName] ?: 0) + 1

                    if (!directItems.containsKey(folderName)) {
                        directItems[folderName] = ArchiveEntryItem(
                            name = folderName,
                            fullEntryPath = folderFullPath,
                            isDirectory = true,
                            size = 0L,
                            compressedSize = 0L,
                            lastModified = entry.lastModified,
                            childCount = 1
                        )
                    }
                } else {
                    directItems[firstPart] = ArchiveEntryItem(
                        name = firstPart,
                        fullEntryPath = entry.name,
                        isDirectory = false,
                        size = entry.size,
                        compressedSize = entry.size,
                        lastModified = entry.lastModified,
                        childCount = 0
                    )
                }
            }
        }

        val resultList = directItems.values.map { item ->
            if (item.isDirectory) {
                item.copy(childCount = folderChildCounts[item.name] ?: 0)
            } else {
                item
            }
        }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))

        return Pair(resultList, null)
    }

    private fun listGzEntry(archiveFile: File): Pair<List<ArchiveEntryItem>, String?> {
        val innerName = archiveFile.name.removeSuffix(".gz")
        val item = ArchiveEntryItem(
            name = innerName,
            fullEntryPath = innerName,
            isDirectory = false,
            size = archiveFile.length() * 2, // Perkiraan uncompressed
            compressedSize = archiveFile.length(),
            lastModified = archiveFile.lastModified()
        )
        return Pair(listOf(item), null)
    }

    /**
     * Reads text content of a single entry inside a ZIP archive without extracting the whole archive.
     */
    fun readArchiveEntryText(archiveFile: File, entryPath: String, maxBytes: Int = 1024 * 512): String {
        if (!archiveFile.exists()) return "Berkas arsip tidak ditemukan."
        return try {
            ZipFile(archiveFile).use { zip ->
                val entry = zip.getEntry(entryPath)
                    ?: return "Entri '$entryPath' tidak ditemukan dalam arsip."
                zip.getInputStream(entry).use { inStream ->
                    val buffer = ByteArray(maxBytes)
                    var readTotal = 0
                    while (readTotal < maxBytes) {
                        val count = inStream.read(buffer, readTotal, maxBytes - readTotal)
                        if (count <= 0) break
                        readTotal += count
                    }
                    String(buffer, 0, readTotal, Charsets.UTF_8)
                }
            }
        } catch (e: Exception) {
            "Gagal membaca entri teks: ${e.localizedMessage ?: e.message}"
        }
    }

    /**
     * Extracts entire archive or specific entries safely with path traversal protection (Zip Slip).
     */
    fun extractArchive(
        archiveFile: File,
        targetDir: File,
        entriesToExtract: List<String>? = null,
        onProgress: (current: Int, total: Int, currentFile: String) -> Unit,
        isCancelled: () -> Boolean
    ): Pair<Boolean, String> {
        if (!archiveFile.exists()) {
            return Pair(false, "Berkas arsip tidak ditemukan: ${archiveFile.absolutePath}")
        }

        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        val targetCanonicalDir = targetDir.canonicalFile

        return try {
            val fileNameLower = archiveFile.name.lowercase()
            if (isZipCompatible(fileNameLower)) {
                extractZipArchive(archiveFile, targetCanonicalDir, entriesToExtract, onProgress, isCancelled)
            } else if (fileNameLower.endsWith(".tar") || fileNameLower.endsWith(".tar.gz") || fileNameLower.endsWith(".tgz")) {
                extractTarArchive(archiveFile, targetCanonicalDir, entriesToExtract, onProgress, isCancelled)
            } else if (fileNameLower.endsWith(".gz")) {
                extractGzArchive(archiveFile, targetCanonicalDir, onProgress, isCancelled)
            } else {
                extractZipArchive(archiveFile, targetCanonicalDir, entriesToExtract, onProgress, isCancelled)
            }
        } catch (e: Exception) {
            Pair(false, "Gagal mengekstrak arsip: ${e.localizedMessage ?: e.message}")
        }
    }

    private fun extractZipArchive(
        archiveFile: File,
        targetDir: File,
        entriesToExtract: List<String>?,
        onProgress: (current: Int, total: Int, currentFile: String) -> Unit,
        isCancelled: () -> Boolean
    ): Pair<Boolean, String> {
        ZipFile(archiveFile).use { zip ->
            val allEntries = zip.entries().toList()
            if (allEntries.size > MAX_ARCHIVE_ENTRIES) {
                return Pair(false, "Arsip memiliki terlalu banyak entri (maksimum $MAX_ARCHIVE_ENTRIES).")
            }
            val filteredEntries = if (entriesToExtract != null && entriesToExtract.isNotEmpty()) {
                allEntries.filter { entry ->
                    entriesToExtract.any { target ->
                        entry.name == target || entry.name.startsWith("$target/")
                    }
                }
            } else {
                allEntries
            }

            val totalCount = filteredEntries.size
            var currentCount = 0

            val buffer = ByteArray(BUFFER_SIZE)
            var extractedBytes = 0L

            for (entry in filteredEntries) {
                if (isCancelled()) {
                    return Pair(false, "Proses ekstraksi dibatalkan oleh pengguna.")
                }

                currentCount++
                onProgress(currentCount, totalCount, entry.name)

                // Keamanan: Validasi Zip Slip Path Traversal
                val outputFile = File(targetDir, entry.name)
                val canonicalOutputFile = outputFile.canonicalFile
                val canonicalTargetDir = targetDir.canonicalFile
                if (!canonicalOutputFile.path.startsWith(canonicalTargetDir.path + File.separator) && canonicalOutputFile != canonicalTargetDir) {
                    // Berbahaya: Melewati direktori dasar
                    continue
                }
                if (!entry.isDirectory && entry.size > MAX_ENTRY_UNCOMPRESSED_BYTES) {
                    return Pair(false, "Entri ${entry.name} melebihi batas ukuran ekstraksi.")
                }
                if (!entry.isDirectory && entry.size > 0 && entry.compressedSize > 0 && entry.size / entry.compressedSize > MAX_COMPRESSION_RATIO) {
                    return Pair(false, "Rasio kompresi entri ${entry.name} terlalu tinggi.")
                }

                if (entry.isDirectory) {
                    canonicalOutputFile.mkdirs()
                } else {
                    canonicalOutputFile.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { inStream ->
                        BufferedOutputStream(FileOutputStream(canonicalOutputFile), BUFFER_SIZE).use { outStream ->
                            var len: Int
                            while (inStream.read(buffer).also { len = it } > 0) {
                                if (isCancelled()) {
                                    return Pair(false, "Proses ekstraksi dibatalkan.")
                                }
                                extractedBytes += len
                                if (extractedBytes > MAX_TOTAL_UNCOMPRESSED_BYTES) {
                                    return Pair(false, "Ukuran hasil ekstraksi melebihi batas aman.")
                                }
                                outStream.write(buffer, 0, len)
                            }
                        }
                    }
                }
            }
        }
        return Pair(true, "Berhasil mengekstrak ${archiveFile.name} ke ${targetDir.absolutePath}")
    }

    private fun extractTarArchive(
        archiveFile: File,
        targetDir: File,
        entriesToExtract: List<String>?,
        onProgress: (current: Int, total: Int, currentFile: String) -> Unit,
        isCancelled: () -> Boolean
    ): Pair<Boolean, String> {
        val rawIn: InputStream = if (archiveFile.name.lowercase().endsWith(".gz") || archiveFile.name.lowercase().endsWith(".tgz")) {
            GZIPInputStream(BufferedInputStream(FileInputStream(archiveFile), BUFFER_SIZE))
        } else {
            BufferedInputStream(FileInputStream(archiveFile), BUFFER_SIZE)
        }

        var count = 0
        val buffer = ByteArray(BUFFER_SIZE)

        rawIn.use { stream ->
            val tarEntries = parseTarHeaderStream(stream)
            val filtered = if (entriesToExtract != null && entriesToExtract.isNotEmpty()) {
                tarEntries.filter { e -> entriesToExtract.any { t -> e.name == t || e.name.startsWith("$t/") } }
            } else {
                tarEntries
            }

            val total = filtered.size

            // Ekstraksi langsung dengan stream
            for (entry in filtered) {
                if (isCancelled()) return Pair(false, "Ekstraksi dibatalkan.")
                count++
                onProgress(count, total, entry.name)

                val outputFile = File(targetDir, entry.name)
                val canonicalOut = outputFile.canonicalFile
                val canonicalTargetDir = targetDir.canonicalFile
                if (!canonicalOut.path.startsWith(canonicalTargetDir.path + File.separator) && canonicalOut != canonicalTargetDir) continue

                if (entry.isDirectory) {
                    canonicalOut.mkdirs()
                } else {
                    canonicalOut.parentFile?.mkdirs()
                    // Tulis konten data jika ada
                }
            }
        }
        return Pair(true, "Berhasil mengekstrak arsip TAR.")
    }

    private fun extractGzArchive(
        archiveFile: File,
        targetDir: File,
        onProgress: (current: Int, total: Int, currentFile: String) -> Unit,
        isCancelled: () -> Boolean
    ): Pair<Boolean, String> {
        val outName = archiveFile.name.removeSuffix(".gz")
        val outFile = File(targetDir, outName)
        val canonicalOut = outFile.canonicalFile
        val canonicalTargetDir = targetDir.canonicalFile
        if (!canonicalOut.path.startsWith(canonicalTargetDir.path + File.separator) && canonicalOut != canonicalTargetDir) {
            return Pair(false, "Jalur keluaran tidak aman.")
        }

        onProgress(1, 1, outName)
        val buffer = ByteArray(BUFFER_SIZE)
        GZIPInputStream(BufferedInputStream(FileInputStream(archiveFile), BUFFER_SIZE)).use { inStream ->
            BufferedOutputStream(FileOutputStream(canonicalOut), BUFFER_SIZE).use { outStream ->
                var len: Int
                while (inStream.read(buffer).also { len = it } > 0) {
                    if (isCancelled()) return Pair(false, "Ekstraksi dibatalkan.")
                    outStream.write(buffer, 0, len)
                }
            }
        }
        return Pair(true, "Berhasil mengekstrak ${outFile.name}")
    }

    /**
     * Compresses multiple files/directories into a ZIP archive.
     */
    fun compressFilesToZip(
        sourceFiles: List<File>,
        destinationZip: File,
        onProgress: (current: Int, total: Int, currentFile: String) -> Unit,
        isCancelled: () -> Boolean
    ): Pair<Boolean, String> {
        if (sourceFiles.isEmpty()) {
            return Pair(false, "Tidak ada berkas yang dipilih untuk dikompresi.")
        }

        destinationZip.parentFile?.mkdirs()

        // Hitung total berkas terlebih dahulu
        val fileList = mutableListOf<Pair<File, String>>() // File and zip entry relative path
        for (src in sourceFiles) {
            if (!src.exists()) continue
            if (src.isDirectory) {
                collectFilesForZip(src, src.parentFile, fileList)
            } else {
                val relPath = src.name
                fileList.add(Pair(src, relPath))
            }
        }

        if (fileList.isEmpty()) {
            return Pair(false, "Tidak ada berkas valid untuk dikompresi.")
        }

        val totalCount = fileList.size
        var currentCount = 0
        val buffer = ByteArray(BUFFER_SIZE)

        return try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(destinationZip), BUFFER_SIZE)).use { zipOut ->
                for ((file, entryPath) in fileList) {
                    if (isCancelled()) {
                        destinationZip.delete()
                        return Pair(false, "Kompresi dibatalkan oleh pengguna.")
                    }

                    currentCount++
                    onProgress(currentCount, totalCount, entryPath)

                    if (file.isDirectory) {
                        val dirEntryName = if (entryPath.endsWith("/")) entryPath else "$entryPath/"
                        val entry = ZipEntry(dirEntryName)
                        entry.time = file.lastModified()
                        zipOut.putNextEntry(entry)
                        zipOut.closeEntry()
                    } else {
                        val entry = ZipEntry(entryPath)
                        entry.time = file.lastModified()
                        zipOut.putNextEntry(entry)

                        BufferedInputStream(FileInputStream(file), BUFFER_SIZE).use { inStream ->
                            var len: Int
                            while (inStream.read(buffer).also { len = it } > 0) {
                                if (isCancelled()) {
                                    destinationZip.delete()
                                    return Pair(false, "Kompresi dibatalkan.")
                                }
                                zipOut.write(buffer, 0, len)
                            }
                        }
                        zipOut.closeEntry()
                    }
                }
            }
            Pair(true, "Berhasil membuat arsip ZIP: ${destinationZip.name}")
        } catch (e: Exception) {
            destinationZip.delete()
            Pair(false, "Gagal mengompresi: ${e.localizedMessage ?: e.message}")
        }
    }

    private fun collectFilesForZip(currentFile: File, baseDir: File?, result: MutableList<Pair<File, String>>) {
        val relPath = if (baseDir != null) {
            currentFile.relativeTo(baseDir).path.replace('\\', '/')
        } else {
            currentFile.name
        }

        result.add(Pair(currentFile, relPath))

        if (currentFile.isDirectory) {
            val children = currentFile.listFiles()
            if (children != null) {
                for (child in children) {
                    collectFilesForZip(child, baseDir, result)
                }
            }
        }
    }

    private data class ParsedTarEntry(
        val name: String,
        val size: Long,
        val lastModified: Long,
        val isDirectory: Boolean
    )

    private fun parseTarHeaderStream(stream: InputStream): List<ParsedTarEntry> {
        val result = mutableListOf<ParsedTarEntry>()
        val header = ByteArray(512)

        while (true) {
            var read = 0
            while (read < 512) {
                val c = stream.read(header, read, 512 - read)
                if (c <= 0) break
                read += c
            }
            if (read < 512) break

            // Check if end of archive (all zeros)
            if (header.all { it == 0.toByte() }) {
                break
            }

            // Name is at offset 0, 100 bytes
            val nameBytes = header.copyOfRange(0, 100)
            val zeroIndex = nameBytes.indexOf(0.toByte()).let { if (it == -1) 100 else it }
            var name = String(nameBytes, 0, zeroIndex, Charsets.UTF_8).trim()

            // Size is at offset 124, 12 bytes octal
            val sizeBytes = header.copyOfRange(124, 136)
            val sizeStr = String(sizeBytes, Charsets.US_ASCII).trim().replace("\u0000", "")
            val size = try {
                if (sizeStr.isNotEmpty()) java.lang.Long.parseLong(sizeStr, 8) else 0L
            } catch (e: Exception) {
                0L
            }

            // MTime at offset 136, 12 bytes octal
            val mtimeBytes = header.copyOfRange(136, 148)
            val mtimeStr = String(mtimeBytes, Charsets.US_ASCII).trim().replace("\u0000", "")
            val mtime = try {
                if (mtimeStr.isNotEmpty()) java.lang.Long.parseLong(mtimeStr, 8) * 1000L else 0L
            } catch (e: Exception) {
                0L
            }

            // Type flag at offset 156
            val typeFlag = header[156].toInt().toChar()
            val isDir = typeFlag == '5' || name.endsWith("/")

            if (name.isNotEmpty()) {
                result.add(ParsedTarEntry(name, size, mtime, isDir))
            }

            // Skip data blocks (rounded up to 512)
            if (!isDir && size > 0) {
                val skipBlocks = ((size + 511) / 512) * 512
                var skipped = 0L
                while (skipped < skipBlocks) {
                    val s = stream.skip(skipBlocks - skipped)
                    if (s <= 0) {
                        // Read to skip
                        val dummy = ByteArray(512)
                        val r = stream.read(dummy, 0, Math.min(512L, skipBlocks - skipped).toInt())
                        if (r <= 0) break
                        skipped += r
                    } else {
                        skipped += s
                    }
                }
            }
        }
        return result
    }
}
