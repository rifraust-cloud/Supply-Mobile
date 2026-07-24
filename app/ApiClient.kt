package com.ptx7.supplymobile

import android.content.Context
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.util.concurrent.Executors

class ApiClient(context: Context) {

    companion object {
        private const val DEFAULT_BASE_URL =
            "https://script.google.com/macros/s/AKfycbzuxsIT9geteQK7Q1n3d-7PokWbE3msRa9G4mdj-ZyiZJMxSOFki4lwdzF7VELDTp90/exec"

        private const val CONNECT_TIMEOUT_MS = 20_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val MAX_REDIRECTS = 5
    }

    private val appContext = context.applicationContext
    private val executor = Executors.newSingleThreadExecutor()
    private val prefs = appContext.getSharedPreferences(
        "supply_mobile_settings",
        Context.MODE_PRIVATE
    )

    fun getBaseUrl(): String {
        return prefs.getString("api_url", DEFAULT_BASE_URL)
            ?.trim()
            .orEmpty()
            .ifBlank { DEFAULT_BASE_URL }
    }

    fun setBaseUrl(value: String) {
        prefs.edit().putString("api_url", value.trim()).apply()
    }

    fun restoreDefaultBaseUrl() {
        prefs.edit().remove("api_url").apply()
    }

    fun get(
        action: String,
        params: Map<String, String> = emptyMap(),
        callback: (Result<JSONObject>) -> Unit
    ) {
        executor.execute {
            try {
                val base = getBaseUrl()
                require(base.isNotBlank()) {
                    "Supply Mobile API URL is not configured."
                }

                val queryParts = mutableListOf(
                    "action=" + URLEncoder.encode(action, "UTF-8")
                )

                params.forEach { (key, value) ->
                    queryParts.add(
                        URLEncoder.encode(key, "UTF-8") +
                            "=" +
                            URLEncoder.encode(value, "UTF-8")
                    )
                }

                val separator = if (base.contains("?")) "&" else "?"
                val url = base + separator + queryParts.joinToString("&")
                val response = request(url, "GET", null)
                callback(Result.success(parseJson(response)))
            } catch (error: Exception) {
                callback(Result.failure(error))
            }
        }
    }

    fun post(
        payload: JSONObject,
        callback: (Result<JSONObject>) -> Unit
    ) {
        executor.execute {
            try {
                val base = getBaseUrl()
                require(base.isNotBlank()) {
                    "Supply Mobile API URL is not configured."
                }

                val response = request(base, "POST", payload.toString())
                callback(Result.success(parseJson(response)))
            } catch (error: Exception) {
                callback(Result.failure(error))
            }
        }
    }

    private fun request(
        initialUrl: String,
        initialMethod: String,
        initialBody: String?
    ): String {
        var currentUrl = initialUrl
        var currentMethod = initialMethod
        var currentBody = initialBody
        var redirectCount = 0

        while (true) {
            val connection = openConnection(currentUrl, currentMethod, currentBody)
            val code = connection.responseCode

            if (code in 300..399) {
                val redirect = connection.getHeaderField("Location")
                connection.disconnect()

                if (redirect.isNullOrBlank()) {
                    throw IllegalStateException(
                        "API returned redirect $code without a Location header."
                    )
                }

                redirectCount++
                if (redirectCount > MAX_REDIRECTS) {
                    throw IllegalStateException("Too many API redirects.")
                }

                currentUrl = URL(URL(currentUrl), redirect).toString()

                if (
                    code == HttpURLConnection.HTTP_MOVED_TEMP ||
                    code == HttpURLConnection.HTTP_SEE_OTHER ||
                    code == HttpURLConnection.HTTP_MOVED_PERM
                ) {
                    currentMethod = "GET"
                    currentBody = null
                }

                continue
            }

            val stream = if (code in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }

            val response = BufferedReader(
                InputStreamReader(stream, Charsets.UTF_8)
            ).use { it.readText() }

            connection.disconnect()

            if (code !in 200..299) {
                throw IllegalStateException(
                    "Supply Mobile API error $code: ${response.take(600)}"
                )
            }

            if (response.isBlank()) {
                throw IllegalStateException(
                    "Supply Mobile API returned an empty response."
                )
            }

            return response
        }
    }

    private fun openConnection(
        url: String,
        method: String,
        body: String?
    ): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            requestMethod = method
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            useCaches = false

            setRequestProperty("Accept", "application/json")
            setRequestProperty(
                "User-Agent",
                "SupplyMobile/${BuildConfig.VERSION_NAME} Android"
            )

            if (body != null) {
                doOutput = true
                setRequestProperty(
                    "Content-Type",
                    "application/json; charset=UTF-8"
                )
                outputStream.bufferedWriter(Charsets.UTF_8).use {
                    it.write(body)
                }
            }
        }
    }

    private fun parseJson(response: String): JSONObject {
        val trimmed = response.trim()

        if (trimmed.startsWith("<", ignoreCase = true)) {
            throw IllegalStateException(
                "The API returned HTML instead of JSON. " +
                    "Confirm the Apps Script deployment URL ends in /exec."
            )
        }

        return try {
            JSONObject(trimmed)
        } catch (error: Exception) {
            throw IllegalStateException(
                "The API returned invalid JSON: ${trimmed.take(400)}",
                error
            )
        }
    }
}
