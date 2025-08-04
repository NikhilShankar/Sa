package com.ammakkutti.sa.domain.model

import com.ammakkutti.sa.presentation.convert.ConversionStats
import com.ammakkutti.sa.presentation.convert.FileConversionStats
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class ConversionHistory(
    val records: List<ConversionRecord> = emptyList()
)

data class ConversionRecord(
    val id: String,
    val timestamp: LocalDateTime,
    val sourceFolder: String,
    val outputFolder: String,
    val stats: ConversionStats,
    val settings: String, // JSON string of optimization settings
    val duration: Long, // Total conversion time in milliseconds
    val title: String // User-friendly title
) {
    val formattedDate: String get() = timestamp.format(DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm"))
    val totalSpaceSavedMB: Long get() = stats.totalSpaceSaved / (1024 * 1024)
    val compressionPercentage: Int get() = (stats.averageCompressionRatio * 100).toInt()
}