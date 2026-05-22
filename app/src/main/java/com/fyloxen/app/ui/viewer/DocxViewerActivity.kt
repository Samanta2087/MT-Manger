package com.fyloxen.app.ui.viewer

import android.os.Bundle
import android.util.Xml
import android.view.View
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.fyloxen.app.databinding.ActivityDocxViewerBinding
import com.fyloxen.app.utils.ThemeManager
import kotlinx.coroutines.*
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.StringReader
import java.util.zip.ZipFile

class DocxViewerActivity : AppCompatActivity() {

    companion object { const val EXTRA_FILE_PATH = "extra_file_path" }

    private lateinit var binding: ActivityDocxViewerBinding
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = 0xFF161B22.toInt()

        binding = ActivityDocxViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val path = intent.getStringExtra(EXTRA_FILE_PATH) ?: run { finish(); return }
        val file = File(path)
        binding.tvDocTitle.text = file.name
        binding.btnBack.setOnClickListener { finish() }

        binding.webView.settings.apply {
            javaScriptEnabled = false
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
        }
        binding.webView.setBackgroundColor(0xFFE8E8E8.toInt())
        binding.webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                binding.progressBar.visibility = View.GONE
            }
        }

        scope.launch {
            val html = withContext(Dispatchers.IO) { docxToHtml(file) }
            binding.webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        }
    }

    // ── HTML builder ─────────────────────────────────────────────────────────

    private fun docxToHtml(file: File): String {
        val css = """
            @import url('https://fonts.googleapis.com/css2?family=Noto+Serif:ital,wght@0,400;0,700;1,400;1,700&display=swap');

            * { box-sizing: border-box; margin: 0; padding: 0; }

            html {
                background: #d0d0d0;
                -webkit-text-size-adjust: 100%;
            }

            body {
                margin: 0; padding: 0;
                background: #d0d0d0;
                display: flex; flex-direction: column; align-items: center;
                min-height: 100vh;
            }

            /* ── Page card: looks like a real sheet of paper ── */
            .page {
                background: #ffffff;
                width: 100%;
                max-width: 680px;
                min-height: 100vh;
                margin: 0 auto;
                padding: 48px 36px 60px 36px;
                box-shadow: 0 2px 24px rgba(0,0,0,0.18), 0 0 1px rgba(0,0,0,0.12);
                font-family: 'Noto Serif', 'Georgia', 'Times New Roman', serif;
                font-size: 14.5px;
                line-height: 1.85;
                color: #1a1a1a;
                word-wrap: break-word;
                overflow-wrap: break-word;
            }

            /* ── Headings ── */
            h1 {
                font-size: 1.75em;
                font-weight: 700;
                color: #111;
                margin: 0 0 14px 0;
                text-align: center;
                letter-spacing: -0.01em;
                line-height: 1.35;
                border-bottom: 2px solid #e0e0e0;
                padding-bottom: 10px;
            }
            h2 {
                font-size: 1.35em;
                font-weight: 700;
                color: #222;
                margin: 28px 0 10px 0;
                line-height: 1.4;
                border-bottom: 1px solid #eee;
                padding-bottom: 5px;
            }
            h3 {
                font-size: 1.1em;
                font-weight: 700;
                color: #333;
                margin: 22px 0 6px 0;
                line-height: 1.45;
            }

            /* ── Paragraphs ── */
            p {
                margin: 0 0 10px 0;
                text-align: justify;
                -webkit-hyphens: auto;
                hyphens: auto;
            }
            p:empty { margin: 0 0 4px 0; }  /* blank lines between paragraphs */

            /* ── Inline formatting ── */
            b, strong { font-weight: 700; }
            i, em { font-style: italic; }
            u { text-decoration: underline; text-decoration-color: #888; text-underline-offset: 2px; }
            s { text-decoration: line-through; color: #777; }

            /* ── Alignment ── */
            .center { text-align: center !important; }
            .right  { text-align: right !important; }

            /* ── Tables ── */
            .tbl-wrap {
                overflow-x: auto;
                -webkit-overflow-scrolling: touch;
                margin: 16px 0;
                border-radius: 4px;
                border: 1px solid #c0c0c0;
            }
            table {
                border-collapse: collapse;
                min-width: 100%;
                font-size: 13px;
                line-height: 1.55;
            }
            th {
                background: #f0f0f0;
                font-weight: 700;
                text-align: center;
                padding: 8px 10px;
                border: 1px solid #bbb;
                color: #222;
                white-space: nowrap;
            }
            td {
                padding: 6px 10px;
                border: 1px solid #ccc;
                vertical-align: top;
                word-break: break-word;
                overflow-wrap: break-word;
                min-width: 50px;
                max-width: 240px;
            }
            tr:nth-child(even) td { background: #fafafa; }

            /* ── Misc ── */
            br { line-height: 1.2; }

            /* ── Mobile responsive tweaks ── */
            @media (max-width: 480px) {
                .page {
                    padding: 28px 18px 40px 18px;
                    box-shadow: none;
                    border-left: none; border-right: none;
                }
                h1 { font-size: 1.5em; }
                table { font-size: 12px; }
                td { max-width: 180px; }
            }
        """.trimIndent()

        val head = """<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=3">
<style>$css</style>
</head>
<body>
<div class="page">"""

        val foot = "</div></body></html>"

        return try {
            val zip = ZipFile(file)
            val xml = zip.getEntry("word/document.xml")
                ?.let { zip.getInputStream(it).bufferedReader(Charsets.UTF_8).readText() }
                ?: return "$head<p style='color:#c00'>Cannot read document content.</p>$foot"
            zip.close()
            "$head${buildBody(xml)}$foot"
        } catch (e: Exception) {
            "$head<p style='color:#c00'>Error reading document: ${e.message}</p>$foot"
        }
    }

    /**
     * Paragraph-buffered XmlPullParser conversion.
     * Buffers each <w:p> so heading level is known before emitting the open tag.
     */
    private fun buildBody(xml: String): String {
        val out = StringBuilder()

        // Per-run formatting state
        var bold = false; var italic = false
        var under = false; var strike = false
        var inText = false

        // Table state
        var inTable = false; var rowNum = 0

        try {
            val xp = Xml.newPullParser()
            xp.setFeature("http://xmlpull.org/v1/doc/features.html#process-namespaces", false)
            xp.setInput(StringReader(xml))

            val pb  = StringBuilder()   // paragraph buffer
            var inP = false
            var hLv = 0; var pAl = ""

            var ev = xp.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                val n = if (ev == XmlPullParser.START_TAG || ev == XmlPullParser.END_TAG) xp.name else ""

                when (ev) {
                    XmlPullParser.START_TAG -> when (n) {
                        "w:p"      -> { inP = true; pb.clear(); hLv = 0; pAl = "" }
                        "w:pStyle" -> {
                            val v = xp.getAttributeValue(null, "w:val") ?: ""
                            hLv = when {
                                v.contains("Heading1", true) || v.equals("Title", true) -> 1
                                v.contains("Heading2", true) -> 2
                                v.contains("Heading3", true) || v.equals("Subtitle", true) -> 3
                                else -> 0
                            }
                        }
                        "w:jc"     -> pAl = xp.getAttributeValue(null, "w:val") ?: ""
                        "w:tbl"    -> { out.append("<div class='tbl-wrap'><table>"); inTable = true; rowNum = 0 }
                        "w:tr"     -> { rowNum++; out.append("<tr>") }
                        "w:tc"     -> out.append(if (rowNum == 1) "<th>" else "<td>")
                        "w:r"      -> { bold = false; italic = false; under = false; strike = false }
                        "w:b"      -> { if (xp.getAttributeValue(null, "w:val") != "0") bold = true }
                        "w:i"      -> { if (xp.getAttributeValue(null, "w:val") != "0") italic = true }
                        "w:u"      -> { if ((xp.getAttributeValue(null, "w:val") ?: "single") != "none") under = true }
                        "w:strike" -> strike = true
                        "w:t"      -> {
                            inText = true
                            if (strike) pb.append("<s>")
                            if (under)  pb.append("<u>")
                            if (italic) pb.append("<i>")
                            if (bold)   pb.append("<b>")
                        }
                        "w:br"     -> pb.append("<br>")
                        "w:tab"    -> pb.append("&emsp;&emsp;")
                    }

                    XmlPullParser.END_TAG -> when (n) {
                        "w:p" -> {
                            if (inP) {
                                val tag = if (hLv in 1..3) "h$hLv" else "p"
                                val cls = when (pAl) {
                                    "center" -> " class='center'"
                                    "right"  -> " class='right'"
                                    else     -> ""
                                }
                                if (inTable) {
                                    out.append("<p style='margin:2px 0'>$pb</p>")
                                } else {
                                    out.append("<$tag$cls>$pb</$tag>")
                                }
                                pb.clear(); inP = false
                            }
                        }
                        "w:tbl" -> { out.append("</table></div>"); inTable = false }
                        "w:tr"  -> out.append("</tr>")
                        "w:tc"  -> out.append(if (rowNum == 1) "</th>" else "</td>")
                        "w:t"   -> {
                            inText = false
                            if (bold)   pb.append("</b>")
                            if (italic) pb.append("</i>")
                            if (under)  pb.append("</u>")
                            if (strike) pb.append("</s>")
                        }
                    }

                    XmlPullParser.TEXT -> {
                        if (inText) pb.append(xp.text.htmlEsc())
                    }
                }
                ev = xp.next()
            }
        } catch (e: Exception) {
            out.append("<p style='color:#c00;font-style:italic'>Parse warning: ${e.message}</p>")
        }

        return out.toString()
    }

    private fun String.htmlEsc() =
        replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}
