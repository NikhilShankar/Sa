package com.ammakkutti.sa.domain.model

import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

data class VideoFile(
    val path: String,
    val name: String,
    val sizeBytes: Long,
    val originalFolder: String,
    val lastModified: Long,
    val extension: String
) {
    val sizeMB: Long get() = sizeBytes / (1024 * 1024)
    val isLarge: Boolean get() = sizeBytes > 1 * 1024 * 1024
    val isVideo: Boolean get() = extension.lowercase() in videoExtensions
    val isImage: Boolean get() = extension.lowercase() in imageExtensions
    val lastModifiedDate: LocalDateTime get() =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(lastModified), ZoneId.systemDefault())

    companion object {
        private val videoExtensions = setOf("mp4", "avi", "mkv", "mov", "wmv", "flv", "webm", "m4v")
        private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "tiff")

        fun fromFile(file: File, originalFolder: String): VideoFile? {
            val extension = file.extension
            return if (extension.lowercase() in videoExtensions || extension.lowercase() in imageExtensions) {
                VideoFile(
                    path = file.absolutePath,
                    name = file.name,
                    sizeBytes = file.length(),
                    originalFolder = originalFolder,
                    lastModified = file.lastModified(),
                    extension = extension
                )
            } else null
        }
    }
}