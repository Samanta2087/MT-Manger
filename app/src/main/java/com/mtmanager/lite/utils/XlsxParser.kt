package com.mtmanager.lite.utils

import java.io.File
import java.util.zip.ZipFile

object XlsxParser {

    fun toCsv(file: File): String {
        val zip = try { ZipFile(file) } catch (e: Exception) { return "" }

        // 1. Shared strings table
        val ss = mutableListOf<String>()
        zip.getEntry("xl/sharedStrings.xml")?.let { e ->
            val xml = zip.getInputStream(e).bufferedReader(Charsets.UTF_8).readText()
            Regex("<si>(.*?)</si>", RegexOption.DOT_MATCHES_ALL).findAll(xml).forEach { si ->
                val t = Regex("<t[^>]*>(.*?)</t>", RegexOption.DOT_MATCHES_ALL)
                    .findAll(si.value).joinToString("") { it.groupValues[1] }
                ss.add(t.xmlDecode())
            }
        }

        // 2. First sheet
        val sheetEntry = zip.getEntry("xl/worksheets/sheet1.xml")
            ?: zip.entries().asSequence().firstOrNull { it.name.matches(Regex("xl/worksheets/sheet\\d+\\.xml")) }
            ?: return zip.close().let { "" }

        val xml = zip.getInputStream(sheetEntry).bufferedReader(Charsets.UTF_8).readText()
        zip.close()

        val rows = sortedMapOf<Int, MutableMap<Int, String>>()

        Regex("<row[^>]+r=\"(\\d+)\"[^>]*>(.*?)</row>", RegexOption.DOT_MATCHES_ALL)
            .findAll(xml).forEach { rowM ->
                val r = rowM.groupValues[1].toIntOrNull() ?: return@forEach
                val rowMap = mutableMapOf<Int, String>()

                Regex("<c r=\"([A-Z]+)(\\d+)\"([^>]*)>(.*?)</c>", RegexOption.DOT_MATCHES_ALL)
                    .findAll(rowM.groupValues[2]).forEach { cm ->
                        val col  = colIdx(cm.groupValues[1])
                        val type = Regex("t=\"([^\"]+)\"").find(cm.groupValues[3])?.groupValues?.get(1) ?: ""
                        val v    = Regex("<v>(.*?)</v>").find(cm.groupValues[4])?.groupValues?.get(1) ?: ""
                        val inl  = Regex("<t[^>]*>(.*?)</t>").find(cm.groupValues[4])?.groupValues?.get(1) ?: ""
                        rowMap[col] = when (type) {
                            "s"         -> ss.getOrNull(v.toIntOrNull() ?: -1) ?: v
                            "inlineStr" -> inl.xmlDecode()
                            "b"         -> if (v == "1") "TRUE" else "FALSE"
                            else        -> v
                        }
                    }
                if (rowMap.isNotEmpty()) rows[r] = rowMap
            }

        if (rows.isEmpty()) return ""
        val maxCol = rows.values.flatMap { it.keys }.maxOrNull() ?: 0
        return buildString {
            rows.values.forEach { row ->
                appendLine((0..maxCol).joinToString(",") { c -> csvEsc(row[c] ?: "") })
            }
        }.trim()
    }

    private fun colIdx(s: String): Int {
        var r = 0; s.forEach { r = r * 26 + (it - 'A' + 1) }; return r - 1
    }
    private fun csvEsc(v: String) =
        if (v.any { it == ',' || it == '"' || it == '\n' }) "\"${v.replace("\"", "\"\"")}\"" else v
    private fun String.xmlDecode() = replace("&amp;","&").replace("&lt;","<")
        .replace("&gt;",">").replace("&quot;","\"").replace("&apos;","'")
}
