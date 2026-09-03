package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Composable that displays either a loaded image/video/APK thumbnail or fallback vector icon.
 */
@Composable
fun DocumentThumbnailView(
    item: DocumentItem,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val (icon, tint) = StorageManagerUtils.getDocumentIcon(item)

    val isMediaOrApk = item.isImage || item.isVideo || item.isApk
    var thumbnailBitmap by remember(item.uri) {
        mutableStateOf(if (isMediaOrApk) ThumbnailCache.get(item.uri.toString()) else null)
    }

    LaunchedEffect(item.uri, item.lastModified) {
        if (!isMediaOrApk) return@LaunchedEffect
        if (thumbnailBitmap != null) return@LaunchedEffect

        val cached = ThumbnailCache.get(item.uri.toString())
        if (cached != null) {
            thumbnailBitmap = cached
            return@LaunchedEffect
        }

        val loaded = withContext(Dispatchers.IO) {
            loadThumbnailBitmap(context, item)
        }
        if (loaded != null) {
            ThumbnailCache.put(item.uri.toString(), loaded)
            thumbnailBitmap = loaded
        }
    }

    if (thumbnailBitmap != null) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFFF1F5F9)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = thumbnailBitmap!!.asImageBitmap(),
                contentDescription = item.name,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop
            )
        }
    } else {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = modifier
        )
    }
}

/**
 * Background worker to extract thumbnail from image, video or APK.
 */
private fun loadThumbnailBitmap(context: Context, item: DocumentItem): Bitmap? {
    return try {
        when {
            item.isImage -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        context.contentResolver.loadThumbnail(item.uri, Size(96, 96), null)
                    } catch (_: Exception) {
                        decodeSampledDocumentBitmap(context, item.uri, 96, 96)
                    }
                } else {
                    decodeSampledDocumentBitmap(context, item.uri, 96, 96)
                }
            }
            item.isVideo -> {
                val realPath = item.realPath
                if (realPath != null && File(realPath).exists()) {
                    ThumbnailUtils.createVideoThumbnail(realPath, MediaStore.Images.Thumbnails.MICRO_KIND)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        context.contentResolver.loadThumbnail(item.uri, Size(96, 96), null)
                    } catch (_: Exception) {
                        null
                    }
                } else {
                    null
                }
            }
            item.isApk -> {
                val realPath = item.realPath
                if (realPath != null && File(realPath).exists()) {
                    val pm = context.packageManager
                    val pkgInfo = pm.getPackageArchiveInfo(realPath, 0)
                    val appInfo = pkgInfo?.applicationInfo
                    if (appInfo != null) {
                        appInfo.sourceDir = realPath
                        appInfo.publicSourceDir = realPath
                        val drawable = appInfo.loadIcon(pm)
                        if (drawable != null) drawableToBitmap(drawable, 96, 96) else null
                    } else null
                } else null
            }
            else -> null
        }
    } catch (_: Exception) {
        null
    }
}

/**
 * Decode sampled bitmap from URI to avoid OutOfMemoryError.
 */
private fun decodeSampledDocumentBitmap(context: Context, uri: Uri, reqWidth: Int, reqHeight: Int): Bitmap? {
    return try {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }

        options.inSampleSize = calculateSampleRatio(options, reqWidth, reqHeight)
        options.inJustDecodeBounds = false

        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }
    } catch (_: Exception) {
        null
    }
}

private fun calculateSampleRatio(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
    val height = options.outHeight
    val width = options.outWidth
    var inSampleSize = 1

    if (height > reqHeight || width > reqWidth) {
        val halfHeight = height / 2
        val halfWidth = width / 2
        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
}

private fun drawableToBitmap(drawable: Drawable, width: Int, height: Int): Bitmap {
    if (drawable is BitmapDrawable && drawable.bitmap != null) {
        return Bitmap.createScaledBitmap(drawable.bitmap, width, height, true)
    }
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}
