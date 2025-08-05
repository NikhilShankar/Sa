package com.ammakkutti.sa.data.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class FileCopyService {

    suspend fun copyFile(
        inputPath: String,
        outputPath: String,
        onProgress: (Float) -> Unit = {}
    ): FileCopyResult = withContext(Dispatchers.IO) {

        try {
            onProgress(0.1f)

            val inputFile = File(inputPath)
            val outputFile = File(outputPath)

            // Ensure output directory exists
            outputFile.parentFile?.mkdirs()

            onProgress(0.3f)

            // Copy file preserving attributes
            Files.copy(
                inputFile.toPath(),
                outputFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.COPY_ATTRIBUTES
            )

            onProgress(1.0f)

            FileCopyResult.Success(
                originalSize = inputFile.length(),
                copiedSize = outputFile.length()
            )

        } catch (e: Exception) {
            FileCopyResult.Error("File copy failed: ${e.message}")
        }
    }
}

sealed class FileCopyResult {
    data class Success(
        val originalSize: Long,
        val copiedSize: Long
    ) : FileCopyResult()

    data class Error(val message: String) : FileCopyResult()
}