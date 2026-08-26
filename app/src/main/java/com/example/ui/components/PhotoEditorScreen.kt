package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect as AndroidRect
import android.media.ExifInterface
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ActiveView
import com.example.EditorViewModel
import com.example.ImageMetadata
import com.example.CommonUtils
import com.example.R
import com.example.RootUtils
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class ViewMode {
    VIEWER, // Fullscreen pan & zoom inspection
    EDITOR  // Canvas editing controls (crop, resize, draw, color, filter)
}

enum class PresetFilter {
    ORIGINAL, GRAYSCALE, SEPIA, INVERT, WARM, COOL, VIBRANT, DRAMATIC
}

enum class EditorTab {
    CROP, RESIZE, DRAW, ADJUST, FILTER, TRANSFORM
}

enum class CropAspectRatio(val label: String, val ratio: Float?) {
    FREE("Bebas", null),
    SQUARE("1:1", 1f),
    FOUR_THREE("4:3", 4f / 3f),
    SIXTEEN_NINE("16:9", 16f / 9f),
    NINE_SIXTEEN("9:16", 9f / 16f),
    THREE_TWO("3:2", 3f / 2f)
}

private enum class DragHandle {
    NONE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT,
    TOP, BOTTOM, LEFT, RIGHT, BODY
}

