import com.ammakkutti.sa.data.services.CompressionResult
import com.ammakkutti.sa.data.services.ConversionHistoryService
import com.ammakkutti.sa.data.services.FFmpegService
import com.ammakkutti.sa.data.services.ImageCompressionResult
import com.ammakkutti.sa.data.services.ImageCompressionService
import com.ammakkutti.sa.domain.model.ConversionHistory
import com.ammakkutti.sa.domain.model.VideoFile
import com.ammakkutti.sa.presentation.convert.ConversionStats
import com.ammakkutti.sa.presentation.convert.ConversionStatus
import com.ammakkutti.sa.presentation.convert.ConvertIntent
import com.ammakkutti.sa.presentation.convert.ConvertState
import com.ammakkutti.sa.presentation.convert.FileConversionStats
import com.ammakkutti.sa.presentation.convert.FolderNode
import com.ammakkutti.sa.presentation.convert.ImageFormat
import com.ammakkutti.sa.presentation.convert.OptimizationSettings
import com.ammakkutti.sa.presentation.convert.SortOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import java.io.File
import javax.swing.JFileChooser

class ConvertViewModel {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val ffmpegService = FFmpegService() // Add this line
    private val historyService = ConversionHistoryService() // NEW
    private val _state = MutableStateFlow(ConvertState())
    val state: StateFlow<ConvertState> = _state.asStateFlow()
    private val imageCompressionService = ImageCompressionService() // NEW


    fun handleIntent(intent: ConvertIntent) {
        when (intent) {
            is ConvertIntent.SelectFolder -> selectFolder()
            is ConvertIntent.FolderSelected -> scanFolder(intent.path)
            is ConvertIntent.ToggleFolder -> toggleFolder(intent.folderPath)
            is ConvertIntent.ChangeSortOption -> changeSortOption(intent.sortOption)
            is ConvertIntent.StartConversion -> startConversion()
            is ConvertIntent.ConversionProgress -> updateProgress(intent.progress, intent.currentFile)

            // NEW: Optimization dialog handlers
            is ConvertIntent.ShowOptimizationDialog -> showOptimizationDialog()
            is ConvertIntent.HideOptimizationDialog -> hideOptimizationDialog()
            is ConvertIntent.UpdateOptimizationSettings -> updateOptimizationSettings(intent.settings)
            is ConvertIntent.StartOptimization -> startOptimization()

            // NEW: Statistics handlers
            is ConvertIntent.StartFileConversion -> startFileConversion(intent.fileStats)
            is ConvertIntent.UpdateFileProgress -> updateFileProgress(intent.progress)
            is ConvertIntent.CompleteFileConversion -> completeFileConversion(intent.fileStats)
            is ConvertIntent.UpdateOverallStats -> updateOverallStats(intent.stats)

            // NEW: Missing completion handling
            is ConvertIntent.ConversionCompleted -> conversionCompleted()
            is ConvertIntent.ClearConversionResults -> clearConversionResults()

            // NEW: History handling
            is ConvertIntent.ShowHistory -> showHistory()
            is ConvertIntent.HideHistory -> hideHistory()
            is ConvertIntent.LoadHistory -> loadHistory()
            is ConvertIntent.HistoryLoaded -> historyLoaded(intent.history)
        }
    }

    private fun selectFolder() {
        val fileChooser = JFileChooser()
        fileChooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        fileChooser.dialogTitle = "Select folder to scan"

        val result = fileChooser.showOpenDialog(null)
        if (result == JFileChooser.APPROVE_OPTION) {
            val selectedPath = fileChooser.selectedFile.absolutePath
            handleIntent(ConvertIntent.FolderSelected(selectedPath))
        }
    }

    private fun scanFolder(folderPath: String) {
        scope.launch(Dispatchers.IO) {
            _state.update { it.copy(isScanning = true, selectedFolder = folderPath) }

            try {
                val rootFolder = File(folderPath)
                val folderStructure = buildFolderTree(rootFolder)
                val mediaFiles = findAllMediaFiles(rootFolder)

                _state.update {
                    it.copy(
                        isScanning = false,
                        folderStructure = folderStructure,
                        mediaFiles = sortFiles(mediaFiles, it.sortBy)
                    )
                }
            } catch (e: Exception) {
                println("Error scanning folder: ${e.message}")
                _state.update { it.copy(isScanning = false) }
            }
        }
    }

