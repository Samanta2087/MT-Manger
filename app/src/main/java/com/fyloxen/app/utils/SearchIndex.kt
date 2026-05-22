package com.fyloxen.app.utils

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

data class FileSearchResult(
    val path: String,
    val name: String,
    val ext: String,
    val parent: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long
) {
    fun toFile(): File = File(path)
}

object SearchIndex {

    private const val DB_NAME = "search_index.db"
    private val SKIP_DIRS = setOf(
        "Android", ".git", ".gradle", "node_modules", "__pycache__",
        ".cache", "build", ".dot", ".idea", ".vscode", ".Trash",
        ".npm", ".rustup", ".cargo", "lost+found"
    )

    val isIndexing = AtomicBoolean(false)

    @Volatile
    private var dbPath: String? = null

    fun init(context: Context) {
        val dbFile = context.getDatabasePath(DB_NAME)
        dbPath = dbFile.absolutePath
val path = dbPath ?: return
            if (dbFile.exists()) {
                var needsReset = false
                var sqlDb: SQLiteDatabase? = null
                try {
                    sqlDb = SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY)
                val cursor = sqlDb.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name='file_idx'", null
                )
                if (cursor.moveToFirst()) {
                    needsReset = true
                }
                cursor.close()
            } catch (_: Exception) {
                needsReset = true
            } finally {
                try { sqlDb?.close() } catch (_: Exception) {}
            }
            if (needsReset) {
                dbFile.delete()
                val wal = File(dbPath + "-wal")
                val shm = File(dbPath + "-shm")
                wal.delete()
                shm.delete()
            }
        }
        dbFile.parentFile?.mkdirs()
    }

    private fun openDb(): SQLiteDatabase {
        val db = SQLiteDatabase.openOrCreateDatabase(dbPath ?: ":memory:", null)
        db.execSQL("PRAGMA journal_mode=WAL")
        db.execSQL("PRAGMA synchronous=NORMAL")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS file_meta (
                _id INTEGER PRIMARY KEY AUTOINCREMENT,
                fullpath TEXT UNIQUE NOT NULL,
                name TEXT NOT NULL,
                name_lower TEXT NOT NULL,
                ext TEXT NOT NULL DEFAULT '',
                parent TEXT NOT NULL,
                is_dir INTEGER NOT NULL DEFAULT 0,
                fsize INTEGER NOT NULL DEFAULT 0,
                mtime INTEGER NOT NULL DEFAULT 0
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_name_lower ON file_meta(name_lower)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_ext ON file_meta(ext)")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS idx_status (
                key TEXT PRIMARY KEY,
                value TEXT NOT NULL
            )
        """)
        return db
    }

    fun search(query: String, filter: Set<String>? = null, limit: Int = 200): List<FileSearchResult> {
        val q = query.lowercase().trim()
        if (q.length < 2) return emptyList()

        val likeQuery = "%$q%"
        val db = try { openDb() } catch (_: Exception) { return emptyList() }

        try {
            val sql: String
            val args: Array<String>

            if (filter != null && filter.isNotEmpty()) {
                val extPlaceholders = filter.joinToString(",") { "?" }
                sql = """
                    SELECT fullpath, name, ext, parent, is_dir, fsize, mtime
                    FROM file_meta
                    WHERE name_lower LIKE ?
                    AND (is_dir = 1 OR ext IN ($extPlaceholders))
                    ORDER BY is_dir DESC, name_lower ASC
                    LIMIT ?
                """
                args = arrayOf(likeQuery) + filter.toTypedArray() + arrayOf(limit.toString())
            } else {
                sql = """
                    SELECT fullpath, name, ext, parent, is_dir, fsize, mtime
                    FROM file_meta
                    WHERE name_lower LIKE ?
                    ORDER BY is_dir DESC, name_lower ASC
                    LIMIT ?
                """
                args = arrayOf(likeQuery, limit.toString())
            }

            val results = mutableListOf<FileSearchResult>()
            db.rawQuery(sql, args).use { cursor ->
                while (cursor.moveToNext()) {
                    results.add(FileSearchResult(
                        path = cursor.getString(0),
                        name = cursor.getString(1),
                        ext = cursor.getString(2),
                        parent = cursor.getString(3),
                        isDirectory = cursor.getInt(4) == 1,
                        size = cursor.getLong(5),
                        lastModified = cursor.getLong(6)
                    ))
                }
            }
            return results
        } catch (_: Exception) {
            return emptyList()
        } finally {
            try { db.close() } catch (_: Exception) {}
        }
    }

    suspend fun indexDirectory(root: File, maxDepth: Int = 3): Int = withContext(Dispatchers.IO) {
        if (!isIndexing.compareAndSet(false, true)) return@withContext -1

        var count = 0
        val db: SQLiteDatabase
        try {
            db = openDb()
        } catch (_: Exception) {
            isIndexing.set(false)
            return@withContext -1
        }

        try {
            db.execSQL("DELETE FROM file_meta")
            db.execSQL("DELETE FROM idx_status")

            val visited = HashSet<String>()
            val queue = ArrayDeque<Pair<File, Int>>()
            queue.add(Pair(root, 0))
            visited.add(root.absolutePath)

            db.beginTransaction()
            try {
                while (queue.isNotEmpty()) {
                    yield()
                    val (dir, depth) = queue.removeFirst()
                    val children = dir.listFiles() ?: continue

                    for (f in children) {
                        if (f.isHidden) continue
                        if (f.isDirectory && f.name in SKIP_DIRS) continue

                        val name = f.name
                        val ext = if (f.isDirectory) "" else f.extension.lowercase()

                        db.execSQL(
                            "INSERT OR IGNORE INTO file_meta(fullpath, name, name_lower, ext, parent, is_dir, fsize, mtime) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                            arrayOf(
                                f.absolutePath,
                                name,
                                name.lowercase(),
                                ext,
                                f.parent ?: "",
                                if (f.isDirectory) "1" else "0",
                                f.length().toString(),
                                f.lastModified().toString()
                            )
                        )
                        count++

                        if (f.isDirectory && depth < maxDepth && f.absolutePath !in visited) {
                            visited.add(f.absolutePath)
                            queue.add(Pair(f, depth + 1))
                        }
                    }

                    if (count % 500 == 0 && count > 0) {
                        db.setTransactionSuccessful()
                        db.endTransaction()
                        db.beginTransaction()
                    }
                }

                db.setTransactionSuccessful()
            } finally {
                try { db.endTransaction() } catch (_: Exception) {}
            }

            db.execSQL("INSERT OR REPLACE INTO idx_status(key, value) VALUES ('root_indexed', ?)",
                arrayOf(root.absolutePath))
            db.execSQL("INSERT OR REPLACE INTO idx_status(key, value) VALUES ('total_files', ?)",
                arrayOf(count.toString()))
            db.execSQL("INSERT OR REPLACE INTO idx_status(key, value) VALUES ('indexed_at', ?)",
                arrayOf(System.currentTimeMillis().toString()))

        } catch (_: Exception) {
        } finally {
            isIndexing.set(false)
            try { db.close() } catch (_: Exception) {}
        }
        count
    }

    fun isIndexed(): Boolean {
        val db = try { openDb() } catch (_: Exception) { return false }
        try {
            val cursor = db.rawQuery("SELECT value FROM idx_status WHERE key = 'root_indexed'", null)
            val has = cursor.moveToFirst()
            cursor.close()
            return has
        } catch (_: Exception) {
            return false
        } finally {
            try { db.close() } catch (_: Exception) {}
        }
    }

    fun getIndexAgeMs(): Long {
        val db = try { openDb() } catch (_: Exception) { return Long.MAX_VALUE }
        try {
            val cursor = db.rawQuery("SELECT value FROM idx_status WHERE key = 'indexed_at'", null)
            if (cursor.moveToFirst()) {
                val age = System.currentTimeMillis() - (cursor.getString(0).toLongOrNull() ?: 0L)
                cursor.close()
                return age
            }
            cursor.close()
        } catch (_: Exception) {
        } finally {
            try { db.close() } catch (_: Exception) {}
        }
        return Long.MAX_VALUE
    }

    fun getIndexedCount(): Int {
        val db = try { openDb() } catch (_: Exception) { return 0 }
        try {
            val cursor = db.rawQuery("SELECT value FROM idx_status WHERE key = 'total_files'", null)
            if (cursor.moveToFirst()) {
                val count = cursor.getString(0).toIntOrNull() ?: 0
                cursor.close()
                return count
            }
            cursor.close()
        } catch (_: Exception) {
        } finally {
            try { db.close() } catch (_: Exception) {}
        }
        return 0
    }

    fun clearIndex() {
        val db = try { openDb() } catch (_: Exception) { return }
        try {
            db.execSQL("DELETE FROM file_meta")
            db.execSQL("DELETE FROM idx_status")
        } catch (_: Exception) {}
        finally {
            try { db.close() } catch (_: Exception) {}
        }
    }
}