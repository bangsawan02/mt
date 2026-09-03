package com.example.ui.components

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ProviderInfo
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.RootUtils
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 3C Toolbox Engine for Android Apps:
 * Native SDK APIs for inspection, component enumeration, extraction, and root controls.
 */
object AppManagerUtils {

    /**
     * Query all installed packages using native Android PackageManager.
     */
    fun loadInstalledApps(context: Context, isRoot: Boolean = false): List<InstalledAppItem> {
        val pm = context.packageManager
        val flags = PackageManager.GET_META_DATA
        val packages = pm.getInstalledPackages(flags)
        val runningPackages = getRunningPackages(context, isRoot)

        return packages.mapNotNull { pkg ->
            try {
                val appInfo = pkg.applicationInfo ?: return@mapNotNull null
                val appName = pm.getApplicationLabel(appInfo).toString().ifBlank { pkg.packageName }
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val isEnabled = appInfo.enabled
                val sourceApk = appInfo.sourceDir ?: ""
                val dataDir = appInfo.dataDir ?: ""
                val uid = appInfo.uid

                val targetSdk = appInfo.targetSdkVersion
                val minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    appInfo.minSdkVersion
                } else {
                    0
                }

                val splitApks = appInfo.splitSourceDirs?.toList() ?: emptyList()

                val baseFile = File(sourceApk)
                var totalSize = if (baseFile.exists()) baseFile.length() else 0L
                for (split in splitApks) {
                    val sFile = File(split)
                    if (sFile.exists()) totalSize += sFile.length()
                }

                val iconBmp = try {
                    val drawable = pm.getApplicationIcon(appInfo)
                    val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96
                    val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                    Bitmap.createScaledBitmap(bitmap, 96, 96, true)
                } catch (_: Exception) {
                    null
                }

                val vCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    pkg.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    pkg.versionCode.toLong()
                }

                InstalledAppItem(
                    appName = appName,
                    packageName = pkg.packageName,
                    versionName = pkg.versionName ?: "1.0",
                    versionCode = vCode,
                    sourceDir = sourceApk,
                    dataDir = dataDir,
                    isSystemApp = isSystem,
                    isEnabled = isEnabled,
                    uid = uid,
                    targetSdkVersion = targetSdk,
                    minSdkVersion = minSdk,
                    firstInstallTime = pkg.firstInstallTime,
                    lastUpdateTime = pkg.lastUpdateTime,
                    fileSize = totalSize,
                    splitApks = splitApks,
                    iconBitmap = iconBmp,
                    isRunning = runningPackages.contains(pkg.packageName)
                )
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * Enumerate activities, services, broadcast receivers, and content providers of an app.
     */
    fun loadAppComponents(context: Context, packageName: String): List<AppComponentItem> {
        val pm = context.packageManager
        val list = mutableListOf<AppComponentItem>()

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            PackageManager.GET_ACTIVITIES or
                    PackageManager.GET_SERVICES or
                    PackageManager.GET_RECEIVERS or
                    PackageManager.GET_PROVIDERS or
                    PackageManager.MATCH_DISABLED_COMPONENTS
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_ACTIVITIES or
                    PackageManager.GET_SERVICES or
                    PackageManager.GET_RECEIVERS or
                    PackageManager.GET_PROVIDERS or
                    PackageManager.GET_DISABLED_COMPONENTS
        }

        val pkgInfo = try {
            pm.getPackageInfo(packageName, flags)
        } catch (_: Exception) {
            return emptyList()
        }

        // 1. Activities
        pkgInfo.activities?.forEach { act ->
            list.add(
                AppComponentItem(
                    name = act.name,
                    simpleName = act.name.substringAfterLast('.'),
                    type = ComponentType.ACTIVITY,
                    isEnabled = act.isEnabled,
                    isExported = act.exported,
                    permission = act.permission
                )
            )
        }

        // 2. Services
        pkgInfo.services?.forEach { srv ->
            list.add(
                AppComponentItem(
                    name = srv.name,
                    simpleName = srv.name.substringAfterLast('.'),
                    type = ComponentType.SERVICE,
                    isEnabled = srv.isEnabled,
                    isExported = srv.exported,
                    permission = srv.permission
                )
            )
        }

        // 3. Receivers
        pkgInfo.receivers?.forEach { rcv ->
            list.add(
                AppComponentItem(
                    name = rcv.name,
                    simpleName = rcv.name.substringAfterLast('.'),
                    type = ComponentType.RECEIVER,
                    isEnabled = rcv.isEnabled,
                    isExported = rcv.exported,
                    permission = rcv.permission
                )
            )
        }

        // 4. Providers
        pkgInfo.providers?.forEach { prv ->
            list.add(
                AppComponentItem(
                    name = prv.name,
                    simpleName = prv.name.substringAfterLast('.'),
                    type = ComponentType.PROVIDER,
                    isEnabled = prv.isEnabled,
                    isExported = prv.exported,
                    permission = prv.readPermission ?: prv.writePermission
                )
            )
        }

        return list
    }

