package com.mtmanager.lite.utils

import com.mtmanager.lite.model.FileItem
import com.mtmanager.lite.model.SortOrder
import kotlinx.coroutines.yield
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object FileUtils {

    val DOC_EXTENSIONS = setOf("txt", "log", "readme", "md", "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "csv", "tsv", "rtf")
    val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "ico", "tiff", "tif")
    val AUDIO_EXTENSIONS = setOf("mp3", "wav", "flac", "aac", "ogg", "m4a", "wma", "opus", "amr", "mid", "midi")
    val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "avi", "mov", "wmv", "3gp", "webm", "flv", "m4v", "ts")
    val ARCHIVE_EXTENSIONS = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "zst", "cab", "iso")
    val CODE_EXTENSIONS = setOf("py", "kt", "java", "js", "mjs", "jsx", "ts", "tsx", "cpp", "c", "h", "hpp", "cs", "go", "rs", "rb", "php", "swift", "dart", "sh", "bash", "bat", "ps1", "sql", "html", "htm", "css", "xml", "json", "yaml", "yml", "toml", "ini", "cfg", "conf", "env")

    fun listFiles(directory: File, showHidden: Boolean, sortOrder: SortOrder): List<FileItem> {
        if (!directory.canRead()) return emptyList()
        val all = directory.listFiles() ?: return emptyList()
        // Single pass: partition & filter without creating FileItem objects yet
        val dirs  = ArrayList<File>(all.size / 2 + 1)
        val files = ArrayList<File>(all.size / 2 + 1)
        for (f in all) {
            if (!showHidden && f.isHidden) continue
            if (f.isDirectory) dirs.add(f) else files.add(f)
        }
        // Sort raw File list (cheaper than sorting FileItem wrappers)
        return (sortList(dirs, sortOrder) + sortList(files, sortOrder)).map { FileItem(it) }
    }

    private fun sortList(files: List<File>, order: SortOrder): List<File> = when (order) {
        SortOrder.NAME_ASC  -> files.sortedBy { it.name.lowercase() }
        SortOrder.NAME_DESC -> files.sortedByDescending { it.name.lowercase() }
        SortOrder.SIZE_ASC  -> files.sortedBy { it.length() }
        SortOrder.SIZE_DESC -> files.sortedByDescending { it.length() }
        SortOrder.DATE_ASC  -> files.sortedBy { it.lastModified() }
        SortOrder.DATE_DESC -> files.sortedByDescending { it.lastModified() }
        SortOrder.TYPE_ASC  -> files.sortedBy { it.extension.lowercase() }
    }

    fun createFile(parent: File, name: String): Result<File> = runCatching {
        val f = File(parent, name)
        if (f.exists()) throw Exception("File already exists")
        f.createNewFile()
        f
    }

    fun createFolder(parent: File, name: String): Result<File> = runCatching {
        val f = File(parent, name)
        if (f.exists()) throw Exception("Folder already exists")
        if (!f.mkdirs()) throw Exception("Failed to create folder")
        f
    }

    fun rename(file: File, newName: String): Result<File> = runCatching {
        // Validate filename
        if (newName.isBlank()) throw Exception("Name cannot be empty")
        if (newName.length > 255) throw Exception("Name too long (max 255 characters)")
        val invalidChars = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
        if (newName.any { it in invalidChars }) throw Exception("Invalid characters in filename")
        if (newName == ".." || newName == ".") throw Exception("Invalid filename")
        
        val dest = File(file.parent, newName)
        if (dest.exists() && dest.absolutePath != file.absolutePath) throw Exception("Name already taken")
        if (!file.renameTo(dest)) throw Exception("Rename failed")
        dest
    }

    fun renameWithPattern(files: List<File>, oldPattern: String, newPattern: String): Result<Int> = runCatching {
        var count = 0
        files.forEach { file ->
            val newName = file.name.replace(oldPattern, newPattern)
            if (newName != file.name) {
                rename(file, newName).getOrThrow()
                count++
            }
        }
        count
    }

    fun delete(file: File): Result<Unit> = runCatching {
        if (!deleteRecursive(file)) throw Exception("Delete failed")
    }

    private fun deleteRecursive(file: File): Boolean {
        if (file.isDirectory) {
            file.listFiles()?.forEach { if (!deleteRecursive(it)) return false }
        }
        return file.delete()
    }

    fun copy(source: File, destDir: File, overwrite: Boolean = false): Result<File> = runCatching {
        val dest = File(destDir, source.name)
        if (dest.exists() && !overwrite) throw Exception("File already exists at destination")
        if (source.isDirectory) {
            copyDirectory(source, dest)
        } else {
            copyFile(source, dest)
        }
        dest
    }

    private fun copyFile(src: File, dst: File) {
        dst.parentFile?.mkdirs()
        FileInputStream(src).use { input ->
            FileOutputStream(dst).use { output ->
                input.copyTo(output, bufferSize = 65536)
            }
        }
    }

    private fun copyDirectory(src: File, dst: File) {
        dst.mkdirs()
        src.listFiles()?.forEach { child ->
            if (child.isDirectory) copyDirectory(child, File(dst, child.name))
            else copyFile(child, File(dst, child.name))
        }
    }

    fun move(source: File, destDir: File): Result<File> = runCatching {
        val dest = File(destDir, source.name)
        if (dest.exists()) throw Exception("File already exists at destination")
        if (source.renameTo(dest)) return@runCatching dest
        // Cross-filesystem: copy then delete
        copy(source, destDir).getOrThrow()
        deleteRecursive(source)
        dest
    }

    fun readText(file: File): Result<String> = runCatching {
        if (file.length() > 5 * 1024 * 1024) throw Exception("File too large (>5MB) to open in editor")
        file.readText(Charsets.UTF_8)
    }

    fun readTextLimited(file: File, maxBytes: Int): Result<String> = runCatching {
        if (maxBytes <= 0) return@runCatching ""
        FileInputStream(file).use { input ->
            val buffer = ByteArray(maxBytes)
            val read = input.read(buffer)
            if (read <= 0) "" else String(buffer, 0, read, Charsets.UTF_8)
        }
    }

    fun writeText(file: File, content: String): Result<Unit> = runCatching {
        file.writeText(content, Charsets.UTF_8)
    }

    fun getFileIcon(extension: String): String = when (extension) {
        "txt", "log", "readme" -> "📄"
        "md" -> "📝"
        "pdf" -> "📕"
        "html", "htm" -> "🌐"
        "css" -> "🎨"
        "js", "mjs", "jsx" -> "🟨"
        "ts", "tsx" -> "🔷"
        "json" -> "📋"
        "xml" -> "📰"
        "kt", "kts" -> "🟣"
        "java" -> "☕"
        "py" -> "🐍"
        "dart" -> "🎯"
        "go" -> "🐹"
        "rs" -> "🦀"
        "rb" -> "💎"
        "c", "cpp", "h", "hpp" -> "⚙️"
        "swift" -> "🍎"
        "php" -> "🐘"
        "sh", "bash", "bat", "ps1" -> "💻"
        "sql" -> "🗄️"
        "yaml", "yml" -> "⚙️"
        "zip", "rar", "7z", "tar", "gz" -> "📦"
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg" -> "🖼️"
        "mp4", "mkv", "avi", "mov" -> "🎬"
        "mp3", "wav", "flac", "aac" -> "🎵"
        "apk" -> "📱"
        "exe", "msi" -> "⚙️"
        "doc", "docx" -> "📘"
        "xls", "xlsx" -> "📗"
        "ppt", "pptx" -> "📙"
        else -> "📄"
    }

    fun getFolderIcon(): String = "📁"

    private val SKIP_DIRS = setOf("Android", ".git", ".gradle", "node_modules", "__pycache__", ".cache", "build", ".dot")

    suspend fun searchFiles(root: File, query: String, maxResults: Int = 200, filter: Set<String>? = null): List<File> {
        val q = query.lowercase()
        val results = mutableListOf<File>()
        val visited = HashSet<String>()
        val queue = ArrayDeque<Pair<File, Int>>()
        queue.add(Pair(root, 0))
        visited.add(root.absolutePath)
        try {
            while (queue.isNotEmpty() && results.size < maxResults) {
                if (results.size % 20 == 0) yield()
                val (dir, depth) = queue.removeFirst()
                val children = dir.listFiles() ?: continue
                for (f in children) {
                    if (results.size >= maxResults) break
                    if (f.name.lowercase().contains(q)) {
                        if (filter == null || f.isDirectory || f.extension.lowercase() in filter) {
                            results.add(f)
                        }
                    }
                    if (f.isDirectory && !f.isHidden && f.name !in SKIP_DIRS && f.absolutePath !in visited && depth < 3) {
                        visited.add(f.absolutePath)
                        queue.add(Pair(f, depth + 1))
                    }
                }
            }
        } catch (_: kotlinx.coroutines.CancellationException) {}
        return results
    }

    fun formatSize(bytes: Long): String {
        val df = java.text.DecimalFormat("#.#")
        return when {
            bytes < 0 -> ""
            bytes < 1024L -> "$bytes B"
            bytes < 1024L * 1024 -> "${df.format(bytes / 1024.0)} KB"
            bytes < 1024L * 1024 * 1024 -> "${df.format(bytes / (1024.0 * 1024))} MB"
            else -> "${df.format(bytes / (1024.0 * 1024 * 1024))} GB"
        }
    }

    fun getFolderSize(directory: File): Long {
        var size = 0L
        try {
            directory.walkTopDown().filter { it.isFile }.forEach { size += it.length() }
        } catch (_: Exception) {}
        return size
    }
}
