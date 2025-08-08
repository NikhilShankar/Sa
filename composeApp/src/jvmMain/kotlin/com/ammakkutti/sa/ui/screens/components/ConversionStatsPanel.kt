package com.ammakkutti.sa.ui.screens.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ammakkutti.sa.presentation.convert.ConversionStats
import com.ammakkutti.sa.presentation.convert.FileConversionStats
import com.ammakkutti.sa.presentation.convert.ConversionStatus

@Composable
fun ConversionStatsPanel(
    stats: ConversionStats,
    currentFile: String,
    modifier: Modifier = Modifier,
    processingStats: List<FileConversionStats>?
) {
    Card(
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())
        ) {
            // Header
            // Header
            Text(
                text = "Conversion Statistics",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Overall Progress Section
            OverallProgressSection(stats = stats, currentFile = currentFile)

            Spacer(modifier = Modifier.height(16.dp))

            // Space Savings Section
            SpaceSavingsSection(stats = stats)

            Spacer(modifier = Modifier.height(16.dp))

//            // Current File Section
//            if (stats.currentFileStats != null) {
//                CurrentFileSection(
//                    fileStats = stats.currentFileStats,
//                    progress = stats.currentFileProgress
//                )
//                Spacer(modifier = Modifier.height(16.dp))
//            }

            processingStats?.forEach {
                CurrentFileSection(
                    fileStats = it,
                    progress = it.conversionProgress
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Completed Files Section
            if (stats.completedFileStats.isNotEmpty()) {
                CompletedFilesSection(completedFiles = stats.completedFileStats)
            }
        }
    }
}

@Composable
private fun OverallProgressSection(stats: ConversionStats, currentFile: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Overall Progress",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Progress bar
            val overallProgress = if (stats.totalFiles > 0) {
                stats.completedFiles.toFloat() / stats.totalFiles
            } else 0f

            LinearProgressIndicator(
                progress = { overallProgress },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("${stats.completedFiles} / ${stats.totalFiles} files")
                Text("${(overallProgress * 100).toInt()}%")
            }

            if (currentFile.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Current: $currentFile",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            // Time estimation
            if (stats.estimatedTimeRemaining > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Estimated time remaining: ${formatTime(stats.estimatedTimeRemaining)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun SpaceSavingsSection(stats: ConversionStats) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Space Savings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Original Size")
                    Text(
                        text = formatFileSize(stats.totalOriginalSize),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text("→", style = MaterialTheme.typography.headlineSmall)

                Column {
                    Text("Compressed Size")
                    Text(
                        text = formatFileSize(stats.totalCompressedSize),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Space Saved: ${formatFileSize(stats.totalSpaceSaved)}",
                    color = Color.Green,
                    fontWeight = FontWeight.Medium
                )

                if (stats.averageCompressionRatio > 0) {
                    Text(
                        text = "Avg Compression: ${(stats.averageCompressionRatio * 100).toInt()}%",
                        color = Color.Green,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun CurrentFileSection(fileStats: FileConversionStats, progress: Float) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Currently Processing",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = fileStats.fileName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Size: ${formatFileSize(fileStats.originalSize)}",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth()
            )

            Text("${(progress * 100).toInt()}%")
        }
    }
}

@Composable
private fun CompletedFilesSection(completedFiles: List<FileConversionStats>) {
    Text(
        text = "Completed Files (${completedFiles.size})",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Medium
    )

    Spacer(modifier = Modifier.height(8.dp))

    LazyColumn(
        modifier = Modifier.heightIn(max = 300.dp)
    ) {
        items(completedFiles) { fileStats ->
            CompletedFileItem(fileStats = fileStats)
        }
    }
}

@Composable
private fun CompletedFileItem(fileStats: FileConversionStats) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (fileStats.status) {
                ConversionStatus.COMPLETED -> Color.Green.copy(alpha = 0.1f)
                ConversionStatus.FAILED -> Color.Red.copy(alpha = 0.1f)
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fileStats.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )

                if (fileStats.status == ConversionStatus.COMPLETED) {
                    Text(
                        text = "${formatFileSize(fileStats.originalSize)} → ${formatFileSize(fileStats.compressedSize)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                if (fileStats.status == ConversionStatus.COMPLETED && fileStats.compressionRatio > 0) {
                    Text(
                        text = "-${(fileStats.compressionRatio * 100).toInt()}%",
                        color = Color.Green,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = fileStats.status.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = when (fileStats.status) {
                        ConversionStatus.COMPLETED -> Color.Green
                        ConversionStatus.FAILED -> Color.Red
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }
    }
}

// Helper functions
private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    val gb = mb / 1024.0
    return "%.1f GB".format(gb)
}

private fun formatTime(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60

    return when {
        hours > 0 -> "${hours}h ${minutes}m ${secs}s"
        minutes > 0 -> "${minutes}m ${secs}s"
        else -> "${secs}s"
    }
}