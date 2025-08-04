package com.ammakkutti.sa.ui.screens

import ConvertViewModel
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.MenuBar


import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import com.ammakkutti.sa.domain.model.VideoFile
import com.ammakkutti.sa.presentation.convert.ConvertIntent
import com.ammakkutti.sa.presentation.convert.FolderNode
import com.ammakkutti.sa.presentation.convert.SortOption
import com.ammakkutti.sa.ui.screens.components.OptimizationDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.font.FontWeight
import com.ammakkutti.sa.presentation.convert.ConversionStats
import com.ammakkutti.sa.ui.screens.components.ConversionStatsPanel
import com.ammakkutti.sa.ui.screens.components.ConversionHistoryDialog

@Composable
fun ConvertScreen(viewModel: ConvertViewModel) {
    val state by viewModel.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Toolbar
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Convert Mode", style = MaterialTheme.typography.headlineSmall)

            // Button group
            Row {
                // History button
                Button(
                    onClick = { viewModel.handleIntent(ConvertIntent.ShowHistory) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary
                    )
                ) {
                    Text("📊 History")
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Select folder button
                Button(onClick = { viewModel.handleIntent(ConvertIntent.SelectFolder) }) {
                    Text("Select Folder")
                }
            }
        }

        HorizontalDivider()

        // Main Content
        when {
            state.selectedFolder == null -> {
                WelcomeContent(
                    onSelectFolder = { viewModel.handleIntent(ConvertIntent.SelectFolder) }
                )
            }

            state.isScanning -> {
                ScanningContent()
            }

            state.hasCompletedConversion && state.lastConversionStats != null -> {
                // NEW: Show completion results
                CompletionResultsContent(
                    lastStats = state.lastConversionStats!!,
                    onClearResults = {
                        viewModel.handleIntent(ConvertIntent.ClearConversionResults) },
                    onShowHistory = { viewModel.handleIntent(ConvertIntent.ShowHistory) }
                )
            }

            else -> {
                MediaBrowserContent(
                    folderStructure = state.folderStructure,
                    mediaFiles = state.mediaFiles,
                    sortBy = state.sortBy,
                    onSortChange = { viewModel.handleIntent(ConvertIntent.ChangeSortOption(it)) },
                    isConverting = state.isConverting,
                    conversionProgress = state.conversionProgress,
                    currentFile = state.currentFile,
                    onStartConversion = { viewModel.handleIntent(ConvertIntent.StartConversion) },
                    onShowOptimizationDialog = { viewModel.handleIntent(ConvertIntent.ShowOptimizationDialog) },
                    conversionStats = state.conversionStats // NEW parameter
                )
            }
        }
    }

    // Optimization Dialog
    if (state.showOptimizationDialog) {
        val videoCount = state.mediaFiles.count { it.isVideo }
        val imageCount = state.mediaFiles.count { it.isImage }

        OptimizationDialog(
            settings = state.optimizationSettings,
            videoCount = videoCount,
            imageCount = imageCount,
            onSettingsChange = {
                viewModel.handleIntent(ConvertIntent.UpdateOptimizationSettings(it))
            },
            onStartOptimization = {
                viewModel.handleIntent(ConvertIntent.StartOptimization)
            },
            onDismiss = {
                viewModel.handleIntent(ConvertIntent.HideOptimizationDialog)
            }
        )
    }


    // NEW: History Dialog
    if (state.showHistoryDialog) {
        ConversionHistoryDialog(
            history = state.conversionHistory,
            isLoading = state.isLoadingHistory,
            onDismiss = {
                viewModel.handleIntent(ConvertIntent.HideHistory)
            }
        )
    }
}

@Composable
private fun WelcomeContent(onSelectFolder: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Select a folder to scan for large video files")
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onSelectFolder) {
                Text("Choose Folder")
            }
        }
    }
}

@Composable
private fun ScanningContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text("Scanning for large video files...")
        }
    }
}

@Composable
private fun MediaBrowserContent(
    folderStructure: List<FolderNode>,
    mediaFiles: List<VideoFile>,
    sortBy: SortOption,
    onSortChange: (SortOption) -> Unit,
    isConverting: Boolean,
    conversionProgress: Float,
    currentFile: String,
    onStartConversion: () -> Unit,
    onShowOptimizationDialog: () -> Unit,
    conversionStats: ConversionStats // NEW parameter
) {
    if (isConverting) {
        // Show conversion dashboard during processing
        ConversionDashboard(
            folderStructure = folderStructure,
            mediaFiles = mediaFiles,
            conversionStats = conversionStats,
            currentFile = currentFile,
            conversionProgress = conversionProgress
        )
    } else {
        // Show normal file browser when not converting
        NormalFileBrowser(
            folderStructure = folderStructure,
            mediaFiles = mediaFiles,
            sortBy = sortBy,
            onSortChange = onSortChange,
            onStartConversion = onStartConversion,
            onShowOptimizationDialog = onShowOptimizationDialog
        )
    }
}

