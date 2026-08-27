package com.example.ui.components
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ComparisonLine
import com.example.EditorViewModel
import com.example.LineDiffType
import java.io.File

@Composable
fun CompareViewScreen(
    fileAPath: String,
    fileBPath: String,
    viewModel: EditorViewModel
) {
    val lines by viewModel.comparisonLines.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.navigateToExplorer() }) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Compare Files (Side-by-Side)",
                style = MaterialTheme.typography.titleMedium
            )
        }

        // Headers showing file names
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Left File (A):",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    File(fileAPath).name,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            VerticalDivider(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp)
                    .padding(horizontal = 4.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Right File (B):",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    File(fileBPath).name,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(lines) { line ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                when (line.type) {
                                    LineDiffType.ONLY_A -> Color(0x33FF0000)
                                    LineDiffType.DIFFERENT -> Color(0x33FFFF00)
                                    else -> Color.Transparent
                                }
                            )
                            .padding(4.dp)
                    ) {
                        Text(
                            line.textA ?: "",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = if (line.textA == null) Color.Gray else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    VerticalDivider(modifier = Modifier.width(1.dp), color = Color.LightGray)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                when (line.type) {
                                    LineDiffType.ONLY_B -> Color(0x3300FF00)
                                    LineDiffType.DIFFERENT -> Color(0x33FFFF00)
                                    else -> Color.Transparent
                                }
                            )
                            .padding(4.dp)
                    ) {
                        Text(
                            line.textB ?: "",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = if (line.textB == null) Color.Gray else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
            }
        }
    }
}
