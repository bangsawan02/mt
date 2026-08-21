package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ActiveView
import com.example.EditorViewModel
import com.example.R
import com.example.RootUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ViewMode {
    VIEWER, // Fullscreen pan & zoom inspection
    EDITOR  // Editing controls (transform, adjustments, filters)
}

enum class PresetFilter {
    ORIGINAL, GRAYSCALE, SEPIA, INVERT, WARM, COOL, VIBRANT, DRAMATIC
}

enum class EditorTab {
    ADJUST, TRANSFORM, FILTER
}

data class ImageMetadata(
    val fileName: String,
    val filePath: String,
    val width: Int,
    val height: Int,
    val fileSize: Long,
    val mimeType: String,
    val lastModified: Long,
    val cameraModel: String? = null,
    val dateTimeOriginal: String? = null,
    val iso: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoEditorView(
    filePath: String,
    viewModel: EditorViewModel
) {
    val context = LocalContext.current
    val isRootEnabled by viewModel.isRootEnabled.collectAsState()

    var viewMode by remember { mutableStateOf(ViewMode.VIEWER) }
    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var metadata by remember { mutableStateOf<ImageMetadata?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showSaveAsDialog by remember { mutableStateOf(false) }

    // Color tuning states
    var brightness by remember { mutableStateOf(0f) } // -0.5f to 0.5f
    var contrast by remember { mutableStateOf(1f) }   // 0.5f to 2.0f
    var saturation by remember { mutableStateOf(1f) } // 0f to 2.0f
    var warmth by remember { mutableStateOf(0f) }     // -0.5f to 0.5f
    var activeFilter by remember { mutableStateOf(PresetFilter.ORIGINAL) }
    var isHoldingOriginal by remember { mutableStateOf(false) }

    // Active Editor Tab
    var activeTab by remember { mutableStateOf(EditorTab.ADJUST) }

    // Pan & Zoom state for Viewer mode
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // Load Image & Metadata safely
    LaunchedEffect(filePath, isRootEnabled) {
        isLoading = true
        val result = loadBitmapWithMetadata(filePath, isRootEnabled, context)
        if (result != null) {
            originalBitmap = result.first
            currentBitmap = result.first
            metadata = result.second
        } else {
            Toast.makeText(context, "Gagal memuat gambar!", Toast.LENGTH_LONG).show()
            viewModel.navigateToExplorer()
        }
        isLoading = false
    }

    // Build the combined real-time ColorMatrix
    val combinedColorMatrix = remember(brightness, contrast, saturation, warmth, activeFilter, isHoldingOriginal) {
        if (isHoldingOriginal) {
            ColorMatrix() // Return identity matrix when user holds "Compare"
        } else {
            val matrix = ColorMatrix()

            // 1. Saturation
            matrix.setToSaturation(saturation)

            // 2. Contrast
            val scaleVal = contrast
            val translate = (-0.5f * scaleVal + 0.5f) * 255f
            val contrastMatrix = ColorMatrix(floatArrayOf(
                scaleVal, 0f, 0f, 0f, translate,
                0f, scaleVal, 0f, 0f, translate,
                0f, 0f, scaleVal, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            ))
            matrix.timesAssign(contrastMatrix)

            // 3. Brightness
            val brightnessOffset = brightness * 255f
            val brightnessMatrix = ColorMatrix(floatArrayOf(
                1f, 0f, 0f, 0f, brightnessOffset,
                0f, 1f, 0f, 0f, brightnessOffset,
                0f, 0f, 1f, 0f, brightnessOffset,
                0f, 0f, 0f, 1f, 0f
            ))
            matrix.timesAssign(brightnessMatrix)

            // 4. Warmth / Temperature
            if (warmth != 0f) {
                val warmthMatrix = ColorMatrix(floatArrayOf(
                    1f + (warmth * 0.2f), 0f, 0f, 0f, warmth * 20f,
                    0f, 1f, 0f, 0f, 0f,
                    0f, 0f, 1f - (warmth * 0.2f), 0f, -warmth * 20f,
                    0f, 0f, 0f, 1f, 0f
                ))
                matrix.timesAssign(warmthMatrix)
            }

            // 5. Custom Preset Filter
            if (activeFilter != PresetFilter.ORIGINAL) {
                val presetMatrix = getPresetFilterMatrix(activeFilter)
                matrix.timesAssign(presetMatrix)
            }

            matrix
        }
    }

    val hasModifications = remember(brightness, contrast, saturation, warmth, activeFilter, currentBitmap, originalBitmap) {
        brightness != 0f || contrast != 1f || saturation != 1f || warmth != 0f ||
                activeFilter != PresetFilter.ORIGINAL || (currentBitmap != null && currentBitmap != originalBitmap)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = File(filePath).name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        metadata?.let { meta ->
                            Text(
                                text = "${meta.width} × ${meta.height} • ${formatFileSize(meta.fileSize)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateToExplorer() }) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    // Info button
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(Icons.Default.Info, contentDescription = "Info Foto")
                    }

                    // Mode Switcher Button (Lihat / Sunting)
                    if (viewMode == ViewMode.VIEWER) {
                        FilledTonalButton(
                            onClick = {
                                viewMode = ViewMode.EDITOR
                                scale = 1f
                                offset = Offset.Zero
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sunting", fontSize = 12.sp)
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                viewMode = ViewMode.VIEWER
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Lihat", fontSize = 12.sp)
                        }

                        // Save Button
                        Button(
                            onClick = {
                                isLoading = true
                                currentBitmap?.let { bmp ->
                                    val finalBitmap = applyColorFiltersToBitmap(bmp, combinedColorMatrix)
                                    val success = saveBitmap(finalBitmap, filePath, isRootEnabled, context)
                                    if (success) {
                                        Toast.makeText(context, "Foto berhasil disimpan!", Toast.LENGTH_SHORT).show()
                                        viewModel.refreshAll()
                                        viewModel.navigateToExplorer()
                                    } else {
                                        Toast.makeText(context, "Gagal menyimpan foto!", Toast.LENGTH_LONG).show()
                                    }
                                }
                                isLoading = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = "Simpan", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Simpan", fontSize = 12.sp)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                )
            )
        }
    ) { innerPadding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Memuat foto...", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(Color(0xFF0F0F0F)) // Dark cinematic editing canvas
            ) {
                // 1. IMAGE DISPLAY CANVAS AREA (Adaptive for Viewer pan/zoom & Editor preview)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(0.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    currentBitmap?.let { bmp ->
                        if (viewMode == ViewMode.VIEWER) {
                            // INTERACTIVE PAN & PINCH-TO-ZOOM VIEWER
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onDoubleTap = { tapOffset ->
                                                if (scale > 1.2f) {
                                                    scale = 1f
                                                    offset = Offset.Zero
                                                } else {
                                                    scale = 2.5f
                                                    offset = Offset.Zero
                                                }
                                            }
                                        )
                                    }
                                    .pointerInput(Unit) {
                                        detectTransformGestures { _, pan, zoom, _ ->
                                            scale = (scale * zoom).coerceIn(1f, 8f)
                                            if (scale > 1f) {
                                                val maxPan = (scale - 1f) * 600f
                                                offset = Offset(
                                                    x = (offset.x + pan.x).coerceIn(-maxPan, maxPan),
                                                    y = (offset.y + pan.y).coerceIn(-maxPan, maxPan)
                                                )
                                            } else {
                                                offset = Offset.Zero
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Foto",
                                    colorFilter = ColorFilter.colorMatrix(combinedColorMatrix),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer(
                                            scaleX = scale,
                                            scaleY = scale,
                                            translationX = offset.x,
                                            translationY = offset.y
                                        ),
                                    contentScale = ContentScale.Fit
                                )

                                // Floating HUD (Zoom level & Reset Button)
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 16.dp)
                                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
                                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                                        .padding(horizontal = 14.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "${(scale * 100).toInt()}%",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )

                                    if (scale > 1.05f || offset != Offset.Zero) {
                                        Text(
                                            text = "•",
                                            color = Color.Gray,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            text = "Reset Zoom",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.clickable {
                                                scale = 1f
                                                offset = Offset.Zero
                                            }
                                        )
                                    }

                                    Text(
                                        text = "•",
                                        color = Color.Gray,
                                        fontSize = 12.sp
                                    )

                                    Icon(
                                        imageVector = Icons.Default.RotateRight,
                                        contentDescription = "Putar Cepat",
                                        tint = Color.White,
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clickable {
                                                currentBitmap?.let { current ->
                                                    currentBitmap = rotateBitmap(current, 90f)
                                                }
                                            }
                                    )
                                }
                            }
                        } else {
                            // EDITOR VIEW
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Foto Suntingan",
                                    colorFilter = ColorFilter.colorMatrix(combinedColorMatrix),
                                    modifier = Modifier
                                        .fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )

                                // Holding to compare original badge
                                if (hasModifications) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(12.dp)
                                    ) {
                                        Button(
                                            onClick = {},
                                            modifier = Modifier.pointerInput(Unit) {
                                                detectTapGestures(
                                                    onPress = {
                                                        isHoldingOriginal = true
                                                        tryAwaitRelease()
                                                        isHoldingOriginal = false
                                                    }
                                                )
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isHoldingOriginal) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.6f),
                                                contentColor = Color.White
                                            ),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            shape = RoundedCornerShape(16.dp)
                                        ) {
                                            Icon(Icons.Default.Compare, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(if (isHoldingOriginal) "Foto Asli" else "Tekan: Bandingkan", fontSize = 10.sp)
                                        }
                                    }
                                }
                            }
                        }
                    } ?: run {
                        Text("Tidak ada gambar terunggah", color = Color.Gray)
                    }
                }

                // 2. EDITING CONTROLS AREA (Visible only in EDITOR mode)
                if (viewMode == ViewMode.EDITOR) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(bottom = 12.dp)
                    ) {
                        // TAB NAVIGATION BAR (ADJUST, TRANSFORM, FILTER)
                        TabRow(
                            selectedTabIndex = activeTab.ordinal,
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.primary
                        ) {
                            Tab(
                                selected = activeTab == EditorTab.ADJUST,
                                onClick = { activeTab = EditorTab.ADJUST },
                                text = { Text("Warna", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                                icon = { Icon(Icons.Default.Tune, contentDescription = "Warna", modifier = Modifier.size(18.dp)) }
                            )
                            Tab(
                                selected = activeTab == EditorTab.TRANSFORM,
                                onClick = { activeTab = EditorTab.TRANSFORM },
                                text = { Text("Transform", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                                icon = { Icon(Icons.Default.CropRotate, contentDescription = "Transformasi", modifier = Modifier.size(18.dp)) }
                            )
                            Tab(
                                selected = activeTab == EditorTab.FILTER,
                                onClick = { activeTab = EditorTab.FILTER },
                                text = { Text("Filter", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                                icon = { Icon(Icons.Default.AutoFixHigh, contentDescription = "Filter", modifier = Modifier.size(18.dp)) }
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // ACTIVE TAB CONTENT (Scrollable to prevent overflow)
                        val activeTabScrollState = rememberScrollState()
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp)
                                .padding(horizontal = 16.dp)
                                .verticalScroll(activeTabScrollState)
                        ) {
                            when (activeTab) {
                                EditorTab.ADJUST -> {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        // Brightness Slider
                                        Column {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("Kecerahan", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                                Text(String.format("%.0f%%", brightness * 100), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                            }
                                            Slider(
                                                value = brightness,
                                                onValueChange = { brightness = it },
                                                valueRange = -0.5f..0.5f,
                                                modifier = Modifier.height(28.dp)
                                            )
                                        }

                                        // Contrast Slider
                                        Column {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("Kontras", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                                Text(String.format("%.1fx", contrast), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                            }
                                            Slider(
                                                value = contrast,
                                                onValueChange = { contrast = it },
                                                valueRange = 0.5f..2.0f,
                                                modifier = Modifier.height(28.dp)
                                            )
                                        }

                                        // Saturation Slider
                                        Column {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("Saturasi", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                                Text(String.format("%.1fx", saturation), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                            }
                                            Slider(
                                                value = saturation,
                                                onValueChange = { saturation = it },
                                                valueRange = 0.0f..2.0f,
                                                modifier = Modifier.height(28.dp)
                                            )
                                        }

                                        // Warmth Slider
                                        Column {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("Temperatur / Suhu", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                                Text(String.format("%.0f%%", warmth * 100), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                            }
                                            Slider(
                                                value = warmth,
                                                onValueChange = { warmth = it },
                                                valueRange = -0.5f..0.5f,
                                                modifier = Modifier.height(28.dp)
                                            )
                                        }

                                        // Reset Adjustment Button
                                        OutlinedButton(
                                            onClick = {
                                                brightness = 0f
                                                contrast = 1f
                                                saturation = 1f
                                                warmth = 0f
                                            },
                                            modifier = Modifier.fillMaxWidth().height(36.dp),
                                            contentPadding = PaddingValues(4.dp)
                                        ) {
                                            Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Reset Penyesuaian Warna", fontSize = 11.sp)
                                        }
                                    }
                                }

                                EditorTab.TRANSFORM -> {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        // Rotation & Flip Buttons
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            FilledTonalButton(
                                                onClick = {
                                                    currentBitmap?.let { bmp ->
                                                        currentBitmap = rotateBitmap(bmp, -90f)
                                                    }
                                                },
                                                modifier = Modifier.weight(1f),
                                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                                            ) {
                                                Icon(Icons.Default.RotateLeft, contentDescription = "Kiri", modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Kiri -90°", fontSize = 10.sp)
                                            }

                                            FilledTonalButton(
                                                onClick = {
                                                    currentBitmap?.let { bmp ->
                                                        currentBitmap = rotateBitmap(bmp, 90f)
                                                    }
                                                },
                                                modifier = Modifier.weight(1f),
                                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                                            ) {
                                                Icon(Icons.Default.RotateRight, contentDescription = "Kanan", modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Kanan 90°", fontSize = 10.sp)
                                            }

                                            FilledTonalButton(
                                                onClick = {
                                                    currentBitmap?.let { bmp ->
                                                        currentBitmap = flipBitmap(bmp, horizontal = true, vertical = false)
                                                    }
                                                },
                                                modifier = Modifier.weight(1f),
                                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                                            ) {
                                                Icon(Icons.Default.Flip, contentDescription = "Flip H", modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Flip H", fontSize = 10.sp)
                                            }

                                            FilledTonalButton(
                                                onClick = {
                                                    currentBitmap?.let { bmp ->
                                                        currentBitmap = flipBitmap(bmp, horizontal = false, vertical = true)
                                                    }
                                                },
                                                modifier = Modifier.weight(1f),
                                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                                            ) {
                                                Icon(Icons.Default.SwapVert, contentDescription = "Flip V", modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Flip V", fontSize = 10.sp)
                                            }
                                        }

                                        // Aspect ratio crops
                                        Text("Potong Rasio Aspek (Tengah)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            listOf(
                                                "1:1 Persegi" to 1f,
                                                "16:9 Banner" to 16f / 9f,
                                                "9:16 Story" to 9f / 16f,
                                                "4:3 Standar" to 4f / 3f,
                                                "3:2 Foto" to 3f / 2f
                                            ).forEach { (label, ratio) ->
                                                OutlinedButton(
                                                    onClick = {
                                                        currentBitmap?.let { bmp ->
                                                            currentBitmap = cropToRatio(bmp, ratio)
                                                        }
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                                ) {
                                                    Text(label, fontSize = 11.sp)
                                                }
                                            }

                                            Button(
                                                onClick = {
                                                    currentBitmap = originalBitmap
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                                ),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Reset Ukuran", fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }

                                EditorTab.FILTER -> {
                                    val filterScrollState = rememberScrollState()
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(filterScrollState),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        PresetFilter.values().forEach { filter ->
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier = Modifier
                                                    .clickable { activeFilter = filter }
                                                    .padding(4.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(56.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .border(
                                                            width = 2.dp,
                                                            color = if (activeFilter == filter) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                            shape = RoundedCornerShape(8.dp)
                                                        )
                                                        .background(Color.Black),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    currentBitmap?.let { bmp ->
                                                        Image(
                                                            bitmap = bmp.asImageBitmap(),
                                                            contentDescription = filter.name,
                                                            modifier = Modifier.fillMaxSize(),
                                                            contentScale = ContentScale.Crop,
                                                            colorFilter = ColorFilter.colorMatrix(getPresetFilterMatrix(filter))
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = when (filter) {
                                                        PresetFilter.ORIGINAL -> "Asli"
                                                        PresetFilter.GRAYSCALE -> "B&W"
                                                        PresetFilter.SEPIA -> "Sepia"
                                                        PresetFilter.INVERT -> "Invert"
                                                        PresetFilter.WARM -> "Hangat"
                                                        PresetFilter.COOL -> "Sejuk"
                                                        PresetFilter.VIBRANT -> "Vibrant"
                                                        PresetFilter.DRAMATIC -> "Dramatis"
                                                    },
                                                    fontSize = 10.sp,
                                                    fontWeight = if (activeFilter == filter) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (activeFilter == filter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Image Metadata / Info Dialog
    if (showInfoDialog && metadata != null) {
        val meta = metadata!!
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
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
                    InfoRow("Nama Berkas", meta.fileName)
                    InfoRow("Lokasi", meta.filePath)
                    InfoRow("Dimensi", "${meta.width} × ${meta.height} piksel (${String.format("%.1f", (meta.width * meta.height) / 1000000.0)} MP)")
                    InfoRow("Ukuran Berkas", formatFileSize(meta.fileSize))
                    InfoRow("Format MIME", meta.mimeType)
                    InfoRow("Terakhir Diubah", formatTimestamp(meta.lastModified))

                    if (!meta.cameraModel.isNullOrBlank()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        InfoRow("Kamera", meta.cameraModel)
                    }
                    if (!meta.dateTimeOriginal.isNullOrBlank()) {
                        InfoRow("Tanggal Pengambilan", meta.dateTimeOriginal)
                    }
                    if (!meta.iso.isNullOrBlank()) {
                        InfoRow("ISO", meta.iso)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text("Tutup")
                }
            }
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
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

// -------------------------------------------------------------
// BITMAP PROCESSING & METADATA UTILITIES (MEMORY-SAFE & ROBUST)
// -------------------------------------------------------------

fun loadBitmapWithMetadata(filePath: String, isRoot: Boolean, context: Context): Pair<Bitmap, ImageMetadata>? {
    val file = File(filePath)

    try {
        var targetFile = file

        // If root file and not directly readable, copy to app cache
        if (isRoot && !file.canRead()) {
            val tempFile = File(context.cacheDir, "temp_img_${System.currentTimeMillis()}.${file.extension.ifEmpty { "png" }}")
            val cmd = "cp \"$filePath\" \"${tempFile.absolutePath}\" && chmod 777 \"${tempFile.absolutePath}\""
            val res = RootUtils.executeCommand(cmd, true)
            if (res.success && tempFile.exists()) {
                targetFile = tempFile
            }
        }

        // 1. Decode Bounds first to prevent OutOfMemory on large camera images
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(targetFile.absolutePath, boundsOptions)

        val origWidth = boundsOptions.outWidth
        val origHeight = boundsOptions.outHeight
        val outMimeType = boundsOptions.outMimeType ?: "image/${file.extension.lowercase()}"

        if (origWidth <= 0 || origHeight <= 0) {
            return null
        }

        // Calculate sample size if resolution exceeds 4096px to avoid OOM
        var sampleSize = 1
        val maxDimension = 4096
        var w = origWidth
        var h = origHeight
        while (w > maxDimension || h > maxDimension) {
            sampleSize *= 2
            w /= 2
            h /= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        var decodedBitmap = BitmapFactory.decodeFile(targetFile.absolutePath, decodeOptions) ?: return null

        // 2. Read EXIF Orientation and rotate if necessary (e.g. camera photos)
        try {
            val exif = ExifInterface(targetFile.absolutePath)
            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            val rotationDegrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }

            if (rotationDegrees != 0f) {
                decodedBitmap = rotateBitmap(decodedBitmap, rotationDegrees)
            }

            val cameraModel = exif.getAttribute(ExifInterface.TAG_MODEL)
            val dateTimeOriginal = exif.getAttribute(ExifInterface.TAG_DATETIME)
            val iso = exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS)

            val meta = ImageMetadata(
                fileName = file.name,
                filePath = file.absolutePath,
                width = decodedBitmap.width,
                height = decodedBitmap.height,
                fileSize = if (file.exists()) file.length() else targetFile.length(),
                mimeType = outMimeType,
                lastModified = file.lastModified(),
                cameraModel = cameraModel,
                dateTimeOriginal = dateTimeOriginal,
                iso = iso
            )

            // Cleanup temp file if created
            if (targetFile != file && targetFile.exists()) {
                targetFile.delete()
            }

            return Pair(decodedBitmap, meta)
        } catch (e: Throwable) {
            // If Exif parsing fails, still return bitmap and basic metadata
            val meta = ImageMetadata(
                fileName = file.name,
                filePath = file.absolutePath,
                width = decodedBitmap.width,
                height = decodedBitmap.height,
                fileSize = file.length(),
                mimeType = outMimeType,
                lastModified = file.lastModified()
            )

            if (targetFile != file && targetFile.exists()) {
                targetFile.delete()
            }

            return Pair(decodedBitmap, meta)
        }
    } catch (t: Throwable) {
        t.printStackTrace()
        return null
    }
}

fun saveBitmap(bitmap: Bitmap, filePath: String, isRoot: Boolean, context: Context): Boolean {
    val ext = filePath.substringAfterLast(".", "").lowercase()
    val format = when (ext) {
        "jpg", "jpeg" -> Bitmap.CompressFormat.JPEG
        "webp" -> Bitmap.CompressFormat.WEBP_LOSSY
        else -> Bitmap.CompressFormat.PNG
    }

    try {
        val tempFile = File(context.cacheDir, "saved_${System.currentTimeMillis()}.$ext")
        if (tempFile.exists()) tempFile.delete()

        tempFile.outputStream().use { out ->
            bitmap.compress(format, 95, out)
        }

        if (isRoot) {
            val cmd = "cp \"${tempFile.absolutePath}\" \"$filePath\" && chmod 666 \"$filePath\""
            val res = RootUtils.executeCommand(cmd, true)
            tempFile.delete()
            return res.success
        } else {
            val destFile = File(filePath)
            if (destFile.exists()) destFile.delete()
            val renamed = tempFile.renameTo(destFile)
            if (!renamed) {
                tempFile.inputStream().use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
            tempFile.delete()
            return true
        }
    } catch (t: Throwable) {
        t.printStackTrace()
        return false
    }
}

fun rotateBitmap(source: Bitmap, degrees: Float): Bitmap {
    if (degrees == 0f) return source
    val matrix = Matrix()
    matrix.postRotate(degrees)
    return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
}

fun flipBitmap(source: Bitmap, horizontal: Boolean, vertical: Boolean): Bitmap {
    val matrix = Matrix()
    matrix.postScale(
        if (horizontal) -1f else 1f,
        if (vertical) -1f else 1f
    )
    return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
}

fun cropToRatio(source: Bitmap, aspectRatio: Float): Bitmap {
    val currentRatio = source.width.toFloat() / source.height.toFloat()
    var newWidth = source.width
    var newHeight = source.height
    var x = 0
    var y = 0

    if (currentRatio > aspectRatio) {
        newWidth = (source.height * aspectRatio).toInt()
        x = (source.width - newWidth) / 2
    } else {
        newHeight = (source.width / aspectRatio).toInt()
        y = (source.height - newHeight) / 2
    }

    if (newWidth <= 0 || newHeight <= 0) return source
    return Bitmap.createBitmap(source, x, y, newWidth, newHeight)
}

fun applyColorFiltersToBitmap(source: Bitmap, colorMatrix: ColorMatrix): Bitmap {
    val result = Bitmap.createBitmap(source.width, source.height, source.config ?: Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)
    val paint = Paint().apply {
        colorFilter = ColorMatrixColorFilter(colorMatrix.values)
    }
    canvas.drawBitmap(source, 0f, 0f, paint)
    return result
}

fun getPresetFilterMatrix(filter: PresetFilter): ColorMatrix {
    val matrix = ColorMatrix()
    when (filter) {
        PresetFilter.GRAYSCALE -> matrix.setToSaturation(0f)
        PresetFilter.SEPIA -> matrix.timesAssign(ColorMatrix(floatArrayOf(
            0.393f, 0.769f, 0.189f, 0f, 0f,
            0.349f, 0.686f, 0.168f, 0f, 0f,
            0.272f, 0.534f, 0.131f, 0f, 0f,
            0f,     0f,     0f,     1f, 0f
        )))
        PresetFilter.INVERT -> matrix.timesAssign(ColorMatrix(floatArrayOf(
            -1f,  0f,  0f, 0f, 255f,
             0f, -1f,  0f, 0f, 255f,
             0f,  0f, -1f, 0f, 255f,
             0f,  0f,  0f, 1f, 0f
        )))
        PresetFilter.WARM -> matrix.timesAssign(ColorMatrix(floatArrayOf(
            1.15f, 0f, 0f, 0f, 15f,
            0f, 1.05f, 0f, 0f, 5f,
            0f, 0f, 0.85f, 0f, -15f,
            0f, 0f, 0f, 1f, 0f
        )))
        PresetFilter.COOL -> matrix.timesAssign(ColorMatrix(floatArrayOf(
            0.85f, 0f, 0f, 0f, -15f,
            0f, 1.05f, 0f, 0f, 5f,
            0f, 0f, 1.20f, 0f, 20f,
            0f, 0f, 0f, 1f, 0f
        )))
        PresetFilter.VIBRANT -> {
            matrix.setToSaturation(1.4f)
            val contrastMatrix = ColorMatrix(floatArrayOf(
                1.1f, 0f, 0f, 0f, -5f,
                0f, 1.1f, 0f, 0f, -5f,
                0f, 0f, 1.1f, 0f, -5f,
                0f, 0f, 0f, 1f, 0f
            ))
            matrix.timesAssign(contrastMatrix)
        }
        PresetFilter.DRAMATIC -> {
            matrix.setToSaturation(0.7f)
            val contrastMatrix = ColorMatrix(floatArrayOf(
                1.3f, 0f, 0f, 0f, -20f,
                0f, 1.3f, 0f, 0f, -20f,
                0f, 0f, 1.3f, 0f, -20f,
                0f, 0f, 0f, 1f, 0f
            ))
            matrix.timesAssign(contrastMatrix)
        }
        else -> {}
    }
    return matrix
}

private fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    return String.format(Locale.US, "%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

private fun formatTimestamp(timestamp: Long): String {
    if (timestamp <= 0) return "-"
    val sdf = SimpleDateFormat("dd MMM yyyy HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
