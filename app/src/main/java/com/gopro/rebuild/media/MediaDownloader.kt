package com.gopro.rebuild.media

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class DownloadProgress(
    val filename: String,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val isComplete: Boolean = false,
    val error: String? = null
) {
    val progress: Float
        get() = if (totalBytes > 0) bytesDownloaded.toFloat() / totalBytes else 0f
}

@Singleton
class MediaDownloader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val client = OkHttpClient()
    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    /**
     * Download with progress tracking
     */
    fun downloadWithProgress(
        url: String,
        filename: String,
        destDir: File = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
    ): Flow<DownloadProgress> = flow {
        val destFile = File(destDir, "GoPro/$filename")
        destFile.parentFile?.mkdirs()

        try {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                emit(DownloadProgress(filename, 0, 0, false, "HTTP ${response.code}"))
                return@flow
            }

            val body = response.body ?: run {
                emit(DownloadProgress(filename, 0, 0, false, "Empty response"))
                return@flow
            }

            val totalBytes = body.contentLength()
            var bytesDownloaded = 0L

            FileOutputStream(destFile).use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(8192)
                    var bytes: Int

                    while (input.read(buffer).also { bytes = it } != -1) {
                        output.write(buffer, 0, bytes)
                        bytesDownloaded += bytes
                        emit(DownloadProgress(filename, bytesDownloaded, totalBytes))
                    }
                }
            }

            emit(DownloadProgress(filename, bytesDownloaded, totalBytes, isComplete = true))

        } catch (e: Exception) {
            emit(DownloadProgress(filename, 0, 0, false, e.message))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Use system DownloadManager (shows in notification)
     */
    fun downloadWithManager(url: String, filename: String): Long {
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(filename)
            .setDescription("Downloading from GoPro")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DCIM, "GoPro/$filename")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)

        return downloadManager.enqueue(request)
    }

    /**
     * Check download status
     */
    fun getDownloadStatus(downloadId: Long): DownloadProgress? {
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query)

        return if (cursor.moveToFirst()) {
            val bytesDownloaded = cursor.getLong(
                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            )
            val totalBytes = cursor.getLong(
                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            )
            val status = cursor.getInt(
                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
            )

            cursor.close()

            DownloadProgress(
                filename = "",
                bytesDownloaded = bytesDownloaded,
                totalBytes = totalBytes,
                isComplete = status == DownloadManager.STATUS_SUCCESSFUL,
                error = if (status == DownloadManager.STATUS_FAILED) "Download failed" else null
            )
        } else {
            cursor.close()
            null
        }
    }
}