    /**
     * Enumerate declared permissions and whether they are currently granted to the app.
     */
    fun loadAppPermissions(context: Context, packageName: String): List<AppPermissionItem> {
        val pm = context.packageManager
        val pkgInfo = try {
            pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
        } catch (_: Exception) {
            return emptyList()
        }

        val requested = pkgInfo.requestedPermissions ?: return emptyList()
        val flags = pkgInfo.requestedPermissionsFlags ?: IntArray(0)

        val result = mutableListOf<AppPermissionItem>()
        for (i in requested.indices) {
            val permName = requested[i]
            val isGranted = if (i < flags.size) {
                (flags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
            } else false

            val isDangerous = permName.contains("LOCATION", ignoreCase = true) ||
                    permName.contains("CAMERA", ignoreCase = true) ||
                    permName.contains("MICROPHONE", ignoreCase = true) ||
                    permName.contains("RECORD_AUDIO", ignoreCase = true) ||
                    permName.contains("CONTACTS", ignoreCase = true) ||
                    permName.contains("CALENDAR", ignoreCase = true) ||
                    permName.contains("SMS", ignoreCase = true) ||
                    permName.contains("CALL", ignoreCase = true) ||
                    permName.contains("STORAGE", ignoreCase = true) ||
                    permName.contains("MEDIA", ignoreCase = true)

            result.add(
                AppPermissionItem(
                    name = permName,
                    simpleName = permName.substringAfterLast('.'),
                    isGranted = isGranted,
                    isDangerous = isDangerous
                )
            )
        }
        return result.sortedWith(compareByDescending<AppPermissionItem> { it.isDangerous }.thenBy { it.simpleName })
    }

    /**
     * Compute signatures & fingerprints (MD5, SHA-1, SHA-256).
     */
    fun loadAppSignature(context: Context, packageName: String): AppSignatureInfo? {
        val pm = context.packageManager
        try {
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val pInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                pInfo.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                val pInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                pInfo.signatures
            }

            val sig = signatures?.firstOrNull() ?: return null
            val certBytes = sig.toByteArray()

            return AppSignatureInfo(
                algorithm = "X.509",
                md5 = hashBytes(certBytes, "MD5"),
                sha1 = hashBytes(certBytes, "SHA-1"),
                sha256 = hashBytes(certBytes, "SHA-256")
            )
        } catch (_: Exception) {
            return null
        }
    }

    private fun hashBytes(data: ByteArray, algorithm: String): String {
        return try {
            val digest = MessageDigest.getInstance(algorithm).digest(data)
            digest.joinToString(":") { String.format("%02X", it) }
        } catch (_: Exception) {
            "N/A"
        }
    }

