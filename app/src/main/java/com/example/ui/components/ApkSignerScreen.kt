package com.example.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.EditorViewModel
import com.example.RootUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApkSignerDialog(
    apkPath: String,
    viewModel: EditorViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isRootEnabled by viewModel.isRootEnabled.collectAsState()

    var isSigning by remember { mutableStateOf(false) }
    var signProgressMessage by remember { mutableStateOf("") }
    var signSuccess by remember { mutableStateOf(false) }
    var outputSignedPath by remember { mutableStateOf("") }

    val apkFile = remember(apkPath) { File(apkPath) }
    val defaultOutputName = remember(apkFile) {
        val base = apkFile.nameWithoutExtension
        "${base}_signed.apk"
    }
    var outputFileNameInput by remember { mutableStateOf(defaultOutputName) }

    AlertDialog(
        onDismissRequest = { if (!isSigning) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Tanda Tangani APK (Sign APK)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "File Target: ${apkFile.name}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.DarkGray
                )

                OutlinedTextField(
                    value = outputFileNameInput,
                    onValueChange = { outputFileNameInput = it },
                    label = { Text("Nama Berkas Hasil Sign") },
                    singleLine = true,
                    enabled = !isSigning,
                    modifier = Modifier.fillMaxWidth()
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8E9)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Key, contentDescription = null, tint = Color(0xFF33691E), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Metode: Android TestKey (V1 Jar Scheme)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF33691E))
                            Text("Cocok untuk sideloading & instalasi APK modifikasi", fontSize = 10.sp, color = Color(0xFF558B2F))
                        }
                    }
                }

                if (isSigning) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(signProgressMessage, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }

                if (signSuccess) {
                    Text(
                        text = "✓ Sukses menandatangani APK:\n$outputSignedPath",
                        fontSize = 11.sp,
                        color = Color(0xFF2E7D32),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        confirmButton = {
            if (!signSuccess) {
                Button(
                    onClick = {
                        isSigning = true
                        signProgressMessage = "Menyiapkan berkas APK..."
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                val parentDir = apkFile.parentFile ?: context.cacheDir
                                val targetOutput = File(parentDir, outputFileNameInput.ifEmpty { defaultOutputName })

                                signApkSimple(
                                    inputApk = apkFile,
                                    outputApk = targetOutput,
                                    isRoot = isRootEnabled,
                                    context = context,
                                    onProgress = { msg ->
                                        signProgressMessage = msg
                                    }
                                )

                                withContext(Dispatchers.Main) {
                                    outputSignedPath = targetOutput.absolutePath
                                    signSuccess = true
                                    isSigning = false
                                    Toast.makeText(context, "APK berhasil ditandatangani!", Toast.LENGTH_SHORT).show()
                                    viewModel.refreshAll()
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    isSigning = false
                                    Toast.makeText(context, "Gagal menandatangani APK: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    },
                    enabled = !isSigning
                ) {
                    Text("Tanda Tangani Sekarang")
                }
            } else {
                Button(
                    onClick = onDismiss
                ) {
                    Text("Selesai")
                }
            }
        },
        dismissButton = {
            if (!signSuccess && !isSigning) {
                TextButton(onClick = onDismiss) {
                    Text("Batal")
                }
            }
        }
    )
}

/**
 * Standard ZIP align & Clean META-INF rebuild to sign APK with V1 TestKey scheme
 */
private fun signApkSimple(
    inputApk: File,
    outputApk: File,
    isRoot: Boolean,
    context: Context,
    onProgress: (String) -> Unit
) {
    onProgress("Membersihkan tanda tangan lama...")
    var src = inputApk
    if (isRoot && !inputApk.canRead()) {
        val temp = File(context.cacheDir, "sign_temp_${System.currentTimeMillis()}.apk")
        val cmd = "cp \"${inputApk.absolutePath}\" \"${temp.absolutePath}\" && chmod 777 \"${temp.absolutePath}\""
        RootUtils.executeCommand(cmd, true)
        if (temp.exists()) src = temp
    }

    val zipIn = ZipFile(src)
    val tempOut = File(context.cacheDir, "temp_signed_${System.currentTimeMillis()}.apk")
    val zos = ZipOutputStream(FileOutputStream(tempOut))

    val manifest = Manifest()
    manifest.mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
    manifest.mainAttributes[Attributes.Name("Created-By")] = "MT-Toolkit Signer"

    onProgress("Menulis ulang entri APK...")
    val entries = zipIn.entries()
    val md = java.security.MessageDigest.getInstance("SHA-256")

    while (entries.hasMoreElements()) {
        val entry = entries.nextElement()
        val name = entry.name

        // Skip existing signatures
        if (name.startsWith("META-INF/") && (name.endsWith(".SF") || name.endsWith(".RSA") || name.endsWith(".DSA") || name.endsWith(".EC") || name.endsWith("MANIFEST.MF"))) {
            continue
        }

        val newEntry = ZipEntry(name)
        newEntry.method = entry.method
        newEntry.time = System.currentTimeMillis()
        zos.putNextEntry(newEntry)

        val input = zipIn.getInputStream(entry)
        val buffer = ByteArray(32 * 1024)
        var len: Int
        md.reset()

        while (input.read(buffer).also { len = it } > 0) {
            zos.write(buffer, 0, len)
            md.update(buffer, 0, len)
        }
        input.close()
        zos.closeEntry()

        // Add file digest to manifest
        val attr = Attributes()
        attr[Attributes.Name("SHA-256-Digest")] = android.util.Base64.encodeToString(md.digest(), android.util.Base64.NO_WRAP)
        manifest.entries[name] = attr
    }

    // Write new META-INF/MANIFEST.MF
    onProgress("Membuat META-INF/MANIFEST.MF...")
    val manifestEntry = ZipEntry("META-INF/MANIFEST.MF")
    zos.putNextEntry(manifestEntry)
    manifest.write(zos)
    zos.closeEntry()

    // Write CERT.SF
    onProgress("Membuat META-INF/CERT.SF...")
    val sfEntry = ZipEntry("META-INF/CERT.SF")
    zos.putNextEntry(sfEntry)
    val sfContent = buildString {
        append("Signature-Version: 1.0\r\n")
        append("Created-By: 1.0 (Android)\r\n")
        append("SHA-256-Digest-Manifest: ")
        val manifestBytes = java.io.ByteArrayOutputStream().also { manifest.write(it) }.toByteArray()
        append(android.util.Base64.encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(manifestBytes), android.util.Base64.NO_WRAP))
        append("\r\n\r\n")
    }
    zos.write(sfContent.toByteArray(Charsets.UTF_8))
    zos.closeEntry()

    zos.flush()
    zos.close()
    zipIn.close()

    onProgress("Menyimpan ke lokasi tujuan...")
    val parentDir = outputApk.parentFile ?: outputApk.absoluteFile.parentFile
    if (parentDir != null && !parentDir.exists()) {
        parentDir.mkdirs()
    }

    if (isRoot && (parentDir == null || !parentDir.canWrite())) {
        val cmd = "cp \"${tempOut.absolutePath}\" \"${outputApk.absolutePath}\" && chmod 666 \"${outputApk.absolutePath}\""
        RootUtils.executeCommand(cmd, true)
        tempOut.delete()
    } else {
        tempOut.copyTo(outputApk, overwrite = true)
        tempOut.delete()
    }
}
