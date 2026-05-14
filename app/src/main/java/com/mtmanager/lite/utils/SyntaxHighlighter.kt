package com.mtmanager.lite.utils

import android.graphics.Color
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import java.util.Locale

object SyntaxHighlighter {

    // ── VSCode Dark+ Professional Palette ─────────────────────────────────────
    private val COLOR_KEYWORD   = Color.parseColor("#569CD6")  // Blue — keywords (POP)
    private val COLOR_STRING    = Color.parseColor("#CE9178")  // Orange — strings
    private val COLOR_NUMBER    = Color.parseColor("#B5CEA8")  // Light green — numbers
    private val COLOR_FUNCTION  = Color.parseColor("#DCDCAA")  // Yellow — functions
    private val COLOR_COMMENT   = Color.parseColor("#6A9955")  // Muted green — comments
    private val COLOR_ERROR     = Color.parseColor("#F07178")  // Red — errors/tags
    private val COLOR_TYPE      = Color.parseColor("#4EC9B0")  // Teal — types
    private val COLOR_OPERATOR  = Color.parseColor("#D4D4D4")  // White — operators
    private val COLOR_TAG       = Color.parseColor("#569CD6")  // Blue — HTML/XML tags
    private val COLOR_ATTR     = Color.parseColor("#9CDCFE")  // Light blue — attributes
    private val COLOR_KEY       = Color.parseColor("#9CDCFE")  // Light blue — JSON keys
    private val COLOR_ANNOTATION= Color.parseColor("#4EC9B0")  // Teal — annotations

    private val KOTLIN_KEYWORDS = setOf(
        "fun","val","var","class","object","interface","if","else","when","for","while",
        "do","return","import","package","private","public","protected","internal","override",
        "abstract","open","sealed","data","enum","companion","init","constructor","this","super",
        "null","true","false","is","as","in","out","by","get","set","it","let","run","apply",
        "also","with","to","and","or","not","try","catch","finally","throw","suspend","coroutine",
        "launch","async","flow","emit","collect","inline","noinline","crossinline","reified","typealias"
    )

    private val JAVA_KEYWORDS = setOf(
        "public","private","protected","class","interface","extends","implements","import","package",
        "static","final","abstract","void","int","long","double","float","boolean","char","byte","short",
        "if","else","for","while","do","return","new","this","super","null","true","false",
        "try","catch","finally","throw","throws","instanceof","switch","case","break","continue",
        "default","synchronized","volatile","transient","native","enum","assert"
    )

    private val PYTHON_KEYWORDS = setOf(
        "def","class","import","from","return","if","elif","else","for","while","with","as",
        "try","except","finally","raise","pass","break","continue","and","or","not","in","is",
        "None","True","False","lambda","yield","global","nonlocal","del","assert","async","await",
        "print","len","range","type","int","str","float","list","dict","set","tuple"
    )

    private val JS_KEYWORDS = setOf(
        "var","let","const","function","return","if","else","for","while","do","switch","case",
        "break","continue","class","extends","import","export","from","default","new","this",
        "super","null","undefined","true","false","typeof","instanceof","delete","in","of",
        "try","catch","finally","throw","async","await","yield","static","get","set","=>",
        "Promise","Array","Object","String","Number","Boolean","Map","Set","Symbol"
    )

    private val SQL_KEYWORDS = setOf(
        "SELECT","FROM","WHERE","INSERT","INTO","VALUES","UPDATE","SET","DELETE","CREATE","TABLE",
        "DROP","ALTER","ADD","INDEX","PRIMARY","KEY","FOREIGN","REFERENCES","JOIN","LEFT","RIGHT",
        "INNER","OUTER","ON","AND","OR","NOT","NULL","IS","IN","LIKE","BETWEEN","ORDER","BY",
        "GROUP","HAVING","LIMIT","OFFSET","DISTINCT","COUNT","SUM","AVG","MAX","MIN","AS",
        "DATABASE","SCHEMA","VIEW","TRIGGER","PROCEDURE","FUNCTION","BEGIN","END","COMMIT","ROLLBACK"
    )

    fun highlight(code: String, extension: String): SpannableString {
        val spannable = SpannableString(code)
        val lang = extension.lowercase(Locale.getDefault())
        return when (lang) {
            "kt", "kts"  -> highlightGeneric(spannable, code, KOTLIN_KEYWORDS)
            "java"       -> highlightGeneric(spannable, code, JAVA_KEYWORDS)
            "py"         -> highlightPython(spannable, code)
            "js","mjs","jsx","ts","tsx" -> highlightGeneric(spannable, code, JS_KEYWORDS)
            "html","htm" -> highlightHtml(spannable, code)
            "css","scss" -> highlightCss(spannable, code)
            "json"       -> highlightJson(spannable, code)
            "xml"        -> highlightXml(spannable, code)
            "sql"        -> highlightSql(spannable, code)
            else         -> spannable
        }
    }