    /**
     * Extract Base APK and any Split APKs to Download/3C_Backups.
     */
    fun extractAppApk(context: Context, app: InstalledAppItem, isRoot: Boolean): Pair<Boolean, String> {
        val baseTargetDir = File(Environment.getExternalStorageDirectory(), "Download/3C_Backups")
        if (!baseTargetDir.exists()) baseTargetDir.mkdirs()

        val cleanName = app.appName.replace("[^a-zA-Z0-9_.-]".toRegex(), "_")
        val cleanVer = app.versionName.replace("[^a-zA-Z0-9_.-]".toRegex(), "_")

        return try {
            if (app.splitApks.isEmpty()) {
                val destFile = File(baseTargetDir, "${cleanName}_v${cleanVer}.apk")
                copyFileWithFallback(app.sourceDir, destFile.absolutePath, isRoot)
                Pair(true, destFile.absolutePath)
            } else {
                // Multi-APK App Bundle folder
                val bundleDir = File(baseTargetDir, "${cleanName}_v${cleanVer}_bundle")
                if (!bundleDir.exists()) bundleDir.mkdirs()

                // Copy base
                val baseDest = File(bundleDir, "base.apk")
                copyFileWithFallback(app.sourceDir, baseDest.absolutePath, isRoot)

                // Copy splits
                for ((idx, splitPath) in app.splitApks.withIndex()) {
                    val splitFile = File(splitPath)
                    val splitDest = File(bundleDir, splitFile.name.ifBlank { "split_$idx.apk" })
                    copyFileWithFallback(splitPath, splitDest.absolutePath, isRoot)
                }
                Pair(true, bundleDir.absolutePath)
            }
        } catch (e: Exception) {
            Pair(false, e.message ?: "Gagal mengekstrak berkas APK")
        }
    }

    private fun copyFileWithFallback(srcPath: String, destPath: String, isRoot: Boolean) {
        val src = File(srcPath)
        val dest = File(destPath)
        if (src.canRead()) {
            FileInputStream(src).use { input ->
                FileOutputStream(dest).use { output ->
                    input.copyTo(output)
                }
            }
        } else if (isRoot) {
            val cmd = "cp \"$srcPath\" \"$destPath\" && chmod 666 \"$destPath\""
            val res = RootUtils.executeCommand(cmd, true)
            if (!res.success) throw Exception(res.error)
        } else {
            throw Exception("Akses berkas ditolak (Memerlukan akses Root untuk aplikasi sistem ini)")
        }
    }