    private suspend fun buildFolderTree(rootFolder: File): List<FolderNode> = withContext(Dispatchers.IO) {
        fun buildNode(folder: File, level: Int): FolderNode? {
            if (!folder.isDirectory) return null

            val children = folder.listFiles()
                ?.filter { it.isDirectory }
                ?.mapNotNull { buildNode(it, level + 1) }
                ?: emptyList()

            return FolderNode(
                name = folder.name,
                path = folder.absolutePath,
                children = children,
                level = level
            )
        }

        listOfNotNull(buildNode(rootFolder, 0))
    }

    private suspend fun findAllMediaFiles(rootFolder: File): List<VideoFile> = withContext(Dispatchers.IO) {
        rootFolder.walk()
            .filter { it.isFile }
            .mapNotNull { file ->
                val relativePath = rootFolder.toPath().relativize(file.parentFile.toPath()).toString()
                VideoFile.fromFile(file, relativePath)
            }
            .toList()
    }

    private fun sortFiles(files: List<VideoFile>, sortBy: SortOption): List<VideoFile> {
        return when (sortBy) {
            SortOption.SIZE_DESC -> files.sortedByDescending { it.sizeBytes }
            SortOption.SIZE_ASC -> files.sortedBy { it.sizeBytes }
            SortOption.DATE_DESC -> files.sortedByDescending { it.lastModified }
            SortOption.DATE_ASC -> files.sortedBy { it.lastModified }
            SortOption.NAME_ASC -> files.sortedBy { it.name }
            SortOption.NAME_DESC -> files.sortedByDescending { it.name }
        }
    }

    private fun toggleFolder(folderPath: String) {
        println("Toggle folder: $folderPath")
    }

    private fun changeSortOption(sortOption: SortOption) {
        _state.update {
            it.copy(
                sortBy = sortOption,
                mediaFiles = sortFiles(it.mediaFiles, sortOption)
            )
        }
    }

    private fun startConversion() {
        val currentState = _state.value
        if (currentState.mediaFiles.isEmpty()) return

        scope.launch {
            _state.update { it.copy(isConverting = true, conversionProgress = 0f) }

            try {
                // Filter only large video files for conversion
                val largeVideos = currentState.mediaFiles.filter {
                    it.isVideo && it.isLarge
                }

                for ((index, videoFile) in largeVideos.withIndex()) {
                    val outputPath = generateOutputPath(videoFile)

                    _state.update {
                        it.copy(currentFile = videoFile.name)
                    }

                    val result = ffmpegService.compressVideo(
                        inputPath = videoFile.path,
                        outputPath = outputPath,
                        onProgress = { progress ->
                            val overallProgress = (index + progress) / largeVideos.size
                            handleIntent(ConvertIntent.ConversionProgress(overallProgress, videoFile.name))
                        }
                    )

                    when (result) {
                        is CompressionResult.Success -> {
                            println("✅ Compressed ${videoFile.name}: ${result.originalSize / 1024 / 1024}MB → ${result.compressedSize / 1024 / 1024}MB")
                        }
                        is CompressionResult.Error -> {
                            println("❌ Failed to compress ${videoFile.name}: ${result.message}")
                        }
                    }
                }

                _state.update {
                    it.copy(
                        isConverting = false,
                        conversionProgress = 1f,
                        currentFile = "Conversion completed!"
                    )
                }

            } catch (e: Exception) {
                println("Conversion error: ${e.message}")
                _state.update { it.copy(isConverting = false) }
            }
        }
    }

    private fun generateOutputPath(videoFile: VideoFile): String {
        val currentState = _state.value
        val rootFolder = currentState.selectedFolder ?: return ""

        // Create compressed folder alongside the selected root folder
        val rootFile = File(rootFolder)
        val parentOfRoot = rootFile.parent
        val compressedRootPath = "$parentOfRoot/${rootFile.name}_compressed"

        // Get the relative path from original root to this file
        val originalFile = File(videoFile.path)
        val rootPath = File(rootFolder).toPath()
        val filePath = originalFile.toPath()
        val relativePath = rootPath.relativize(filePath)

        // Create the same folder structure in compressed directory
        val compressedFilePath = "$compressedRootPath/$relativePath"
        val compressedFile = File(compressedFilePath)

        // Ensure parent directories exist
        compressedFile.parentFile?.mkdirs()

        // Change extension to .mp4 and add _compressed suffix
        val nameWithoutExt = originalFile.nameWithoutExtension
        val compressedFileName = "${nameWithoutExt}_compressed.mp4"

        return "${compressedFile.parent}/$compressedFileName"
    }

