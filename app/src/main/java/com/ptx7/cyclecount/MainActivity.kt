package com.ptx7.cyclecount

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import java.io.File

/**
 * PTX7 Cycle Count — full build.
 * Scheme: monospace, teal/white/navy, big type, picker wheel + manual entry, giant confirm.
 * Flow: checklist home -> scan/select -> count (wheel or manual) -> confirm -> save & next.
 * Export: copy vertical column + open the master sheet; user pastes. No backend, offline.
 */
class MainActivity : Activity() {

    private companion object {
        const val TEAL = "#0E7C86"
        const val TEALDK = "#0A5A62"
        const val NAVY = "#13293D"
        const val GREEN = "#209447"
        const val BLUE = "#1764C0"
        const val RED = "#C0291F"
        const val TEXT = "#13293D"
        const val MUTED = "#8794A1"
        const val BORDER = "#0E7C86"
        const val LIGHTBORDER = "#D5DEE4"
        const val SELBG = "#E4F1F2"
        const val BG = "#F4F7F9"
        const val CARD = "#FFFFFF"
        const val BTNMUTE = "#E6EAED"
    }

    private lateinit var store: ItemStore
    private lateinit var topBar: TextView
    private lateinit var contentHolder: FrameLayout
    private val mono = Typeface.MONOSPACE

