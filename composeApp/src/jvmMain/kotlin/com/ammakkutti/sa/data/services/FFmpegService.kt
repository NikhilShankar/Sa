package com.ammakkutti.sa.data.services

import ws.schild.jave.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ws.schild.jave.encode.AudioAttributes
import ws.schild.jave.encode.EncodingAttributes
import ws.schild.jave.encode.VideoAttributes
import ws.schild.jave.info.MultimediaInfo
import ws.schild.jave.progress.EncoderProgressListener
import java.io.File

class FFmpegService {

    suspend fun compressVideo(
        inputPath: String,
        outputPath: String,
        onProgress: (Float) -> Unit = {}
    ): CompressionResult = withContext(Dispatchers.IO) {

        try {
            val source = MultimediaObject(File(inputPath))
            val target = File(outputPath)

            // Create encoder (not converter)
            val encoder = Encoder()

            // Set up encoding attributes
            val audioAttributes = AudioAttributes().apply {
                setCodec("aac")
                setBitRate(128000)
                setSamplingRate(44100)
                setChannels(2)
            }

            val videoAttributes = VideoAttributes().apply {
                setCodec("libx265")
                setBitRate(1000000)
                setFrameRate(30)
            }

            val encodingAttributes = EncodingAttributes().apply {
                setOutputFormat("mp4")
                setAudioAttributes(audioAttributes)
                setVideoAttributes(videoAttributes)
            }

            // Encode with progress listener
            encoder.encode(source, target, encodingAttributes, object : EncoderProgressListener {
                override fun sourceInfo(info: MultimediaInfo?) {
                    println("Source info: ${info?.duration} ms")
                }

                override fun progress(permil: Int) {
                    val progressFloat = permil / 1000f
                    onProgress(progressFloat)
                }

                override fun message(message: String?) {
                    println("FFmpeg: $message")
                }
            })

            // Calculate results
            val inputFile = File(inputPath)
            val originalSize = inputFile.length()
            val compressedSize = target.length()
            val compressionRatio = (originalSize - compressedSize).toFloat() / originalSize

            CompressionResult.Success(
                originalSize = originalSize,
                compressedSize = compressedSize,
                compressionRatio = compressionRatio
            )

        } catch (e: Exception) {
            CompressionResult.Error("Compression failed: ${e.message}")
        }
    }
}

// Keep the same CompressionResult sealed class from before

sealed class CompressionResult {
    data class Success(
        val originalSize: Long,
        val compressedSize: Long,
        val compressionRatio: Float
    ) : CompressionResult()

    data class Error(val message: String) : CompressionResult()
}