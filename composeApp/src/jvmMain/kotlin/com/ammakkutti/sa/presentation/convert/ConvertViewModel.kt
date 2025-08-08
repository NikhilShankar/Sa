import com.ammakkutti.sa.data.services.CompressionResult
import com.ammakkutti.sa.data.services.ConversionHistoryService
import com.ammakkutti.sa.data.services.FFmpegService
import com.ammakkutti.sa.data.services.FileCopyResult
import com.ammakkutti.sa.data.services.FileCopyService
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import java.io.File
import javax.swing.JFileChooser

class ConvertViewModel {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val ffmpegService = FFmpegService() // Add this line
    private val historyService = ConversionHistoryService() // NEW
    private val fileCopyService = FileCopyService() // NEW

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
            is ConvertIntent.UpdateFileProgress -> updateFileProgress(intent.fileStats, intent.progress)
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
                        it.copy(currentStatus = videoFile.name)
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
                        currentStatus = "Conversion completed!"
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
                currentStatus = currentFile
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

                // NEW: Other files to copy
                val otherFilesToCopy = if (settings.copyOtherFiles) {
                    allFiles.filter { it.isOtherFile }
                } else emptyList()

                val filesToProcess = videosToProcess + imagesToProcess + otherFilesToCopy
                val totalOriginalSize = filesToProcess.sumOf { it.sizeBytes }

                println("🔍 DEBUG: Starting optimization - ${videosToProcess.size} videos, ${imagesToProcess.size} images")

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

                // Process files in parallel batches
                val batchSize = determineBatchSize(videosToProcess.size, imagesToProcess.size)
                println("🔍 DEBUG: Using batch size: $batchSize")

                val completedFiles = mutableListOf<FileConversionStats>()
                var totalCompressedSize = 0L

                // Process all files in batches
                filesToProcess.chunked(batchSize).forEachIndexed { batchIndex, batch ->
                    println("🔍 DEBUG: Processing batch ${batchIndex + 1}/${(filesToProcess.size + batchSize - 1) / batchSize}")

                    // Process current batch in parallel
                    val batchResults = batch.mapIndexed { fileIndexInBatch, file ->
                        async(Dispatchers.IO) {
                            val globalIndex = batchIndex * batchSize + fileIndexInBatch
                            processFileWithProgress(file, settings, globalIndex, filesToProcess.size)
                        }
                    }.awaitAll()

                    // Update results after each batch completes
                    completedFiles.addAll(batchResults)
                    totalCompressedSize += batchResults.sumOf { it.compressedSize }

                    // Update overall statistics
                    val spaceSaved = totalOriginalSize - totalCompressedSize
                    val avgCompressionRatio = if (completedFiles.isNotEmpty()) {
                        completedFiles.filter { it.compressionRatio > 0 }.map { it.compressionRatio }.average().toFloat()
                    } else 0f

                    val timeEstimate = calculateTimeEstimate(
                        completedFiles.size,
                        filesToProcess.size,
                        initialStats.conversionStartTime
                    )

                    val updatedStats = _state.value.conversionStats.copy(
                        completedFiles = completedFiles.size,
                        currentFileIndex = completedFiles.size,
                        totalCompressedSize = totalCompressedSize,
                        totalSpaceSaved = spaceSaved,
                        averageCompressionRatio = avgCompressionRatio,
                        completedFileStats = completedFiles.toList(),
                        estimatedTimeRemaining = timeEstimate
                    )

                    handleIntent(ConvertIntent.UpdateOverallStats(updatedStats))

                    // Update overall progress
                    val overallProgress = completedFiles.size.toFloat() / filesToProcess.size
                    _state.update { it.copy(conversionProgress = overallProgress) }

                    println("🔍 DEBUG: Batch ${batchIndex + 1} completed. Total completed: ${completedFiles.size}/${filesToProcess.size}")
                }

