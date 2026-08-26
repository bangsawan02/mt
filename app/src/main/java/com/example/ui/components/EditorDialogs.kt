package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ImageMetadata
import com.example.CommonUtils

@Composable
fun GoToLineDialog(
    lineCount: Int,
    initialInput: String = "",
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var input by remember { mutableStateOf(initialInput) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FormatListNumbered, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Lompat ke Baris")
            }
        },
        text = {
            Column {
                Text(
                    text = "Masukkan nomor baris target (1 - $lineCount):",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.filter { char -> char.isDigit() } },
                    singleLine = true,
                    placeholder = { Text("misal: 25") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val targetLine = input.toIntOrNull()
                    if (targetLine != null && targetLine in 1..lineCount) {
                        onConfirm(targetLine)
                    }
                }
            ) {
                Text("Lompat")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}

@Composable
fun UnsavedChangesDialog(
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFF9800))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Simpan Perubahan?")
            }
        },
        text = {
            Text("Ada perubahan pada berkas ini yang belum disimpan. Apakah Anda ingin menyimpannya sekarang?")
        },
        confirmButton = {
            Button(
                onClick = onSave
            ) {
                Text("Simpan & Keluar")
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = onDiscard
                ) {
                    Text("Buang Perubahan", color = Color(0xFFE53935))
                }
                TextButton(onClick = onDismiss) {
                    Text("Batal")
                }
            }
        }
    )
}

@Composable
fun PhotoInfoDialog(
    metadata: ImageMetadata,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Detail Berkas Foto", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InfoRow("Nama Berkas", metadata.fileName)
                InfoRow("Lokasi", metadata.filePath)
                InfoRow("Dimensi", "${metadata.width} × ${metadata.height} piksel (${String.format("%.1f", (metadata.width * metadata.height) / 1000000.0)} MP)")
                InfoRow("Ukuran Berkas", CommonUtils.formatFileSize(metadata.fileSize))
                InfoRow("Format MIME", metadata.mimeType)
                InfoRow("Terakhir Diubah", CommonUtils.formatTimestamp(metadata.lastModified))

                if (!metadata.cameraModel.isNullOrBlank()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    InfoRow("Kamera", metadata.cameraModel)
                }
                if (!metadata.dateTimeOriginal.isNullOrBlank()) {
                    InfoRow("Tanggal Pengambilan", metadata.dateTimeOriginal)
                }
                if (!metadata.iso.isNullOrBlank()) {
                    InfoRow("ISO", metadata.iso)
                }
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
fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
    }
}
