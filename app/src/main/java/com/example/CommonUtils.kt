package com.example

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CommonUtils {
    fun formatFileSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        return String.format(Locale.US, "%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    fun formatTimestamp(timestamp: Long): String {
        if (timestamp <= 0) return "-"
        val sdf = SimpleDateFormat("dd MMM yyyy HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun getStorageInfo(path: String): String {
        return try {
            val stat = android.os.StatFs(path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong

            val totalBytes = totalBlocks * blockSize
            val freeBytes = availableBlocks * blockSize
            val usedBytes = totalBytes - freeBytes

            val usedGB = usedBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
            val totalGB = totalBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)

            String.format(java.util.Locale.US, "Penyimpanan: %.2fG/%.2fG", usedGB, totalGB)
        } catch (e: Exception) {
            "Penyimpanan: --/--"
        }
    }
}
