package com.mtmanager.lite.model

import java.io.File
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class FileItem(
    val file: File,
    var isSelected: Boolean = false
) {
    val name: String get() = file.name
    val isDirectory: Boolean get() = file.isDirectory
    val size: Long get() = if (file.isDirectory) -1L else file.length()
    val lastModified: Long get() = file.lastModified()
    val extension: String get() = file.extension.lowercase(Locale.getDefault())
    val isHidden: Boolean get() = file.isHidden

    fun formattedSize(): String {
        if (isDirectory) return ""
        val bytes = size
        val df = DecimalFormat("#.#")
        return when {
            bytes < 1024L -> "$bytes B"
            bytes < 1024L * 1024 -> "${df.format(bytes / 1024.0)} KB"
            bytes < 1024L * 1024 * 1024 -> "${df.format(bytes / (1024.0 * 1024))} MB"
            else -> "${df.format(bytes / (1024.0 * 1024 * 1024))} GB"
        }
    }

    fun formattedDate(): String =
        SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(lastModified))

    // ── Category helpers ──────────────────────────────────────────────────────
    fun isTextFile()     = extension in TEXT_EXTENSIONS
    fun isImageFile()    = extension in IMAGE_EXTENSIONS
    fun isVideoFile()    = extension in VIDEO_EXTENSIONS
    fun isAudioFile()    = extension in AUDIO_EXTENSIONS
    fun isDocumentFile() = extension in DOCUMENT_EXTENSIONS
    fun isApkFile()      = extension == "apk"

    fun mimeType(): String = MIME_MAP[extension] ?: "*/*"

    companion object {

        /**
         * Extensions opened in the built-in code/text editor.
         * Intentionally excludes spreadsheets (csv/tsv), documents, and archives —
         * those are handled by system apps for a better experience.
         */
        val TEXT_EXTENSIONS = setOf(
            // Plain text / config
            "txt", "log", "md", "readme", "ini", "cfg", "conf", "env",
            "properties", "toml", "gitignore", "editorconfig", "lock",
            "babelrc", "eslintrc", "htaccess",
            // Web
            "html", "htm", "css", "js", "mjs", "cjs", "jsx", "ts", "tsx",
            // Data (human-readable, not spreadsheet)
            "json", "xml", "yaml", "yml",
            // Programming languages
            "kt", "kts", "java", "py", "go", "rs", "rb", "php", "swift",
            "dart", "scala", "r", "lua", "pl", "groovy", "gradle",
            "c", "cpp", "h", "hpp", "cs", "vb", "f90", "f95",
            // Shell / scripts
            "sh", "bash", "zsh", "fish", "bat", "ps1", "cmd",
            // Database queries
            "sql",
            // Other code
            "makefile", "dockerfile", "tf", "hcl", "proto",
            "patch", "diff"
        )

        val IMAGE_EXTENSIONS = setOf(
            "jpg", "jpeg", "png", "gif", "webp", "bmp",
            "svg", "ico", "tiff", "tif", "heic", "heif", "avif", "raw"
        )

        val VIDEO_EXTENSIONS = setOf(
            "mp4", "mkv", "avi", "mov", "wmv", "flv",
            "webm", "3gp", "3g2", "ts", "m4v", "ogv", "rm", "rmvb"
        )

        val AUDIO_EXTENSIONS = setOf(
            "mp3", "wav", "flac", "aac", "ogg", "m4a",
            "wma", "opus", "amr", "mid", "midi", "ape", "alac"
        )

        /**
         * Spreadsheets, office documents, PDFs — always open with system/Google apps.
         * CSV → Google Sheets, XLSX → Google Sheets / Excel, PDF → PDF viewer, etc.
         */
        val DOCUMENT_EXTENSIONS = setOf(
            // Spreadsheets
            "csv", "tsv", "xls", "xlsx", "xlsm", "xlsb", "ods", "numbers",
            // Word processors
            "doc", "docx", "docm", "odt", "rtf", "pages",
            // Presentations
            "ppt", "pptx", "pptm", "odp", "key",
            // PDF / eBooks
            "pdf", "epub", "mobi",
        )

        // ── Complete MIME map ─────────────────────────────────────────────────
        val MIME_MAP: Map<String, String> = buildMap {
            // Plain text
            put("txt",  "text/plain"); put("log", "text/plain")
            put("md",   "text/markdown"); put("readme", "text/plain")
            put("ini",  "text/plain"); put("cfg", "text/plain")
            put("conf", "text/plain"); put("env", "text/plain")
            put("properties", "text/plain"); put("toml", "text/plain")
            put("gitignore", "text/plain"); put("lock", "text/plain")
            put("editorconfig", "text/plain"); put("htaccess", "text/plain")
            put("patch", "text/x-patch"); put("diff", "text/x-diff")
            // Web
            put("html", "text/html"); put("htm", "text/html")
            put("css",  "text/css")
            put("js",   "text/javascript"); put("mjs", "text/javascript")
            put("jsx",  "text/javascript"); put("ts",  "text/typescript")
            put("tsx",  "text/typescript")
            // Data
            put("json", "application/json")
            put("xml",  "text/xml")
            put("yaml", "text/yaml"); put("yml", "text/yaml")
            // CSV / TSV → opens Google Sheets / Excel
            put("csv",  "text/csv")
            put("tsv",  "text/tab-separated-values")
            // Code files
            put("kt",    "text/x-kotlin"); put("kts",   "text/x-kotlin")
            put("java",  "text/x-java")
            put("py",    "text/x-python")
            put("go",    "text/x-go")
            put("rs",    "text/x-rust")
            put("rb",    "text/x-ruby")
            put("php",   "application/x-php")
            put("swift", "text/x-swift")
            put("dart",  "text/x-dart")
            put("c",     "text/x-csrc"); put("cpp", "text/x-c++src")
            put("h",     "text/x-chdr"); put("hpp", "text/x-c++hdr")
            put("cs",    "text/x-csharp")
            put("sh",    "text/x-sh"); put("bash", "text/x-sh")
            put("bat",   "text/x-bat"); put("ps1",  "text/plain")
            put("sql",   "text/x-sql")
            put("scala", "text/x-scala"); put("groovy", "text/x-groovy")
            // Images
            put("jpg",  "image/jpeg"); put("jpeg", "image/jpeg")
            put("png",  "image/png"); put("gif",  "image/gif")
            put("webp", "image/webp"); put("bmp",  "image/bmp")
            put("svg",  "image/svg+xml"); put("ico",  "image/x-icon")
            put("tiff", "image/tiff"); put("tif",  "image/tiff")
            put("heic", "image/heic"); put("heif", "image/heif")
            put("avif", "image/avif")
            // Video
            put("mp4",  "video/mp4"); put("mkv",  "video/x-matroska")
            put("avi",  "video/x-msvideo"); put("mov",  "video/quicktime")
            put("wmv",  "video/x-ms-wmv"); put("flv",  "video/x-flv")
            put("webm", "video/webm"); put("3gp",  "video/3gpp")
            put("m4v",  "video/x-m4v"); put("ogv",  "video/ogg")
            // Audio
            put("mp3",  "audio/mpeg"); put("wav",  "audio/wav")
            put("flac", "audio/flac"); put("aac",  "audio/aac")
            put("ogg",  "audio/ogg"); put("m4a",  "audio/x-m4a")
            put("wma",  "audio/x-ms-wma"); put("opus", "audio/opus")
            put("amr",  "audio/amr"); put("mid",  "audio/midi")
            put("midi", "audio/midi")
            // Spreadsheets
            put("xls",  "application/vnd.ms-excel")
            put("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            put("xlsm", "application/vnd.ms-excel.sheet.macroEnabled.12")
            put("ods",  "application/vnd.oasis.opendocument.spreadsheet")
            // Word processors
            put("doc",  "application/msword")
            put("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
            put("odt",  "application/vnd.oasis.opendocument.text")
            put("rtf",  "application/rtf")
            // Presentations
            put("ppt",  "application/vnd.ms-powerpoint")
            put("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation")
            put("odp",  "application/vnd.oasis.opendocument.presentation")
            // PDF / eBook
            put("pdf",  "application/pdf")
            put("epub", "application/epub+zip")
            put("mobi", "application/x-mobipocket-ebook")
            // Archives
            put("zip",  "application/zip")
            put("jar",  "application/java-archive")
            put("rar",  "application/x-rar-compressed")
            put("7z",   "application/x-7z-compressed")
            put("tar",  "application/x-tar")
            put("gz",   "application/gzip")
            // APK
            put("apk",  "application/vnd.android.package-archive")
        }
    }
}

enum class SortOrder(val label: String) {
    NAME_ASC("Name ↑"),
    NAME_DESC("Name ↓"),
    SIZE_ASC("Size ↑"),
    SIZE_DESC("Size ↓"),
    DATE_ASC("Date ↑"),
    DATE_DESC("Date ↓"),
    TYPE_ASC("Type")
}
