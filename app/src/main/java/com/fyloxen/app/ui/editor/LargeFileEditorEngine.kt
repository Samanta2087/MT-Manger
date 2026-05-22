package com.fyloxen.app.ui.editor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile

/**
 * Streaming file engine that handles files up to 2 GB without loading them into RAM.
 *
 * Strategy:
 *  - Scan file once in 64 KB chunks, recording offset of every BLOCK_SIZE-th line.
 *  - To read line N: binary-search the block list, seek, scan ≤ BLOCK_SIZE newlines.
 *  - Edits are stored in a HashMap<lineNum, newText>; the file itself is never mutated.
 *  - Save: stream all lines to a temp file, then atomically rename.
 */
class LargeFileEditorEngine(val file: File) {

    companion object {
        const val BLOCK_SIZE = 128   // lines between block-index entries
        private const val IO_BUF = 65_536
    }

    // Block index: blocks[i] = byte-offset where block i starts (block i covers lines i*BLOCK_SIZE … (i+1)*BLOCK_SIZE-1)
    private var blockOffsets = LongArray(0)

    private var _lineCount = 0
    val lineCount: Int get() = _lineCount

    // Modified lines (line-number → new content)
    val patchMap = HashMap<Int, String>()
    var isModified = false

    // RandomAccessFile for on-demand reads (synchronized for thread safety)
    private var raf: RandomAccessFile? = null

    // ── Index building ────────────────────────────────────────────────────────

    suspend fun buildIndex(onProgress: (Int) -> Unit = {}) = withContext(Dispatchers.IO) {
        val fileLen = file.length().coerceAtLeast(1L)
        val offList = ArrayList<Long>(((fileLen / 50).coerceAtMost(200_000L)).toInt())
        offList.add(0L)   // block 0 starts at byte 0

        val buf = ByteArray(IO_BUF)
        var pos = 0L
        var lineNum = 0

        FileInputStream(file).use { fis ->
            var read: Int
            while (fis.read(buf).also { read = it } != -1) {
                for (i in 0 until read) {
                    if (buf[i] == '\n'.code.toByte()) {
                        lineNum++
                        if (lineNum % BLOCK_SIZE == 0) {
                            offList.add(pos + i + 1)
                        }
                    }
                }
                pos += read
                onProgress((pos * 100L / fileLen).toInt().coerceIn(0, 99))
            }
        }

        _lineCount = lineNum + 1   // last line even if no trailing newline
        blockOffsets = offList.toLongArray()
        onProgress(100)

        raf?.close()
        raf = RandomAccessFile(file, "r")
    }

    // ── Line access ───────────────────────────────────────────────────────────

    fun getLine(lineNum: Int): String {
        patchMap[lineNum]?.let { return it }
        return readFromFile(lineNum)
    }

    @Synchronized
    private fun readFromFile(n: Int): String {
        val r = raf ?: return ""
        return try {
            val blockIdx = (n / BLOCK_SIZE).coerceIn(0, blockOffsets.size - 1)
            val firstLineInBlock = blockIdx * BLOCK_SIZE
            r.seek(blockOffsets[blockIdx])

            var curLine = firstLineInBlock
            // Skip to the target line within the block
            while (curLine < n) {
                val b = r.read()
                if (b == -1) return ""
                if (b == '\n'.code) curLine++
            }

            // Read the target line
            val sb = StringBuilder()
            while (true) {
                val b = r.read()
                if (b == -1 || b == '\n'.code) break
                if (b != '\r'.code) sb.append(b.toChar())
            }
            sb.toString()
        } catch (e: Exception) { "" }
    }

    // ── Editing ───────────────────────────────────────────────────────────────

    fun setLine(lineNum: Int, content: String) {
        patchMap[lineNum] = content
        isModified = true
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    suspend fun saveToFile(onProgress: (Int) -> Unit): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val tmp = File(file.parent, "${file.name}.mgt_tmp")
            tmp.bufferedWriter(Charsets.UTF_8, IO_BUF).use { w ->
                for (i in 0 until _lineCount) {
                    w.write(getLine(i))
                    if (i < _lineCount - 1) w.newLine()
                    if (i % 10_000 == 0) onProgress((i * 100L / _lineCount).toInt())
                }
            }
            // Close RAF before rename so Windows allows it
            raf?.close(); raf = null

            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }

            patchMap.clear()
            isModified = false
            buildIndex { }   // rebuild after save
            onProgress(100)
        }
    }

    fun close() { raf?.close(); raf = null }
}
