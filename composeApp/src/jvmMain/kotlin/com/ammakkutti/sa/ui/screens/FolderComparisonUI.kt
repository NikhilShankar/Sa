package com.ammakkutti.sa.ui.screens


import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material3.Button
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.ammakkutti.sa.data.services.FolderComparison
import com.ammakkutti.sa.data.services.FileInfo
import com.ammakkutti.sa.data.services.FileDifference
import com.ammakkutti.sa.data.services.DifferenceType
import com.ammakkutti.sa.presentation.convert.ConvertState
import com.ammakkutti.sa.presentation.convert.ConvertIntent
import com.ammakkutti.sa.presentation.convert.ComparisonViewFilter
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun FolderComparisonDialog(
    state: ConvertState,
    onIntent: (ConvertIntent) -> Unit,
    onSelectSourceFolder: () -> Unit,
    onSelectDestinationFolder: () -> Unit
) {
    Dialog(onDismissRequest = { onIntent(ConvertIntent.HideFolderComparisonDialog) }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f),
            shape = RoundedCornerShape(16.dp),
            elevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Compare Folders",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Button(onClick = { onIntent(ConvertIntent.HideFolderComparisonDialog) }) {
                        Text("Close")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Folder selection
                FolderSelectionSection(
                    sourceFolder = state.sourceComparisonFolder,
                    destinationFolder = state.destinationComparisonFolder,
                    onSelectSource = onSelectSourceFolder,
                    onSelectDestination = onSelectDestinationFolder
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Progress indicator
                if (state.isComparingFolders) {
                    Column {
                        Text("Comparing folders...")
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = state.comparisonProgress,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "${(state.comparisonProgress * 100).toInt()}%",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                } else {
                    // Start comparison button
                    Button(
                        onClick = { onIntent(ConvertIntent.StartFolderComparison) },
                        enabled = state.sourceComparisonFolder != null && state.destinationComparisonFolder != null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        //Icon(Icons.Default.Search, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Compare Folders")
                    }
                }
            }
        }
    }
}

