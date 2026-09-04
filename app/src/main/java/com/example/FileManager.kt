package com.example

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.channels.FileChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow

object FileManager {

    suspend fun copyFileItem(
        source: File,
        targetDir: File,
        isRoot: Boolean,
        progressFlow: MutableStateFlow<Float?>
    ): CommandResult = withContext(Dispatchers.IO) {
        if (isRoot) {
            progressFlow.value = null
            return@withContext RootUtils.executeCommandArgs(
                listOf("cp", "-rf", "--", source.absolutePath, targetDir.absolutePath), true
            )
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

                        FileInputStream(file).channel.use { inChannel ->
                            FileOutputStream(targetFile).channel.use { outChannel ->
                                val size = inChannel.size()
                                var currentPos = 0L
                                while (currentPos < size) {
                                    val count = inChannel.transferTo(currentPos, 1024 * 1024L, outChannel) // 1MB chunks
                                    if (count <= 0) break
                                    currentPos += count
                                    copiedBytes += count
                                    progressFlow.value = if (totalBytes > 0) copiedBytes.toFloat() / totalBytes.toFloat() else 1f
                                }
                            }
                        }
                    }
                    progressFlow.value = null
                    return@withContext CommandResult(true, 0, "", "")
                } else {
                    progressFlow.value = 0f
                    val dest = File(targetDir, source.name)
                    FileInputStream(source).channel.use { inChannel ->
                        FileOutputStream(dest).channel.use { outChannel ->
                            val size = inChannel.size()
                            var currentPos = 0L
                            while (currentPos < size) {
                                val count = inChannel.transferTo(currentPos, 1024 * 1024L, outChannel) // 1MB chunks
                                if (count <= 0) break
                                currentPos += count
                                progressFlow.value = if (size > 0) currentPos.toFloat() / size.toFloat() else 1f
                            }
                        }
                    }
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
            return@withContext RootUtils.executeCommandArgs(
                listOf("mv", "-f", "--", source.absolutePath, targetDir.absolutePath), true
            )
        } else {
            try {
                progressFlow.value = null
                val dest = File(targetDir, source.name)
                val success = source.renameTo(dest)
                if (!success) {
                    val copyRes = copyFileItem(source, targetDir, false, progressFlow)
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
            return@withContext RootUtils.executeCommandArgs(listOf("rm", "-rf", "--", target.absolutePath), true)
        } else {
            try {
                progressFlow.value = null
                val success = target.deleteRecursively()
                return@withContext CommandResult(success, if (success) 0 else -1, "", if (success) "" else "Failed to delete")
            } catch (e: Exception) {
                return@withContext CommandResult(false, -1, "", e.localizedMessage ?: "Unknown error")
            }
        }
    }
}
