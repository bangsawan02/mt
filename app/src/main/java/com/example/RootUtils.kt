package com.example

import com.topjohnwu.superuser.Shell
import java.io.File

object RootUtils {

    // Check if root access is available using libsu or fallback paths
    fun isRootAvailable(): Boolean {
        return try {
            Shell.getShell().isRoot
        } catch (e: Exception) {
            val paths = arrayOf(
                "/system/app/Superuser.apk",
                "/sbin/su",
                "/system/bin/su",
                "/system/xbin/su",
                "/data/local/xbin/su",
                "/data/local/bin/su",
                "/system/sd/xbin/su",
                "/system/bin/failsafe/su",
                "/data/local/su"
            )
            for (path in paths) {
                if (File(path).exists()) return true
            }
            false
        }
    }

    // Attempt to execute a shell command as Root using libsu or falling back to standard command
    fun executeCommand(command: String, runAsRoot: Boolean): CommandResult {
        return try {
            if (runAsRoot) {
                val result = Shell.cmd(command).exec()
                CommandResult(
                    success = result.isSuccess,
                    exitCode = result.code,
                    output = result.out.joinToString("\n"),
                    error = result.err.joinToString("\n")
                )
            } else {
                val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
                val output = StringBuilder()
                val errorOutput = StringBuilder()
                val isReader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
                var line: String?
                while (isReader.readLine().also { line = it } != null) {
                    output.append(line).append("\n")
                }
                val errReader = java.io.BufferedReader(java.io.InputStreamReader(process.errorStream))
                while (errReader.readLine().also { line = it } != null) {
                    errorOutput.append(line).append("\n")
                }
                val exitValue = process.waitFor()
                CommandResult(
                    success = (exitValue == 0),
                    exitCode = exitValue,
                    output = output.toString().trim(),
                    error = errorOutput.toString().trim()
                )
            }
        } catch (e: Exception) {
            CommandResult(
                success = false,
                exitCode = -1,
                output = "",
                error = e.localizedMessage ?: "Unknown shell error"
            )
        }
    }
}

data class CommandResult(
    val success: Boolean,
    val exitCode: Int,
    val output: String,
    val error: String
)
