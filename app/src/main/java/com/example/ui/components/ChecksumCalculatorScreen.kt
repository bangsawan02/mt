package com.example.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
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
import java.security.MessageDigest
import java.util.zip.CRC32

data class FileChecksums(
    val fileName: String,
    val filePath: String,
    val fileSize: Long,
    val crc32: String,
    val md5: String,
    val sha1: String,
    val sha256: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChecksumViewerDialog(
    filePath: String,
    isRoot: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var checksums by remember { mutableStateOf<FileChecksums?>(null) }
    var isCalculating by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var verifyHashInput by remember { mutableStateOf("") }
    var matchResult by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(filePath) {
        isCalculating = true
        withContext(Dispatchers.IO) {
            try {
                val file = File(filePath)
                var targetFile = file
                if (isRoot && !file.canRead()) {
                    val temp = File(context.cacheDir, "chk_temp_${System.currentTimeMillis()}.bin")
                    val cmd = "cp \"$filePath\" \"${temp.absolutePath}\" && chmod 777 \"${temp.absolutePath}\""
                    val res = RootUtils.executeCommand(cmd, true)
                    if (res.success && temp.exists()) {
                        targetFile = temp
                    }
                }

                val size = targetFile.length()
                val md5Digest = MessageDigest.getInstance("MD5")
                val sha1Digest = MessageDigest.getInstance("SHA-1")
                val sha256Digest = MessageDigest.getInstance("SHA-256")
                val crc = CRC32()

                val buffer = ByteArray(64 * 1024)
                FileInputStream(targetFile).use { fis ->
                    var bytesRead: Int
                    while (fis.read(buffer).also { bytesRead = it } != -1) {
                        md5Digest.update(buffer, 0, bytesRead)
                        sha1Digest.update(buffer, 0, bytesRead)
                        sha256Digest.update(buffer, 0, bytesRead)
                        crc.update(buffer, 0, bytesRead)
                    }
                }

                checksums = FileChecksums(
                    fileName = file.name,
                    filePath = file.absolutePath,
                    fileSize = size,
                    crc32 = String.format("%08X", crc.value),
                    md5 = md5Digest.digest().joinToString("") { "%02x".format(it) },
                    sha1 = sha1Digest.digest().joinToString("") { "%02x".format(it) },
                    sha256 = sha256Digest.digest().joinToString("") { "%02x".format(it) }
                )
            } catch (e: Exception) {
                errorMessage = e.message
            } finally {
                isCalculating = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Fingerprint, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Informasi Checksum & Hash", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
        },
        text = {
            if (isCalculating) {
                Box(modifier = Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Menghitung hash berkas...", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            } else if (checksums != null) {
                val data = checksums!!
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${data.fileName} (${formatByteSize(data.fileSize)})",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.DarkGray
                    )

                    HorizontalDivider(color = Color(0xFFEEEEEE))

                    HashItemCard("CRC32", data.crc32) {
                        clipboardManager.setText(AnnotatedString(data.crc32))
                        Toast.makeText(context, "CRC32 disalin", Toast.LENGTH_SHORT).show()
                    }

                    HashItemCard("MD5", data.md5) {
                        clipboardManager.setText(AnnotatedString(data.md5))
                        Toast.makeText(context, "MD5 disalin", Toast.LENGTH_SHORT).show()
                    }

                    HashItemCard("SHA-1", data.sha1) {
                        clipboardManager.setText(AnnotatedString(data.sha1))
                        Toast.makeText(context, "SHA-1 disalin", Toast.LENGTH_SHORT).show()
                    }

                    HashItemCard("SHA-256", data.sha256) {
                        clipboardManager.setText(AnnotatedString(data.sha256))
                        Toast.makeText(context, "SHA-256 disalin", Toast.LENGTH_SHORT).show()
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Verification field
                    OutlinedTextField(
                        value = verifyHashInput,
                        onValueChange = { input ->
                            verifyHashInput = input.trim()
                            val cleanInput = input.trim().lowercase()
                            matchResult = if (cleanInput.isEmpty()) {
                                null
                            } else {
                                cleanInput == data.crc32.lowercase() ||
                                        cleanInput == data.md5.lowercase() ||
                                        cleanInput == data.sha1.lowercase() ||
                                        cleanInput == data.sha256.lowercase()
                            }
                        },
                        label = { Text("Verifikasi / Cocokkan Hash", fontSize = 11.sp) },
                        placeholder = { Text("Tempel hash untuk verifikasi...", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            matchResult?.let { matched ->
                                Icon(
                                    imageVector = if (matched) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                    contentDescription = null,
                                    tint = if (matched) Color(0xFF2E7D32) else Color(0xFFC62828)
                                )
                            }
                        }
                    )

                    if (matchResult != null) {
                        Text(
                            text = if (matchResult == true) "✓ Checksum COCOK (Valid)" else "✗ Checksum TIDAK COCOK (Invalid)",
                            color = if (matchResult == true) Color(0xFF2E7D32) else Color(0xFFC62828),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
            } else {
                Text("Gagal membaca file: ${errorMessage ?: "Kesalahan tidak diketahui"}", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup")
            }
        }
    )
}

@Composable
private fun HashItemCard(label: String, hash: String, onCopy: () -> Unit) {
    Surface(
        color = Color(0xFFF5F5F5),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onCopy)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                Text(hash, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color.Black)
            }
            Icon(Icons.Default.ContentCopy, contentDescription = "Salin", modifier = Modifier.size(16.dp), tint = Color.Gray)
        }
    }
}

private fun formatByteSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
    val pre = "KMGTPE"[exp - 1]
    return String.format(java.util.Locale.US, "%.1f %sB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
}
