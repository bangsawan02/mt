package com.example.ui.components

import android.graphics.Bitmap

/**
 * Data structures for 3C Toolbox-style Application Manager.
 */
data class InstalledAppItem(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val sourceDir: String,
    val dataDir: String,
    val isSystemApp: Boolean,
    val isEnabled: Boolean,
    val uid: Int,
    val targetSdkVersion: Int,
    val minSdkVersion: Int,
    val firstInstallTime: Long,
    val lastUpdateTime: Long,
    val fileSize: Long,
    val splitApks: List<String>,
    val iconBitmap: Bitmap?,
    val isRunning: Boolean = false
)

enum class ComponentType {
    ACTIVITY, SERVICE, RECEIVER, PROVIDER
}

data class AppComponentItem(
    val name: String,
    val simpleName: String,
    val type: ComponentType,
    val isEnabled: Boolean,
    val isExported: Boolean,
    val permission: String? = null
)

data class AppPermissionItem(
    val name: String,
    val simpleName: String,
    val isGranted: Boolean,
    val isDangerous: Boolean
)

data class AppSignatureInfo(
    val algorithm: String,
    val md5: String,
    val sha1: String,
    val sha256: String
)

enum class AppFilter(val label: String) {
    ALL("Semua"),
    USER("Pengguna"),
    SYSTEM("Sistem"),
    RUNNING("Berjalan"),
    DISABLED("Nonaktif")
}

enum class AppSort(val label: String) {
    NAME_ASC("Nama (A-Z)"),
    NAME_DESC("Nama (Z-A)"),
    SIZE_DESC("Ukuran (Terbesar)"),
    SIZE_ASC("Ukuran (Terkecil)"),
    UPDATE_TIME_DESC("Terakhir Diperbarui"),
    INSTALL_TIME_DESC("Tanggal Pasang"),
    TARGET_SDK_DESC("Target SDK (API)"),
    UID_ASC("UID")
}

data class AppStats(
    val totalCount: Int = 0,
    val userCount: Int = 0,
    val systemCount: Int = 0,
    val runningCount: Int = 0,
    val disabledCount: Int = 0,
    val totalStorageBytes: Long = 0L
)