    private fun updateProgress(progress: Float, currentFile: String) {
        _state.update {
            it.copy(
                conversionProgress = progress,
                currentFile = currentFile
            )
        }
    }

    private fun showOptimizationDialog() {
        _state.update { it.copy(showOptimizationDialog = true) }
    }

    private fun hideOptimizationDialog() {
        _state.update { it.copy(showOptimizationDialog = false) }
    }

    private fun updateOptimizationSettings(settings: OptimizationSettings) {
        _state.update { it.copy(optimizationSettings = settings) }
    }

    private fun startOptimization() {
        val currentState = _state.value

        // Hide dialog first
        _state.update { it.copy(showOptimizationDialog = false) }

        scope.launch {
            try {
                val settings = currentState.optimizationSettings
                val allFiles = currentState.mediaFiles

                // Filter files based on settings
                val videosToProcess = if (settings.enableVideoCompression) {
                    allFiles.filter { it.isVideo }
                } else emptyList()

                val imagesToProcess = if (settings.enableImageOptimization) {
                    allFiles.filter { it.isImage }
                } else emptyList()

                val filesToProcess = videosToProcess + imagesToProcess
                val totalOriginalSize = filesToProcess.sumOf { it.sizeBytes }

                // Initialize conversion statistics
                val initialStats = ConversionStats(
                    totalFiles = filesToProcess.size,
                    completedFiles = 0,
                    currentFileIndex = 0,
                    totalOriginalSize = totalOriginalSize,
                    conversionStartTime = System.currentTimeMillis()
                )

                _state.update {
                    it.copy(
                        isConverting = true,
                        conversionProgress = 0f,
                        conversionStats = initialStats
                    )
                }

                var totalCompressedSize = 0L
                val completedFiles = mutableListOf<FileConversionStats>()

                // Process each file
                filesToProcess.forEachIndexed { index, file ->
                    val fileStartTime = System.currentTimeMillis()

                    // Create initial file stats
                    val initialFileStats = FileConversionStats(
                        fileName = file.name,
                        originalSize = file.sizeBytes,
                        status = ConversionStatus.IN_PROGRESS
                    )

                    // Update state to show current file
                    handleIntent(ConvertIntent.StartFileConversion(initialFileStats))

                    if (file.isVideo) {
                        // Process video
                        val outputPath = generateOptimizedOutputPath(file, isVideo = true)

                        val result = ffmpegService.compressVideo(
                            inputPath = file.path,
                            outputPath = outputPath,
                            onProgress = { fileProgress ->
                                handleIntent(ConvertIntent.UpdateFileProgress(fileProgress))

                                // Update overall progress
                                val overallProgress = (index + fileProgress) / filesToProcess.size
                                _state.update {
                                    it.copy(conversionProgress = overallProgress)
                                }
                            }
                        )

                        // Complete file processing
                        val processingTime = System.currentTimeMillis() - fileStartTime
                        val completedFileStats = when (result) {
                            is CompressionResult.Success -> {
                                totalCompressedSize += result.compressedSize
                                FileConversionStats(
                                    fileName = file.name,
                                    originalSize = result.originalSize,
                                    compressedSize = result.compressedSize,
                                    compressionRatio = result.compressionRatio,
                                    processingTimeMs = processingTime,
                                    status = ConversionStatus.COMPLETED
                                )
                            }
                            is CompressionResult.Error -> {
                                FileConversionStats(
                                    fileName = file.name,
                                    originalSize = file.sizeBytes,
                                    processingTimeMs = processingTime,
                                    status = ConversionStatus.FAILED
                                )
                            }
                        }

                        completedFiles.add(completedFileStats)
                        handleIntent(ConvertIntent.CompleteFileConversion(completedFileStats))

                    } else {
                        // Process image - FIXED
                        _state.update { it.copy(currentFile = "Optimizing ${file.name}") }

                        val completedFileStats = processImageFile(file, settings) // Changed from imageFile to file

                        val processingTime = System.currentTimeMillis() - fileStartTime
                        val updatedStats = completedFileStats.copy(processingTimeMs = processingTime)

                        if (completedFileStats.status == ConversionStatus.COMPLETED) {
                            totalCompressedSize += completedFileStats.compressedSize
                        } else {
                            totalCompressedSize += file.sizeBytes // Changed from imageFile to file
                        }

                        completedFiles.add(updatedStats)
                        handleIntent(ConvertIntent.CompleteFileConversion(updatedStats))
                    }

                    // Update overall statistics
                    val spaceSaved = totalOriginalSize - totalCompressedSize
                    val avgCompressionRatio = if (completedFiles.isNotEmpty()) {
                        completedFiles.map { it.compressionRatio }.average().toFloat()
                    } else 0f

                    val updatedStats = _state.value.conversionStats.copy(
                        completedFiles = completedFiles.size,
                        currentFileIndex = index + 1,
                        totalCompressedSize = totalCompressedSize,
                        totalSpaceSaved = spaceSaved,
                        averageCompressionRatio = avgCompressionRatio,
                        completedFileStats = completedFiles.toList()
                    )

                    handleIntent(ConvertIntent.UpdateOverallStats(updatedStats))
                }

                // Conversion completed
                _state.update {
                    it.copy(
                        isConverting = false,
                        conversionProgress = 1f,
                        currentFile = "Optimization completed!"
                    )
                }

            } catch (e: Exception) {
                println("Optimization error: ${e.message}")
                _state.update { it.copy(isConverting = false) }
            }
        }
    }