@Composable
fun FolderSelectionSection(
    sourceFolder: String?,
    destinationFolder: String?,
    onSelectSource: () -> Unit,
    onSelectDestination: () -> Unit
) {
    Column {
        Text(
            text = "Select folders to compare:",
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Source folder
        FolderSelectionCard(
            title = "Source Folder",
            selectedPath = sourceFolder,
            onClick = onSelectSource,
            //icon = Icons.Default.FolderOpen,
            color = MaterialTheme.colors.primary
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Destination folder
        FolderSelectionCard(
            title = "Destination Folder",
            selectedPath = destinationFolder,
            onClick = onSelectDestination,
            //icon = Icons.Default.Folder,
            color = MaterialTheme.colors.secondary
        )
    }
}

@Composable
fun FolderSelectionCard(
    title: String,
    selectedPath: String?,
    onClick: () -> Unit,
    //icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        elevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
//            Icon(
//                imageVector = icon,
//                contentDescription = title,
//                tint = color,
//                modifier = Modifier.size(24.dp)
//            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
                Text(
                    text = selectedPath ?: "Click to select folder",
                    fontSize = 12.sp,
                    color = if (selectedPath != null) Color.Gray else color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

//            Icon(
//                imageVector = Icons.Default.ChevronRight,
//                contentDescription = "Select",
//                tint = Color.Gray
//            )
        }
    }
}

@Composable
fun FolderComparisonResults(
    state: ConvertState,
    onIntent: (ConvertIntent) -> Unit
) {
    val comparison = state.folderComparison ?: return

    Dialog(onDismissRequest = { onIntent(ConvertIntent.HideComparisonResults) }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(16.dp),
            elevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                // Header
                ComparisonResultsHeader(
                    comparison = comparison,
                    onClose = { onIntent(ConvertIntent.HideComparisonResults) }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Summary statistics
                ComparisonSummary(comparison = comparison)

                Spacer(modifier = Modifier.height(16.dp))

                // Filter tabs
                FilterTabs(
                    selectedFilter = state.comparisonViewFilter,
                    comparison = comparison,
                    onFilterChanged = { filter ->
                        onIntent(ConvertIntent.ChangeComparisonFilter(filter))
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Results list
                ComparisonResultsList(
                    comparison = comparison,
                    filter = state.comparisonViewFilter
                )
            }
        }
    }
}

@Composable
fun ComparisonResultsHeader(
    comparison: FolderComparison,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Folder Comparison Results",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Compared on ${SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(comparison.comparisonDate))}",
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
        Button(onClick = { onClose() }) {
            Text("Close")
        }
//        IconButton(onClick = onClose) {
//            Icon(Icons.Default.Close, contentDescription = "Close")
//        }
    }
}

@Composable
fun ComparisonSummary(comparison: FolderComparison) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        elevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Summary",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SummaryItem(
                    label = "Missing in Dest",
                    count = comparison.totalMissingInDestination,
                    color = Color(0xFFE57373)
                )
                SummaryItem(
                    label = "Missing in Source",
                    count = comparison.totalMissingInSource,
                    color = Color(0xFFFFB74D)
                )
                SummaryItem(
                    label = "Different",
                    count = comparison.totalDifferentFiles,
                    color = Color(0xFF81C784)
                )
                SummaryItem(
                    label = "Identical",
                    count = comparison.totalIdenticalFiles,
                    color = Color(0xFF64B5F6)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (comparison.hasDifferences) {
                Text(
                    text = "⚠️ Folders are not synchronized",
                    color = Color(0xFFFF9800),
                    fontWeight = FontWeight.Medium
                )
            } else {
                Text(
                    text = "✅ Folders are synchronized",
                    color = Color(0xFF4CAF50),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun SummaryItem(
    label: String,
    count: Int,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = count.toString(),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color.Gray
        )
    }
}

@Composable
fun FilterTabs(
    selectedFilter: ComparisonViewFilter,
    comparison: FolderComparison,
    onFilterChanged: (ComparisonViewFilter) -> Unit
) {
    val filters = listOf(
        ComparisonViewFilter.ALL to "All",
        ComparisonViewFilter.MISSING_IN_DESTINATION to "Missing in Dest (${comparison.totalMissingInDestination})",
        ComparisonViewFilter.MISSING_IN_SOURCE to "Missing in Source (${comparison.totalMissingInSource})",
        ComparisonViewFilter.DIFFERENT_FILES to "Different (${comparison.totalDifferentFiles})",
        ComparisonViewFilter.IDENTICAL_FILES to "Identical (${comparison.totalIdenticalFiles})"
    )

    LazyColumn {
        items(filters) { (filter, label) ->
            FilterTab(
                label = label,
                isSelected = selectedFilter == filter,
                onClick = { onFilterChanged(filter) }
            )
        }
    }
}

@Composable
fun FilterTab(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) MaterialTheme.colors.primary else Color.Transparent
    val textColor = if (isSelected) Color.White else MaterialTheme.colors.onSurface

    Text(
        text = label,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .background(backgroundColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        color = textColor,
        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
    )
}

@Composable
fun ComparisonResultsList(
    comparison: FolderComparison,
    filter: ComparisonViewFilter
) {
    val items: List<ComparisonItem> = when (filter) {
        ComparisonViewFilter.ALL -> {
            buildList {
                comparison.missingInDestination.forEach { add(ComparisonItem.MissingInDest(it)) }
                comparison.missingInSource.forEach { add(ComparisonItem.MissingInSource(it)) }
                comparison.differentFiles.forEach { add(ComparisonItem.Different(it)) }
                comparison.identicalFiles.forEach { add(ComparisonItem.Identical(it)) }
            }
        }
        ComparisonViewFilter.MISSING_IN_DESTINATION ->
            comparison.missingInDestination.map { ComparisonItem.MissingInDest(it) }
        ComparisonViewFilter.MISSING_IN_SOURCE ->
            comparison.missingInSource.map { ComparisonItem.MissingInSource(it) }
        ComparisonViewFilter.DIFFERENT_FILES ->
            comparison.differentFiles.map { ComparisonItem.Different(it) }
        ComparisonViewFilter.IDENTICAL_FILES ->
            comparison.identicalFiles.map { ComparisonItem.Identical(it) }
    }

    LazyColumn {
        items(items) { item ->
            ComparisonItemCard(item = item)
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

sealed class ComparisonItem {
    data class MissingInDest(val file: FileInfo) : ComparisonItem()
    data class MissingInSource(val file: FileInfo) : ComparisonItem()
    data class Different(val difference: FileDifference) : ComparisonItem()
    data class Identical(val file: FileInfo) : ComparisonItem()
}

@Composable
fun ComparisonItemCard(item: ComparisonItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(4.dp),
        elevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
//            // Status icon
//            val (icon, color) = when (item) {
//                is ComparisonItem.MissingInDest -> Icons.Default.Warning to Color(0xFFE57373)
//                is ComparisonItem.MissingInSource -> Icons.Default.Info to Color(0xFFFFB74D)
//                is ComparisonItem.Different -> Icons.Default.Sync to Color(0xFF81C784)
//                is ComparisonItem.Identical -> Icons.Default.CheckCircle to Color(0xFF64B5F6)
//            }
//
//            Icon(
//                imageVector = icon,
//                contentDescription = null,
//                tint = color,
//                modifier = Modifier.size(20.dp)
//            )

            Spacer(modifier = Modifier.width(12.dp))

            // File info
            Column(modifier = Modifier.weight(1f)) {
                val fileName = when (item) {
                    is ComparisonItem.MissingInDest -> item.file.name
                    is ComparisonItem.MissingInSource -> item.file.name
                    is ComparisonItem.Different -> item.difference.sourceFile.name
                    is ComparisonItem.Identical -> item.file.name
                }

                Text(
                    text = fileName,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val subtitle = when (item) {
                    is ComparisonItem.MissingInDest -> "Missing in destination • ${formatFileSize(item.file.size)}"
                    is ComparisonItem.MissingInSource -> "Missing in source • ${formatFileSize(item.file.size)}"
                    is ComparisonItem.Different -> "Different: ${item.difference.differenceType.name.lowercase().replace('_', ' ')}"
                    is ComparisonItem.Identical -> "Identical • ${formatFileSize(item.file.size)}"
                }

                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

fun formatFileSize(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0

    return when {
        gb >= 1 -> String.format("%.1f GB", gb)
        mb >= 1 -> String.format("%.1f MB", mb)
        kb >= 1 -> String.format("%.1f KB", kb)
        else -> "$bytes B"
    }
}