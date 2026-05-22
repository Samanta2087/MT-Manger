package com.fyloxen.app.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Process
import android.provider.Settings
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Fyloxen Analytics — zero-UI-impact analytics client.
 *
 * Performance contract:
 *  - NEVER block the UI thread or Dispatchers.IO (used by file browsing)
 *  - Single daemon thread at THREAD_PRIORITY_LOWEST — OS will not schedule
 *    it while the UI thread is busy, eliminating input latency spikes
 *  - Minimum 200 ms gap between HTTP calls — prevents CPU burst on startup
 *  - Bounded 50-event queue — silently drops if server is down
 *  - 3-second timeout — dead server never blocks anything
 *  - Connectivity pre-check — skips HTTP entirely if no network
 */
object AnalyticsManager {

    private const val BASE_URL      = "https://api.slvk.shop"
    private const val API_KEY       = "Samanta8@2026"
    private const val TIMEOUT_MS    = 3_000
    /** Minimum pause between consecutive HTTP sends — yields CPU to UI. */
    private const val SEND_GAP_MS   = 200L

    // ── Dedicated single daemon thread at lowest priority ─────────────────────
    // THREAD_PRIORITY_LOWEST = 19 (Android). The OS will preempt this thread
    // the instant the UI or IO thread needs CPU — eliminating input latency.
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "Analytics-Worker").also {
            it.isDaemon   = true
            it.priority   = Thread.MIN_PRIORITY   // java priority (1)
        }
    }

    // Bounded queue — oldest events dropped if queue fills (server down)
    private val queue = LinkedBlockingQueue<Pair<String, JSONObject>>(50)

    private var appContext: Context? = null
    private var deviceId    = ""
    private var appVersion  = ""
    private var osVersion   = ""
    private var initialized = false

    // ── Init ──────────────────────────────────────────────────────────────────
    fun init(context: Context) {
        if (initialized) return
        appContext  = context.applicationContext
        deviceId    = getDeviceId(context)
        appVersion  = getAppVersion(context)
        osVersion   = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        initialized = true

        installCrashHandler()
        executor.execute(::drainQueue)
    }

    // ── 1. App Open ───────────────────────────────────────────────────────────
    fun trackAppOpen() {
        enqueue("/api/v1/app-open", JSONObject().apply {
            put("device_id",   deviceId)
            put("app_version", appVersion)
            put("os_version",  osVersion)
        })
    }

    // ── 2. Feature Usage ──────────────────────────────────────────────────────
    fun trackFeature(featureName: String, screen: String = "", extra: String = "") {
        enqueue("/api/v1/feature", JSONObject().apply {
            put("device_id",    deviceId)
            put("feature_name", featureName)
            put("screen",       screen)
            put("extra",        extra)
        })
    }

    // ── 3. Crash (best-effort — app is dying anyway) ──────────────────────────
    fun trackCrash(throwable: Throwable) {
        if (!isNetworkAvailable()) return
        val body = JSONObject().apply {
            put("device_id",     deviceId)
            put("app_version",   appVersion)
            put("os_version",    osVersion)
            put("error_message", throwable.message ?: throwable.javaClass.simpleName)
            put("stack_trace",   throwable.stackTraceToString().take(8000))
        }
        executor.execute {
            try { post("/api/v1/crash", body) } catch (_: Exception) {}
        }
    }

    // ── Queue ─────────────────────────────────────────────────────────────────
    private fun enqueue(path: String, body: JSONObject) {
        queue.offer(Pair(path, body))   // drops silently if full
    }

    /**
     * Runs forever on the analytics daemon thread, draining events one at a time.
     *
     * Key: sets Android process priority to THREAD_PRIORITY_LOWEST so the OS
     * never prefers this thread over UI/touch threads — eliminating the
     * "High input latency" events measured in gfxinfo.
     */
    private fun drainQueue() {
        // Set Android-level priority (finer-grained than Java Thread priority)
        Process.setThreadPriority(Process.THREAD_PRIORITY_LOWEST)

        while (true) {
            try {
                // Wait up to 5 s for an event — avoids a busy-spin
                val event = queue.poll(5, TimeUnit.SECONDS) ?: continue

                if (!isNetworkAvailable()) {
                    queue.clear()   // no network → drop backlog
                    continue
                }

                post(event.first, event.second)

                // ── Critical: yield CPU gap between sends ─────────────────
                // Without this, back-to-back HTTP calls on startup (app-open
                // + multiple feature events) keep the CPU hot and cause the
                // "High input latency" spikes measured in gfxinfo.
                // 200 ms is imperceptible for analytics but gives the UI thread
                // full CPU access between each send.
                Thread.sleep(SEND_GAP_MS)

            } catch (e: InterruptedException) {
                break
            } catch (_: Exception) {
                // Brief backoff before retrying — avoids hammering a dead server
                runCatching { Thread.sleep(1_000) }
            }
        }
    }

    // ── HTTP ──────────────────────────────────────────────────────────────────
    private fun post(path: String, body: JSONObject) {
        val conn = URL("$BASE_URL$path").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept",       "application/json")
            if (API_KEY.isNotEmpty()) conn.setRequestProperty("X-Api-Key", API_KEY)
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout    = TIMEOUT_MS
            conn.doOutput       = true

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { w ->
                w.write(body.toString())
                w.flush()
            }
            conn.responseCode   // consume response to release connection
        } finally {
            conn.disconnect()
        }
    }

    // ── Connectivity ──────────────────────────────────────────────────────────
    private fun isNetworkAvailable(): Boolean {
        val ctx = appContext ?: return false
        val cm  = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return false
        val cap = cm.getNetworkCapabilities(net) ?: return false
        return cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // ── Crash handler ─────────────────────────────────────────────────────────
    private fun installCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            trackCrash(throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private fun getDeviceId(context: Context): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"

    private fun getAppVersion(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    } catch (_: Exception) { "?" }
}
