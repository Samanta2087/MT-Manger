package com.mtmanager.lite.utils

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.io.File

object UriUtils {

    fun getPathFromUri(context: Context, uri: Uri): String? {
        if (DocumentsContract.isDocumentUri(context, uri)) {
            if (isExternalStorageDocument(uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":")
                val type = split[0]
                if ("primary".equals(type, ignoreCase = true)) {
                    return Environment.getExternalStorageDirectory().toString() + "/" + split[1]
                }
            } else if (isDownloadsDocument(uri)) {
                val id = DocumentsContract.getDocumentId(uri)
                if (id.startsWith("raw:")) {
                    return id.replaceFirst("raw:", "")
                }
                try {
                    val contentUri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"), java.lang.Long.valueOf(id)
                    )
                    return getDataColumn(context, contentUri, null, null)
                } catch (e: Exception) {
                    return null
                }
            } else if (isMediaDocument(uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":")
                val type = split[0]

                var contentUri: Uri? = null
                when (type) {
                    "image" -> contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    "video" -> contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    "audio" -> contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }

                val selection = "_id=?"
                val selectionArgs = arrayOf(split[1])
                return getDataColumn(context, contentUri, selection, selectionArgs)
            }
        } else if ("content".equals(uri.scheme, ignoreCase = true)) {
            // First try the standard _data column
            val path = getDataColumn(context, uri, null, null)
            if (path != null) return path

            // Second, try the ultimate FD symlink trick (works for many local file providers)
            val fdPath = getPathViaFd(context, uri)
            if (fdPath != null && File(fdPath).exists()) {
                return fdPath
            }

            // Third, aggressively try to extract a path from the URI itself
            val uriPath = uri.path
            if (uriPath != null) {
                val decodedPath = Uri.decode(uriPath)
                val externalRoot = Environment.getExternalStorageDirectory().absolutePath

                val segments = decodedPath.split("/")
                for (i in segments.indices) {
                    val possibleSuffix = segments.subList(i, segments.size).joinToString("/")
                    if (possibleSuffix.isBlank()) continue

                    val fileOnExt = File(externalRoot, possibleSuffix)
                    if (fileOnExt.exists()) return fileOnExt.absolutePath

                    val fileOnRoot = File("/", possibleSuffix)
                    if (fileOnRoot.exists()) return fileOnRoot.absolutePath
                }
            }

            // Last resort: copy the content URI to a temporary file
            val tempPath = createTempFileFromUri(context, uri)
            if (tempPath != null) return tempPath

            return null
        } else if ("file".equals(uri.scheme, ignoreCase = true)) {
            return uri.path
        }
        return null
    }

    private fun getPathViaFd(context: Context, uri: Uri): String? {
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val fd = pfd.fd
                val procFile = File("/proc/self/fd/$fd")
                if (procFile.exists()) {
                    val realPath = android.system.Os.readlink(procFile.absolutePath)
                    // Ensure the resolved path is a real file and not an anonymous inode
                    if (realPath != null && realPath.startsWith("/")) {
                        return realPath
                    }
                }
            }
        } catch (e: Exception) {
            // ignore
        }
        return null
    }

    private fun getDataColumn(context: Context, uri: Uri?, selection: String?, selectionArgs: Array<String>?): String? {
        if (uri == null) return null
        var cursor: Cursor? = null
        val column = "_data"
        val projection = arrayOf(column)
        try {
            cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, null)
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndexOrThrow(column)
                return cursor.getString(index)
            }
        } catch (e: Exception) {
            // ignore
        } finally {
            cursor?.close()
        }
        return null
    }

    private fun isExternalStorageDocument(uri: Uri): Boolean = "com.android.externalstorage.documents" == uri.authority
    private fun isDownloadsDocument(uri: Uri): Boolean = "com.android.providers.downloads.documents" == uri.authority
    private fun isMediaDocument(uri: Uri): Boolean = "com.android.providers.media.documents" == uri.authority

    private fun createTempFileFromUri(context: Context, uri: Uri): String? {
        return try {
            // Get file name from URI if possible
            var fileName = "temp_file"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameColumn = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameColumn >= 0) {
                        fileName = cursor.getString(nameColumn)
                    }
                }
            }

            // Create temp file and copy content
            val tempDir = context.cacheDir
            val tempFile = File(tempDir, fileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            tempFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }
}