    private fun generateOptimizedOutputPath(file: VideoFile, isVideo: Boolean, settings: OptimizationSettings? = null): String {
        val currentState = _state.value
        val rootFolder = currentState.selectedFolder ?: return ""

        val rootFile = File(rootFolder)
        val parentOfRoot = rootFile.parent
        val optimizedRootPath = "$parentOfRoot/${rootFile.name}_optimized"

        val originalFile = File(file.path)
        val rootPath = File(rootFolder).toPath()
        val filePath = originalFile.toPath()
        val relativePath = rootPath.relativize(filePath)

        val optimizedFilePath = "$optimizedRootPath/$relativePath"
        val optimizedFile = File(optimizedFilePath)
        optimizedFile.parentFile?.mkdirs()

        return if (isVideo) {
            val nameWithoutExt = originalFile.nameWithoutExtension
            "${optimizedFile.parent}/${nameWithoutExt}_optimized.mp4"
        } else {
            // Handle image format conversion
            val nameWithoutExt = originalFile.nameWithoutExtension
            val newExtension = when (settings?.imageFormat) {
                ImageFormat.JPEG -> "jpg"
                ImageFormat.AVIF -> "jpg" // For now, until we add proper AVIF support
                else -> originalFile.extension
            }
            "${optimizedFile.parent}/${nameWithoutExt}_optimized.$newExtension"
        }
    }

    // Remove the old copyImageFile method and replace with this:
    private suspend fun processImageFile(imageFile: VideoFile, settings: OptimizationSettings): FileConversionStats {
        val outputPath = generateOptimizedOutputPath(imageFile, isVideo = false, settings = settings)

        val result = imageCompressionService.compressImage(
            inputPath = imageFile.path,
            outputPath = outputPath,
            settings = settings,
            onProgress = { progress ->
                handleIntent(ConvertIntent.UpdateFileProgress(progress))
            }
        )

        return when (result) {
            is ImageCompressionResult.Success -> {
                FileConversionStats(
                    fileName = imageFile.name,
                    originalSize = result.originalSize,
                    compressedSize = result.compressedSize,
                    compressionRatio = result.compressionRatio,
                    status = ConversionStatus.COMPLETED
                )
            }
            is ImageCompressionResult.Error -> {
                FileConversionStats(
                    fileName = imageFile.name,
                    originalSize = imageFile.sizeBytes,
                    status = ConversionStatus.FAILED
                )
            }
        }
    }

    private fun startFileConversion(fileStats: FileConversionStats) {
        _state.update {
            it.copy(
                currentFile = "Processing ${fileStats.fileName}",
                conversionStats = it.conversionStats.copy(
                    currentFileStats = fileStats,
                    currentFileProgress = 0f
                )
            )
        }
    }

