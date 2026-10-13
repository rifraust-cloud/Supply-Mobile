package com.ptx7.cyclecount

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * One item from the cycle count sheet. Stable attributes come from the bundled seed
 * (and any items the user adds for this site); [todayCount] is the current count entered
 * on the phone for the current count day.
 */
data class CountItem(
    val pac: String,
    var desc: String,
    var uom: String,
    var min: Double?,
    var max: Double?,
    var lastCount: String,
    var lastDate: String,
    var todayCount: String = "",
    val userAdded: Boolean = false
) {
    val counted: Boolean get() = todayCount.isNotBlank()

    val belowMinimum: Boolean
        get() {
            val v = todayCount.toDoubleOrNull() ?: return false
            val m = min ?: return false
            return v < m
        }
}

/**
 * Loads the item list (seed + user-added), persists today's counts locally (offline-first),
 * tracks per-PAC photo files, and builds the paste-ready export column.
 * No networking: Build 1 is scan -> count -> export only.
 */
class ItemStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("cycle_count", Context.MODE_PRIVATE)

    val items: MutableList<CountItem> = loadItems()
    private fun byPac(pac: String) = items.firstOrNull { it.pac.equals(pac, true) }

    init {
        rolloverIfNewDay()
        restoreTodayCounts()
    }

    fun countDate(): String = prefs.getString(KEY_DATE, today()) ?: today()

    fun find(scanned: String): CountItem? {
        val key = scanned.trim()
        byPac(key)?.let { return it }
        val upper = key.uppercase(Locale.US)
        return items.firstOrNull { upper.contains(it.pac.uppercase(Locale.US)) }
    }

    fun setCount(pac: String, value: String) {
        byPac(pac)?.let { it.todayCount = value.trim(); persistTodayCounts() }
    }

    fun countedTotal(): Int = items.count { it.counted }
    fun total(): Int = items.size
    fun remaining(): List<CountItem> = items.filter { !it.counted }
    fun belowMinimum(): List<CountItem> = items.filter { it.counted && it.belowMinimum }
    fun isComplete(): Boolean = total() > 0 && countedTotal() == total()

    fun clearToday() {
        items.forEach { it.todayCount = "" }
        prefs.edit().remove(KEY_COUNTS).putString(KEY_DATE, today()).apply()
    }

    // --- item management (this is our site's list) ---

    fun addItem(pac: String, desc: String, uom: String, min: Double?, max: Double?): Boolean {
        if (pac.isBlank() || byPac(pac) != null) return false
        items.add(CountItem(pac.trim(), desc.trim(), uom.trim(), min, max, "", "", userAdded = true))
        persistUserItems()
        return true
    }

    fun deleteItem(pac: String) {
        items.removeAll { it.pac.equals(pac, true) }
        photoFile(pac).takeIf { it.exists() }?.delete()
        persistUserItems()
        persistTodayCounts()
    }

    fun updateItem(pac: String, desc: String, uom: String, min: Double?, max: Double?) {
        byPac(pac)?.apply {
            this.desc = desc.trim(); this.uom = uom.trim(); this.min = min; this.max = max
        }
        persistUserItems()
    }

    // --- photos (local, per PAC) ---

    fun photoDir(): File = File(appContext.filesDir, "photos").apply { mkdirs() }
    fun photoFile(pac: String): File = File(photoDir(), sanitize(pac) + ".jpg")
    fun hasPhoto(pac: String): Boolean = photoFile(pac).exists()
    fun deletePhoto(pac: String) { photoFile(pac).takeIf { it.exists() }?.delete() }

    // --- export (Build 1 Option A: vertical column, blank for uncounted) ---

    /** One value per line, in sheet row order, blank for uncounted. Paste into the dated column. */
    fun buildColumnText(): String =
        items.joinToString("\n") { if (it.counted) it.todayCount else "" }

    /** PAC + count + description, for the app-side confidence check / review. */
    fun buildReviewText(): String {
        val sb = StringBuilder("PAC\tCount\tDescription\n")
        items.forEach {
            sb.append(it.pac).append('\t')
                .append(if (it.counted) it.todayCount else "").append('\t')
                .append(it.desc).append('\n')
        }
        return sb.toString()
    }

    fun buildCsv(): String {
        val sb = StringBuilder("PAC,Description,UOM,Min,Max,${csv(countDate())}\n")
        items.forEach {
            sb.append(csv(it.pac)).append(',')
                .append(csv(it.desc)).append(',')
                .append(csv(it.uom)).append(',')
                .append(csv(it.min?.clean() ?: "")).append(',')
                .append(csv(it.max?.clean() ?: "")).append(',')
                .append(csv(it.todayCount)).append('\n')
        }
        return sb.toString()
    }

    /** Items the user added that may not yet exist on the master sheet (export caution). */
    fun userAddedCounted(): List<CountItem> = items.filter { it.userAdded && it.counted }

    // --- the sheet URL (configurable; defaults to the provided SharePoint link) ---

    fun sheetUrl(): String = prefs.getString(KEY_URL, DEFAULT_SHEET_URL) ?: DEFAULT_SHEET_URL
    fun setSheetUrl(url: String) { prefs.edit().putString(KEY_URL, url.trim()).apply() }

    // --- persistence ---

    private fun persistTodayCounts() {
        val json = JSONObject()
        items.forEach { if (it.counted) json.put(it.pac, it.todayCount) }
        prefs.edit().putString(KEY_COUNTS, json.toString()).putString(KEY_DATE, countDate()).apply()
    }

    private fun restoreTodayCounts() {
        val raw = prefs.getString(KEY_COUNTS, null) ?: return
        val json = JSONObject(raw)
        for (key in json.keys()) byPac(key)?.todayCount = json.optString(key)
    }

    private fun persistUserItems() {
        val arr = JSONArray()
        items.filter { it.userAdded }.forEach {
            arr.put(JSONObject()
                .put("pac", it.pac).put("desc", it.desc).put("uom", it.uom)
                .put("min", it.min?.toString() ?: "").put("max", it.max?.toString() ?: ""))
        }
        prefs.edit().putString(KEY_USER_ITEMS, arr.toString()).apply()
    }

    private fun rolloverIfNewDay() {
        val saved = prefs.getString(KEY_DATE, null)
        if (saved == null) prefs.edit().putString(KEY_DATE, today()).apply()
        // Note: new-day rollover is gated in the UI on the prior day being exported;
        // we do not auto-wipe counts here so an unfinished day is never silently lost.
    }

    /** Advance to a fresh count day (called from UI only after prior day exported). */
    fun startNewDay() {
        items.forEach { it.lastCount = if (it.counted) it.todayCount else it.lastCount
                        it.lastDate = if (it.counted) countDate() else it.lastDate
                        it.todayCount = "" }
        prefs.edit().remove(KEY_COUNTS).putString(KEY_DATE, today())
            .putBoolean(KEY_EXPORTED, false).apply()
    }

    fun markExported() { prefs.edit().putBoolean(KEY_EXPORTED, true).apply() }
    fun wasExported(): Boolean = prefs.getBoolean(KEY_EXPORTED, false)

    private fun loadItems(): MutableList<CountItem> {
        val out = ArrayList<CountItem>()
        // seed (fixed master row order)
        val raw = appContext.resources.openRawResource(R.raw.items_seed)
            .bufferedReader().use { it.readText() }
        val arr = JSONArray(raw)
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(CountItem(
                pac = o.optString("pac"), desc = o.optString("desc"), uom = o.optString("uom"),
                min = o.optString("min").toDoubleOrNull(), max = o.optString("max").toDoubleOrNull(),
                lastCount = o.optString("lastCount"), lastDate = o.optString("lastDate")))
        }
        // user-added (appended after seed, preserving row order)
        prefs.getString(KEY_USER_ITEMS, null)?.let { u ->
            val ua = JSONArray(u)
            for (i in 0 until ua.length()) {
                val o = ua.getJSONObject(i)
                if (out.none { it.pac.equals(o.optString("pac"), true) }) {
                    out.add(CountItem(
                        pac = o.optString("pac"), desc = o.optString("desc"), uom = o.optString("uom"),
                        min = o.optString("min").toDoubleOrNull(), max = o.optString("max").toDoubleOrNull(),
                        lastCount = "", lastDate = "", userAdded = true))
                }
            }
        }
        return out
    }

    private fun csv(v: String) =
        if (v.contains(',') || v.contains('"') || v.contains('\n'))
            "\"" + v.replace("\"", "\"\"") + "\"" else v

    private fun sanitize(s: String) = s.replace(Regex("[^A-Za-z0-9_-]"), "_")

    companion object {
        private const val KEY_COUNTS = "today_counts"
        private const val KEY_DATE = "count_date"
        private const val KEY_USER_ITEMS = "user_items"
        private const val KEY_URL = "sheet_url"
        private const val KEY_EXPORTED = "day_exported"
        const val DEFAULT_SHEET_URL =
            "https://amazon-my.sharepoint.com/:x:/p/horcher/IQCgwIbdGpaiQ6zP7CmsHf7kAYsCKA1XEoeX4HaFIPOroEI?e=pbtsjd"
        private fun today(): String = SimpleDateFormat("M/d/yyyy", Locale.US).format(Date())
    }
}

