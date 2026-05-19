<div align="center">

<img src="Logo/WhatsApp Image 2026-05-07 at 10.22.59 PM.jpeg" width="120" alt="FYLOXEN Logo" />

# ✦ FYLOXEN File Manager

**A powerful, modern, all-in-one file manager for Android**

[![Android](https://img.shields.io/badge/Platform-Android%208.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-26-orange?style=for-the-badge)](https://developer.android.com)
[![ExoPlayer](https://img.shields.io/badge/Media-ExoPlayer%20Media3%201.3.1-FF0000?style=for-the-badge&logo=youtube&logoColor=white)](https://developer.android.com/media/media3)
[![License](https://img.shields.io/badge/License-MIT-blue?style=for-the-badge)](LICENSE)

> *Browse. Edit. View. Play. All in one app — without limits.*

---

</div>

## 🌟 Overview

**FYLOXEN** is a premium Android file manager built from the ground up with performance, beauty, and functionality at its core. From browsing your storage in a dual-pane layout to editing a 2 GB log file without a single crash — FYLOXEN handles it all.

It replaces the need for 5+ separate apps with a single, unified experience:

| Without FYLOXEN | With FYLOXEN ✦ |
|---|---|
| File Manager + PDF Viewer | ✅ Built-in PDF Viewer |
| Separate Video Player | ✅ Universal ExoPlayer |
| Separate Audio Player | ✅ Animated Audio Player |
| Separate Text / Code Editor | ✅ Full Code Editor (up to 2 GB!) |
| Separate Image Viewer | ✅ Pinch-to-Zoom Image Viewer |
| Separate DOCX Reader | ✅ Built-in Word Document Viewer |
| Separate Spreadsheet App | ✅ CSV / XLSX / TSV Viewer |

---

## ✨ Features

### 📁 File Management

- **Dual-Pane Layout** — Browse two directories side-by-side simultaneously
- **Full File Operations** — Copy, Cut, Paste, Rename, Delete, Move, Share
- **Multi-Select** — Long-press any file to enter selection mode; select all or individually
- **Visual Selection Bar** — Contextual action bar appears on multi-select with item count
- **Hidden Files Toggle** — Show or hide dotfiles with one tap
- **Sort Options** — Sort by Name, Size, Date Modified, Type (Ascending/Descending)
- **In-Pane Filter** — Live search bar to filter files within the current folder instantly
- **Bookmarks** — Pin your favorite folders for instant access
- **Swap Panes** — Instantly swap left ↔ right pane paths
- **History Navigation** — Full Back / Forward navigation history per pane, with cancellation of stale loads
- **Swipe-to-Refresh** — Pull down to refresh directory contents
- **File Stats Panel** — Toggleable animated panel showing folder/file counts per pane
- **Properties Dialog** — Name, type, path, size, date, read/write status for any file or folder
- **Move Dialog** — In-app folder browser to move files to any location without leaving the app
- **Highlight on Navigate** — Files opened from search are automatically highlighted in the pane

---

### 🎨 Themes — Liquid Glass Design System

FYLOXEN ships with **two premium themes**, switchable at runtime without restarting the app:

| Theme | Description |
|---|---|
| 🌑 **Dark Glass** *(default)* | Deep space dark background with frosted glass cards, neon accent colors |
| 🌕 **Liquid Glass Light** | Ultra-premium frosted light glass, sky-blue gradients, soft shadows |

- Theme is **persisted across app restarts** via `SharedPreferences`
- Toggle via the menu — applies instantly via `Activity.recreate()`
- All Activities (editor, viewers, bottom sheets) apply the correct theme in `onCreate()`

---

### 📄 PDF Viewer

- **High-Resolution Rendering** — Pages rendered at 2× screen density for crisp zoom
- **Pinch-to-Zoom** — Smooth multi-touch zoom with no quality loss
- **Page Navigation** — Swipe left/right OR use Prev/Next buttons
- **Password-Protected PDFs** — Secure password dialog with retry support
- **External Open** — Appears in "Open with" for PDF files from WhatsApp, Gmail, Drive

---

### 📝 DOCX / Word Viewer

- **No external dependencies** — Parses `.docx` files natively using Android's `ZipFile` + `XmlPullParser`
- **WebView Rendering** — Extracted content rendered as styled HTML for clean typography
- **Coroutine-based** — Document parsing runs off the main thread; UI stays smooth
- **External Open** — Appears in "Open with" for `.docx` files from any app

---

### 🎬 Video Player

- **Powered by ExoPlayer (Media3)** — Industry-standard, Google-maintained player
- **Universal Codec Support** — MP4, MKV, AVI, WebM, MOV and more
- **Auto Aspect Ratio** — Videos always fill the screen correctly
- **Audio-Only Detection** — MP4 files without a video track show a music UI automatically
- **Immersive Fullscreen** — Auto-hiding controls with persistent scrubber bar
- **Full Controls** — Play/Pause, ±10s skip, scrubber with timestamps
- **External Open** — Appears in "Open with" for all video files system-wide

---

### 🎵 Audio Player

- **Supports All Formats** — MP3, FLAC, AAC, OGG, WAV, M4A and more
- **Animated Music UI** — Smooth gradient background, full seek bar, duration display
- **Full Playback Controls** — Play/Pause, Rewind, Fast-Forward, Seek
- **External Open** — Appears in "Open with" for all audio files

---

### 📝 Code / Text Editor — *Edit Up to 2 GB Without Crashing*

- **Dual Engine Architecture:**
  - **Small files (< 5 MB)** → `LineAdapter`: Fast in-memory RecyclerView-based editor
  - **Large files (≥ 5 MB)** → `LargeFileEditorEngine` + `StreamingLineAdapter`: Streaming engine, only visible lines in RAM
- **Streaming Line-Index Engine** — 64 KB chunked scan builds a block index (1 offset per 128 lines) at open time; uses ~50 MB RAM regardless of file size
- **RecyclerView-Based Lines** — Ultra-smooth scrolling through millions of lines
- **Syntax Highlighting** — Regex-based real-time highlighting for Kotlin, Python, JavaScript, JSON, XML, HTML, CSS, SQL
- **Line Numbers** — Shown on every line, perfectly synced while scrolling
- **Find & Replace** — Real-time search, match counter (e.g. `3/47`), Prev/Next navigation, Replace All
- **Undo / Redo** — Full 30-state edit history stack (snapshot-based)
- **Floating Text Action Popup** — Cut, Copy, Paste, Select All, Search in Find bar — appears above the current selection with debounced show/hide logic
- **Select All Mode** — Visual highlight across all rows; Cut/Copy/Paste work on the entire file
- **Multi-line Paste** — Pastes text with newlines correctly split across multiple lines in the model
- **Adjustable Font Size** — Increase/decrease (8–32sp) from the editor overflow menu
- **Symbol Bar** — Quick-insert `{ } [ ] ( ) ; < > / \` without switching keyboards
- **Progress Save Dialog** — Large file saves show a `%` progress bar
- **Cursor Position** — `Ln X, Col Y` displayed live in the status bar
- **Modified Indicator** — Visual dot appears in header when unsaved changes exist
- **Unsaved Changes Dialog** — Prompts Save/Discard when leaving with unsaved edits
- **Share File** — Share the current file to any app from the editor menu
- **Read-Only Mode** — Auto-detected; save button disabled, warning shown on open
- **External Open** — Appears as "Edit with FYLOXEN" for `.txt`, `.json`, `.xml`, `.kt`, `.py`, `.js`, `.html`, `.css`, `.md`, `.log` etc.

---

### 🖼️ Image Viewer

- **Pinch-to-Zoom & Pan** — Custom `ZoomableImageView` with smooth, natural gesture-based zooming and panning
- **All Formats** — JPEG, PNG, WebP, GIF, BMP, HEIC
- **Image Info** — Resolution (W × H) and file size displayed in the header
- **Share Button** — Instantly share to any app via Android FileProvider
- **External Open** — Appears in "Open with" for all image types

---

### 📊 CSV / XLSX / Spreadsheet Viewer

- **Google Sheets–Style Grid** — Rows and columns with a frozen sticky header row
- **Horizontal Scroll** — Full bidirectional scroll for wide tables
- **XLSX Support** — Native `.xlsx` parsing via `XlsxParser` (no third-party library)
- **TSV Support** — Both comma-separated (`.csv`) and tab-separated (`.tsv`) files
- **External Open** — Registered in Android for `.csv`, `.tsv`, `.xlsx` MIME types

---

### 📦 Archive Support (ZIP)

- **Single-Tap Extract** — Tap any ZIP to extract immediately
- **Password-Protected ZIPs** — Auto-detects encrypted archives and prompts for password with retry on wrong password
- **Overwrite Confirmation** — Warns if destination folder already exists
- **Progress Toast** — Shows extraction count on success
- **Auto-Navigate** — Opens the extracted folder automatically after completion

---

### 📱 APK Inspector

- **APK Info Dialog** — Shows app name, package ID, version code/name, and permissions before installing
- **One-Tap Install** — Opens Android system installer directly from the info dialog

---

### 🔍 Search — Indexed & Recursive

- **SQLite Search Index** — `SearchIndex` builds an on-device SQLite database of all filenames for instant results
- **Full-Filesystem Search** — Falls back to recursive `File.walkTopDown()` search across all accessible directories
- **Smart Skip** — Ignores system/build directories (`.git`, `node_modules`, `Android/data`, `build/`, etc.)
- **Real-Time Results** — Results stream in as the search runs
- **Open from Results** — Tap any result to open the file directly in the correct viewer
- **Locate in Pane** — "Show in folder" navigates the active pane to the file's directory and highlights it

---

## 🚀 Universal "Open With" Support

FYLOXEN registers itself for every major file type. When any app shares a file, FYLOXEN appears in the Android share sheet:

| File Type | MIME Type(s) | Opens In |
|---|---|---|
| 📄 PDF | `application/pdf` | PDF Viewer |
| 📝 DOCX | `application/vnd.openxmlformats-officedocument.wordprocessingml.document` | DOCX Viewer |
| 🖼️ Images | `image/*` | Image Viewer |
| 🎬 Videos | `video/*` | Video Player |
| 🎵 Audio | `audio/*` | Audio Player |
| 📊 Spreadsheets | `text/csv`, `text/tab-separated-values`, `application/vnd.ms-excel` | CSV/XLSX Viewer |
| 📝 Text / Code | `text/*` | Code Editor |
| 📦 Any File | `*/*` | File Manager |

Works with **WhatsApp**, **Gmail**, **Google Drive**, **Telegram**, **Files by Google**, and any app using Android's standard `ACTION_VIEW` / `ACTION_SEND` intent.

---

## 🏗️ Architecture

```
FYLOXEN (com.mtmanager.lite)
├── ui/
│   ├── MainActivity.kt              ← Dual-pane host · intent router · theme toggle · breadcrumb
│   ├── pane/
│   │   ├── FilePaneFragment.kt      ← File list · back/forward nav · multi-select · ZIP extract · Move dialog
│   │   ├── FileClipboard.kt         ← Global singleton clipboard (copy/cut state)
│   │   └── BreadcrumbAdapter.kt     ← Horizontal scrollable path breadcrumb strip
│   ├── viewer/
│   │   ├── PdfViewerActivity.kt     ← pdfium-android renderer · zoom · password dialog
│   │   ├── VideoPlayerActivity.kt   ← ExoPlayer Media3 · audio-only auto-detect · immersive fullscreen
│   │   ├── AudioPlayerActivity.kt   ← MediaPlayer via FileDescriptor · animated gradient UI
│   │   ├── ImageViewerActivity.kt   ← Custom ZoomableImageView · pinch-to-zoom · share
│   │   ├── CsvViewerActivity.kt     ← Grid renderer · frozen header · XLSX/CSV/TSV
│   │   ├── DocxViewerActivity.kt    ← ZIP+XmlPullParser DOCX parser · WebView renderer
│   │   └── ZoomableImageView.kt     ← Custom View: ScaleGestureDetector + GestureDetector
│   ├── editor/
│   │   ├── FileEditorActivity.kt    ← Editor host · find/replace · undo/redo · floating popup
│   │   ├── LargeFileEditorEngine.kt ← Block-index streaming engine (2 GB · RandomAccessFile)
│   │   ├── StreamingLineAdapter.kt  ← Coroutine-per-ViewHolder on-demand line loader
│   │   ├── LineAdapter.kt           ← In-memory adapter for small files · selection · syntax highlight
│   │   └── SelectionEditText.kt     ← Custom EditText subclass for per-line text selection events
│   └── search/
│       └── SearchActivity.kt        ← Recursive filesystem search · SQLite index query
└── utils/
    ├── FileUtils.kt                  ← Read/write · copy/move/delete · MIME map · formatSize
    ├── UriUtils.kt                   ← content:// → File path resolution (MediaStore + FileProvider)
    ├── PermissionHelper.kt           ← Android 8–14 storage permission handling
    ├── ThemeManager.kt               ← Dark Glass / Liquid Glass Light persisted theme switcher
    ├── SyntaxHighlighter.kt          ← Regex-based live syntax coloring (8 languages)
    ├── SearchIndex.kt                ← SQLite-backed file index · background rebuild · cancellable scan
    ├── XlsxParser.kt                 ← Native XLSX reader (ZIP + XML, no external deps)
    └── ZipUtils.kt                   ← zip4j wrapper · isEncrypted · extract with password
```

---

## ⚡ Large File Editor — How 2 GB Works Without Crashing

```
File on disk  (e.g. 800 MB server.log — 8 million lines)
        │
        ▼  Scanned once in 64 KB chunks  →  O(fileSize/65536) time
LargeFileEditorEngine
  ├─ Block Index   → 1 offset per 128 lines  → ~500 KB RAM for 8M lines
  ├─ PatchMap      → HashMap<lineNum, String> for edited lines only
  └─ RandomAccessFile  → thread-safe seekable reader (synchronized)
        │  one coroutine per visible ViewHolder
        ▼
StreamingLineAdapter  (RecyclerView)
  └─ Only ≈20 visible lines exist in RAM at any moment
  └─ ViewHolder recycled  →  coroutine cancelled  →  no stale writes
        │
        ▼
Save  →  stream to temp file  →  atomic rename  →  index rebuild
```

**Result:** ~50 MB RAM used regardless of file size. File never fully loaded into memory.

---

## 🛠️ Tech Stack

| Component | Technology |
|---|---|
| Language | **Kotlin** |
| UI | **XML Layouts + ViewBinding** |
| Async | **Kotlin Coroutines + lifecycleScope** |
| Video | **AndroidX Media3 / ExoPlayer 1.3.1** |
| PDF | **pdfium-android 1.9.0** |
| ZIP (password) | **zip4j 2.11.5** |
| DOCX | **Native (ZipFile + XmlPullParser + WebView)** |
| XLSX | **Native parser (XlsxParser.kt — no external lib)** |
| Syntax Highlight | **Custom SyntaxHighlighter (SpannableString + Regex)** |
| Search Index | **SQLite (via Android SQLiteDatabase)** |
| File Sharing | **androidx.core.content.FileProvider** |
| Design | **Material Components 3 + Custom Liquid Glass Themes** |

---

## 🔑 Permissions

| Permission | Purpose |
|---|---|
| `READ_EXTERNAL_STORAGE` | Browse files on Android ≤ 12 |
| `WRITE_EXTERNAL_STORAGE` | Edit and save files on Android ≤ 12 |
| `MANAGE_EXTERNAL_STORAGE` | Full storage access on Android 13+ |
| `REQUEST_INSTALL_PACKAGES` | Install APK files from the APK inspector |

---

## 📦 Build & Install

```bash
# Clone
git clone https://github.com/Samanta2087/xyvion.git
cd xyvion

# Build debug APK
./gradlew assembleDebug

# Install via ADB
adb install app/build/outputs/apk/debug/app-debug.apk
```

> **Release builds** use R8 full-mode minification + resource shrinking for a significantly smaller APK.
> Run `./gradlew assembleRelease` to produce an optimized release build.

---

## ⚙️ Build Configuration Highlights

| Setting | Value | Effect |
|---|---|---|
| `minSdk` | 26 (Android 8.0) | |
| `targetSdk` | 34 (Android 14) | |
| `isMinifyEnabled` (release) | ✅ `true` | R8 removes unused code |
| `isShrinkResources` (release) | ✅ `true` | Strips unused res/ files |
| `android.enableR8.fullMode` | ✅ `true` | Aggressive dead-code elimination |
| ABI filters | `armeabi-v7a`, `arm64-v8a` | Excludes x86 native libs (~30% smaller) |
| Java / Kotlin target | **17** | |

---

## 🗺️ Roadmap

- [x] ✅ Dark / Light theme toggle (Liquid Glass)
- [x] ✅ Syntax highlighting in code editor (8 languages)
- [x] ✅ DOCX / Word document viewer
- [x] ✅ XLSX spreadsheet parser
- [x] ✅ SQLite search index
- [x] ✅ Breadcrumb path navigation strip
- [x] ✅ Move-to folder browser dialog
- [ ] Archive creation (ZIP, TAR)
- [ ] Hex viewer for binary files
- [ ] FTP / SFTP remote storage browsing
- [ ] Root file access (rooted devices)
- [ ] Cloud storage (Google Drive, OneDrive)
- [ ] Grid layout view

---

## 📄 License

```
MIT License — Copyright (c) 2026 FYLOXEN

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.
```

---

<div align="center">

Made with ❤️ for Android

⭐ Star this repo if FYLOXEN replaced 5 apps for you!

</div>
