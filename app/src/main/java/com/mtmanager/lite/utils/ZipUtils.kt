package com.mtmanager.lite.utils

import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.model.enums.EncryptionMethod
import java.io.File

object ZipUtils {

    /** Supported archive extensions handled by [extract]. */
    private val SUPPORTED = setOf("zip", "jar", "apk", "xapk")

    /** Returns true if [file] is an archive we can extract. */
    fun isSupported(file: File): Boolean = file.extension.lowercase() in SUPPORTED

    /**
     * Returns true if the zip is encrypted (needs a password).
     * Uses zip4j to inspect the local file header.
     */
    fun isEncrypted(file: File): Boolean = runCatching {
        ZipFile(file).isEncrypted
    }.getOrDefault(false)

    /**
     * Extracts [zipFile] into [destDir].
     * Pass [password] for encrypted archives; null or blank = no password.
     * Returns the count of files extracted.
     * Throws [ZipException] on wrong password or corrupt zip.
     */
    fun extract(zipFile: File, destDir: File, password: String? = null): Result<Int> = runCatching {
        destDir.mkdirs()

        val zf = if (!password.isNullOrBlank()) {
            ZipFile(zipFile, password.toCharArray())
        } else {
            ZipFile(zipFile)
        }

        zf.use { zip ->
            // Validate password before extraction attempt
            if (zip.isEncrypted && password.isNullOrBlank()) {
                throw ZipException("Password required for this archive")
            }
            zip.extractAll(destDir.absolutePath)
        }

        // Count extracted files
        destDir.walkTopDown().count { it.isFile }
    }
}