    private fun updateFileProgress(progress: Float) {
        _state.update {
            it.copy(
                conversionStats = it.conversionStats.copy(
                    currentFileProgress = progress
                )
            )
        }
    }

    private fun completeFileConversion(fileStats: FileConversionStats) {
        val currentState = _state.value
        val currentStats = currentState.conversionStats

        // Calculate total duration
        val durationMs = System.currentTimeMillis() - currentStats.conversionStartTime

        // Save to history
        scope.launch {
            try {
                val sourceFolder = currentState.selectedFolder ?: ""
                val outputFolder = generateOutputFolderPath(sourceFolder)

                historyService.saveConversionRecord(
                    sourceFolder = sourceFolder,
                    outputFolder = outputFolder,
                    stats = currentStats,
                    settings = currentState.optimizationSettings,
                    durationMs = durationMs
                )

                println("✅ Conversion record saved to history")

            } catch (e: Exception) {
                println("❌ Failed to save conversion record: ${e.message}")
            }
        }

        _state.update {
            it.copy(
                isConverting = false,
                conversionProgress = 1f,
                currentFile = "Optimization completed! Results saved to history.",
                hasCompletedConversion = true,
                lastConversionStats = currentStats
            )
        }
    }

    private fun generateOutputFolderPath(sourceFolder: String): String {
        val rootFile = File(sourceFolder)
        val parentOfRoot = rootFile.parent
        return "$parentOfRoot/${rootFile.name}_optimized"
    }

    private fun updateOverallStats(stats: ConversionStats) {
        _state.update {
            it.copy(conversionStats = stats)
        }
    }

    private fun showHistory() {
        _state.update { it.copy(showHistoryDialog = true) }
        loadHistory()
    }

    private fun hideHistory() {
        _state.update { it.copy(showHistoryDialog = false) }
    }

    private fun loadHistory() {
        _state.update { it.copy(isLoadingHistory = true) }

        scope.launch {
            try {
                val history = historyService.loadHistory()
                handleIntent(ConvertIntent.HistoryLoaded(history))
            } catch (e: Exception) {
                println("Error loading history: ${e.message}")
                _state.update { it.copy(isLoadingHistory = false) }
            }
        }
    }

    private fun historyLoaded(history: ConversionHistory) {
        _state.update {
            it.copy(
                conversionHistory = history,
                isLoadingHistory = false
            )
        }
    }


    // Completion methods
    private fun conversionCompleted() {
        val currentState = _state.value
        val currentStats = currentState.conversionStats

        println("🔍 DEBUG: conversionCompleted called")
        println("🔍 DEBUG: currentStats = $currentStats")

        // Calculate total duration
        val durationMs = System.currentTimeMillis() - currentStats.conversionStartTime

        // Save to history
        scope.launch {
            try {
                val sourceFolder = currentState.selectedFolder ?: ""
                val outputFolder = generateOutputFolderPath(sourceFolder)

                historyService.saveConversionRecord(
                    sourceFolder = sourceFolder,
                    outputFolder = outputFolder,
                    stats = currentStats,
                    settings = currentState.optimizationSettings,
                    durationMs = durationMs
                )

                println("✅ Conversion record saved to history")

            } catch (e: Exception) {
                println("❌ Failed to save conversion record: ${e.message}")
            }
        }

        _state.update {
            val newState = it.copy(
                isConverting = false,
                conversionProgress = 1f,
                currentFile = "Optimization completed! Results saved to history.",
                hasCompletedConversion = true,
                lastConversionStats = currentStats
            )
            println("🔍 DEBUG: Updated state - hasCompletedConversion = ${newState.hasCompletedConversion}")
            newState
        }
    }

    private fun clearConversionResults() {
        _state.update {
            it.copy(
                // Clear completion state
                hasCompletedConversion = false,
                lastConversionStats = null,
                conversionStats = ConversionStats(),

                // NEW: Reset to initial state for new conversion
                selectedFolder = null,
                mediaFiles = emptyList(),
                folderStructure = emptyList(),
                currentFile = "",
                conversionProgress = 0f,
                isConverting = false,
                isScanning = false
            )
        }
        println("🔍 DEBUG: Cleared conversion results, back to welcome screen")
    }

}