    // photo capture state
    private var pendingPhotoPac: String? = null
    private val REQ_PHOTO = 42
    private var afterPhoto: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ItemStore(this)
        buildShell()
        showHome()
    }

    // ---------- shell ----------

    private fun buildShell() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor(BG))
        }
        topBar = TextView(this).apply {
            text = "CYCLE  COUNT"
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            typeface = Typeface.create(mono, Typeface.BOLD)
            setBackgroundColor(Color.parseColor(TEAL))
            setOnClickListener { showHome() }
        }
        page.addView(topBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(60)))
        contentHolder = FrameLayout(this)
        page.addView(contentHolder, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(page)
    }

    // ---------- HOME (Option 3: checklist) ----------

    private fun showHome() {
        topBar.text = "CYCLE  COUNT"
        val page = column()

        // progress header
        val counted = store.countedTotal(); val total = store.total()
        val below = store.belowMinimum().size
        val header = card()
        val headRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        headRow.addView(text("$counted / $total", 22f, TEXT, true),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.9f))
        headRow.addView(text(store.countDate(), 13f, MUTED, true))
        header.addView(headRow)
        header.addView(space(8))
        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = if (total == 0) 1 else total; progress = counted
        }
        header.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(12)))
        header.addView(space(6))
        header.addView(text(
            "${store.remaining().size} remaining" + if (below > 0) "   ·   $below below min" else "",
            13f, if (below > 0) RED else MUTED, true))
        page.addView(header)
        page.addView(space(12))

        // action bar: SCAN + EXPORT
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(bigButton("⟐ SCAN", TEAL) { scan(individual = false) },
            LinearLayout.LayoutParams(0, dp(56), 1f).apply { marginEnd = dp(6) })
        actions.addView(bigButton("⇪ EXPORT", GREEN) { showExport() },
            LinearLayout.LayoutParams(0, dp(56), 1f).apply { marginStart = dp(6) })
        page.addView(actions)
        page.addView(space(10))

        val sub = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        sub.addView(smallButton("INDIVIDUAL UPDATE") { scan(individual = true) },
            LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginEnd = dp(6) })
        sub.addView(smallButton("MANAGE ITEMS") { showManage() },
            LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginStart = dp(6) })
        page.addView(sub)
        page.addView(space(14))

        // checklist
        page.addView(text("ITEMS", 13f, NAVY, true))
        page.addView(space(8))
        store.items.forEach { item ->
            page.addView(checklistRow(item))
            page.addView(space(8))
        }
        showPage(page)
    }

    private fun checklistRow(item: CountItem): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = rounded(CARD, if (item.belowMinimum) RED else LIGHTBORDER, 12, if (item.belowMinimum) 2 else 1)
            setOnClickListener { showCount(item, individual = false) }
        }
        // check indicator
        val check = TextView(this).apply {
            text = if (item.counted) "●" else "○"
            textSize = 22f
            typeface = mono
            setTextColor(Color.parseColor(if (item.counted) GREEN else MUTED))
        }
        row.addView(check, LinearLayout.LayoutParams(dp(30), ViewGroup.LayoutParams.WRAP_CONTENT))
        val words = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), 0, 0, 0) }
        words.addView(text(item.pac, 13f, BLUE, true))
        words.addView(text(item.desc, 13f, TEXT))
        row.addView(words, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val value = if (item.counted) item.todayCount else "—"
        row.addView(text(value, 18f, if (item.belowMinimum) RED else TEXT, true))
        return row
    }

    // ---------- scanning ----------

    private fun scan(individual: Boolean) {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_CODE_128, Barcode.FORMAT_CODE_39, Barcode.FORMAT_QR_CODE,
                Barcode.FORMAT_DATA_MATRIX, Barcode.FORMAT_EAN_13, Barcode.FORMAT_UPC_A)
            .enableAutoZoom().build()
        GmsBarcodeScanning.getClient(this, options).startScan()
            .addOnSuccessListener { bc ->
                val raw = bc.rawValue?.trim().orEmpty()
                if (raw.isEmpty()) { toast("No value read."); return@addOnSuccessListener }
                val item = store.find(raw)
                if (item != null) showCount(item, individual) else showUnknown(raw, individual)
            }
            .addOnCanceledListener { }
            .addOnFailureListener { e -> toast("Scanner error: ${e.message}") }
    }

    private fun showUnknown(raw: String, individual: Boolean) {
        val page = column()
        page.addView(text("CODE NOT IN LIST", 20f, TEXT, true))
        page.addView(space(10))
        page.addView(text("Scanned:", 13f, MUTED))
        page.addView(text(raw, 20f, RED, true))
        page.addView(space(16))
        page.addView(bigButton("⟐ SCAN AGAIN", TEAL) { scan(individual) })
        page.addView(space(8))
        page.addView(bigButton("+ ADD THIS PAC", BLUE) { showAddItem(prefill = raw) })
        page.addView(space(8))
        page.addView(smallButton("BACK TO HOME") { showHome() })
        showPage(page)
    }

    // ---------- COUNT (wheel + manual) ----------

    private var wheelValues: List<Double> = emptyList()

    private fun showCount(item: CountItem, individual: Boolean) {
        topBar.text = item.pac
        val page = column()

        // item card
        val ic = card()
        ic.addView(text(item.desc, 16f, TEXT, true))
        ic.addView(text("${item.pac}   ·   ${item.uom.ifBlank { "—" }}", 12f, MUTED))
        val lastStr = if (item.lastCount.isBlank()) "Last: —" else "Last: ${item.lastCount}" +
            (if (item.lastDate.isBlank()) "" else "  (${item.lastDate})")
        ic.addView(text(lastStr, 13f, TEALDK, true))
        val mm = "Min ${item.min?.clean() ?: "—"}   Max ${item.max?.clean() ?: "—"}"
        ic.addView(text(mm, 12f, MUTED))
        page.addView(ic)
        page.addView(space(8))

        // photo (tap to view/add)
        page.addView(photoView(item))
        page.addView(space(12))

        page.addView(text("TODAY'S COUNT", 13f, NAVY, true))
        page.addView(space(8))

        // center the wheel on current count, else last, else min/0
        val center = item.todayCount.toDoubleOrNull()
            ?: item.lastCount.toDoubleOrNull()
            ?: item.min ?: 0.0
        wheelValues = WheelSteps.window(center)
        val startIndex = closestIndex(wheelValues, center)

        val picker = NumberPicker(this).apply {
            minValue = 0
            maxValue = wheelValues.size - 1
            displayedValues = wheelValues.map { it.clean() }.toTypedArray()
            value = startIndex
            wrapSelectorWheel = false
            descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
        }

        // manual entry + steppers
        val manual = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(wheelValues[startIndex].clean())
            textSize = 22f
            typeface = Typeface.create(mono, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = rounded(CARD, BORDER, 12, 2)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        // keep wheel -> manual in sync
        picker.setOnValueChangedListener { _, _, newVal ->
            manual.setText(wheelValues[newVal].clean())
        }

        val wheelRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        wheelRow.addView(picker, LinearLayout.LayoutParams(0, dp(170), 1.1f))
        val right = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), 0, 0, 0) }
        right.addView(text("MANUAL", 11f, MUTED, true))
        right.addView(space(4))
        right.addView(manual, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)))
        right.addView(space(8))
        val stepRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        stepRow.addView(stepperButton("−") { nudge(manual, -1) },
            LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(4) })
        stepRow.addView(stepperButton("+") { nudge(manual, +1) },
            LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginStart = dp(4) })
        right.addView(stepRow)
        wheelRow.addView(right, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        page.addView(wheelRow)
        page.addView(space(16))

        val footer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        footer.addView(smallButton("CANCEL") { showHome() },
            LinearLayout.LayoutParams(0, dp(54), 1f).apply { marginEnd = dp(6) })
        footer.addView(bigButton("REVIEW", TEAL) {
            val v = manual.text.toString().trim()
            if (v.toDoubleOrNull() == null) { toast("Enter a number."); return@bigButton }
            showConfirm(item, v, individual)
        }, LinearLayout.LayoutParams(0, dp(54), 1f).apply { marginStart = dp(6) })
        page.addView(footer)
        showPage(page)
    }

    private fun nudge(field: EditText, dir: Int) {
        val cur = field.text.toString().toDoubleOrNull() ?: 0.0
        val step = WheelSteps.step(cur)
        val next = (cur + dir * step).coerceAtLeast(0.0)
        field.setText(next.clean())
    }

    // ---------- CONFIRM (giant number) ----------

    private fun showConfirm(item: CountItem, value: String, individual: Boolean) {
        topBar.text = "CONFIRM"
        val v = value.toDoubleOrNull()
        val below = item.min != null && v != null && v < item.min!!
        val accent = if (below) RED else TEAL
        val page = column()

        val ic = card()
        ic.addView(text(item.desc, 15f, TEXT, true))
        ic.addView(text(item.pac, 12f, MUTED))
        page.addView(ic)
        page.addView(space(14))

        val big = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(24), dp(16), dp(24))
            background = rounded(if (below) "#FBEDEC" else "#E8F3F4", accent, 16, 3)
        }
        big.addView(text(value, 64f, accent, true, Gravity.CENTER))
        big.addView(space(6))
        big.addView(text("${item.pac}   ·   ${item.uom.ifBlank { "—" }}", 13f, NAVY, true, Gravity.CENTER))
        val lastShown = item.lastCount.ifBlank { "—" }
        big.addView(text("Last $lastShown  →  now $value", 14f, NAVY, true, Gravity.CENTER))
        if (below) big.addView(text("BELOW MIN ${item.min?.clean()}", 13f, RED, true, Gravity.CENTER))
        page.addView(big)
        page.addView(space(18))

        val footer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        footer.addView(smallButton("BACK") { showCount(item, individual) },
            LinearLayout.LayoutParams(0, dp(54), 1f).apply { marginEnd = dp(6) })
        val saveLabel = if (individual) "SAVE" else "SAVE & NEXT"
        footer.addView(bigButton(saveLabel, TEAL) {
            store.setCount(item.pac, value)
            toast("Saved ${item.pac} = $value")
            if (individual) showHome()
            else {
                val next = store.remaining().firstOrNull()
                if (next == null) { toast("All items counted."); showHome() }
                else scan(individual = false)   // keep the scan rhythm going
            }
        }, LinearLayout.LayoutParams(0, dp(54), 1f).apply { marginStart = dp(6) })
        page.addView(footer)
        showPage(page)
    }

    // ---------- photos ----------

    private fun photoView(item: CountItem): View {
        val holder = FrameLayout(this).apply {
            background = rounded("#0E1621", "#0E1621", 14, 0)
            setOnClickListener { capturePhoto(item.pac) { refreshIfOn(item) } }
        }
        val lp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(150))
        if (store.hasPhoto(item.pac)) {
            val iv = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageURI(Uri.fromFile(store.photoFile(item.pac)))
            }
            holder.addView(iv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            holder.addView(text("tap to retake", 10f, "#DDE6EC").apply {
                setPadding(dp(8), dp(4), dp(8), dp(4))
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END))
        } else {
            holder.addView(text("📷  tap to add photo", 13f, "#8AA0AE", true, Gravity.CENTER),
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        holder.layoutParams = lp
        return holder
    }

    private fun refreshIfOn(item: CountItem) { showCount(item, individual = false) }

    private fun capturePhoto(pac: String, after: () -> Unit) {
        val file = store.photoFile(pac)
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        pendingPhotoPac = pac
        afterPhoto = after
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, uri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        if (intent.resolveActivity(packageManager) != null) {
            startActivityForResult(intent, REQ_PHOTO)
        } else {
            toast("No camera app found.")
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PHOTO && resultCode == Activity.RESULT_OK) {
            afterPhoto?.invoke()
        }
    }

    // ---------- MANAGE items ----------

    private fun showManage() {
        topBar.text = "MANAGE ITEMS"
        val page = column()
        page.addView(bigButton("+ ADD ITEM", BLUE) { showAddItem(null) })
        page.addView(space(10))
        page.addView(smallButton("CONNECTION / SHEET URL") { showUrlSettings() })
        page.addView(space(14))
        page.addView(text("TAP AN ITEM TO EDIT / DELETE", 12f, MUTED, true))
        page.addView(space(8))
        store.items.forEach { item ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = rounded(CARD, LIGHTBORDER, 12, 1)
                setOnClickListener { showEditItem(item) }
            }
            row.addView(text(item.pac + (if (item.userAdded) "  (added)" else ""), 13f, BLUE, true))
            row.addView(text(item.desc, 13f, TEXT))
            page.addView(row); page.addView(space(8))
        }
        showPage(page)
    }

    private fun showAddItem(prefill: String?) {
        topBar.text = "ADD ITEM"
        val page = column()
        val pac = labeledInput("PAC", prefill ?: "")
        val desc = labeledInput("Description", "")
        val uom = labeledInput("UOM (e.g. Case)", "")
        val min = labeledInput("Min on hand", "")
        val max = labeledInput("Max on hand", "")
        listOf(pac, desc, uom, min, max).forEach { page.addView(it); page.addView(space(8)) }
        page.addView(text("Added items must also exist on the master sheet before counts stick.", 11f, MUTED))
        page.addView(space(10))
        page.addView(bigButton("SAVE ITEM", BLUE) {
            val ok = store.addItem(
                valueOf(pac), valueOf(desc), valueOf(uom),
                valueOf(min).toDoubleOrNull(), valueOf(max).toDoubleOrNull())
            if (!ok) toast("PAC blank or already exists.") else { toast("Item added."); showManage() }
        })
        page.addView(space(8))
        page.addView(smallButton("CANCEL") { showManage() })
        showPage(page)
    }

    private fun showEditItem(item: CountItem) {
        topBar.text = item.pac
        val page = column()
        val desc = labeledInput("Description", item.desc)
        val uom = labeledInput("UOM", item.uom)
        val min = labeledInput("Min", item.min?.clean() ?: "")
        val max = labeledInput("Max", item.max?.clean() ?: "")
        listOf(desc, uom, min, max).forEach { page.addView(it); page.addView(space(8)) }
        page.addView(bigButton("SAVE CHANGES", TEAL) {
            store.updateItem(item.pac, valueOf(desc), valueOf(uom), valueOf(min).toDoubleOrNull(), valueOf(max).toDoubleOrNull())
            toast("Updated."); showManage()
        })
        page.addView(space(8))
        page.addView(bigButton("DELETE ITEM", RED) {
            store.deleteItem(item.pac); toast("Deleted ${item.pac}."); showManage()
        })
        page.addView(space(8))
        page.addView(smallButton("BACK") { showManage() })
        showPage(page)
    }

    private fun showUrlSettings() {
        topBar.text = "SHEET URL"
        val page = column()
        page.addView(text("MASTER SHEET LINK", 13f, NAVY, true))
        page.addView(space(8))
        val url = EditText(this).apply {
            setText(store.sheetUrl()); minLines = 3; typeface = mono; textSize = 12f
            background = rounded(CARD, LIGHTBORDER, 12, 1); setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        page.addView(url)
        page.addView(space(10))
        page.addView(bigButton("SAVE LINK", TEAL) {
            store.setSheetUrl(url.text.toString()); toast("Saved."); showManage()
        })
        page.addView(space(8))
        page.addView(smallButton("BACK") { showManage() })
        showPage(page)
    }

    // ---------- EXPORT ----------

    private fun showExport() {
        topBar.text = "EXPORT"
        val page = column()
        val counted = store.countedTotal(); val total = store.total()
        page.addView(text("$counted / $total counted", 20f, TEXT, true))
        page.addView(text(store.countDate(), 13f, MUTED, true))
        page.addView(space(8))

        if (counted < total) {
            page.addView(text("${total - counted} item(s) uncounted — their cell will be left BLANK.", 12f, RED, true))
            page.addView(space(10))
        }

        val added = store.userAddedCounted()
        if (added.isNotEmpty()) {
            page.addView(text("Caution: ${added.size} added item(s) counted — confirm they exist on the sheet.", 12f, RED))
            page.addView(space(10))
        }

        // PRIMARY: copy + open sheet
        page.addView(bigButton("⇪  COPY & OPEN SHEET", GREEN) {
            copyColumn()
            store.markExported()
            openSheet()
        })
        page.addView(space(6))
        page.addView(text("Copies the column, opens the sheet. Then tap the new date column and paste.", 11f, MUTED))
        page.addView(space(14))

        page.addView(smallButton("COPY COLUMN ONLY") { copyColumn(); toast("Column copied.") })
        page.addView(space(8))
        page.addView(smallButton("SHARE CSV (BACKUP)") { shareCsv() })
        page.addView(space(16))

        page.addView(text("COLUMN PREVIEW (pastes top-to-bottom)", 12f, MUTED, true))
        page.addView(space(6))
        val preview = TextView(this).apply {
            text = store.items.joinToString("\n") { "${it.pac}  ${if (it.counted) it.todayCount else "(blank)"}" }
            textSize = 12f; typeface = mono; setTextColor(Color.parseColor(TEXT))
            setPadding(dp(12), dp(12), dp(12), dp(12)); background = rounded(CARD, LIGHTBORDER, 12, 1)
        }
        page.addView(preview)
        page.addView(space(16))

        page.addView(smallButton("START NEW DAY (after paste)") { confirmNewDay() })
        page.addView(space(8))
        page.addView(smallButton("BACK TO HOME") { showHome() })
        showPage(page)
    }

    private fun copyColumn() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("PTX7 counts column", store.buildColumnText()))
    }

    private fun openSheet() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(store.sheetUrl())))
        } catch (e: Exception) {
            toast("Could not open sheet link.")
        }
    }

    private fun shareCsv() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, "PTX7 Cycle Count ${store.countDate()}")
            putExtra(Intent.EXTRA_TEXT, store.buildCsv())
        }
        startActivity(Intent.createChooser(intent, "Share counts CSV"))
    }

    private fun confirmNewDay() {
        val page = column()
        page.addView(text("START NEW DAY?", 20f, TEXT, true))
        page.addView(space(10))
        if (!store.wasExported()) {
            page.addView(text("You have NOT exported today's counts yet. Export first so nothing is lost.", 13f, RED, true))
            page.addView(space(12))
            page.addView(smallButton("GO EXPORT FIRST") { showExport() })
            page.addView(space(8))
            page.addView(smallButton("CANCEL") { showExport() })
        } else {
            page.addView(text("Today's counts become 'last count' and today's entries clear for a fresh sweep.", 13f, MUTED))
            page.addView(space(14))
            page.addView(bigButton("YES, START NEW DAY", TEAL) {
                store.startNewDay(); toast("New day started."); showHome()
            })
            page.addView(space(8))
            page.addView(smallButton("CANCEL") { showExport() })
        }
        showPage(page)
    }

    // ---------- helpers ----------

    private fun closestIndex(values: List<Double>, target: Double): Int {
        var best = 0; var bestD = Double.MAX_VALUE
        values.forEachIndexed { i, v -> val d = Math.abs(v - target); if (d < bestD) { bestD = d; best = i } }
        return best
    }

    private fun valueOf(e: EditText) = e.text.toString().trim()

    private fun labeledInput(label: String, value: String): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(text(label, 11f, MUTED, true))
        box.addView(space(2))
        box.addView(EditText(this).apply {
            setText(value); setSingleLine(); typeface = mono; textSize = 15f
            background = rounded(CARD, LIGHTBORDER, 10, 1); setPadding(dp(12), dp(8), dp(12), dp(8))
            tag = "input"
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)))
        return box
    }

    private fun showPage(view: View) {
        contentHolder.removeAllViews()
        val scroll = ScrollView(this).apply { isFillViewport = true; setBackgroundColor(Color.parseColor(BG)) }
        scroll.addView(view)
        contentHolder.addView(scroll, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun column() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(40))
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(14), dp(14), dp(14))
        background = rounded(CARD, BORDER, 14, 2)
    }

    private fun bigButton(label: String, color: String, action: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 15f; setTextColor(Color.WHITE)
        typeface = Typeface.create(mono, Typeface.BOLD); background = rounded(color, color, 12, 0)
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56))
    }

    private fun smallButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 13f; setTextColor(Color.parseColor(NAVY))
        typeface = Typeface.create(mono, Typeface.BOLD); background = rounded(BTNMUTE, LIGHTBORDER, 12, 1)
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48))
    }

    private fun stepperButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 20f; setTextColor(Color.parseColor(TEAL))
        typeface = Typeface.create(mono, Typeface.BOLD); background = rounded("#EDF2F4", BORDER, 10, 1)
        setOnClickListener { action() }
    }

    private fun text(value: String, size: Float, color: String, bold: Boolean = false, g: Int = Gravity.START) =
        TextView(this).apply {
            text = value; textSize = size; gravity = g; setTextColor(Color.parseColor(color))
            typeface = if (bold) Typeface.create(mono, Typeface.BOLD) else mono
        }

    private fun space(h: Int) = Space(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(h)) }

    private fun rounded(fill: String, stroke: String, radius: Int, strokeW: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE; cornerRadius = dp(radius).toFloat()
        setColor(Color.parseColor(fill))
        if (strokeW > 0) setStroke(dp(strokeW), Color.parseColor(stroke))
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