@Composable
private fun ConversionDashboard(
    folderStructure: List<FolderNode>,
    mediaFiles: List<VideoFile>,
    conversionStats: ConversionStats,
    currentFile: String,
    conversionProgress: Float
) {
    Row(modifier = Modifier.fillMaxSize()) {
        // Left side - Folder tree (smaller during conversion)
        Card(
            modifier = Modifier.width(200.dp).fillMaxHeight().padding(8.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    "Folders",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (folderStructure.isEmpty()) {
                    Text("No subfolders", style = MaterialTheme.typography.bodySmall)
                } else {
                    LazyColumn {
                        items(folderStructure) { folder ->
                            Text(
                                text = folder.name,
                                modifier = Modifier.padding(vertical = 2.dp),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

        // Middle - File list (condensed during conversion)
        Card(
            modifier = Modifier.width(300.dp).fillMaxHeight().padding(8.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    "Files (${mediaFiles.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn {
                    items(mediaFiles) { file ->
                        CompactFileItem(file, conversionStats)
                    }
                }
            }
        }

        // Right side - Statistics Panel (main focus during conversion)
        ConversionStatsPanel(
            stats = conversionStats,
            currentFile = currentFile,
            modifier = Modifier.weight(1f).padding(8.dp)
        )
    }
}

@Composable
private fun NormalFileBrowser(
    folderStructure: List<FolderNode>,
    mediaFiles: List<VideoFile>,
    sortBy: SortOption,
    onSortChange: (SortOption) -> Unit,
    onStartConversion: () -> Unit,
    onShowOptimizationDialog: () -> Unit
) {
    Row(modifier = Modifier.fillMaxSize()) {
        // Left side - Folder tree
        Card(
            modifier = Modifier.width(300.dp).fillMaxHeight().padding(8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Folders", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(8.dp))

                if (folderStructure.isEmpty()) {
                    Text("No subfolders found")
                } else {
                    LazyColumn {
                        items(folderStructure) { folder ->
                            Text(
                                text = folder.name,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // Right side - Media files with controls
        Card(
            modifier = Modifier.fillMaxSize().padding(8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header with buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Media Files (${mediaFiles.size})", style = MaterialTheme.typography.headlineSmall)

                    Row {
                        Button(onClick = { onSortChange(SortOption.SIZE_DESC) }) {
                            Text("Sort by Size")
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = onShowOptimizationDialog,
                            enabled = mediaFiles.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("Optimize All")
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        val largeVideos = mediaFiles.filter { it.isVideo && it.isLarge }
                        Button(
                            onClick = onStartConversion,
                            enabled = largeVideos.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            )
                        ) {
                            Text("Large Only (${largeVideos.size})")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // File list
                if (mediaFiles.isEmpty()) {
                    Text("No media files found")
                } else {
                    LazyColumn {
                        items(mediaFiles) { file ->
                            MediaFileItem(file)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactFileItem(file: VideoFile, conversionStats: ConversionStats) {
    val isCurrentFile = conversionStats.currentFileStats?.fileName == file.name
    val isCompleted = conversionStats.completedFileStats.any { it.fileName == file.name }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isCurrentFile -> MaterialTheme.colorScheme.primaryContainer
                isCompleted -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(8.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (isCurrentFile) FontWeight.Bold else FontWeight.Normal
                )
                Text(
                    text = "${file.sizeMB} MB",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // Status indicator
            when {
                isCurrentFile -> Text("⏳", style = MaterialTheme.typography.bodySmall)
                isCompleted -> Text("✅", style = MaterialTheme.typography.bodySmall)
                else -> Text("⏸️", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun MediaFileItem(file: VideoFile) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(file.name, style = MaterialTheme.typography.bodyLarge)
            Text("Size: ${file.sizeMB} MB • ${file.extension.uppercase()}")
            Text("Folder: ${file.originalFolder}")
        }
    }
}

@Composable
private fun CompletionResultsContent(
    lastStats: ConversionStats,
    onClearResults: () -> Unit,
    onShowHistory: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.padding(32.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🎉 Conversion Completed!",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Quick stats
                Row(
                    horizontalArrangement = Arrangement.spacedBy(32.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${lastStats.completedFiles}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text("Files Processed")
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${lastStats.totalSpaceSaved / (1024 * 1024)} MB",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text("Space Saved")
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${(lastStats.averageCompressionRatio * 100).toInt()}%",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text("Avg Compression")
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = onShowHistory,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("📊 View All History")
                    }

                    Button(
                        onClick = onClearResults,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Text("🆕 New Conversion")
                    }
                }
            }
        }
    }
}