package com.ammakkutti.sa.data.services

import com.ammakkutti.sa.domain.model.ConversionHistory
import com.ammakkutti.sa.domain.model.ConversionRecord
import com.ammakkutti.sa.presentation.convert.ConversionStats
import com.ammakkutti.sa.presentation.convert.OptimizationSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.util.*
import kotlinx.serialization.*
import kotlinx.serialization.json.Json

@Serializable
data class SerializableConversionRecord(
    val id: String,
    val timestamp: String,
    val sourceFolder: String,
    val outputFolder: String,
    val totalFiles: Int,
    val completedFiles: Int,
    val totalOriginalSizeMB: Long,
    val totalCompressedSizeMB: Long,
    val totalSpaceSavedMB: Long,
    val averageCompressionRatio: Float,
    val durationMs: Long,
    val title: String,
    val settingsJson: String
)

class ConversionHistoryService {
    private val historyFile = File(System.getProperty("user.home"), ".mediaconverter/conversion_history.json")
    private val json = Json { prettyPrint = true }

    init {
        // Ensure directory exists
        historyFile.parentFile?.mkdirs()
    }

    suspend fun loadHistory(): ConversionHistory = withContext(Dispatchers.IO) {
        try {
            if (!historyFile.exists()) {
                return@withContext ConversionHistory()
            }

            val jsonContent = historyFile.readText()
            val serializableRecords = json.decodeFromString<List<SerializableConversionRecord>>(jsonContent)

            val records = serializableRecords.map { it.toConversionRecord() }
            ConversionHistory(records)

        } catch (e: Exception) {
            println("Error loading conversion history: ${e.message}")
            ConversionHistory()
        }
    }

    suspend fun saveConversionRecord(
        sourceFolder: String,
        outputFolder: String,
        stats: ConversionStats,
        settings: OptimizationSettings,
        durationMs: Long
    ) = withContext(Dispatchers.IO) {
        try {
            val currentHistory = loadHistory()

            val newRecord = ConversionRecord(
                id = UUID.randomUUID().toString(),
                timestamp = LocalDateTime.now(),
                sourceFolder = sourceFolder,
                outputFolder = outputFolder,
                stats = stats,
                settings = serializeSettings(settings),
                duration = durationMs,
                title = generateTitle(sourceFolder, stats)
            )

            val updatedRecords = listOf(newRecord) + currentHistory.records
            val updatedHistory = ConversionHistory(updatedRecords)

            saveHistory(updatedHistory)

        } catch (e: Exception) {
            println("Error saving conversion record: ${e.message}")
        }
    }

    private suspend fun saveHistory(history: ConversionHistory) {
        val serializableRecords = history.records.map { it.toSerializable() }
        val jsonContent = json.encodeToString(serializableRecords)
        historyFile.writeText(jsonContent)
    }

    private fun generateTitle(sourceFolder: String, stats: ConversionStats): String {
        val folderName = File(sourceFolder).name
        val fileCount = stats.totalFiles
        val spaceSavedMB = stats.totalSpaceSaved / (1024 * 1024)
        return "$folderName - $fileCount files, ${spaceSavedMB}MB saved"
    }

    private fun serializeSettings(settings: OptimizationSettings): String {
        return json.encodeToString(settings)
    }

    // Extension functions for conversion
    private fun SerializableConversionRecord.toConversionRecord(): ConversionRecord {
        val stats = ConversionStats(
            totalFiles = totalFiles,
            completedFiles = completedFiles,
            totalOriginalSize = totalOriginalSizeMB * 1024 * 1024,
            totalCompressedSize = totalCompressedSizeMB * 1024 * 1024,
            totalSpaceSaved = totalSpaceSavedMB * 1024 * 1024,
            averageCompressionRatio = averageCompressionRatio
        )

        return ConversionRecord(
            id = id,
            timestamp = LocalDateTime.parse(timestamp),
            sourceFolder = sourceFolder,
            outputFolder = outputFolder,
            stats = stats,
            settings = settingsJson,
            duration = durationMs,
            title = title
        )
    }

    private fun ConversionRecord.toSerializable(): SerializableConversionRecord {
        return SerializableConversionRecord(
            id = id,
            timestamp = timestamp.toString(),
            sourceFolder = sourceFolder,
            outputFolder = outputFolder,
            totalFiles = stats.totalFiles,
            completedFiles = stats.completedFiles,
            totalOriginalSizeMB = stats.totalOriginalSize / (1024 * 1024),
            totalCompressedSizeMB = stats.totalCompressedSize / (1024 * 1024),
            totalSpaceSavedMB = stats.totalSpaceSaved / (1024 * 1024),
            averageCompressionRatio = stats.averageCompressionRatio,
            durationMs = duration,
            title = title,
            settingsJson = settings
        )
    }
}