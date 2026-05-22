plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.fyloxen.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.fyloxen.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        // Only ARM ABIs — drops x86/x86_64 (emulator-only) from APK
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }

        // Disable unused language resources (saves ~1 MB in Material/ExoPlayer)
        resourceConfigurations += listOf("en")
    }

    buildTypes {
        release {
            // ── R8 full-mode: shrink + obfuscate + optimize ─────────────────
            isMinifyEnabled   = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Deterministic builds + strip debug info from release APK
            isDebuggable = false
        }
        debug {
            isMinifyEnabled   = false
            isShrinkResources = false
            isDebuggable      = true
            // Faster incremental builds during development
            aaptOptions {  }
        }
    }

    // ── Compiler ─────────────────────────────────────────────────────────────
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        // Free compiler args for extra optimizations
        freeCompilerArgs += listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-Xjvm-default=all"              // default interface methods — smaller bytecode
        )
    }

    buildFeatures {
        viewBinding  = true
        buildConfig  = false    // disable unused BuildConfig class
        aidl         = false    // not used
        renderScript = false    // not used
        shaders      = false    // not used
    }

    // ── Bundle / APK splits ──────────────────────────────────────────────────
    bundle {
        language {
            enableSplit = true  // AAB: serve only device language
        }
        density {
            enableSplit = true  // AAB: serve only screen density resources
        }
        abi {
            enableSplit = true  // AAB: serve only device ABI
        }
    }

    // ── Packaging ────────────────────────────────────────────────────────────
    packaging {
        resources {
            excludes += listOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/*.kotlin_module",
                "META-INF/versions/**",
                "kotlin/reflect/reflect.kotlin_builtins",
                "kotlin/**/*.kotlin_builtins",
                "*.proto",
                "**/*.kotlin_builtins",
                "**/*.kotlin_metadata",
                "DebugProbesKt.bin",        // coroutines debug — not needed in release
            )
        }
        jniLibs {
            useLegacyPackaging = false       // use compressed .so — smaller APK
        }
    }
}

dependencies {
    // Core — pinned to latest stable
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.fragment:fragment-ktx:1.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    // Coroutines — only android runtime, no test or jdk8 overhead
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // ZIP (password-protected archives)
    implementation("net.lingala.zip4j:zip4j:2.11.5")

    // PDF rendering
    implementation("com.github.barteksc:pdfium-android:1.9.0")

    // ExoPlayer — only what we use (no OkHttp, no HLS, no DASH)
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
}
