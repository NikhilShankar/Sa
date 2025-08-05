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
    val isLarge: Boolean get() = sizeBytes > 50 * 1024 * 1024
    val isVideo: Boolean get() = extension.lowercase() in videoExtensions
    val isImage: Boolean get() = extension.lowercase() in imageExtensions
    val isOtherFile: Boolean get() = !isVideo && !isImage // NEW: All other files
    val lastModifiedDate: LocalDateTime get() =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(lastModified), ZoneId.systemDefault())

    val fileType: FileType get() = when {
        isVideo -> FileType.VIDEO
        isImage -> FileType.IMAGE
        else -> FileType.OTHER
    }

    companion object {
        private val videoExtensions = setOf("mp4", "avi", "mkv", "mov", "wmv", "flv", "webm", "m4v", "3gp", "mpg", "mpeg")
        private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "tiff", "svg", "ico", "heic", "raw")

        fun fromFile(file: File, originalFolder: String): VideoFile {
            // Accept ALL files now, not just media files
            return VideoFile(
                path = file.absolutePath,
                name = file.name,
                sizeBytes = file.length(),
                originalFolder = originalFolder,
                lastModified = file.lastModified(),
                extension = file.extension.ifEmpty { "unknown" }
            )
        }
    }
}

enum class FileType {
    VIDEO, IMAGE, OTHER
}