data class DrawStroke(
    val path: List<Offset>,
    val color: Color,
    val strokeWidth: Float
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

    // Color tuning states
    var brightness by remember { mutableStateOf(0f) } // -0.5f to 0.5f
    var contrast by remember { mutableStateOf(1f) }   // 0.5f to 2.0f
    var saturation by remember { mutableStateOf(1f) } // 0f to 2.0f
    var warmth by remember { mutableStateOf(0f) }     // -0.5f to 0.5f
    var activeFilter by remember { mutableStateOf(PresetFilter.ORIGINAL) }
    var isHoldingOriginal by remember { mutableStateOf(false) }

    // Active Editor Tab
    var activeTab by remember { mutableStateOf(EditorTab.CROP) }

    // Crop Canvas States
    var selectedCropRatio by remember { mutableStateOf(CropAspectRatio.FREE) }
    var triggerCropApply by remember { mutableStateOf(false) }
    var triggerCropReset by remember { mutableStateOf(false) }

    // Drawing Canvas States
    var drawingStrokes by remember { mutableStateOf(listOf<DrawStroke>()) }
    var currentDrawStroke by remember { mutableStateOf<DrawStroke?>(null) }
    var brushColor by remember { mutableStateOf(Color.Red) }
    var brushWidth by remember { mutableStateOf(8f) }

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

    val hasModifications = remember(brightness, contrast, saturation, warmth, activeFilter, currentBitmap, originalBitmap, drawingStrokes) {
        brightness != 0f || contrast != 1f || saturation != 1f || warmth != 0f ||
                activeFilter != PresetFilter.ORIGINAL || (currentBitmap != null && currentBitmap != originalBitmap) ||
                drawingStrokes.isNotEmpty()
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
                        val activeBmp = currentBitmap
                        if (activeBmp != null) {
                            Text(
                                text = "${activeBmp.width} × ${activeBmp.height} px • ${CommonUtils.formatFileSize(metadata?.fileSize ?: 0L)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateToExplorer() }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
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
                                    // Apply drawing strokes if any
                                    val withDrawings = if (drawingStrokes.isNotEmpty()) {
                                        applyStrokesToBitmap(bmp, drawingStrokes)
                                    } else bmp

                                    val finalBitmap = applyColorFiltersToBitmap(withDrawings, combinedColorMatrix)
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
                // 1. IMAGE DISPLAY & INTERACTIVE CANVAS AREA
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
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
                                            onDoubleTap = {
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
                                        Text(text = "•", color = Color.Gray, fontSize = 12.sp)
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

                                    Text(text = "•", color = Color.Gray, fontSize = 12.sp)

                                    Icon(
                                        imageVector = Icons.Default.RotateRight,
                                        contentDescription = "Putar Cepat",
                                        tint = Color.White,
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clickable {
                                                currentBitmap = rotateBitmap(bmp, 90f)
                                            }
                                    )
                                }
                            }
                        } else {
                            // INTERACTIVE EDITING CANVAS (Adapts based on active tab)
                            when (activeTab) {
                                EditorTab.CROP -> {
                                    // Interactive Cropping Canvas with draggable box & handles
                                    CropEditingCanvas(
                                        bitmap = bmp,
                                        colorMatrix = combinedColorMatrix,
                                        aspectRatio = selectedCropRatio.ratio,
                                        triggerApply = triggerCropApply,
                                        triggerReset = triggerCropReset,
                                        onCropApplied = { croppedBmp ->
                                            currentBitmap = croppedBmp
                                            triggerCropApply = false
                                        },
                                        onResetHandled = {
                                            triggerCropReset = false
                                        }
                                    )
                                }
                                EditorTab.DRAW -> {
                                    // Interactive Annotation / Doodle Canvas
                                    DoodleCanvas(
                                        bitmap = bmp,
                                        colorMatrix = combinedColorMatrix,
                                        strokes = drawingStrokes,
                                        currentStroke = currentDrawStroke,
                                        brushColor = brushColor,
                                        brushWidth = brushWidth,
                                        onStrokeAdded = { stroke ->
                                            drawingStrokes = drawingStrokes + stroke
                                            currentDrawStroke = null
                                        },
                                        onStrokeUpdated = { stroke ->
                                            currentDrawStroke = stroke
                                        }
                                    )
                                }
                                else -> {
                                    // Standard Editor Preview Canvas
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
                                            modifier = Modifier.fillMaxSize(),
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
                            .padding(bottom = 8.dp)
                    ) {
                        // SCROLLABLE TAB NAVIGATION BAR
                        ScrollableTabRow(
                            selectedTabIndex = activeTab.ordinal,
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.primary,
                            edgePadding = 8.dp
                        ) {
                            Tab(
                                selected = activeTab == EditorTab.CROP,
                                onClick = { activeTab = EditorTab.CROP },
                                text = { Text("Potong", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                                icon = { Icon(Icons.Default.Crop, contentDescription = "Potong", modifier = Modifier.size(18.dp)) }
                            )
                            Tab(
                                selected = activeTab == EditorTab.RESIZE,
                                onClick = { activeTab = EditorTab.RESIZE },
                                text = { Text("Ukuran", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                                icon = { Icon(Icons.Default.AspectRatio, contentDescription = "Ubah Ukuran", modifier = Modifier.size(18.dp)) }
                            )
                            Tab(
                                selected = activeTab == EditorTab.DRAW,
                                onClick = { activeTab = EditorTab.DRAW },
                                text = { Text("Coretan", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                                icon = { Icon(Icons.Default.Draw, contentDescription = "Coretan", modifier = Modifier.size(18.dp)) }
                            )
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

                        Spacer(modifier = Modifier.height(8.dp))

                        // ACTIVE TAB CONTENT (Scrollable container to prevent overflow)
                        val activeTabScrollState = rememberScrollState()
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp)
                                .padding(horizontal = 16.dp)
                                .verticalScroll(activeTabScrollState)
                        ) {
                            when (activeTab) {
                                EditorTab.CROP -> {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = "Rasio Aspek Pemotongan (Geser kotak pada canvas):",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            CropAspectRatio.values().forEach { aspect ->
                                                FilterChip(
                                                    selected = selectedCropRatio == aspect,
                                                    onClick = { selectedCropRatio = aspect },
                                                    label = { Text(aspect.label, fontSize = 11.sp) },
                                                    leadingIcon = if (selectedCropRatio == aspect) {
                                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                                    } else null
                                                )
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = { triggerCropApply = true },
                                                modifier = Modifier.weight(1f),
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                                contentPadding = PaddingValues(vertical = 8.dp)
                                            ) {
                                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Terapkan Potong", fontSize = 12.sp)
                                            }

                                            OutlinedButton(
                                                onClick = { triggerCropReset = true },
                                                modifier = Modifier.weight(1f),
                                                contentPadding = PaddingValues(vertical = 8.dp)
                                            ) {
                                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Reset Kotak", fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }

                                EditorTab.RESIZE -> {
                                    currentBitmap?.let { bmp ->
                                        ResizeToolControls(
                                            bitmap = bmp,
                                            onApplyResize = { newWidth, newHeight ->
                                                val resized = resizeBitmap(bmp, newWidth, newHeight)
                                                currentBitmap = resized
                                                Toast.makeText(context, "Ukuran diubah: ${newWidth}x${newHeight} px", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    }
                                }

                                EditorTab.DRAW -> {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("Pilih Warna Kuas:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            listOf(
                                                Color.Red,
                                                Color.Yellow,
                                                Color.Green,
                                                Color.Cyan,
                                                Color.Blue,
                                                Color.Magenta,
                                                Color.White,
                                                Color.Black
                                            ).forEach { col ->
                                                Box(
                                                    modifier = Modifier
                                                        .size(28.dp)
                                                        .clip(CircleShape)
                                                        .background(col)
                                                        .border(
                                                            width = if (brushColor == col) 3.dp else 1.dp,
                                                            color = if (brushColor == col) MaterialTheme.colorScheme.primary else Color.Gray,
                                                            shape = CircleShape
                                                        )
                                                        .clickable { brushColor = col }
                                                )
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Ketebalan Kuas: ${brushWidth.toInt()} px", style = MaterialTheme.typography.bodySmall)
                                            Slider(
                                                value = brushWidth,
                                                onValueChange = { brushWidth = it },
                                                valueRange = 2f..30f,
                                                modifier = Modifier.weight(1f).padding(start = 12.dp)
                                            )
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = {
                                                    if (drawingStrokes.isNotEmpty()) {
                                                        drawingStrokes = drawingStrokes.dropLast(1)
                                                    }
                                                },
                                                enabled = drawingStrokes.isNotEmpty(),
                                                modifier = Modifier.weight(1f),
                                                contentPadding = PaddingValues(4.dp)
                                            ) {
                                                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Urungkan", fontSize = 11.sp)
                                            }

                                            OutlinedButton(
                                                onClick = { drawingStrokes = emptyList() },
                                                enabled = drawingStrokes.isNotEmpty(),
                                                modifier = Modifier.weight(1f),
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                                contentPadding = PaddingValues(4.dp)
                                            ) {
                                                Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Hapus Semua", fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }

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

                                        // Reset to Original Button
                                        Button(
                                            onClick = {
                                                currentBitmap = originalBitmap
                                                drawingStrokes = emptyList()
                                                brightness = 0f
                                                contrast = 1f
                                                saturation = 1f
                                                warmth = 0f
                                                activeFilter = PresetFilter.ORIGINAL
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                                            ),
                                            modifier = Modifier.fillMaxWidth(),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Kembalikan ke Foto Asli", fontSize = 11.sp)
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

    // Photo Info Dialog
    if (showInfoDialog && metadata != null) {
        PhotoInfoDialog(
            metadata = metadata!!,
            onDismiss = { showInfoDialog = false }
        )
    }
}

// -------------------------------------------------------------------
// INTERACTIVE CROPPING CANVAS USING JETPACK COMPOSE & GRAPHICS CANVAS
// -------------------------------------------------------------------

@Composable
fun CropEditingCanvas(
    bitmap: Bitmap,
    colorMatrix: ColorMatrix,
    aspectRatio: Float?,
    triggerApply: Boolean,
    triggerReset: Boolean,
    onCropApplied: (Bitmap) -> Unit,
    onResetHandled: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        val containerWidth = constraints.maxWidth.toFloat()
        val containerHeight = constraints.maxHeight.toFloat()

        val bmpWidth = bitmap.width.toFloat()
        val bmpHeight = bitmap.height.toFloat()

        // Calculate aspect fit destination rectangle for the image
        val scaleFit = min(containerWidth / bmpWidth, containerHeight / bmpHeight)
        val imageDisplayWidth = bmpWidth * scaleFit
        val imageDisplayHeight = bmpHeight * scaleFit

        val imageLeft = (containerWidth - imageDisplayWidth) / 2f
        val imageTop = (containerHeight - imageDisplayHeight) / 2f
        val imageBounds = remember(imageLeft, imageTop, imageDisplayWidth, imageDisplayHeight) {
            Rect(imageLeft, imageTop, imageLeft + imageDisplayWidth, imageTop + imageDisplayHeight)
        }

        // Crop rect state in local canvas coordinates
        var cropRect by remember(imageBounds, aspectRatio) {
            val initial = if (aspectRatio != null) {
                // Fit initial crop with specified aspect ratio
                val initialW = imageBounds.width * 0.85f
                val initialH = initialW / aspectRatio
                val fittedW = if (initialH > imageBounds.height * 0.85f) {
                    (imageBounds.height * 0.85f) * aspectRatio
                } else initialW
                val fittedH = fittedW / aspectRatio
                val left = imageBounds.left + (imageBounds.width - fittedW) / 2f
                val top = imageBounds.top + (imageBounds.height - fittedH) / 2f
                Rect(left, top, left + fittedW, top + fittedH)
            } else {
                // Freeform: 90% of image bounds
                val padX = imageBounds.width * 0.05f
                val padY = imageBounds.height * 0.05f
                Rect(imageBounds.left + padX, imageBounds.top + padY, imageBounds.right - padX, imageBounds.bottom - padY)
            }
            mutableStateOf(initial)
        }

        // Handle Reset
        LaunchedEffect(triggerReset) {
            if (triggerReset) {
                val padX = imageBounds.width * 0.02f
                val padY = imageBounds.height * 0.02f
                cropRect = Rect(imageBounds.left + padX, imageBounds.top + padY, imageBounds.right - padX, imageBounds.bottom - padY)
                onResetHandled()
            }
        }

        // Handle Apply Crop
        LaunchedEffect(triggerApply) {
            if (triggerApply) {
                val normLeft = ((cropRect.left - imageBounds.left) / imageBounds.width).coerceIn(0f, 1f)
                val normTop = ((cropRect.top - imageBounds.top) / imageBounds.height).coerceIn(0f, 1f)
                val normWidth = (cropRect.width / imageBounds.width).coerceIn(0.01f, 1f)
                val normHeight = (cropRect.height / imageBounds.height).coerceIn(0.01f, 1f)

                val cropX = (normLeft * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
                val cropY = (normTop * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
                val cropW = (normWidth * bitmap.width).toInt().coerceIn(1, bitmap.width - cropX)
                val cropH = (normHeight * bitmap.height).toInt().coerceIn(1, bitmap.height - cropY)

                val cropped = Bitmap.createBitmap(bitmap, cropX, cropY, cropW, cropH)
                onCropApplied(cropped)
            }
        }

        var activeDragHandle by remember { mutableStateOf(DragHandle.NONE) }
        val touchTolerance = 48f

        // Display image and interactive crop overlay on Canvas
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                colorFilter = ColorFilter.colorMatrix(colorMatrix),
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = 0f
                        translationY = 0f
                    },
                contentScale = ContentScale.Fit
            )

            // Compose Canvas overlay for crop box, rule of thirds, darkened mask, and grab handles
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(imageBounds, aspectRatio) {
                        detectDragGestures(
                            onDragStart = { startPos ->
                                activeDragHandle = getTouchedHandle(startPos, cropRect, touchTolerance)
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                cropRect = updateCropRect(
                                    current = cropRect,
                                    bounds = imageBounds,
                                    handle = activeDragHandle,
                                    drag = dragAmount,
                                    aspectRatio = aspectRatio
                                )
                            },
                            onDragEnd = {
                                activeDragHandle = DragHandle.NONE
                            },
                            onDragCancel = {
                                activeDragHandle = DragHandle.NONE
                            }
                        )
                    }
            ) {
                // 1. Draw darkened surrounding area (outside crop rectangle)
                val darkOverlay = Color(0x99000000)
                // Top
                drawRect(darkOverlay, topLeft = Offset(imageBounds.left, imageBounds.top), size = Size(imageBounds.width, max(0f, cropRect.top - imageBounds.top)))
                // Bottom
                drawRect(darkOverlay, topLeft = Offset(imageBounds.left, cropRect.bottom), size = Size(imageBounds.width, max(0f, imageBounds.bottom - cropRect.bottom)))
                // Left
                drawRect(darkOverlay, topLeft = Offset(imageBounds.left, cropRect.top), size = Size(max(0f, cropRect.left - imageBounds.left), cropRect.height))
                // Right
                drawRect(darkOverlay, topLeft = Offset(cropRect.right, cropRect.top), size = Size(max(0f, imageBounds.right - cropRect.right), cropRect.height))

                // 2. Crop border
                drawRect(
                    color = Color.White,
                    topLeft = Offset(cropRect.left, cropRect.top),
                    size = Size(cropRect.width, cropRect.height),
                    style = Stroke(width = 2.dp.toPx())
                )

                // 3. Rule of Thirds Grid Lines
                val thirdW = cropRect.width / 3f
                val thirdH = cropRect.height / 3f
                val gridColor = Color.White.copy(alpha = 0.35f)
                val gridStroke = 1.dp.toPx()

                // Vertical lines
                drawLine(gridColor, Offset(cropRect.left + thirdW, cropRect.top), Offset(cropRect.left + thirdW, cropRect.bottom), strokeWidth = gridStroke)
                drawLine(gridColor, Offset(cropRect.left + thirdW * 2, cropRect.top), Offset(cropRect.left + thirdW * 2, cropRect.bottom), strokeWidth = gridStroke)
                // Horizontal lines
                drawLine(gridColor, Offset(cropRect.left, cropRect.top + thirdH), Offset(cropRect.right, cropRect.top + thirdH), strokeWidth = gridStroke)
                drawLine(gridColor, Offset(cropRect.left, cropRect.top + thirdH * 2), Offset(cropRect.right, cropRect.top + thirdH * 2), strokeWidth = gridStroke)

                // 4. Corner Handles (L-shaped thick brackets)
                val handleLen = 22.dp.toPx()
                val handleThick = 4.dp.toPx()
                val handleColor = Color(0xFF64B5F6)

                // Top-Left Corner
                drawLine(handleColor, Offset(cropRect.left - 2, cropRect.top), Offset(cropRect.left + handleLen, cropRect.top), strokeWidth = handleThick)
                drawLine(handleColor, Offset(cropRect.left, cropRect.top - 2), Offset(cropRect.left, cropRect.top + handleLen), strokeWidth = handleThick)

                // Top-Right Corner
                drawLine(handleColor, Offset(cropRect.right + 2, cropRect.top), Offset(cropRect.right - handleLen, cropRect.top), strokeWidth = handleThick)
                drawLine(handleColor, Offset(cropRect.right, cropRect.top - 2), Offset(cropRect.right, cropRect.top + handleLen), strokeWidth = handleThick)

                // Bottom-Left Corner
                drawLine(handleColor, Offset(cropRect.left - 2, cropRect.bottom), Offset(cropRect.left + handleLen, cropRect.bottom), strokeWidth = handleThick)
                drawLine(handleColor, Offset(cropRect.left, cropRect.bottom + 2), Offset(cropRect.left, cropRect.bottom - handleLen), strokeWidth = handleThick)

                // Bottom-Right Corner
                drawLine(handleColor, Offset(cropRect.right + 2, cropRect.bottom), Offset(cropRect.right - handleLen, cropRect.bottom), strokeWidth = handleThick)
                drawLine(handleColor, Offset(cropRect.right, cropRect.bottom + 2), Offset(cropRect.right, cropRect.bottom - handleLen), strokeWidth = handleThick)

                // Edge Grab Indicators
                val edgeLen = 14.dp.toPx()
                // Top & Bottom Centers
                drawLine(handleColor, Offset(cropRect.center.x - edgeLen, cropRect.top), Offset(cropRect.center.x + edgeLen, cropRect.top), strokeWidth = handleThick)
                drawLine(handleColor, Offset(cropRect.center.x - edgeLen, cropRect.bottom), Offset(cropRect.center.x + edgeLen, cropRect.bottom), strokeWidth = handleThick)
                // Left & Right Centers
                drawLine(handleColor, Offset(cropRect.left, cropRect.center.y - edgeLen), Offset(cropRect.left, cropRect.center.y + edgeLen), strokeWidth = handleThick)
                drawLine(handleColor, Offset(cropRect.right, cropRect.center.y - edgeLen), Offset(cropRect.right, cropRect.center.y + edgeLen), strokeWidth = handleThick)
            }

            // Real-time Crop Dimension Pill (in real bitmap pixels)
            val cropPixelW = ((cropRect.width / imageBounds.width) * bitmap.width).toInt()
            val cropPixelH = ((cropRect.height / imageBounds.height) * bitmap.height).toInt()

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
                    .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(16.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Potong: $cropPixelW × $cropPixelH px",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

private fun getTouchedHandle(pos: Offset, rect: Rect, tol: Float): DragHandle {
    val tl = (pos - rect.topLeft).getDistance()
    val tr = (pos - rect.topRight).getDistance()
    val bl = (pos - rect.bottomLeft).getDistance()
    val br = (pos - rect.bottomRight).getDistance()

    val topDist = abs(pos.y - rect.top)
    val botDist = abs(pos.y - rect.bottom)
    val leftDist = abs(pos.x - rect.left)
    val rightDist = abs(pos.x - rect.right)

    return when {
        tl < tol -> DragHandle.TOP_LEFT
        tr < tol -> DragHandle.TOP_RIGHT
        bl < tol -> DragHandle.BOTTOM_LEFT
        br < tol -> DragHandle.BOTTOM_RIGHT
        topDist < tol && pos.x in rect.left..rect.right -> DragHandle.TOP
        botDist < tol && pos.x in rect.left..rect.right -> DragHandle.BOTTOM
        leftDist < tol && pos.y in rect.top..rect.bottom -> DragHandle.LEFT
        rightDist < tol && pos.y in rect.top..rect.bottom -> DragHandle.RIGHT
        rect.contains(pos) -> DragHandle.BODY
        else -> DragHandle.NONE
    }
}

private fun updateCropRect(
    current: Rect,
    bounds: Rect,
    handle: DragHandle,
    drag: Offset,
    aspectRatio: Float?
): Rect {
    val minSize = 40f
    var l = current.left
    var t = current.top
    var r = current.right
    var b = current.bottom

    when (handle) {
        DragHandle.BODY -> {
            var dx = drag.x
            var dy = drag.y
            if (l + dx < bounds.left) dx = bounds.left - l
            if (r + dx > bounds.right) dx = bounds.right - r
            if (t + dy < bounds.top) dy = bounds.top - t
            if (b + dy > bounds.bottom) dy = bounds.bottom - b
            return current.translate(dx, dy)
        }
        DragHandle.TOP_LEFT -> {
            l = (l + drag.x).coerceIn(bounds.left, r - minSize)
            t = (t + drag.y).coerceIn(bounds.top, b - minSize)
            if (aspectRatio != null) {
                val newW = r - l
                val newH = newW / aspectRatio
                t = (b - newH).coerceIn(bounds.top, b - minSize)
                l = r - (b - t) * aspectRatio
            }
        }
        DragHandle.TOP_RIGHT -> {
            r = (r + drag.x).coerceIn(l + minSize, bounds.right)
            t = (t + drag.y).coerceIn(bounds.top, b - minSize)
            if (aspectRatio != null) {
                val newW = r - l
                val newH = newW / aspectRatio
                t = (b - newH).coerceIn(bounds.top, b - minSize)
                r = l + (b - t) * aspectRatio
            }
        }
        DragHandle.BOTTOM_LEFT -> {
            l = (l + drag.x).coerceIn(bounds.left, r - minSize)
            b = (b + drag.y).coerceIn(t + minSize, bounds.bottom)
            if (aspectRatio != null) {
                val newW = r - l
                val newH = newW / aspectRatio
                b = (t + newH).coerceIn(t + minSize, bounds.bottom)
                l = r - (b - t) * aspectRatio
            }
        }
        DragHandle.BOTTOM_RIGHT -> {
            r = (r + drag.x).coerceIn(l + minSize, bounds.right)
            b = (b + drag.y).coerceIn(t + minSize, bounds.bottom)
            if (aspectRatio != null) {
                val newW = r - l
                val newH = newW / aspectRatio
                b = (t + newH).coerceIn(t + minSize, bounds.bottom)
                r = l + (b - t) * aspectRatio
            }
        }
        DragHandle.TOP -> {
            t = (t + drag.y).coerceIn(bounds.top, b - minSize)
            if (aspectRatio != null) {
                val newH = b - t
                val newW = newH * aspectRatio
                val center = (l + r) / 2f
                l = (center - newW / 2f).coerceIn(bounds.left, bounds.right - minSize)
                r = (l + newW).coerceIn(l + minSize, bounds.right)
            }
        }
        DragHandle.BOTTOM -> {
            b = (b + drag.y).coerceIn(t + minSize, bounds.bottom)
            if (aspectRatio != null) {
                val newH = b - t
                val newW = newH * aspectRatio
                val center = (l + r) / 2f
                l = (center - newW / 2f).coerceIn(bounds.left, bounds.right - minSize)
                r = (l + newW).coerceIn(l + minSize, bounds.right)
            }
        }
        DragHandle.LEFT -> {
            l = (l + drag.x).coerceIn(bounds.left, r - minSize)
            if (aspectRatio != null) {
                val newW = r - l
                val newH = newW / aspectRatio
                val center = (t + b) / 2f
                t = (center - newH / 2f).coerceIn(bounds.top, bounds.bottom - minSize)
                b = (t + newH).coerceIn(t + minSize, bounds.bottom)
            }
        }
        DragHandle.RIGHT -> {
            r = (r + drag.x).coerceIn(l + minSize, bounds.right)
            if (aspectRatio != null) {
                val newW = r - l
                val newH = newW / aspectRatio
                val center = (t + b) / 2f
                t = (center - newH / 2f).coerceIn(bounds.top, bounds.bottom - minSize)
                b = (t + newH).coerceIn(t + minSize, bounds.bottom)
            }
        }
        DragHandle.NONE -> {}
    }

    return Rect(l, t, r, b)
}

// -------------------------------------------------------------------
// DOODLE / ANNOTATION CANVAS
// -------------------------------------------------------------------

@Composable
fun DoodleCanvas(
    bitmap: Bitmap,
    colorMatrix: ColorMatrix,
    strokes: List<DrawStroke>,
    currentStroke: DrawStroke?,
    brushColor: Color,
    brushWidth: Float,
    onStrokeAdded: (DrawStroke) -> Unit,
    onStrokeUpdated: (DrawStroke?) -> Unit
) {
    var points by remember { mutableStateOf(listOf<Offset>()) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            colorFilter = ColorFilter.colorMatrix(colorMatrix),
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(brushColor, brushWidth) {
                    detectDragGestures(
                        onDragStart = { startPos ->
                            points = listOf(startPos)
                            onStrokeUpdated(DrawStroke(points, brushColor, brushWidth))
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            points = points + change.position
                            onStrokeUpdated(DrawStroke(points, brushColor, brushWidth))
                        },
                        onDragEnd = {
                            if (points.isNotEmpty()) {
                                onStrokeAdded(DrawStroke(points, brushColor, brushWidth))
                                points = emptyList()
                            }
                        },
                        onDragCancel = {
                            points = emptyList()
                            onStrokeUpdated(null)
                        }
                    )
                }
        ) {
            // Render committed strokes
            strokes.forEach { s ->
                if (s.path.size > 1) {
                    val p = Path().apply {
                        moveTo(s.path.first().x, s.path.first().y)
                        s.path.drop(1).forEach { pt -> lineTo(pt.x, pt.y) }
                    }
                    drawPath(p, color = s.color, style = Stroke(width = s.strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
                } else if (s.path.size == 1) {
                    drawCircle(s.color, radius = s.strokeWidth / 2f, center = s.path.first())
                }
            }

            // Render current active stroke
            currentStroke?.let { s ->
                if (s.path.size > 1) {
                    val p = Path().apply {
                        moveTo(s.path.first().x, s.path.first().y)
                        s.path.drop(1).forEach { pt -> lineTo(pt.x, pt.y) }
                    }
                    drawPath(p, color = s.color, style = Stroke(width = s.strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
                } else if (s.path.size == 1) {
                    drawCircle(s.color, radius = s.strokeWidth / 2f, center = s.path.first())
                }
            }
        }
    }
}

// -------------------------------------------------------------------
// RESIZE TOOL CONTROLS & CANVAS SCALING
// -------------------------------------------------------------------

@Composable
fun ResizeToolControls(
    bitmap: Bitmap,
    onApplyResize: (Int, Int) -> Unit
) {
    var targetWidthStr by remember(bitmap) { mutableStateOf(bitmap.width.toString()) }
    var targetHeightStr by remember(bitmap) { mutableStateOf(bitmap.height.toString()) }
    var keepAspectRatio by remember { mutableStateOf(true) }

    val originalWidth = bitmap.width
    val originalHeight = bitmap.height
    val aspectRatio = originalWidth.toFloat() / originalHeight.toFloat()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Ukuran Asli: ${originalWidth} × ${originalHeight} px",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = keepAspectRatio,
                    onCheckedChange = { keepAspectRatio = it },
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Kunci Rasio", fontSize = 11.sp)
            }
        }

        // Quick Preset Scale Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                "25%" to 0.25f,
                "50%" to 0.50f,
                "75%" to 0.75f,
                "1080p (FHD)" to (1080f / max(originalWidth, originalHeight)),
                "720p (HD)" to (720f / max(originalWidth, originalHeight)),
                "480p (SD)" to (480f / max(originalWidth, originalHeight))
            ).forEach { (label, scaleFactor) ->
                SuggestionChip(
                    onClick = {
                        val newW = max(1, (originalWidth * scaleFactor).toInt())
                        val newH = max(1, (originalHeight * scaleFactor).toInt())
                        targetWidthStr = newW.toString()
                        targetHeightStr = newH.toString()
                    },
                    label = { Text(label, fontSize = 10.sp) }
                )
            }
        }

        // Width and Height Input Fields
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = targetWidthStr,
                onValueChange = { newVal ->
                    val clean = newVal.filter { it.isDigit() }
                    targetWidthStr = clean
                    if (keepAspectRatio && clean.isNotEmpty()) {
                        val w = clean.toIntOrNull()
                        if (w != null && w > 0) {
                            targetHeightStr = max(1, (w / aspectRatio).toInt()).toString()
                        }
                    }
                },
                label = { Text("Lebar (px)") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )

            Text("×", fontWeight = FontWeight.Bold, fontSize = 16.sp)

            OutlinedTextField(
                value = targetHeightStr,
                onValueChange = { newVal ->
                    val clean = newVal.filter { it.isDigit() }
                    targetHeightStr = clean
                    if (keepAspectRatio && clean.isNotEmpty()) {
                        val h = clean.toIntOrNull()
                        if (h != null && h > 0) {
                            targetWidthStr = max(1, (h * aspectRatio).toInt()).toString()
                        }
                    }
                },
                label = { Text("Tinggi (px)") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }

        // Apply Resize Button
        Button(
            onClick = {
                val w = targetWidthStr.toIntOrNull() ?: originalWidth
                val h = targetHeightStr.toIntOrNull() ?: originalHeight
                if (w > 0 && h > 0 && (w != originalWidth || h != originalHeight)) {
                    onApplyResize(w, h)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = (targetWidthStr.toIntOrNull() ?: 0) > 0 && (targetHeightStr.toIntOrNull() ?: 0) > 0
        ) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Terapkan Ukuran Baru", fontSize = 12.sp)
        }
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
        "webp" -> if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }
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

/**
 * Resizes a bitmap using Android Canvas with anti-aliasing and bilinear filtering
 */
fun resizeBitmap(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
    if (source.width == targetWidth && source.height == targetHeight) return source
    val result = Bitmap.createBitmap(targetWidth, targetHeight, source.config ?: Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)
    val paint = Paint().apply {
        isAntiAlias = true
        isFilterBitmap = true
        isDither = true
    }
    val srcRect = AndroidRect(0, 0, source.width, source.height)
    val dstRect = AndroidRect(0, 0, targetWidth, targetHeight)
    canvas.drawBitmap(source, srcRect, dstRect, paint)
    return result
}

fun applyStrokesToBitmap(source: Bitmap, strokes: List<DrawStroke>): Bitmap {
    val result = Bitmap.createBitmap(source.width, source.height, source.config ?: Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)
    canvas.drawBitmap(source, 0f, 0f, null)

    val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    strokes.forEach { s ->
        paint.color = s.color.toArgb()
        paint.strokeWidth = s.strokeWidth
        if (s.path.size > 1) {
            val androidPath = android.graphics.Path()
            androidPath.moveTo(s.path.first().x, s.path.first().y)
            s.path.drop(1).forEach { pt -> androidPath.lineTo(pt.x, pt.y) }
            canvas.drawPath(androidPath, paint)
        } else if (s.path.size == 1) {
            val pt = s.path.first()
            paint.style = Paint.Style.FILL
            canvas.drawCircle(pt.x, pt.y, s.strokeWidth / 2f, paint)
            paint.style = Paint.Style.STROKE
        }
    }

    return result
}

fun applyColorFiltersToBitmap(source: Bitmap, colorMatrix: ColorMatrix): Bitmap {
    val result = Bitmap.createBitmap(source.width, source.height, source.config ?: Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)
    val paint = Paint().apply {
        isAntiAlias = true
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
            0f, 0f, 0.85f, 0f, 0f
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
