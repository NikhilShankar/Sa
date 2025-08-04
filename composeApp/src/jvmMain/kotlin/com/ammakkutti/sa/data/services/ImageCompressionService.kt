package com.ammakkutti.sa.data.services


import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.coobird.thumbnailator.Thumbnails
import net.coobird.thumbnailator.geometry.Positions
import java.io.File
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import com.ammakkutti.sa.presentation.convert.ImageFormat
import com.ammakkutti.sa.presentation.convert.OptimizationSettings

class ImageCompressionService {

    suspend fun compressImage(
        inputPath: String,
        outputPath: String,
        settings: OptimizationSettings,
        onProgress: (Float) -> Unit = {}
    ): ImageCompressionResult = withContext(Dispatchers.IO) {

        try {
            onProgress(0.1f)

            val inputFile = File(inputPath)
            val outputFile = File(outputPath)

            // Ensure output directory exists
            outputFile.parentFile?.mkdirs()

            onProgress(0.3f)

            when (settings.imageFormat) {
                ImageFormat.KEEP_ORIGINAL -> {
                    // Just optimize without changing format
                    optimizeImage(inputFile, outputFile, settings)
                }
                ImageFormat.JPEG -> {
                    // Convert to JPEG with quality setting
                    convertToJpeg(inputFile, outputFile, settings)
                }
                ImageFormat.AVIF -> {
                    // For now, convert to JPEG (AVIF support is complex)
                    // TODO: Add proper AVIF support later
                    convertToJpeg(inputFile, outputFile, settings)
                }
            }

            onProgress(0.9f)

            val originalSize = inputFile.length()
            val compressedSize = outputFile.length()
            val compressionRatio = (originalSize - compressedSize).toFloat() / originalSize

            onProgress(1.0f)

            ImageCompressionResult.Success(
                originalSize = originalSize,
                compressedSize = compressedSize,
                compressionRatio = compressionRatio
            )

        } catch (e: Exception) {
            ImageCompressionResult.Error("Image compression failed: ${e.message}")
        }
    }

    private fun optimizeImage(inputFile: File, outputFile: File, settings: OptimizationSettings) {
        var builder = Thumbnails.of(inputFile)

        // Resize if needed
        if (settings.resizeLargeImages) {
            val originalImage = ImageIO.read(inputFile)
            if (originalImage.width > settings.maxImageSize || originalImage.height > settings.maxImageSize) {
                builder = builder.size(settings.maxImageSize, settings.maxImageSize)
            } else {
                builder = builder.scale(1.0) // No scaling needed
            }
        } else {
            builder = builder.scale(1.0)
        }

        // Apply quality if it's a JPEG
        if (inputFile.extension.lowercase() in listOf("jpg", "jpeg")) {
            builder = builder.outputQuality(settings.imageQuality / 100.0)
        }

        builder.toFile(outputFile)
    }

    private fun convertToJpeg(inputFile: File, outputFile: File, settings: OptimizationSettings) {
        var builder = Thumbnails.of(inputFile)
            .outputFormat("jpg")
            .outputQuality(settings.imageQuality / 100.0)

        // Resize if needed
        if (settings.resizeLargeImages) {
            val originalImage = ImageIO.read(inputFile)
            if (originalImage.width > settings.maxImageSize || originalImage.height > settings.maxImageSize) {
                builder = builder.size(settings.maxImageSize, settings.maxImageSize)
            } else {
                builder = builder.scale(1.0)
            }
        } else {
            builder = builder.scale(1.0)
        }

        builder.toFile(outputFile)
    }
}

sealed class ImageCompressionResult {
    data class Success(
        val originalSize: Long,
        val compressedSize: Long,
        val compressionRatio: Float
    ) : ImageCompressionResult()

    data class Error(val message: String) : ImageCompressionResult()
}