package com.ammakkutti.sa.data.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes

class FolderComparisonService {

    suspend fun compareFolders(
        sourceFolder: String,
        destinationFolder: String,
        onProgress: (Float) -> Unit = {}
    ): FolderComparisonResult = withContext(Dispatchers.IO) {

        try {
            onProgress(0.1f)

            val sourceDir = File(sourceFolder)
            val destDir = File(destinationFolder)

            if (!sourceDir.exists() || !sourceDir.isDirectory) {
                return@withContext FolderComparisonResult.Error("Source folder does not exist or is not a directory")
            }

            if (!destDir.exists() || !destDir.isDirectory) {
                return@withContext FolderComparisonResult.Error("Destination folder does not exist or is not a directory")
            }

            onProgress(0.2f)

            // Get all files from both directories
            val sourceFiles = getAllFilesWithInfo(sourceDir)
            onProgress(0.5f)

            val destFiles = getAllFilesWithInfo(destDir)
            onProgress(0.8f)

            // Compare the file structures
            val comparison = performComparison(sourceDir, destDir, sourceFiles, destFiles)
            onProgress(1.0f)

            FolderComparisonResult.Success(comparison)

        } catch (e: Exception) {
            FolderComparisonResult.Error("Comparison failed: ${e.message}")
        }
    }

    private suspend fun getAllFilesWithInfo(rootDir: File): Map<String, FileInfo> = withContext(Dispatchers.IO) {
        val fileMap = mutableMapOf<String, FileInfo>()

        rootDir.walkTopDown().forEach { file ->
            if (file.isFile) {
                val relativePath = rootDir.toPath().relativize(file.toPath()).toString()
                val attributes = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java)

                fileMap[relativePath] = FileInfo(
                    name = file.name,
                    relativePath = relativePath,
                    absolutePath = file.absolutePath,
                    size = file.length(),
                    lastModified = file.lastModified(),
                    isDirectory = false,
                    checksum = calculateChecksum(file)
                )
            } else if (file.isDirectory && file != rootDir) {
                val relativePath = rootDir.toPath().relativize(file.toPath()).toString()
                fileMap[relativePath] = FileInfo(
                    name = file.name,
                    relativePath = relativePath,
                    absolutePath = file.absolutePath,
                    size = 0,
                    lastModified = file.lastModified(),
                    isDirectory = true
                )
            }
        }

        fileMap
    }

    private fun performComparison(
        sourceDir: File,
        destDir: File,
        sourceFiles: Map<String, FileInfo>,
        destFiles: Map<String, FileInfo>
    ): FolderComparison {

        val missingInDestination = mutableListOf<FileInfo>()
        val missingInSource = mutableListOf<FileInfo>()
        val differentFiles = mutableListOf<FileDifference>()
        val identicalFiles = mutableListOf<FileInfo>()

        // Find files missing in destination
        sourceFiles.forEach { (relativePath, fileInfo) ->
            val destFile = destFiles[relativePath]
            if (destFile == null) {
                missingInDestination.add(fileInfo)
            } else {
                // File exists in both, check if they're different
                if (filesAreDifferent(fileInfo, destFile)) {
                    differentFiles.add(
                        FileDifference(
                            relativePath = relativePath,
                            sourceFile = fileInfo,
                            destFile = destFile,
                            differenceType = determineDifferenceType(fileInfo, destFile)
                        )
                    )
                } else {
                    identicalFiles.add(fileInfo)
                }
            }
        }

        // Find files missing in source (extra files in destination)
        destFiles.forEach { (relativePath, fileInfo) ->
            if (!sourceFiles.containsKey(relativePath)) {
                missingInSource.add(fileInfo)
            }
        }

        return FolderComparison(
            sourceFolder = sourceDir.absolutePath,
            destinationFolder = destDir.absolutePath,
            totalSourceFiles = sourceFiles.size,
            totalDestFiles = destFiles.size,
            missingInDestination = missingInDestination,
            missingInSource = missingInSource,
            differentFiles = differentFiles,
            identicalFiles = identicalFiles,
            comparisonDate = System.currentTimeMillis()
        )
    }

    private fun filesAreDifferent(sourceFile: FileInfo, destFile: FileInfo): Boolean {
        // Compare by size first (quick check)
        if (sourceFile.size != destFile.size) return true

        // Compare by last modified time
        if (sourceFile.lastModified != destFile.lastModified) return true

        // Compare by checksum if available (most reliable but slower)
        if (sourceFile.checksum != null && destFile.checksum != null) {
            return sourceFile.checksum != destFile.checksum
        }

        return false
    }

    private fun determineDifferenceType(sourceFile: FileInfo, destFile: FileInfo): DifferenceType {
        return when {
            sourceFile.size != destFile.size -> DifferenceType.SIZE_DIFFERENT
            sourceFile.lastModified != destFile.lastModified -> DifferenceType.MODIFIED_DATE_DIFFERENT
            sourceFile.checksum != destFile.checksum -> DifferenceType.CONTENT_DIFFERENT
            else -> DifferenceType.IDENTICAL
        }
    }

    private fun calculateChecksum(file: File): String? {
        return try {
            // Simple checksum using file size + last modified time
            // You could implement a proper hash (MD5, SHA1) if needed
            "${file.length()}_${file.lastModified()}".hashCode().toString()
        } catch (e: Exception) {
            null
        }
    }
}

data class FileInfo(
    val name: String,
    val relativePath: String,
    val absolutePath: String,
    val size: Long,
    val lastModified: Long,
    val isDirectory: Boolean = false,
    val checksum: String? = null
)

data class FileDifference(
    val relativePath: String,
    val sourceFile: FileInfo,
    val destFile: FileInfo,
    val differenceType: DifferenceType
)

enum class DifferenceType {
    SIZE_DIFFERENT,
    MODIFIED_DATE_DIFFERENT,
    CONTENT_DIFFERENT,
    IDENTICAL
}

data class FolderComparison(
    val sourceFolder: String,
    val destinationFolder: String,
    val totalSourceFiles: Int,
    val totalDestFiles: Int,
    val missingInDestination: List<FileInfo>,
    val missingInSource: List<FileInfo>,
    val differentFiles: List<FileDifference>,
    val identicalFiles: List<FileInfo>,
    val comparisonDate: Long
) {
    val totalMissingInDestination = missingInDestination.size
    val totalMissingInSource = missingInSource.size
    val totalDifferentFiles = differentFiles.size
    val totalIdenticalFiles = identicalFiles.size

    val sourceSizeBytes = missingInDestination.sumOf { it.size }
    val destSizeBytes = missingInSource.sumOf { it.size }

    val hasDifferences = totalMissingInDestination > 0 || totalMissingInSource > 0 || totalDifferentFiles > 0
}

sealed class FolderComparisonResult {
    data class Success(val comparison: FolderComparison) : FolderComparisonResult()
    data class Error(val message: String) : FolderComparisonResult()
}