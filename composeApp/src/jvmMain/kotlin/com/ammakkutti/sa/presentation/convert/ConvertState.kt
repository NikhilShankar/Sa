package com.ammakkutti.sa.presentation.convert

import com.ammakkutti.sa.domain.model.ConversionHistory
import com.ammakkutti.sa.domain.model.VideoFile

data class ConvertState(
    val selectedFolder: String? = null,
    val folderStructure: List<FolderNode> = emptyList(),
    val mediaFiles: List<VideoFile> = emptyList(),
    val isScanning: Boolean = false,
    val isConverting: Boolean = false,
    val conversionProgress: Float = 0f,
    val currentFile: String = "",
    val sortBy: SortOption = SortOption.SIZE_DESC,

    // Optimization dialog state
    val showOptimizationDialog: Boolean = false,
    val optimizationSettings: OptimizationSettings = OptimizationSettings(),

    // NEW: Detailed conversion statistics
    val conversionStats: ConversionStats = ConversionStats(),

    // NEW: Keep stats after completion
    val hasCompletedConversion: Boolean = false,
    val lastConversionStats: ConversionStats? = null,

    // NEW: History viewing
    val showHistoryDialog: Boolean = false,
    val conversionHistory: ConversionHistory = ConversionHistory(),
    val isLoadingHistory: Boolean = false
)

data class ConversionStats(
    val totalFiles: Int = 0,
    val completedFiles: Int = 0,
    val currentFileIndex: Int = 0,
    val currentFileProgress: Float = 0f,

    // File being processed
    val currentFileStats: FileConversionStats? = null,

    // Completed files
    val completedFileStats: List<FileConversionStats> = emptyList(),

    // Overall statistics
    val totalOriginalSize: Long = 0,
    val totalCompressedSize: Long = 0,
    val estimatedTimeRemaining: Long = 0, // in seconds
    val conversionStartTime: Long = 0,

    // Space savings
    val totalSpaceSaved: Long = 0,
    val averageCompressionRatio: Float = 0f
)

data class FileConversionStats(
    val fileName: String,
    val originalSize: Long,
    val compressedSize: Long = 0,
    val compressionRatio: Float = 0f,
    val processingTimeMs: Long = 0,
    val status: ConversionStatus = ConversionStatus.PENDING,
    val videoDuration: Long = 0, // in seconds
    val videoResolution: String = "",
    val originalBitrate: Long = 0,
    val compressedBitrate: Long = 0
)

enum class ConversionStatus {
    PENDING, IN_PROGRESS, COMPLETED, FAILED
}

// ADD THESE MISSING DATA CLASSES:
data class FolderNode(
    val name: String,
    val path: String,
    val isExpanded: Boolean = false,
    val children: List<FolderNode> = emptyList(),
    val level: Int = 0
)

enum class SortOption {
    SIZE_DESC, SIZE_ASC, DATE_DESC, DATE_ASC, NAME_ASC, NAME_DESC
}

// Your existing OptimizationSettings and other classes...
data class OptimizationSettings(
    // Video settings
    val enableVideoCompression: Boolean = true,
    val videoQualityPreset: VideoQualityPreset = VideoQualityPreset.BALANCED,
    val customCRF: Int = 23,

    // Image settings
    val enableImageOptimization: Boolean = true,
    val imageFormat: ImageFormat = ImageFormat.JPEG,
    val resizeLargeImages: Boolean = true,
    val maxImageSize: Int = 2000,
    val imageQuality: Int = 90
)

enum class VideoQualityPreset(val displayName: String, val crf: Int, val preset: String) {
    HIGH_QUALITY("High Quality", 18, "slow"),
    BALANCED("Balanced", 23, "medium"),
    SMALL_SIZE("Small Size", 28, "fast")
}

enum class ImageFormat(val displayName: String, val extension: String) {
    KEEP_ORIGINAL("Keep Original", ""),
    JPEG("JPEG", "jpg"),
    AVIF("AVIF (Best compression)", "avif")
}

sealed class ConvertIntent {
    object SelectFolder : ConvertIntent()
    data class FolderSelected(val path: String) : ConvertIntent()
    data class ToggleFolder(val folderPath: String) : ConvertIntent()
    data class ChangeSortOption(val sortOption: SortOption) : ConvertIntent()
    object StartConversion : ConvertIntent()
    data class ConversionProgress(val progress: Float, val currentFile: String) : ConvertIntent()

    // NEW: Optimization dialog intents
    object ShowOptimizationDialog : ConvertIntent()
    object HideOptimizationDialog : ConvertIntent()
    data class UpdateOptimizationSettings(val settings: OptimizationSettings) : ConvertIntent()
    object StartOptimization : ConvertIntent()

    // NEW: Statistics tracking intents
    data class StartFileConversion(val fileStats: FileConversionStats) : ConvertIntent()
    data class UpdateFileProgress(val progress: Float) : ConvertIntent()
    data class CompleteFileConversion(val fileStats: FileConversionStats) : ConvertIntent()
    data class UpdateOverallStats(val stats: ConversionStats) : ConvertIntent()

    object ConversionCompleted: ConvertIntent()
    object ClearConversionResults: ConvertIntent()

    // NEW: History intents
    object ShowHistory : ConvertIntent()
    object HideHistory : ConvertIntent()
    object LoadHistory : ConvertIntent()
    data class HistoryLoaded(val history: ConversionHistory) : ConvertIntent()

}

