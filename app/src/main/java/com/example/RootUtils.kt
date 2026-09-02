package com.example

import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.io.InputStreamReader

object RootUtils {

    // Check if root access is available natively
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
            "/data/local/su",
            "/su/bin/su"
        )
        for (path in paths) {
            if (File(path).exists()) return true
        }

        return try {
            val process = ProcessBuilder("which", "su").start()
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (_: Exception) {
            false
        }
    }

    // Execute shell command using native Android ProcessBuilder
    fun executeCommand(command: String, runAsRoot: Boolean): CommandResult {
        return try {
            val process = if (runAsRoot) {
                val p = ProcessBuilder("su").redirectErrorStream(false).start()
                val os = DataOutputStream(p.outputStream)
                os.writeBytes("$command\nexit\n")
                os.flush()
                os.close()
                p
            } else {
                ProcessBuilder("sh", "-c", command).redirectErrorStream(false).start()
            }

            val output = StringBuilder()
            val errorOutput = StringBuilder()

            val isReader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (isReader.readLine().also { line = it } != null) {
                if (output.isNotEmpty()) output.append("\n")
                output.append(line)
            }
            isReader.close()

            val errReader = BufferedReader(InputStreamReader(process.errorStream))
            while (errReader.readLine().also { line = it } != null) {
                if (errorOutput.isNotEmpty()) errorOutput.append("\n")
                errorOutput.append(line)
            }
            errReader.close()

            val exitValue = process.waitFor()
            CommandResult(
                success = (exitValue == 0),
                exitCode = exitValue,
                output = output.toString(),
                error = errorOutput.toString()
            )
        } catch (e: Exception) {
            CommandResult(
                success = false,
                exitCode = -1,
                output = "",
                error = e.localizedMessage ?: "Unknown native shell error"
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

