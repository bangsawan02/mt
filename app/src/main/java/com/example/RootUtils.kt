package com.example

import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

object RootUtils {
    private const val COMMAND_TIMEOUT_SECONDS = 30L
    private const val MAX_OUTPUT_CHARS = 1_000_000

    fun isRootAvailable(): Boolean {
        val paths = arrayOf("/system/app/Superuser.apk", "/sbin/su", "/system/bin/su", "/system/xbin/su", "/data/local/xbin/su", "/data/local/bin/su", "/system/sd/xbin/su", "/system/bin/failsafe/su", "/data/local/su", "/su/bin/su")
        if (paths.any { File(it).exists() }) return true
        return try {
            ProcessBuilder("which", "su").start().waitFor() == 0
        } catch (_: Exception) {
            false
        }
    }

    /** Runs a fixed command with arguments quoted for the Android shell. */
    fun executeCommandArgs(args: List<String>, runAsRoot: Boolean): CommandResult {
        require(args.isNotEmpty()) { "Command arguments must not be empty" }
        return executeCommand(args.joinToString(" ") { shellQuote(it) }, runAsRoot)
    }

    /**
     * Runs a shell expression. Only use this for fixed expressions or the terminal feature;
     * file paths and user-supplied values must go through [executeCommandArgs].
     */
    fun executeCommand(command: String, runAsRoot: Boolean): CommandResult {
        return try {
            val process = if (runAsRoot) {
                ProcessBuilder("su").redirectErrorStream(false).start().also { process ->
                    DataOutputStream(process.outputStream).use { output ->
                        output.writeBytes("$command\nexit\n")
                        output.flush()
                    }
                }
            } else {
                ProcessBuilder("sh", "-c", command).redirectErrorStream(false).start()
            }

            val output = StringBuilder()
            val errorOutput = StringBuilder()
            // Drain both streams concurrently: reading one stream first can deadlock a noisy child.
            val stdoutThread = thread { drain(process.inputStream.bufferedReader(), output) }
            val stderrThread = thread { drain(process.errorStream.bufferedReader(), errorOutput) }
            val finished = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
            }
            stdoutThread.join()
            stderrThread.join()

            if (!finished) {
                CommandResult(false, -1, output.toString(), "Command timed out after $COMMAND_TIMEOUT_SECONDS seconds")
            } else {
                CommandResult(process.exitValue() == 0, process.exitValue(), output.toString(), errorOutput.toString())
            }
        } catch (e: Exception) {
            CommandResult(false, -1, "", e.localizedMessage ?: "Unknown native shell error")
        }
    }

    private fun drain(reader: BufferedReader, destination: StringBuilder) {
        reader.use {
            val buffer = CharArray(8 * 1024)
            while (true) {
                val count = it.read(buffer)
                if (count < 0 || destination.length >= MAX_OUTPUT_CHARS) break
                val remaining = MAX_OUTPUT_CHARS - destination.length
                destination.append(buffer, 0, minOf(count, remaining))
            }
        }
    }

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"
}

data class CommandResult(val success: Boolean, val exitCode: Int, val output: String, val error: String)
