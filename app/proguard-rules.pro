# ═══════════════════════════════════════════════════════════════════════════════
# Fyloxen — R8 / ProGuard Rules (Release Build)
# ═══════════════════════════════════════════════════════════════════════════════

# ── App entry points ──────────────────────────────────────────────────────────
-keep class com.fyloxen.app.ui.** { *; }
-keep class com.fyloxen.app.model.** { *; }
-keep class com.fyloxen.app.adapter.** { *; }
-keep class com.fyloxen.app.utils.** { *; }

# ── ViewBinding ───────────────────────────────────────────────────────────────
-keepclassmembers class * implements androidx.viewbinding.ViewBinding {
    public static *** inflate(android.view.LayoutInflater);
    public static *** inflate(android.view.LayoutInflater, android.view.ViewGroup, boolean);
    public static *** bind(android.view.View);
}

# ── Kotlin ────────────────────────────────────────────────────────────────────
-keepclassmembers class **$WhenMappings { <fields>; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-dontwarn kotlinx.**

# ── Coroutines ────────────────────────────────────────────────────────────────
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.debug.**

# ── ExoPlayer / Media3 ───────────────────────────────────────────────────────
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.ui.** { *; }
-keep class androidx.media3.common.** { *; }
-dontwarn androidx.media3.**
-dontwarn com.google.android.exoplayer2.**

# ── PDF (pdfium-android) ─────────────────────────────────────────────────────
-keep class com.shockwave.** { *; }
-dontwarn com.shockwave.**

# ── Zip4j ─────────────────────────────────────────────────────────────────────
-keep class net.lingala.zip4j.** { *; }
-dontwarn net.lingala.zip4j.**

# ── AndroidX ─────────────────────────────────────────────────────────────────
-keep class androidx.core.content.FileProvider { *; }
-keep class androidx.core.app.** { *; }
-dontwarn androidx.**

# ── Material ──────────────────────────────────────────────────────────────────
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**

# ── RecyclerView ─────────────────────────────────────────────────────────────
-keep class androidx.recyclerview.widget.RecyclerView$LayoutManager { *; }
-keep class androidx.recyclerview.widget.LinearLayoutManager { *; }

# ── Remove ALL logging in release (performance + security) ───────────────────
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
}

# ── Parcelable ────────────────────────────────────────────────────────────────
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# ── Enum (keep values/ordinals) ───────────────────────────────────────────────
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ── R8 Optimizations ─────────────────────────────────────────────────────────
# Allow R8 to assume getters/setters have no side effects
-allowaccessmodification
# Merge classes that only differ by name (aggressive shrink)
-repackageclasses "f"
# Strip source file names and line numbers from stack traces (smaller + obfuscated)
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable

# ── Suppress noisy third-party warnings ───────────────────────────────────────
-dontwarn org.xmlpull.**
-dontwarn javax.annotation.**
-dontwarn sun.misc.**
-dontwarn java.lang.invoke.**
-dontwarn okio.**
-dontwarn okhttp3.**
