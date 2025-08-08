package com.ammakkutti.sa.presentation.convert

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.ammakkutti.sa.domain.model.ConversionHistory
import com.ammakkutti.sa.domain.model.VideoFile
import com.ammakkutti.sa.data.services.FolderComparison
import kotlinx.serialization.Serializable
import java.io.Serial

data class ConvertState(
    val selectedFolder: String? = null,
    val folderStructure: List<FolderNode> = emptyList(),
    val mediaFiles: List<VideoFile> = emptyList(),
    val isScanning: Boolean = false,
    val isConverting: Boolean = false,
    val conversionProgress: Float = 0f,
    val currentStatus: String? = null,
    val sortBy: SortOption = SortOption.SIZE_DESC,

    // Optimization dialog state
    val showOptimizationDialog: Boolean = false,
    val optimizationSettings: OptimizationSettings = OptimizationSettings(),

    // Detailed conversion statistics
    val conversionStats: ConversionStats = ConversionStats(),
    val processingStats: MutableList<FileConversionStats> = arrayListOf<FileConversionStats>(),

    // Keep stats after completion
    val hasCompletedConversion: Boolean = false,
    val lastConversionStats: ConversionStats? = null,

    // History viewing
    val showHistoryDialog: Boolean = false,
    val conversionHistory: ConversionHistory = ConversionHistory(),
    val isLoadingHistory: Boolean = false,

    // NEW: Folder comparison state
    val showFolderComparisonDialog: Boolean = false,
    val sourceComparisonFolder: String? = null,
    val destinationComparisonFolder: String? = null,
    val isComparingFolders: Boolean = false,
    val folderComparison: FolderComparison? = null,
    val comparisonProgress: Float = 0f,
    val showComparisonResults: Boolean = false,
    val comparisonViewFilter: ComparisonViewFilter = ComparisonViewFilter.ALL
)

// NEW: Filter options for comparison results
enum class ComparisonViewFilter {
    ALL,
    MISSING_IN_DESTINATION,
    MISSING_IN_SOURCE,
    DIFFERENT_FILES,
    IDENTICAL_FILES
}

data class ConversionStats(
    val totalFiles: Int = 0,
    val completedFiles: Int = 0,
    val currentFileIndex: Int = 0,

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
    val compressedBitrate: Long = 0,
    val conversionProgress: Float = 0f
)

enum class ConversionStatus {
    PENDING, IN_PROGRESS, COMPLETED, FAILED
}

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

@Serializable
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
    val imageQuality: Int = 90,

    // Other files settings
    val copyOtherFiles: Boolean = true // Copy documents, text files, etc.
)

@Serializable
enum class VideoQualityPreset(val displayName: String, val crf: Int, val preset: String) {
    HIGH_QUALITY("High Quality", 18, "slow"),
    BALANCED("Balanced", 23, "medium"),
    SMALL_SIZE("Small Size", 28, "fast")
}

@Serializable
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

    // Optimization dialog intents
    object ShowOptimizationDialog : ConvertIntent()
    object HideOptimizationDialog : ConvertIntent()
    data class UpdateOptimizationSettings(val settings: OptimizationSettings) : ConvertIntent()
    object StartOptimization : ConvertIntent()

    // Statistics tracking intents
    data class StartFileConversion(val fileStats: FileConversionStats) : ConvertIntent()
    data class UpdateFileProgress(val fileStats: FileConversionStats, val progress: Float) : ConvertIntent()
    data class CompleteFileConversion(val fileStats: FileConversionStats) : ConvertIntent()
    data class UpdateOverallStats(val stats: ConversionStats) : ConvertIntent()

    object ConversionCompleted: ConvertIntent()
    object ClearConversionResults: ConvertIntent()

    // History intents
    object ShowHistory : ConvertIntent()
    object HideHistory : ConvertIntent()
    object LoadHistory : ConvertIntent()
    data class HistoryLoaded(val history: ConversionHistory) : ConvertIntent()

    // NEW: Folder comparison intents
    object ShowFolderComparisonDialog : ConvertIntent()
    object HideFolderComparisonDialog : ConvertIntent()
    data class SelectSourceComparisonFolder(val path: String) : ConvertIntent()
    data class SelectDestinationComparisonFolder(val path: String) : ConvertIntent()
    object StartFolderComparison : ConvertIntent()
    data class ComparisonProgress(val progress: Float) : ConvertIntent()
    data class ComparisonCompleted(val comparison: FolderComparison) : ConvertIntent()
    data class ComparisonError(val message: String) : ConvertIntent()
    object ShowComparisonResults : ConvertIntent()
    object HideComparisonResults : ConvertIntent()
    data class ChangeComparisonFilter(val filter: ComparisonViewFilter) : ConvertIntent()
}