    /**
     * Lightweight per-line highlight used by the RecyclerView editor.
     * Works on a single line — no block-comment state tracking.
     * Called on a background thread; returns SpannableString safe to post to UI.
     */
    fun highlightLine(line: String, extension: String): SpannableString {
        val sp   = SpannableString(line)
        val lang = extension.lowercase(Locale.getDefault())
        when (lang) {
            "kt","kts"  -> { applyInlineComment(sp, line, "//"); applyStringHighlight(sp, line); applyNumberHighlight(sp, line); applyAnnotationHighlight(sp, line); applyFunctionHighlight(sp, line); applyKeywords(sp, line, KOTLIN_KEYWORDS) }
            "java"      -> { applyInlineComment(sp, line, "//"); applyStringHighlight(sp, line); applyNumberHighlight(sp, line); applyAnnotationHighlight(sp, line); applyFunctionHighlight(sp, line); applyKeywords(sp, line, JAVA_KEYWORDS) }
            "py"        -> { applyInlineComment(sp, line, "#");  applyStringHighlight(sp, line); applyNumberHighlight(sp, line); applyAnnotationHighlight(sp, line); applyFunctionHighlight(sp, line); applyKeywords(sp, line, PYTHON_KEYWORDS) }
            "js","mjs","jsx","ts","tsx" -> { applyInlineComment(sp, line, "//"); applyStringHighlight(sp, line); applyNumberHighlight(sp, line); applyAnnotationHighlight(sp, line); applyFunctionHighlight(sp, line); applyKeywords(sp, line, JS_KEYWORDS) }
            "html","htm"-> { applyRegex(sp, line, Regex("</?[a-zA-Z][a-zA-Z0-9]*"), COLOR_TAG); applyRegex(sp, line, Regex("[a-zA-Z-]+="), COLOR_ATTR); applyStringHighlight(sp, line) }
            "xml"       -> { applyRegex(sp, line, Regex("</?[a-zA-Z][a-zA-Z0-9.:_-]*"), COLOR_TAG); applyRegex(sp, line, Regex("[a-zA-Z:_][a-zA-Z0-9:._-]*(?==)"), COLOR_ATTR); applyStringHighlight(sp, line) }
            "css","scss"-> { applyRegex(sp, line, Regex("[.#]?[a-zA-Z][a-zA-Z0-9_-]*(?=\\s*\\{)"), COLOR_FUNCTION); applyRegex(sp, line, Regex("[a-zA-Z-]+(?=\\s*:)"), COLOR_TYPE); applyStringHighlight(sp, line); applyNumberHighlight(sp, line) }
            "json"      -> { applyRegex(sp, line, Regex("\"([^\"\\\\]|\\\\.)*\"(?=\\s*:)"), COLOR_KEY); applyRegex(sp, line, Regex("(?<=:\\s)\"([^\"\\\\]|\\\\.)*\""), COLOR_STRING); applyRegex(sp, line, Regex("\\b(true|false|null)\\b"), COLOR_KEYWORD); applyNumberHighlight(sp, line) }
            "sql"       -> { applyInlineComment(sp, line, "--"); applyStringHighlight(sp, line); applyKeywords(sp, line, SQL_KEYWORDS) }
        }
        return sp
    }

