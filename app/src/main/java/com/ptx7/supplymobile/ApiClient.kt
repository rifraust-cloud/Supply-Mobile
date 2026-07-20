package com.ptx7.supplymobile

import android.content.Context
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.util.concurrent.Executors

class ApiClient(private val context: Context) {

    private val executor = Executors.newSingleThreadExecutor()
    private val prefs = context.getSharedPreferences("supply_mobile_settings", Context.MODE_PRIVATE)

    fun getBaseUrl(): String = prefs.getString("api_url", "") ?: ""

    fun setBaseUrl(value: String) {
        prefs.edit().putString("api_url", value.trim()).apply()
    }

    fun get(
        action: String,
        params: Map<String, String> = emptyMap(),
        callback: (Result<JSONObject>) -> Unit
    ) {
        executor.execute {
            try {
                val base = getBaseUrl()
                require(base.isNotBlank()) { "Supply Mobile API URL is not configured." }

                val query = buildList {
                    add("action=" + URLEncoder.encode(action, "UTF-8"))
                    params.forEach { (key, value) ->
                        add(URLEncoder.encode(key, "UTF-8") + "=" + URLEncoder.encode(value, "UTF-8"))
                    }
                }.joinToString("&")

                val url = base + if (base.contains("?")) "&" else "?" + query
                callback(Result.success(JSONObject(request(url, "GET", null))))
            } catch (error: Exception) {
                callback(Result.failure(error))
            }
        }
    }

    fun post(payload: JSONObject, callback: (Result<JSONObject>) -> Unit) {
        executor.execute {
            try {
                val base = getBaseUrl()
                require(base.isNotBlank()) { "Supply Mobile API URL is not configured." }
                callback(Result.success(JSONObject(request(base, "POST", payload.toString()))))
            } catch (error: Exception) {
                callback(Result.failure(error))
            }
        }
    }

    private fun request(url: String, method: String, body: String?): String {
        var connection = URL(url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.requestMethod = method
        connection.connectTimeout = 20000
        connection.readTimeout = 30000
        connection.setRequestProperty("Accept", "application/json")

        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.bufferedWriter().use { it.write(body) }
        }

        var code = connection.responseCode

        if (code in 300..399) {
            val redirect = connection.getHeaderField("Location")
            if (!redirect.isNullOrBlank()) {
                connection.disconnect()
                connection = URL(redirect).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 20000
                connection.readTimeout = 30000
                code = connection.responseCode
            }
        }

        val stream = if (code in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream ?: connection.inputStream
        }

        val response = BufferedReader(InputStreamReader(stream)).use { it.readText() }
        connection.disconnect()

        if (code !in 200..299) {
            throw IllegalStateException("API error $code: $response")
        }

        return response
    }
}
