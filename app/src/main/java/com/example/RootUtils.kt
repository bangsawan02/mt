package com.example

import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.io.InputStreamReader

object RootUtils {

    // Simple check if device has su binary
    fun isRootAvailable(): Boolean {
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
        return false
    }

    // Attempt to execute a shell command as Root or falling back to standard command
    fun executeCommand(command: String, runAsRoot: Boolean): CommandResult {
        var process: Process? = null
        var os: DataOutputStream? = null
        var isReader: BufferedReader? = null
        val output = StringBuilder()
        val errorOutput = StringBuilder()

        try {
            if (runAsRoot) {
                process = Runtime.getRuntime().exec("su")
                os = DataOutputStream(process.outputStream)
                os.writeBytes(command + "\n")
                os.writeBytes("exit\n")
                os.flush()
            } else {
                process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            }

            isReader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (isReader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }

            val errReader = BufferedReader(InputStreamReader(process.errorStream))
            while (errReader.readLine().also { line = it } != null) {
                errorOutput.append(line).append("\n")
            }

            val exitValue = process.waitFor()
            return CommandResult(
                success = (exitValue == 0),
                exitCode = exitValue,
                output = output.toString().trim(),
                error = errorOutput.toString().trim()
            )
        } catch (e: Exception) {
            return CommandResult(
                success = false,
                exitCode = -1,
                output = "",
                error = e.localizedMessage ?: "Unknown shell error"
            )
        } finally {
            try {
                os?.close()
                isReader?.close()
                process?.destroy()
            } catch (ignore: Exception) {}
        }
    }
}

data class CommandResult(
    val success: Boolean,
    val exitCode: Int,
    val output: String,
    val error: String
)