    /** Highlights from inline comment marker to end of line. */
    private fun applyInlineComment(sp: SpannableString, line: String, marker: String) {
        val idx = line.indexOf(marker)
        if (idx >= 0 && idx < sp.length) {
            sp.setSpan(ForegroundColorSpan(COLOR_COMMENT), idx, sp.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun highlightGeneric(sp: SpannableString, code: String, keywords: Set<String>): SpannableString {
        applyCommentHighlight(sp, code, "//", null)
        applyBlockComment(sp, code, "/*", "*/")
        applyStringHighlight(sp, code)
        applyNumberHighlight(sp, code)
        applyAnnotationHighlight(sp, code)
        applyKeywords(sp, code, keywords)
        return sp
    }

    private fun highlightPython(sp: SpannableString, code: String): SpannableString {
        applyCommentHighlight(sp, code, "#", null)
        applyBlockComment(sp, code, "\"\"\"", "\"\"\"")
        applyStringHighlight(sp, code)
        applyNumberHighlight(sp, code)
        applyAnnotationHighlight(sp, code)
        applyKeywords(sp, code, PYTHON_KEYWORDS)
        return sp
    }

    private fun highlightHtml(sp: SpannableString, code: String): SpannableString {
        applyBlockComment(sp, code, "<!--", "-->")
        applyRegex(sp, code, Regex("</?[a-zA-Z][a-zA-Z0-9]*"), COLOR_TAG)
        applyRegex(sp, code, Regex("[a-zA-Z-]+="), COLOR_ATTR)
        applyStringHighlight(sp, code)
        return sp
    }

    private fun highlightXml(sp: SpannableString, code: String): SpannableString {
        applyBlockComment(sp, code, "<!--", "-->")
        applyRegex(sp, code, Regex("</?[a-zA-Z][a-zA-Z0-9.:_-]*"), COLOR_TAG)
        applyRegex(sp, code, Regex("[a-zA-Z:_][a-zA-Z0-9:._-]*(?==)"), COLOR_ATTR)
        applyStringHighlight(sp, code)
        return sp
    }

    private fun highlightCss(sp: SpannableString, code: String): SpannableString {
        applyBlockComment(sp, code, "/*", "*/")
        applyRegex(sp, code, Regex("[.#]?[a-zA-Z][a-zA-Z0-9_-]*(?=\\s*\\{)"), COLOR_FUNCTION)
        applyRegex(sp, code, Regex("[a-zA-Z-]+(?=\\s*:)"), COLOR_TYPE)
        applyStringHighlight(sp, code)
        applyNumberHighlight(sp, code)
        return sp
    }

    private fun highlightJson(sp: SpannableString, code: String): SpannableString {
        applyRegex(sp, code, Regex("\"([^\"\\\\]|\\\\.)*\"(?=\\s*:)"), COLOR_KEY)
        applyRegex(sp, code, Regex("(?<=:\\s)\"([^\"\\\\]|\\\\.)*\""), COLOR_STRING)
        applyRegex(sp, code, Regex("\\b(true|false|null)\\b"), COLOR_KEYWORD)
        applyNumberHighlight(sp, code)
        return sp
    }

    private fun highlightSql(sp: SpannableString, code: String): SpannableString {
        applyCommentHighlight(sp, code, "--", null)
        applyBlockComment(sp, code, "/*", "*/")
        applyStringHighlight(sp, code)
        applyKeywords(sp, code, SQL_KEYWORDS)
        return sp
    }

    private fun applyKeywords(sp: SpannableString, code: String, keywords: Set<String>) {
        for (kw in keywords) {
            val pattern = Regex("\\b${Regex.escape(kw)}\\b")
            applyRegex(sp, code, pattern, COLOR_KEYWORD)
        }
    }

    private fun applyRegex(sp: SpannableString, code: String, regex: Regex, color: Int) {
        regex.findAll(code).forEach { match ->
            sp.setSpan(ForegroundColorSpan(color), match.range.first, match.range.last + 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun applyCommentHighlight(sp: SpannableString, code: String, single: String, multi: String?) {
        val lines = code.lines()
        var offset = 0
        for (line in lines) {
            val idx = line.indexOf(single)
            if (idx >= 0) {
                val start = offset + idx
                val end = offset + line.length
                if (start < sp.length && end <= sp.length) {
                    sp.setSpan(ForegroundColorSpan(COLOR_COMMENT), start, end,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            offset += line.length + 1
        }
    }

    private fun applyBlockComment(sp: SpannableString, code: String, open: String, close: String) {
        var start = 0
        while (true) {
            val s = code.indexOf(open, start)
            if (s < 0) break
            val e = code.indexOf(close, s + open.length)
            val end = if (e < 0) code.length else e + close.length
            if (s < sp.length && end <= sp.length) {
                sp.setSpan(ForegroundColorSpan(COLOR_COMMENT), s, end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            start = end
        }
    }

    private fun applyStringHighlight(sp: SpannableString, code: String) {
        applyRegex(sp, code, Regex("\"([^\"\\\\]|\\\\.)*\""), COLOR_STRING)
        applyRegex(sp, code, Regex("'([^'\\\\]|\\\\.)*'"), COLOR_STRING)
    }

    private fun applyNumberHighlight(sp: SpannableString, code: String) {
        applyRegex(sp, code, Regex("\\b\\d+(\\.\\d+)?([eE][+-]?\\d+)?[fFdDlL]?\\b"), COLOR_NUMBER)
        applyRegex(sp, code, Regex("\\b0x[0-9a-fA-F]+\\b"), COLOR_NUMBER)
    }

    private fun applyAnnotationHighlight(sp: SpannableString, code: String) {
        applyRegex(sp, code, Regex("@[a-zA-Z][a-zA-Z0-9]*"), COLOR_ANNOTATION)
    }

    /** Highlights function/method names (word directly followed by '(') in Cyan. */
    private fun applyFunctionHighlight(sp: SpannableString, code: String) {
        applyRegex(sp, code, Regex("\\b([a-zA-Z_][a-zA-Z0-9_]*)(?=\\s*\\()"), COLOR_FUNCTION)
    }
}