                // ALL FILES PROCESSED - Now complete the conversion
                println("🔍 DEBUG: All files processed, calling conversionCompleted")
                handleIntent(ConvertIntent.ConversionCompleted)

            } catch (e: Exception) {
                println("Optimization error: ${e.message}")
                e.printStackTrace()
                _state.update { it.copy(isConverting = false) }
            }
        }
    }

    // NEW: Determine optimal batch size based on file types and system resources
    private fun determineBatchSize(videoCount: Int, imageCount: Int): Int {
        val availableProcessors = Runtime.getRuntime().availableProcessors()

        return when {
            // Videos are CPU/GPU intensive - smaller batches
            videoCount > imageCount -> minOf(2, availableProcessors / 2, videoCount).coerceAtLeast(1)

            // Images are lighter - larger batches
            imageCount > videoCount -> minOf(6, availableProcessors, imageCount).coerceAtLeast(1)

            // Mixed workload - balanced batch size
            else -> minOf(4, availableProcessors, videoCount + imageCount).coerceAtLeast(1)
        }
    }

    // NEW: Process individual file with progress tracking
    private suspend fun processFileWithProgress(
        file: VideoFile,
        settings: OptimizationSettings,
        globalIndex: Int,
        totalFiles: Int
    ): FileConversionStats = withContext(Dispatchers.IO) {

        val fileStartTime = System.currentTimeMillis()

        println("🔍 DEBUG: Processing file ${globalIndex + 1}/$totalFiles: ${file.name}")

        // Create initial file stats
        val initialFileStats = FileConversionStats(
            fileName = file.name,
            originalSize = file.sizeBytes,
            status = ConversionStatus.IN_PROGRESS
        )

        // Update state to show current file (this might race, but it's okay for UI)
        launch(Dispatchers.Main) {
            handleIntent(ConvertIntent.StartFileConversion(initialFileStats))
        }

        val result = when {
            file.isVideo -> {
                // Process video
                val outputPath = generateOptimizedOutputPath(file, isVideo = true, settings = settings)

                val compressionResult = ffmpegService.compressVideo(
                    inputPath = file.path,
                    outputPath = outputPath,
                    onProgress = { fileProgress ->
                        launch(Dispatchers.Main) {
                            handleIntent(ConvertIntent.UpdateFileProgress(initialFileStats, fileProgress))
                        }
                    }
                )

                val processingTime = System.currentTimeMillis() - fileStartTime
                when (compressionResult) {
                    is CompressionResult.Success -> {
                        FileConversionStats(
                            fileName = file.name,
                            originalSize = compressionResult.originalSize,
                            compressedSize = compressionResult.compressedSize,
                            compressionRatio = compressionResult.compressionRatio,
                            processingTimeMs = processingTime,
                            status = ConversionStatus.COMPLETED
                        )
                    }
                    is CompressionResult.Error -> {
                        FileConversionStats(
                            fileName = file.name,
                            originalSize = file.sizeBytes,
                            compressedSize = file.sizeBytes,
                            processingTimeMs = processingTime,
                            status = ConversionStatus.FAILED
                        )
                    }
                }
            }

            file.isImage -> {
                // Process image
                launch(Dispatchers.Main) {
                    _state.update { it.copy(currentStatus = "Optimizing ${file.name}") }
                }

                val imageResult = processImageFile(file, settings)
                val processingTime = System.currentTimeMillis() - fileStartTime
                imageResult.copy(processingTimeMs = processingTime)
            }

            else -> {
                // NEW: Copy other files
                launch(Dispatchers.Main) {
                    _state.update { it.copy(currentStatus = "Copying ${file.name}") }
                }

                val copyResult = copyOtherFile(file, settings)
                val processingTime = System.currentTimeMillis() - fileStartTime
                copyResult.copy(processingTimeMs = processingTime)
            }
        }

        println("🔍 DEBUG: Completed ${file.fileType} file ${globalIndex + 1}/$totalFiles: ${result.status} - ${result.fileName}")

        result
    }

    // NEW: Method to copy other files
    private suspend fun copyOtherFile(file: VideoFile, settings: OptimizationSettings): FileConversionStats {
        val outputPath = generateOptimizedOutputPath(file, isVideo = false, settings = settings)

        val result = fileCopyService.copyFile(
            inputPath = file.path,
            outputPath = outputPath,
            onProgress = { progress ->
            }
        )

        return when (result) {
            is FileCopyResult.Success -> {
                FileConversionStats(
                    fileName = file.name,
                    originalSize = result.originalSize,
                    compressedSize = result.copiedSize,
                    compressionRatio = 0f, // No compression for copied files
                    status = ConversionStatus.COMPLETED
                )
            }
            is FileCopyResult.Error -> {
                FileConversionStats(
                    fileName = file.name,
                    originalSize = file.sizeBytes,
                    status = ConversionStatus.FAILED
                )
            }
        }
    }

    // Time estimation method
    private fun calculateTimeEstimate(
        completedFiles: Int,
        totalFiles: Int,
        startTime: Long
    ): Long {
        if (completedFiles == 0) return 0

        val elapsedTime = System.currentTimeMillis() - startTime
        val averageTimePerFile = elapsedTime / completedFiles
        val remainingFiles = totalFiles - completedFiles

        return (remainingFiles * averageTimePerFile) / 1000 // Convert to seconds
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
            "${optimizedFile.parent}/${nameWithoutExt}.mp4"
        } else if(file.isImage) {
            // Handle image format conversion
            val nameWithoutExt = originalFile.nameWithoutExtension
            val newExtension = when (settings?.imageFormat) {
                ImageFormat.JPEG -> "jpg"
                ImageFormat.AVIF -> "jpg" // For now, until we add proper AVIF support
                else -> originalFile.extension
            }
            "${optimizedFile.parent}/${nameWithoutExt}.$newExtension"
        } else {
            val nameWithoutExt = originalFile.nameWithoutExtension
            val newExtension = originalFile.extension
            "${optimizedFile.parent}/${nameWithoutExt}.$newExtension"
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
                currentStatus = "Processing ${fileStats.fileName}",
                processingStats = it.processingStats.also {

                    it.add(fileStats.copy(conversionProgress = 0f))
                },
                conversionStats = it.conversionStats.copy(
                    currentFileStats = fileStats,
                )
            )
        }
    }

    private fun updateFileProgress(fileStats: FileConversionStats, progress: Float) {
        val updatedStats = _state.value.processingStats.map { stat ->
            if (stat.fileName == fileStats.fileName) {
                stat.copy(conversionProgress = progress)
            } else {
                stat
            }
        }

        _state.value = _state.value.copy(processingStats = updatedStats as MutableList<FileConversionStats>)
    }

    private fun completeFileConversion(fileStats: FileConversionStats) {
        val currentState = _state.value
        val currentStats = currentState.conversionStats

        _state.update {
            it.copy(
                processingStats = it.processingStats.also {
                    it.remove(fileStats)
                }
            )
        }

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
                currentStatus = "Optimization completed! Results saved to history.",
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
                currentStatus = "Optimization completed! Results saved to history.",
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
                currentStatus = "",
                conversionProgress = 0f,
                isConverting = false,
                isScanning = false
            )
        }
        println("🔍 DEBUG: Cleared conversion results, back to welcome screen")
    }

}