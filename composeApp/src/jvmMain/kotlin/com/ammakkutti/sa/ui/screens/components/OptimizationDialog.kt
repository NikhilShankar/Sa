package com.ammakkutti.sa.ui.screens.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ammakkutti.sa.presentation.convert.ImageFormat
import com.ammakkutti.sa.presentation.convert.OptimizationSettings
import com.ammakkutti.sa.presentation.convert.VideoQualityPreset

@Composable
fun OptimizationDialog(
    settings: OptimizationSettings,
    videoCount: Int,
    imageCount: Int,
    otherFileCount: Int,
    onSettingsChange: (OptimizationSettings) -> Unit,
    onStartOptimization: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Card(
            modifier = Modifier
                .width(600.dp)
                .heightIn(max = 700.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Text(
                    text = "Optimization Settings",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Video Compression Section
                VideoCompressionSection(
                    settings = settings,
                    videoCount = videoCount,
                    onSettingsChange = onSettingsChange
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                // Image Optimization Section
                ImageOptimizationSection(
                    settings = settings,
                    imageCount = imageCount,
                    onSettingsChange = onSettingsChange
                )

                // Add this after the ImageOptimizationSection in OptimizationDialog
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

// Other Files Section
                OtherFilesSection(
                    settings = settings,
                    otherFileCount = otherFileCount, // You'll need to pass this count
                    onSettingsChange = onSettingsChange
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = onStartOptimization,
                        enabled = settings.enableVideoCompression || settings.enableImageOptimization
                    ) {
                        Text("Start Optimization")
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoCompressionSection(
    settings: OptimizationSettings,
    videoCount: Int,
    onSettingsChange: (OptimizationSettings) -> Unit
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = settings.enableVideoCompression,
                onCheckedChange = {
                    onSettingsChange(settings.copy(enableVideoCompression = it))
                }
            )
            Text(
                text = "Video Compression ($videoCount videos)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
        }

        if (settings.enableVideoCompression) {
            Column(
                modifier = Modifier.padding(start = 32.dp, top = 8.dp)
            ) {
                Text("Quality Preset:")

                VideoQualityPreset.values().forEach { preset ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = settings.videoQualityPreset == preset,
                            onClick = {
                                onSettingsChange(settings.copy(videoQualityPreset = preset))
                            }
                        )
                        Text(preset.displayName)
                    }
                }

                Text(
                    text = "All videos will be converted to H.265/MP4 format",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ImageOptimizationSection(
    settings: OptimizationSettings,
    imageCount: Int,
    onSettingsChange: (OptimizationSettings) -> Unit
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = settings.enableImageOptimization,
                onCheckedChange = {
                    onSettingsChange(settings.copy(enableImageOptimization = it))
                }
            )
            Text(
                text = "Image Optimization ($imageCount images)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
        }

        if (settings.enableImageOptimization) {
            Column(
                modifier = Modifier.padding(start = 32.dp, top = 8.dp)
            ) {
                Text("Output Format:")

                ImageFormat.values().forEach { format ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = settings.imageFormat == format,
                            onClick = {
                                onSettingsChange(settings.copy(imageFormat = format))
                            }
                        )
                        Text(format.displayName)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = settings.resizeLargeImages,
                        onCheckedChange = {
                            onSettingsChange(settings.copy(resizeLargeImages = it))
                        }
                    )
                    Text("Resize large images to ${settings.maxImageSize}px max")
                }

                if (settings.imageFormat != ImageFormat.KEEP_ORIGINAL) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Quality: ${settings.imageQuality}%")
                    Slider(
                        value = settings.imageQuality.toFloat(),
                        onValueChange = {
                            onSettingsChange(settings.copy(imageQuality = it.toInt()))
                        },
                        valueRange = 70f..100f,
                        steps = 6
                    )
                }

                if (settings.imageFormat == ImageFormat.AVIF) {
                    Text(
                        text = "⚠️ AVIF offers best compression but may not be viewable in all applications",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun OtherFilesSection(
    settings: OptimizationSettings,
    otherFileCount: Int,
    onSettingsChange: (OptimizationSettings) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = settings.copyOtherFiles,
            onCheckedChange = {
                onSettingsChange(settings.copy(copyOtherFiles = it))
            }
        )
        Text(
            text = "Copy Other Files ($otherFileCount files)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )
    }

    if (settings.copyOtherFiles) {
        Text(
            text = "Documents, text files, and other non-media files will be copied as-is",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 32.dp, top = 4.dp)
        )
    }
}