/** Render 10.0 as "10" but keep 4.5 as "4.5". */
fun Double.clean(): String = if (this % 1.0 == 0.0) toLong().toString() else toString()

/**
 * Smart-step wheel values (option B): 0.25 increments for small values, whole numbers
 * once large, so the wheel stays fast in both regimes. Centered on [current] or [last].
 */
object WheelSteps {
    /** 0.25 increments for small counts, whole numbers once large (option B). */
    fun step(forValue: Double): Double = if (forValue >= 20.0) 1.0 else 0.25

    /**
     * Build a window of candidate values around a center for the picker, using a SINGLE
     * step size chosen from the center value. This keeps large counts (e.g. 38, 100)
     * reachable instead of being capped while filling 0.25 steps from zero.
     */
    fun window(center: Double, radius: Int = 60): List<Double> {
        val s = step(center)
        val c = roundToStep(center, s)
        val start = (c - radius * s).coerceAtLeast(0.0)
        val out = ArrayList<Double>()
        var v = roundToStep(start, s)
        var guard = 0
        while (out.size <= radius * 2 && guard < 5000) {
            out.add(v)
            v = roundToStep(v + s, s)
            guard++
        }
        // Guarantee the exact center value is present so it can be pre-selected.
        if (out.none { Math.abs(it - c) < 1e-9 }) {
            out.add(c)
            out.sort()
        }
        return out
    }

    private fun roundToStep(v: Double, s: Double): Double = Math.round(v / s) * s
}