    /**
     * Share APK file to other applications via Android Intent.
     */
    fun shareApk(context: Context, app: InstalledAppItem) {
        try {
            val baseFile = File(app.sourceDir)
            val shareFile: File
            if (baseFile.canRead()) {
                shareFile = baseFile
            } else {
                // Copy to cache
                val cacheDir = File(context.cacheDir, "shared_apks").apply { if (!exists()) mkdirs() }
                val target = File(cacheDir, "${app.packageName}.apk")
                copyFileWithFallback(app.sourceDir, target.absolutePath, false)
                shareFile = target
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                shareFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "${app.appName} (v${app.versionName})")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Bagikan APK: ${app.appName}"))
        } catch (e: Exception) {
            // Fallback: copy to Downloads and open settings
            val res = extractAppApk(context, app, false)
            if (res.first) {
                val file = File(res.second)
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/vnd.android.package-archive"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Bagikan APK: ${app.appName}"))
            } else {
                throw Exception("Gagal membagikan APK: ${e.message}")
            }
        }
    }

    fun launchApp(context: Context, packageName: String): Boolean {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
        return true
    }

    fun openAppDetailsSettings(context: Context, packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openInPlayStore(context: Context, packageName: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
        }
    }

    // 3C Toolbox Root Operations
    fun freezeApp(packageName: String, isRoot: Boolean): Pair<Boolean, String> {
        if (!isRoot) return Pair(false, "Memerlukan akses Root untuk membekukan aplikasi")
        val cmd = "pm disable-user --user 0 $packageName || pm disable $packageName"
        val res = RootUtils.executeCommand(cmd, true)
        return Pair(res.success, if (res.success) "Aplikasi berhasil dibekukan" else res.error)
    }

    fun unfreezeApp(packageName: String, isRoot: Boolean): Pair<Boolean, String> {
        if (!isRoot) return Pair(false, "Memerlukan akses Root untuk mengaktifkan aplikasi")
        val cmd = "pm enable $packageName"
        val res = RootUtils.executeCommand(cmd, true)
        return Pair(res.success, if (res.success) "Aplikasi berhasil diaktifkan" else res.error)
    }

    fun forceStopApp(packageName: String, isRoot: Boolean): Pair<Boolean, String> {
        if (!isRoot) return Pair(false, "Memerlukan akses Root untuk menghentikan paksa")
        val cmd = "am force-stop $packageName"
        val res = RootUtils.executeCommand(cmd, true)
        return Pair(res.success, if (res.success) "Aplikasi berhasil dihentikan paksa" else res.error)
    }

    fun clearAppData(packageName: String, isRoot: Boolean): Pair<Boolean, String> {
        if (!isRoot) return Pair(false, "Memerlukan akses Root untuk membersihkan data aplikasi")
        val cmd = "pm clear $packageName"
        val res = RootUtils.executeCommand(cmd, true)
        return Pair(res.success, if (res.success) "Data & cache aplikasi dibersihkan" else res.error)
    }

    fun uninstallApp(context: Context, packageName: String, isRoot: Boolean): Pair<Boolean, String> {
        if (isRoot) {
            val cmd = "pm uninstall $packageName"
            val res = RootUtils.executeCommand(cmd, true)
            return Pair(res.success, if (res.success) "Aplikasi berhasil dicopot via Root" else res.error)
        } else {
            val intent = Intent(Intent.ACTION_DELETE).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return Pair(true, "Membuka dialog copot pemasangan")
        }
    }

    // Running Processes & Services Detection
    fun getRunningPackages(context: Context, isRoot: Boolean): Set<String> {
        val set = mutableSetOf<String>()
        // 1. Native SDK ActivityManager
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            am?.runningAppProcesses?.forEach { proc ->
                proc.pkgList?.forEach { pkg -> set.add(pkg) }
                if (proc.processName.contains('.')) {
                    set.add(proc.processName.substringBefore(':'))
                }
            }
        } catch (_: Exception) {}

        // 2. Root ps inspection if root is available
        if (isRoot) {
            try {
                val res = RootUtils.executeCommand("ps -A -o NAME 2>/dev/null || ps -o NAME 2>/dev/null", true)
                if (res.success) {
                    res.output.lines().forEach { line ->
                        val clean = line.trim()
                        if (clean.contains('.') && !clean.startsWith("/") && !clean.startsWith("[")) {
                            set.add(clean.substringBefore(':'))
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return set
    }

    /**
     * Kill running services and background processes of a specific application.
     * Uses native ActivityManager.killBackgroundProcesses and Root command when available.
     */
    fun killRunningServices(context: Context, packageName: String, isRoot: Boolean): Pair<Boolean, String> {
        var nativeExecuted = false
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            am?.killBackgroundProcesses(packageName)
            nativeExecuted = true
        } catch (_: Exception) {}

        if (isRoot) {
            val cmd = "am kill $packageName ; pkill -f $packageName"
            val res = RootUtils.executeCommand(cmd, true)
            return Pair(true, "Layanan dan proses latar belakang $packageName berhasil dihentikan (SDK & Root)")
        } else {
            return if (nativeExecuted) {
                Pair(true, "Layanan latar belakang $packageName berhasil dihentikan via Android SDK")
            } else {
                Pair(false, "Gagal menghentikan proses layanan")
            }
        }
    }

    /**
     * Stop a specific Service component by component name (Root operation).
     */
    fun stopSpecificService(packageName: String, serviceName: String, isRoot: Boolean): Pair<Boolean, String> {
        if (!isRoot) {
            return Pair(false, "Menghentikan spesifik service memerlukan akses Root")
        }
        val target = if (serviceName.startsWith(".")) "$packageName$serviceName" else serviceName
        val cmd = "am stopservice $packageName/$target || am stop-service $packageName/$target"
        val res = RootUtils.executeCommand(cmd, true)
        return Pair(res.success, if (res.success) "Layanan $serviceName berhasil dihentikan" else res.error.ifBlank { "Layanan dihentikan" })
    }

    /**
     * Clear application cache ONLY (without wiping user data or databases).
     */
    fun clearAppCache(context: Context, packageName: String, isRoot: Boolean): Pair<Boolean, String> {
        if (isRoot) {
            var freedBytes = 0L
            val duRes = RootUtils.executeCommand("du -sk /data/data/$packageName/cache /data/user/0/$packageName/cache 2>/dev/null", true)
            if (duRes.success) {
                duRes.output.lines().forEach { line ->
                    val sizeKb = line.trim().split("\\s+".toRegex()).firstOrNull()?.toLongOrNull() ?: 0L
                    freedBytes += sizeKb * 1024L
                }
            }

            val cmd = "rm -rf /data/data/$packageName/cache/* /data/data/$packageName/code_cache/* /data/user/0/$packageName/cache/* /data/user/0/$packageName/code_cache/* /sdcard/Android/data/$packageName/cache/* /storage/emulated/0/Android/data/$packageName/cache/*"
            val res = RootUtils.executeCommand(cmd, true)
            val freedText = if (freedBytes > 0) " (${formatBytes(freedBytes)} dibersihkan)" else ""
            return Pair(res.success, if (res.success) "Cache $packageName berhasil dibersihkan$freedText" else res.error)
        } else {
            var freedBytes = 0L
            var anyDeleted = false
            try {
                val extCache = File(Environment.getExternalStorageDirectory(), "Android/data/$packageName/cache")
                if (extCache.exists()) {
                    freedBytes = extCache.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
                    anyDeleted = extCache.deleteRecursively()
                }
            } catch (_: Exception) {}

            if (anyDeleted) {
                return Pair(true, "Cache eksternal $packageName dibersihkan (${formatBytes(freedBytes)} dibebaskan)")
            } else {
                openAppDetailsSettings(context, packageName)
                return Pair(true, "Membuka halaman info aplikasi sistem. Pilih Penyimpanan -> Hapus Cache")
            }
        }
    }

    /**
     * Retrieve estimated cache size for an application.
     */
    fun getAppCacheSize(packageName: String, isRoot: Boolean): Long {
        if (!isRoot) return 0L
        var total = 0L
        val duRes = RootUtils.executeCommand("du -sk /data/data/$packageName/cache /data/user/0/$packageName/cache 2>/dev/null", true)
        if (duRes.success) {
            duRes.output.lines().forEach { line ->
                val sizeKb = line.trim().split("\\s+".toRegex()).firstOrNull()?.toLongOrNull() ?: 0L
                total += sizeKb * 1024L
            }
        }
        return total
    }

    /**
     * Batch kill running background services/processes for a list of packages.
     */
    fun killBatchRunningServices(context: Context, packages: List<String>, isRoot: Boolean): Pair<Int, String> {
        var count = 0
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        for (pkg in packages) {
            try {
                am?.killBackgroundProcesses(pkg)
                if (isRoot) {
                    RootUtils.executeCommand("am kill $pkg ; pkill -f $pkg", true)
                }
                count++
            } catch (_: Exception) {}
        }
        return Pair(count, "Berhasil menghentikan layanan latar belakang dari $count aplikasi")
    }

    /**
     * Batch clear cache for a list of packages.
     */
    fun clearBatchAppCache(context: Context, packages: List<String>, isRoot: Boolean): Pair<Int, String> {
        var count = 0
        for (pkg in packages) {
            val (ok, _) = clearAppCache(context, pkg, isRoot)
            if (ok) count++
        }
        return Pair(count, "Selesai membersihkan cache dari $count aplikasi")
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
            mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
            kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
            else -> "$bytes B"
        }
    }

    fun formatDateTime(timestamp: Long): String {
        if (timestamp <= 0) return "Tidak diketahui"
        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}
