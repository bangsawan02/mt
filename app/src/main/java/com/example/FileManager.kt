package com.example

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import com.topjohnwu.superuser.Shell

object FileManager {

    suspend fun copyFileItem(
        source: File,
        targetDir: File,
        isRoot: Boolean,
        progressFlow: MutableStateFlow<Float?>
    ): CommandResult = withContext(Dispatchers.IO) {
        if (isRoot) {
            progressFlow.value = null // Root shell cp doesn't give easy progress
            val res = Shell.cmd("cp -rf \"${source.absolutePath}\" \"${targetDir.absolutePath}/\"").exec()
            val success = res.isSuccess
            val err = res.err.joinToString("\n")
            return@withContext CommandResult(success, if(success) 0 else -1, res.out.joinToString("\n"), err)
        } else {
            try {
                if (source.isDirectory) {
                    val allFiles = source.walkTopDown().filter { it.isFile }.toList()
                    val totalBytes = allFiles.sumOf { it.length() }
                    var copiedBytes = 0L
                    progressFlow.value = 0f

                    for (file in allFiles) {
                        val relativePath = file.relativeTo(source).path
                        val targetFile = File(File(targetDir, source.name), relativePath)
                        targetFile.parentFile?.mkdirs()

                        val inChannel = FileInputStream(file).channel
                        val outChannel = FileOutputStream(targetFile).channel
                        val size = inChannel.size()
                        var currentPos = 0L

                        while (currentPos < size) {
                            val count = inChannel.transferTo(currentPos, 1024 * 1024L, outChannel) // 1MB chunks
                            if (count <= 0) break
                            currentPos += count
                            copiedBytes += count
                            progressFlow.value = if (totalBytes > 0) copiedBytes.toFloat() / totalBytes.toFloat() else 1f
                        }
                        inChannel.close()
                        outChannel.close()
                    }
                    progressFlow.value = null
                    return@withContext CommandResult(true, 0, "", "")
                } else {
                    progressFlow.value = 0f
                    val dest = File(targetDir, source.name)
                    val inChannel = FileInputStream(source).channel
                    val outChannel = FileOutputStream(dest).channel
                    val size = inChannel.size()
                    var currentPos = 0L

                    while (currentPos < size) {
                        val count = inChannel.transferTo(currentPos, 1024 * 1024L, outChannel) // 1MB chunks
                        if (count <= 0) break
                        currentPos += count
                        progressFlow.value = if (size > 0) currentPos.toFloat() / size.toFloat() else 1f
                    }
                    inChannel.close()
                    outChannel.close()
                    progressFlow.value = null
                    return@withContext CommandResult(true, 0, "", "")
                }
            } catch (e: Exception) {
                progressFlow.value = null
                return@withContext CommandResult(false, -1, "", e.localizedMessage ?: "Unknown error")
            }
        }
    }

    suspend fun moveFileItem(
        source: File,
        targetDir: File,
        isRoot: Boolean,
        progressFlow: MutableStateFlow<Float?>
    ): CommandResult = withContext(Dispatchers.IO) {
        if (isRoot) {
            progressFlow.value = null
            val res = Shell.cmd("mv -f \"${source.absolutePath}\" \"${targetDir.absolutePath}/\"").exec()
            return@withContext CommandResult(res.isSuccess, if(res.isSuccess) 0 else -1, res.out.joinToString("\n"), res.err.joinToString("\n"))
        } else {
            try {
                progressFlow.value = null // Move is usually instantaneous on same FS
                val dest = File(targetDir, source.name)
                val success = source.renameTo(dest)
                if (!success) {
                    // Fallback to copy then delete
                    val copyRes = FileManager.copyFileItem(source, targetDir, false, progressFlow)
                    if (copyRes.success) source.deleteRecursively()
                    return@withContext copyRes
                }
                return@withContext CommandResult(true, 0, "", "")
            } catch (e: Exception) {
                return@withContext CommandResult(false, -1, "", e.localizedMessage ?: "Unknown error")
            }
        }
    }

    suspend fun deleteFileItem(
        target: File,
        isRoot: Boolean,
        progressFlow: MutableStateFlow<Float?>
    ): CommandResult = withContext(Dispatchers.IO) {
        if (isRoot) {
            progressFlow.value = null
            val res = Shell.cmd("rm -rf \"${target.absolutePath}\"").exec()
            return@withContext CommandResult(res.isSuccess, if(res.isSuccess) 0 else -1, res.out.joinToString("\n"), res.err.joinToString("\n"))
        } else {
            try {
                progressFlow.value = null
                val success = target.deleteRecursively()
                return@withContext CommandResult(success, 0, "", if (success) "" else "Failed to delete")
            } catch (e: Exception) {
                return@withContext CommandResult(false, -1, "", e.localizedMessage ?: "Unknown error")
            }
        }
    }
}
