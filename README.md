<div align="center">

<img src="Logo/WhatsApp Image 2026-05-07 at 10.22.59 PM.jpeg" width="120" alt="XYvion Logo" />

# ✦ XYvion File Manager

**A powerful, modern, all-in-one file manager for Android**

[![Android](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/License-MIT-blue?style=for-the-badge)](LICENSE)
[![ExoPlayer](https://img.shields.io/badge/Media-ExoPlayer%20Media3-FF0000?style=for-the-badge&logo=youtube&logoColor=white)](https://developer.android.com/media/media3)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-26-orange?style=for-the-badge)](https://developer.android.com)

> *Browse. Edit. View. Play. All in one app — without limits.*

---

</div>

## 🌟 Overview

**XYvion** is a premium Android file manager built from the ground up with performance, beauty, and functionality at its core. From browsing your storage in a dual-pane layout to editing a 2 GB log file without a single crash — XYvion handles it all.

It replaces the need for 5 separate apps with a single, unified experience:

| Without XYvion | With XYvion ✦ |
|---|---|
| File Manager + PDF Viewer | ✅ Built-in PDF Viewer |
| Separate Video Player | ✅ Universal ExoPlayer |
| Separate Audio Player | ✅ Beautiful Audio Player |
| Separate Text Editor | ✅ Full Code Editor (up to 2 GB!) |
| Separate Image Viewer | ✅ Pinch-to-Zoom Image Viewer |

---

## ✨ Features

### 📁 File Management

- **Dual-Pane Layout** — Browse two directories side-by-side simultaneously
- **Full File Operations** — Copy, Cut, Paste, Rename, Delete, Move, Share
- **Multi-Select** — Long-press any file to enter selection mode; select all or individually
- **Hidden Files Toggle** — Show or hide dotfiles with one tap
- **Sort Options** — Sort by Name, Size, Date Modified, Type (Ascending/Descending)
- **Bookmarks** — Pin your favorite folders for instant access
- **Grid / List View** — Switch between compact list and icon grid layouts
- **Swap Panes** — Instantly swap left ↔ right pane paths
- **History Navigation** — Full Back / Forward navigation history per pane

---

### 📄 PDF Viewer

- **High-Resolution Rendering** — Pages rendered at 2× screen density for crisp zoom
- **Pinch-to-Zoom** — Smooth multi-touch zoom with no quality loss
- **Page Navigation** — Swipe left/right OR use Prev/Next buttons
- **Password-Protected PDFs** — Secure password dialog with retry support
- **External Open** — Appears in "Open with" for PDF files from WhatsApp, Gmail, Drive

---

### 🎬 Video Player

- **Powered by ExoPlayer (Media3)** — Industry-standard, Google-maintained player
- **Universal Codec Support** — MP4, MKV, AVI, WebM, MOV and more
- **Auto Aspect Ratio** — Videos always fill the screen correctly
- **Audio-Only Detection** — MP4 files without a video track show a music UI automatically
- **Full Controls** — Play/Pause, ±10s skip, scrubber bar with timestamps
- **External Open** — Appears in "Open with" for all video files system-wide

---

### 🎵 Audio Player

- **Supports All Formats** — MP3, FLAC, AAC, OGG, WAV, M4A and more
- **Beautiful Music UI** — Animated music-art card, full seek bar, duration display
- **Full Playback Controls** — Play/Pause, Rewind, Fast-Forward, Seek
- **External Open** — Appears in "Open with" for all audio files

---

### 📝 Code / Text Editor — *Edit Up to 2 GB Without Crashing*

- **Streaming Line-Index Engine** — Never loads the full file into RAM
- **RecyclerView-Based Lines** — Ultra-smooth scrolling through millions of lines
- **Line Numbers** — Shown on every line, perfectly synced while scrolling
- **Find & Replace** — Real-time search, match counter, Prev/Next, Replace All
- **Undo / Redo** — Full edit history stack
- **Floating Text Actions** — Cut, Copy, Paste, Select All popup on long-press
- **Adjustable Font Size** — Increase/decrease from the menu
- **Symbol Bar** — Quick-insert `{ } [ ] ( ) ; < > / \` without switching keyboards
- **Progress Save Dialog** — Large file saves show progress % instead of freezing
- **External Open** — Appears as "Edit with XYvion" for `.txt`, `.json`, `.xml`, `.kt`, `.py` etc.

---

### 🖼️ Image Viewer

- **Pinch-to-Zoom & Pan** — Smooth, natural gesture-based zooming and panning
- **All Formats** — JPEG, PNG, WebP, GIF, BMP, HEIC
- **Image Info** — Resolution (W × H) and file size displayed in the header
- **Share Button** — Instantly share to any app via Android FileProvider
- **External Open** — Appears in "Open with" for all image types

---

### 📊 CSV / Spreadsheet Viewer

- **Google Sheets–Style Grid** — Rows and columns with a frozen sticky header row
- **Horizontal Scroll** — Full bidirectional scroll for wide tables
- **TSV Support** — Both comma-separated (`.csv`) and tab-separated (`.tsv`) files
- **External Open** — Registered in Android for `.csv` and `.tsv` MIME types

---

### 📦 Archive Support (ZIP)

- **Single-Tap Extract** — Tap any ZIP to extract immediately
- **Password-Protected ZIPs** — Detects encrypted archives and prompts for password
- **Progress Display** — Shows extraction progress for large archives

---

### 📱 APK Inspector

- **APK Info Dialog** — Shows app name, package ID, version, and permissions before installing
- **One-Tap Install** — Opens Android system installer directly from the info dialog

---

### 🔍 Search

- **Full-Filesystem Search** — Searches recursively across all folders and sub-directories
- **Real-Time Results** — Results stream in as the search runs
- **Open from Results** — Tap any result to open the file directly in the correct viewer

---

### 🎨 UI & Design

- **Glassmorphism Design** — Frosted glass cards, deep dark background, modern aesthetic
- **Gradient Branding** — "XYvion" title rendered with sky-blue → violet → hot-pink gradient
- **Premium App Icon** — Cloud, folder, pencil, and file types on a vibrant gradient background
- **Smooth Animations** — Fade-ins, ripple effects, and micro-interactions throughout
- **Bottom Action Bar** — Floating FAB create button with Back/Forward/Swap/Sort controls
- **File Stats Strip** — Per-pane toggle-able file count and storage stats bar

---

## 🚀 Universal "Open With" Support

XYvion registers itself for every major file type. When any app shares a file, XYvion appears in the Android share sheet:

| File Type | MIME Type(s) | Opens In |
|---|---|---|
| 📄 PDF | `application/pdf` | PDF Viewer |
| 🖼️ Images | `image/*` | Image Viewer |
| 🎬 Videos | `video/*` | Video Player |
| 🎵 Audio | `audio/*` | Audio Player |
| 📊 Spreadsheets | `text/csv`, `text/tab-separated-values` | CSV Viewer |
| 📝 Text / Code | `text/*` | Code Editor |
| 📦 Any File | `*/*` | File Manager |

Works with **WhatsApp**, **Gmail**, **Google Drive**, **Telegram**, **Files by Google**, and any app using Android's standard `ACTION_VIEW` intent.

---

## 🏗️ Architecture

```
XYvion/
├── ui/
│   ├── MainActivity.kt              ← Dual-pane host + intent router + gradient branding
│   ├── pane/
│   │   ├── FilePaneFragment.kt      ← File list, back/forward nav, multi-select
│   │   └── FileClipboard.kt         ← Global copy/cut/paste clipboard state
│   ├── viewer/
│   │   ├── PdfViewerActivity.kt     ← pdfium-android renderer, zoom, password dialog
│   │   ├── VideoPlayerActivity.kt   ← ExoPlayer Media3, audio-only auto-detect
│   │   ├── AudioPlayerActivity.kt   ← MediaPlayer via FileDescriptor
│   │   ├── ImageViewerActivity.kt   ← Custom pinch-to-zoom ZoomableImageView
│   │   └── CsvViewerActivity.kt     ← Grid renderer with frozen header
│   ├── editor/
│   │   ├── FileEditorActivity.kt    ← Editor host: find/replace, undo/redo, menus
│   │   ├── LargeFileEditorEngine.kt ← Block-index streaming engine (2 GB support)
│   │   ├── StreamingLineAdapter.kt  ← Coroutine-based on-demand line loader
│   │   └── LineAdapter.kt           ← Fast in-memory adapter for small files
│   └── search/
│       └── SearchActivity.kt        ← Recursive filesystem search
└── utils/
    ├── FileUtils.kt                  ← Read/write helpers, extension MIME map
    ├── UriUtils.kt                   ← content:// → File path resolution
    └── PermissionHelper.kt           ← Android 10–14 storage permission handling
```

---

## ⚡ Large File Editor — How 2 GB Works Without Crashing

```
File on disk (e.g. 800 MB server.log — 8 million lines)
        │
        ▼ scan once in 64 KB chunks
LargeFileEditorEngine
  ├─ Block Index  → 1 offset per 128 lines → ~500 KB RAM for 8M lines
  ├─ PatchMap     → HashMap<lineNum, text> for edited lines only
  └─ RandomAccessFile → seekable reader (synchronized)
        │  coroutine per ViewHolder
        ▼
StreamingLineAdapter  (RecyclerView)
  └─ Only visible lines (≈ 20) exist in RAM at any moment
  └─ ViewHolder recycled → coroutine cancelled → no stale writes
        │
        ▼
Save → stream to temp file → atomic rename → index rebuild
```

**Result:** ~50 MB RAM used regardless of file size.

---

## 🛠️ Tech Stack

| Component | Technology |
|---|---|
| Language | **Kotlin** |
| UI | **XML Layouts + ViewBinding** |
| Video | **AndroidX Media3 / ExoPlayer 1.3.1** |
| PDF | **pdfium-android 1.9.0** |
| ZIP | **zip4j 2.11.5** |
| Concurrency | **Kotlin Coroutines + lifecycleScope** |
| File Sharing | **androidx.core.content.FileProvider** |
| Design | **Material Components 3 + Custom Glassmorphism** |

---

## 🔑 Permissions

| Permission | Purpose |
|---|---|
| `READ_EXTERNAL_STORAGE` | Browse files on Android ≤ 12 |
| `WRITE_EXTERNAL_STORAGE` | Edit and save files on Android ≤ 12 |
| `MANAGE_EXTERNAL_STORAGE` | Full storage access on Android 13+ |
| `REQUEST_INSTALL_PACKAGES` | Install APK files from the inspector |

---

## 📦 Installation

```bash
# Clone
git clone https://github.com/yourname/xyvion.git
cd xyvion

# Build
./gradlew assembleDebug

# Install via ADB
adb install app/build/outputs/apk/debug/app-debug.apk
```

Or download the latest APK from [Releases](../../releases) and sideload it.

---

## 🗺️ Roadmap

- [ ] Dark / Light theme toggle
- [ ] FTP / SFTP remote storage browsing
- [ ] Archive creation (ZIP, TAR)
- [ ] Hex viewer for binary files
- [ ] Root file access (rooted devices)
- [ ] Cloud storage (Google Drive, OneDrive)
- [ ] Syntax highlighting in code editor

---

## 📄 License

```
MIT License — Copyright (c) 2026 XYvion

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

⭐ Star this repo if XYvion replaced 5 apps for you!

</div>
