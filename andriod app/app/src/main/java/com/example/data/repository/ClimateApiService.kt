package com.example.data.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

import com.example.BuildConfig

/**
 * ClimateApiService - lightweight HTTP client that talks to the EcoPulse
 * Python bot server (bot/server.py) running locally or remotely.
 *
 * No Retrofit/OkHttp dependency needed - uses the standard Java HttpURLConnection
 * which is already available on Android.
 */
object ClimateApiService {

    private const val TAG = "ClimateApiService"

    /**
     * Base URL of the bot server.
     * Value is securely injected at compile time via BuildConfig, based on the build type:
     * - Debug: http://10.0.2.2:8000 (unless overridden in .env)
     * - Release: The production URL defined in your .env file
     */
    val BASE_URL: String = BuildConfig.CLIMATE_API_URL.trimEnd('/')
    private const val TIMEOUT_MS = 60_000 // 60s - local CPU inference can be slow

    data class AskResult(val reply: String, val source: String, val error: String? = null)
    data class HealthResult(val ok: Boolean, val ollamaReachable: Boolean, val error: String? = null)

    /**
     * Ask the Climate Guide a question. Returns the AI reply or an error message.
     * Must be called from a coroutine (suspending).
     */
    suspend fun ask(text: String, userId: Int = 0): AskResult = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("text", text)
                put("user_id", userId)
            }.toString()

            val url = URL("$BASE_URL/ask")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                doOutput = true
            }

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }

            val responseCode = conn.responseCode
            if (responseCode !in 200..299) {
                val errBody = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $responseCode"
                Log.w(TAG, "ask() HTTP error $responseCode: $errBody")
                return@withContext AskResult(
                    reply = buildFallbackReply(text),
                    source = "fallback",
                    error = "HTTP $responseCode"
                )
            }

            val responseBody = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            val json = JSONObject(responseBody)
            AskResult(
                reply = json.optString("reply", "(no reply)"),
                source = json.optString("source", "unknown")
            )
        } catch (e: Exception) {
            Log.w(TAG, "ask() failed: ${e.message}")
            AskResult(
                reply = buildFallbackReply(text),
                source = "fallback",
                error = e.message
            )
        }
    }

    /**
     * Quick health check - returns true if the server is up and Ollama is reachable.
     * Non-blocking, used for status indicators.
     */
    suspend fun health(): HealthResult = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL/health")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3_000
                readTimeout = 3_000
            }
            val code = conn.responseCode
            if (code !in 200..299) return@withContext HealthResult(ok = false, ollamaReachable = false)
            val body = JSONObject(conn.inputStream.bufferedReader().readText())
            conn.disconnect()
            HealthResult(
                ok = true,
                ollamaReachable = body.optBoolean("ollama_reachable", false)
            )
        } catch (e: Exception) {
            HealthResult(ok = false, ollamaReachable = false, error = e.message)
        }
    }

    /**
     * Get a real-time weather/flood alert for a location from the bot server.
     */
    suspend fun getWeatherAlert(location: String): String = withContext(Dispatchers.IO) {
        try {
            val encodedLocation = java.net.URLEncoder.encode(location, "UTF-8")
            val url = URL("$BASE_URL/weather/$encodedLocation")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 15_000
            }
            val code = conn.responseCode
            if (code !in 200..299) return@withContext "Weather data unavailable (HTTP $code)."
            val body = JSONObject(conn.inputStream.bufferedReader().readText())
            conn.disconnect()
            body.optString("alert", "No weather data available for $location.")
        } catch (e: Exception) {
            Log.w(TAG, "getWeatherAlert($location) failed: ${e.message}")
            "Weather data temporarily unavailable. Check your connection to the EcoPulse server."
        }
    }

    /**
     * Clear conversation history for a user on the server.
     */
    suspend fun clearHistory(userId: Int = 0): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL/clear/$userId")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 5_000
                readTimeout = 5_000
            }
            val code = conn.responseCode
            conn.disconnect()
            code in 200..299
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Offline-capable keyword-based fallback for when the server is unreachable.
     * Mirrors the keyword logic originally in EcoPulseViewModel.askAiGuide().
     */
    private fun buildFallbackReply(query: String): String {
        val q = query.lowercase()
        return when {
            q.contains("flood") || q.contains("river") ->
                "Regarding Floods in East Africa: Wet seasons are bringing heavier, " +
                "rapid precipitation. Keep storm drains clear and ensure your household has " +
                "a safe higher-elevation contact point. If you notice blocked culverts, use " +
                "the EcoPulse Map to log a report so NEMA and local community units can clear it! " +
                "(Note: AI server offline — connect the EcoPulse server for real-time responses.)"

            q.contains("osint") || q.contains("satellite") || q.contains("investig") ->
                "OSINT (Open Source Intelligence) is a game-changer for environmental defense. " +
                "By examining satellite bands (like NDVI for vegetation loss), verifying camera " +
                "EXIF tags, and auditing public registry land concessions, we gather undeniable, " +
                "legally viable facts. Your verified audits help NGOs take legal action! " +
                "(Note: AI server offline.)"

            q.contains("mining") || q.contains("gold") ->
                "Artisanal gold mining using mercury threatens river health and poisons " +
                "downstream domestic wells. Auditing mining boundaries ensures local communities " +
                "can map illegal mercury usage hotspots and lobby for chemical-free gravity " +
                "separation techniques. (Note: AI server offline.)"

            q.contains("tree") || q.contains("logging") || q.contains("deforestat") ->
                "Protected forests act as carbon sinks and support wildlife. We audit canopy " +
                "density anomalies and compare logs with suspended forestry concessions to find " +
                "unauthorized operations before major portions of land are cleared. " +
                "(Note: AI server offline.)"

            else ->
                "Habari! I am your AI Climate Guide. Protecting our environment requires " +
                "everyday climate actions like proper waste separation, localized water " +
                "management, and real citizen audits. Check the Investigate tab to start your " +
                "first OSINT challenge! (Note: AI server is offline — start bot/server.py to " +
                "enable full AI responses.)"
        }
    }
}
