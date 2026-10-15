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
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * PTX7 Cycle Count — scanning via the Point Mobile PM86 built-in hardware scanner
 * (keyboard-wedge mode). No camera scanning. Local offline data, paste-to-sheet export.
 *
 * Features: checklist home, wheel/manual count, confirm, per-PAC reference photos
 * (camera or file pick), barcode->PAC reverse-lookup links, and zip backup/restore.
 */
class MainActivity : Activity() {

    private companion object {
        const val TEAL = "#0E7C86"
        const val TEALDK = "#0A5A62"
        const val NAVY = "#13293D"
        const val GREEN = "#1F9E54"
        const val BLUE = "#1764C0"
        const val RED = "#C0291F"
        const val TEXT = "#1B2B3A"
        const val MUTED = "#7C8A97"
        const val HAIRLINE = "#E3E9EE"
        const val SOFTGREEN = "#EAF6EF"
        const val SOFTTEAL = "#E8F3F4"
        const val BG = "#F2F5F8"
        const val CARD = "#FFFFFF"
        const val BTNMUTE = "#EDF1F4"

        const val REQ_CAMERA = 101
        const val REQ_PICK_IMAGE = 102
        const val REQ_EXPORT_BACKUP = 103
        const val REQ_IMPORT_BACKUP = 104
    }

    private lateinit var store: ItemStore
    private lateinit var topBar: TextView
    private lateinit var contentHolder: FrameLayout
    private val mono = Typeface.MONOSPACE

    /** True when the current screen is Home (controls Back = exit vs. Back = go home). */
    private var onHome = true

    /** PAC awaiting a photo result (camera or file pick). */
    private var pendingPhotoPac: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ItemStore(this)
        buildShell()
        showHome()
    }

    override fun onBackPressed() {
        if (onHome) super.onBackPressed() else showHome()
    }

    // ---------- shell ----------

    private fun buildShell() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor(BG))
        }
        topBar = TextView(this).apply {
            text = "CYCLE  COUNT"
            textSize = 19f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            letterSpacing = 0.08f
            typeface = Typeface.create(mono, Typeface.BOLD)
            setBackgroundColor(Color.parseColor(TEAL))
            setOnClickListener { showHome() }
        }
        page.addView(topBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(60)))
        contentHolder = FrameLayout(this)
        page.addView(contentHolder, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(page)
    }

    // ---------- HOME (checklist) ----------

    private fun showHome() {
        onHome = true
        topBar.text = "CYCLE  COUNT"
        val page = column()

        val counted = store.countedTotal(); val total = store.total()
        val below = store.belowMinimum().size

        // progress card
        val header = card()
        val headRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        headRow.addView(text("$counted / $total", 26f, TEXT, true),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        headRow.addView(pill(store.countDate(), SOFTTEAL, TEALDK))
        header.addView(headRow)
        header.addView(space(10))
        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = if (total == 0) 1 else total; progress = counted
        }
        header.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(10)))
        header.addView(space(8))
        header.addView(text(
            "${store.remaining().size} remaining" + if (below > 0) "   ·   $below below min" else "",
            13f, if (below > 0) RED else MUTED, true))
        page.addView(header)
        page.addView(space(14))

        // primary actions
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(bigButton("SCAN", TEAL) { scan(individual = false) },
            LinearLayout.LayoutParams(0, dp(58), 1f).apply { marginEnd = dp(7) })
        actions.addView(bigButton("EXPORT", GREEN) { showExport() },
            LinearLayout.LayoutParams(0, dp(58), 1f).apply { marginStart = dp(7) })
        page.addView(actions)
        page.addView(space(10))

        val sub = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        sub.addView(smallButton("INDIVIDUAL UPDATE") { scan(individual = true) },
            LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginEnd = dp(7) })
        sub.addView(smallButton("MANAGE ITEMS") { showManage() },
            LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginStart = dp(7) })
        page.addView(sub)
        page.addView(space(18))

        page.addView(sectionLabel("ITEMS"))
        page.addView(space(10))
        store.items.forEach { item ->
            page.addView(checklistRow(item))
            page.addView(space(8))
        }
        showPage(page, home = true)
    }

    private fun checklistRow(item: CountItem): View {
        val fill = when {
            item.belowMinimum -> "#FCEEEC"
            item.counted -> SOFTGREEN
            else -> CARD
        }
        val stroke = if (item.belowMinimum) RED else HAIRLINE
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(13), dp(14), dp(13))
            background = rounded(fill, stroke, 14, if (item.belowMinimum) 2 else 1)
            setOnClickListener { showCount(item, individual = false) }
        }
        val check = TextView(this).apply {
            text = if (item.counted) "●" else "○"
            textSize = 20f; typeface = mono
            setTextColor(Color.parseColor(if (item.counted) GREEN else "#C3CDD5"))
        }
        row.addView(check, LinearLayout.LayoutParams(dp(28), ViewGroup.LayoutParams.WRAP_CONTENT))
        val words = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), 0, 0, 0) }
        words.addView(text(item.pac, 13f, BLUE, true))
        words.addView(space(1))
        words.addView(text(item.desc, 13f, TEXT))
        row.addView(words, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(text(if (item.counted) item.todayCount else "—", 19f,
            if (item.belowMinimum) RED else TEXT, true))
        return row
    }

    // ---------- scanning (PM86 keyboard-wedge) ----------

    /**
     * Point Mobile PM86 built-in scanner, keyboard-wedge mode (EmKit ScanSettings:
     * Result type = Keyboard Event, Terminator = LF). The trigger types the PAC into the
     * focused field and sends Enter/LF; we read it and route to the item. No camera.
     * (Camera scanning intentionally removed; can be re-added later if ever needed.)
     */
    private fun scan(individual: Boolean) {
        topBar.text = if (individual) "INDIVIDUAL UPDATE" else "SCAN"
        val page = column()
        page.addView(text("Pull the trigger to scan", 20f, TEXT, true))
        page.addView(space(6))
        page.addView(text("Aim the PM86 at the shelf PAC label and pull the scan trigger.", 13f, MUTED))
        page.addView(space(18))

        val capture = EditText(this).apply {
            hint = "Scan or type a PAC"
            setSingleLine()
            typeface = mono
            textSize = 20f
            background = rounded(CARD, TEAL, 14, 2)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            imeOptions = EditorInfo.IME_ACTION_DONE
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }

        fun submit() {
            val raw = capture.text.toString().trim()
            if (raw.isEmpty()) { toast("No value read."); return }
            val item = store.find(raw)
            if (item != null) showCount(item, individual) else showUnknown(raw, individual)
        }

        capture.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                submit(); true
            } else false
        }
        capture.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) { submit(); true } else false
        }

        page.addView(capture)
        page.addView(space(14))
        page.addView(bigButton("LOOK UP", TEAL) { submit() })
        page.addView(space(10))
        page.addView(smallButton("BACK TO HOME") { showHome() })
        showPage(page)
        capture.requestFocus()
    }

    private fun showUnknown(raw: String, individual: Boolean) {
        val page = column()
        page.addView(text("Code not in list", 20f, TEXT, true))
        page.addView(space(12))
        val c = card()
        c.addView(text("SCANNED", 11f, MUTED, true))
        c.addView(space(4))
        c.addView(text(raw, 22f, RED, true))
        page.addView(c)
        page.addView(space(18))
        page.addView(bigButton("SCAN AGAIN", TEAL) { scan(individual) })
        page.addView(space(8))
        page.addView(bigButton("LINK TO AN ITEM", GREEN) { showLinkBarcode(raw) })
        page.addView(space(8))
        page.addView(bigButton("ADD THIS PAC", BLUE) { showAddItem(prefill = raw) })
        page.addView(space(8))
        page.addView(smallButton("BACK TO HOME") { showHome() })
        showPage(page)
    }

    // ---------- COUNT (wheel + manual) ----------

    private var wheelValues: List<Double> = emptyList()

    private fun showCount(item: CountItem, individual: Boolean) {
        topBar.text = item.pac
        val page = column()

        val ic = card()
        ic.addView(text(item.desc, 17f, TEXT, true))
        ic.addView(space(3))
        ic.addView(text("${item.pac}   ·   ${item.uom.ifBlank { "—" }}", 12f, MUTED))
        ic.addView(space(8))
        val lastStr = if (item.lastCount.isBlank()) "Last  —" else
            "Last  ${item.lastCount}" + (if (item.lastDate.isBlank()) "" else "   (${item.lastDate})")
        val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        chips.addView(pill(lastStr, SOFTTEAL, TEALDK))
        chips.addView(space(6).also { it.layoutParams = LinearLayout.LayoutParams(dp(6), 1) })
        chips.addView(pill("Min ${item.min?.clean() ?: "—"} / Max ${item.max?.clean() ?: "—"}", BTNMUTE, MUTED))
        ic.addView(chips)
        if (item.location.isNotBlank()) {
            ic.addView(space(6))
            ic.addView(pill("📍 ${item.location}", SOFTGREEN, GREEN))
        }
        page.addView(ic)
        page.addView(space(14))

        // inline reference photo (above the count), if one exists
        if (store.hasPhoto(item.pac)) {
            val iv = ImageView(this).apply {
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageURI(Uri.fromFile(store.photoFile(item.pac)))
                background = rounded(CARD, HAIRLINE, 14, 1)
                setOnClickListener { showEditPhotoLocation(item) }
            }
            page.addView(iv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(180)))
            page.addView(space(14))
        }

        page.addView(sectionLabel("TODAY'S COUNT"))
        page.addView(space(10))

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

        val manual = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(wheelValues[startIndex].clean())
            textSize = 24f
            typeface = Typeface.create(mono, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = rounded(CARD, TEAL, 12, 2)
            setPadding(dp(8), dp(10), dp(8), dp(10))
        }
        picker.setOnValueChangedListener { _, _, newVal -> manual.setText(wheelValues[newVal].clean()) }

        val wheelCard = card()
        val wheelRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        wheelRow.addView(picker, LinearLayout.LayoutParams(0, dp(170), 1.1f))
        val right = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0) }
        right.addView(text("MANUAL", 11f, MUTED, true))
        right.addView(space(4))
        right.addView(manual, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)))
        right.addView(space(10))
        val stepRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        stepRow.addView(stepperButton("−") { nudge(manual, -1) },
            LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginEnd = dp(5) })
        stepRow.addView(stepperButton("+") { nudge(manual, +1) },
            LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginStart = dp(5) })
        right.addView(stepRow)
        wheelRow.addView(right, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        wheelCard.addView(wheelRow)
        page.addView(wheelCard)
        page.addView(space(18))

        val footer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        footer.addView(smallButton("CANCEL") { showHome() },
            LinearLayout.LayoutParams(0, dp(56), 1f).apply { marginEnd = dp(7) })
        footer.addView(bigButton("REVIEW", TEAL) {
            val v = manual.text.toString().trim()
            if (v.toDoubleOrNull() == null) { toast("Enter a number."); return@bigButton }
            showConfirm(item, v, individual)
        }, LinearLayout.LayoutParams(0, dp(56), 1f).apply { marginStart = dp(7) })
        page.addView(footer)
        page.addView(space(10))
        page.addView(smallButton("EDIT PHOTO / LOCATION") { showEditPhotoLocation(item) })
        showPage(page)
    }

    private fun nudge(field: EditText, dir: Int) {
        val cur = field.text.toString().toDoubleOrNull() ?: 0.0
        val step = WheelSteps.step(cur)
        field.setText((cur + dir * step).coerceAtLeast(0.0).clean())
    }

    // ---------- CONFIRM ----------

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
        page.addView(space(16))

        val big = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(28), dp(16), dp(28))
            background = rounded(if (below) "#FBEDEC" else SOFTTEAL, accent, 18, 3)
        }
        big.addView(text(value, 68f, accent, true, Gravity.CENTER))
        big.addView(space(8))
        big.addView(text("${item.pac}   ·   ${item.uom.ifBlank { "—" }}", 13f, NAVY, true, Gravity.CENTER))
        big.addView(space(2))
        big.addView(text("Last ${item.lastCount.ifBlank { "—" }}   →   now $value", 14f, NAVY, true, Gravity.CENTER))
        if (below) { big.addView(space(6)); big.addView(text("BELOW MIN ${item.min?.clean()}", 13f, RED, true, Gravity.CENTER)) }
        page.addView(big)
        page.addView(space(20))

        val footer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        footer.addView(smallButton("BACK") { showCount(item, individual) },
            LinearLayout.LayoutParams(0, dp(56), 1f).apply { marginEnd = dp(7) })
        footer.addView(bigButton(if (individual) "SAVE" else "SAVE & NEXT", TEAL) {
            store.setCount(item.pac, value)
            toast("Saved ${item.pac} = $value")
            if (individual) showHome()
            else if (store.remaining().isEmpty()) { toast("All items counted."); showHome() }
            else scan(individual = false)
        }, LinearLayout.LayoutParams(0, dp(56), 1f).apply { marginStart = dp(7) })
        page.addView(footer)
        showPage(page)
    }

    // ---------- MANAGE ----------

    private fun showManage() {
        topBar.text = "MANAGE ITEMS"
        val page = column()
        page.addView(bigButton("ADD ITEM", BLUE) { showAddItem(null) })
        page.addView(space(10))
        page.addView(smallButton("PAIR A BARCODE") { pairBarcode() })
        page.addView(space(8))
        page.addView(smallButton("SHEET LINK") { showUrlSettings() })
        page.addView(space(8))
        page.addView(smallButton("BACKUP / RESTORE") { showBackup() })
        page.addView(space(18))
        page.addView(sectionLabel("TAP AN ITEM TO EDIT OR DELETE"))
        page.addView(space(10))
        store.items.forEach { item ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = rounded(CARD, HAIRLINE, 14, 1)
                setOnClickListener { showEditItem(item) }
            }
            row.addView(text(item.pac + (if (item.userAdded) "   · added" else ""), 13f, BLUE, true))
            row.addView(space(1))
            row.addView(text(item.desc, 13f, TEXT))
            page.addView(row); page.addView(space(8))
        }
        showPage(page)
    }

    private fun showAddItem(prefill: String?) {
        topBar.text = "ADD ITEM"
        val page = column()
        val pac = labeledInput(prefill ?: "")
        val desc = labeledInput("")
        val uom = labeledInput("")
        val min = labeledInput("")
        val max = labeledInput("")
        addLabeled(page, "PAC", pac)
        addLabeled(page, "Description", desc)
        addLabeled(page, "UOM (e.g. Case)", uom)
        addLabeled(page, "Min on hand", min)
        addLabeled(page, "Max on hand", max)
        page.addView(text("Added items must also exist on the master sheet before counts stick.", 11f, MUTED))
        page.addView(space(12))
        page.addView(bigButton("SAVE ITEM", BLUE) {
            val ok = store.addItem(valueOf(pac), valueOf(desc), valueOf(uom),
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
        val desc = labeledInput(item.desc)
        val uom = labeledInput(item.uom)
        val min = labeledInput(item.min?.clean() ?: "")
        val max = labeledInput(item.max?.clean() ?: "")
        addLabeled(page, "Description", desc)
        addLabeled(page, "UOM", uom)
        addLabeled(page, "Min", min)
        addLabeled(page, "Max", max)
        page.addView(bigButton("SAVE CHANGES", TEAL) {
            store.updateItem(item.pac, valueOf(desc), valueOf(uom), valueOf(min).toDoubleOrNull(), valueOf(max).toDoubleOrNull())
            toast("Updated."); showManage()
        })
        page.addView(space(16))

        // linked product barcodes
        page.addView(sectionLabel("LINKED BARCODES"))
        page.addView(space(8))
        val codes = store.barcodesFor(item.pac)
        if (codes.isEmpty()) {
            page.addView(text("None yet. Use 'Pair a Barcode' or scan an unknown code.", 12f, MUTED))
        } else {
            codes.forEach { code ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    background = rounded(CARD, HAIRLINE, 12, 1)
                }
                row.addView(text(code, 13f, TEALDK, true),
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(smallButton("REMOVE") {
                    store.unlinkBarcode(code); toast("Removed."); showEditItem(item)
                }, LinearLayout.LayoutParams(dp(110), dp(42)))
                page.addView(row); page.addView(space(6))
            }
        }
        page.addView(space(16))

        page.addView(bigButton("DELETE ITEM", RED) {
            store.deleteItem(item.pac); toast("Deleted ${item.pac}."); showManage()
        })
        page.addView(space(8))
        page.addView(smallButton("BACK") { showManage() })
        showPage(page)
    }

    private fun showUrlSettings() {
        topBar.text = "SHEET LINK"
        val page = column()
        page.addView(sectionLabel("MASTER SHEET LINK"))
        page.addView(space(10))
        val url = EditText(this).apply {
            setText(store.sheetUrl()); minLines = 3; typeface = mono; textSize = 12f
            background = rounded(CARD, HAIRLINE, 12, 1); setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        page.addView(url)
        page.addView(space(12))
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

        val head = card()
        head.addView(text("$counted / $total counted", 22f, TEXT, true))
        head.addView(space(3))
        head.addView(text(store.countDate(), 13f, MUTED, true))
        if (counted < total) {
            head.addView(space(8))
            head.addView(text("${total - counted} uncounted — those cells stay BLANK.", 12f, RED, true))
        }
        val added = store.userAddedCounted()
        if (added.isNotEmpty()) {
            head.addView(space(8))
            head.addView(text("${added.size} added item(s) counted — confirm they exist on the sheet.", 12f, RED))
        }
        page.addView(head)
        page.addView(space(16))

        page.addView(bigButton("COPY & OPEN SHEET", GREEN) {
            copyColumn(); store.markExported(); openSheet()
        })
        page.addView(space(6))
        page.addView(text("Copies the column and opens the sheet. Tap the new date column and paste.", 11f, MUTED))
        page.addView(space(16))

        page.addView(smallButton("COPY COLUMN ONLY") { copyColumn(); toast("Column copied.") })
        page.addView(space(8))
        page.addView(smallButton("SHARE CSV (BACKUP)") { shareCsv() })
        page.addView(space(18))

        page.addView(sectionLabel("COLUMN PREVIEW"))
        page.addView(space(8))
        val preview = TextView(this).apply {
            text = store.items.joinToString("\n") { "${it.pac}   ${if (it.counted) it.todayCount else "·"}" }
            textSize = 12f; typeface = mono; setTextColor(Color.parseColor(TEXT))
            setPadding(dp(14), dp(12), dp(14), dp(12)); background = rounded(CARD, HAIRLINE, 12, 1)
        }
        page.addView(preview)
        page.addView(space(18))

        page.addView(smallButton("START NEW DAY") { confirmNewDay() })
        page.addView(space(8))
        page.addView(smallButton("BACK TO HOME") { showHome() })
        showPage(page)
    }

    private fun copyColumn() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("PTX7 counts column", store.buildColumnText()))
    }

    private fun openSheet() {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(store.sheetUrl()))) }
        catch (e: Exception) { toast("Could not open sheet link.") }
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
        page.addView(text("Start a new day?", 20f, TEXT, true))
        page.addView(space(12))
        if (!store.wasExported()) {
            page.addView(text("You have NOT exported today's counts yet. Export first so nothing is lost.", 13f, RED, true))
            page.addView(space(14))
            page.addView(bigButton("GO EXPORT FIRST", TEAL) { showExport() })
            page.addView(space(8))
            page.addView(smallButton("CANCEL") { showExport() })
        } else {
            page.addView(text("Today's counts become 'last count' and today's entries clear for a fresh sweep.", 13f, MUTED))
            page.addView(space(16))
            page.addView(bigButton("YES, START NEW DAY", TEAL) {
                store.startNewDay(); toast("New day started."); showHome()
            })
            page.addView(space(8))
            page.addView(smallButton("CANCEL") { showExport() })
        }
        showPage(page)
    }

    // ---------- EDIT PHOTO / LOCATION (camera or file pick; rack wheel) ----------

    private fun showEditPhotoLocation(item: CountItem) {
        topBar.text = item.pac
        val page = column()
        page.addView(text(item.desc, 16f, TEXT, true))
        page.addView(text(item.pac, 12f, MUTED))
        page.addView(space(14))

        // --- Location (rack wheel) ---
        page.addView(sectionLabel("LOCATION"))
        page.addView(space(8))
        val rackOptions = listOf("—", "Rack 1", "Rack 2", "Rack 3", "Rack 4", "Rack 5", "Rack 6")
        val current = item.location.ifBlank { "—" }
        val startIdx = rackOptions.indexOf(current).let { if (it < 0) 0 else it }
        val rackPicker = NumberPicker(this).apply {
            minValue = 0; maxValue = rackOptions.size - 1
            displayedValues = rackOptions.toTypedArray()
            value = startIdx; wrapSelectorWheel = false
            descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
        }
        val rackCard = card(); rackCard.addView(rackPicker,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(150)))
        page.addView(rackCard)
        page.addView(space(8))
        page.addView(bigButton("SAVE LOCATION", GREEN) {
            val sel = rackOptions[rackPicker.value]
            store.setLocation(item.pac, if (sel == "—") "" else sel)
            toast(if (sel == "—") "Location cleared." else "Location set to $sel.")
            showEditPhotoLocation(item)
        })
        page.addView(space(18))

        // --- Photo ---
        page.addView(sectionLabel("PHOTO"))
        page.addView(space(8))
        if (store.hasPhoto(item.pac)) {
            val iv = ImageView(this).apply {
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageURI(Uri.fromFile(store.photoFile(item.pac)))
                background = rounded(CARD, HAIRLINE, 14, 1)
            }
            page.addView(iv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(240)))
            page.addView(space(12))
            page.addView(bigButton("RETAKE PHOTO", TEAL) { takePhoto(item.pac) })
            page.addView(space(8))
            page.addView(bigButton("CHOOSE FROM FILES", BLUE) { pickImage(item.pac) })
            page.addView(space(8))
            page.addView(smallButton("REMOVE PHOTO") {
                store.deletePhoto(item.pac); toast("Photo removed."); showEditPhotoLocation(item)
            })
        } else {
            page.addView(text("No reference photo yet.", 14f, MUTED))
            page.addView(space(12))
            page.addView(bigButton("TAKE PHOTO", TEAL) { takePhoto(item.pac) })
            page.addView(space(8))
            page.addView(bigButton("CHOOSE FROM FILES", BLUE) { pickImage(item.pac) })
        }
        page.addView(space(14))
        page.addView(smallButton("DONE") { showCount(item, false) })
        showPage(page)
    }

    private fun takePhoto(pac: String) {
        pendingPhotoPac = pac
        val file = store.photoFile(pac)
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            .putExtra(MediaStore.EXTRA_OUTPUT, uri)
            .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        if (intent.resolveActivity(packageManager) != null) startActivityForResult(intent, REQ_CAMERA)
        else toast("No camera app found.")
    }

    private fun pickImage(pac: String) {
        pendingPhotoPac = pac
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*"; addCategory(Intent.CATEGORY_OPENABLE) }
        startActivityForResult(Intent.createChooser(intent, "Choose image"), REQ_PICK_IMAGE)
    }


    /** Front-door: scan the product barcode first, then link it to a PAC. */
    private fun pairBarcode() {
        topBar.text = "PAIR A BARCODE"
        val page = column()
        page.addView(text("Scan the product barcode", 18f, TEXT, true))
        page.addView(space(6))
        page.addView(text("Pull the trigger on the box's own barcode, then you'll pick the PAC.", 13f, MUTED))
        page.addView(space(16))
        val capture = EditText(this).apply {
            hint = "Scan or type the product barcode"
            setSingleLine(); typeface = mono; textSize = 18f
            background = rounded(CARD, TEAL, 14, 2); setPadding(dp(16), dp(14), dp(16), dp(14))
            imeOptions = EditorInfo.IME_ACTION_DONE
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        fun go() {
            val raw = capture.text.toString().trim()
            if (raw.isEmpty()) { toast("No value read."); return }
            showLinkBarcode(raw)
        }
        capture.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                go(); true
            } else false
        }
        page.addView(capture)
        page.addView(space(12))
        page.addView(bigButton("NEXT", TEAL) { go() })
        page.addView(space(8))
        page.addView(smallButton("BACK") { showManage() })
        showPage(page)
        capture.requestFocus()
    }

    // ---------- BARCODE -> PAC association ----------

    private fun showLinkBarcode(barcode: String) {
        topBar.text = "LINK BARCODE"
        val page = column()
        val c = card()
        c.addView(text("PRODUCT BARCODE", 11f, MUTED, true)); c.addView(space(4))
        c.addView(text(barcode, 18f, TEALDK, true))
        page.addView(c)
        page.addView(space(14))
        page.addView(text("Scan the PAC label (or type the PAC) to link:", 13f, MUTED))
        page.addView(space(8))
        val pacField = labeledInput("").apply {
            hint = "Scan or type PAC, then Enter"
            imeOptions = EditorInfo.IME_ACTION_DONE
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        fun doLink() {
            val raw = valueOf(pacField)
            // resolve either a PAC or an already-linked barcode to the real PAC
            val resolved = store.find(raw)?.pac ?: raw
            if (store.linkBarcode(barcode, resolved)) {
                toast("Linked → $resolved")
                store.find(resolved)?.let { showCount(it, false) } ?: showHome()
            } else toast("Unknown PAC — add it first.")
        }
        pacField.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                doLink(); true
            } else false
        }
        addLabeled(page, "PAC", pacField)
        page.addView(bigButton("LINK", GREEN) { doLink() })
        page.addView(space(8))
        page.addView(smallButton("CANCEL") { showHome() })
        showPage(page)
        pacField.requestFocus()
    }

    // ---------- BACKUP / RESTORE ----------

    private fun showBackup() {
        topBar.text = "BACKUP / RESTORE"
        val page = column()
        page.addView(text("Portable backup", 18f, TEXT, true))
        page.addView(space(6))
        page.addView(text("Saves items, counts, barcode links, and photos to a .zip you can keep and import into a new version.", 13f, MUTED))
        page.addView(space(16))
        page.addView(bigButton("EXPORT BACKUP (.zip)", GREEN) { startExportBackup() })
        page.addView(space(8))
        page.addView(bigButton("IMPORT BACKUP (.zip)", BLUE) { startImportBackup() })
        page.addView(space(10))
        page.addView(smallButton("BACK") { showManage() })
        showPage(page)
    }

    private fun startExportBackup() {
        val name = "ptx7-cycle-count-backup-" +
            SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date()) + ".zip"
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "application/zip"
            putExtra(Intent.EXTRA_TITLE, name)
        }
        startActivityForResult(intent, REQ_EXPORT_BACKUP)
    }

    private fun startImportBackup() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "application/zip"
        }
        startActivityForResult(Intent.createChooser(intent, "Choose backup .zip"), REQ_IMPORT_BACKUP)
    }

    // ---------- activity results ----------

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK) return
        when (requestCode) {
            REQ_CAMERA -> {
                pendingPhotoPac?.let { pac -> store.find(pac)?.let { showEditPhotoLocation(it) } }
            }
            REQ_PICK_IMAGE -> {
                val uri = data?.data ?: return
                val pac = pendingPhotoPac ?: return
                try {
                    contentResolver.openInputStream(uri)?.use { store.savePhotoFrom(pac, it) }
                    store.find(pac)?.let { showEditPhotoLocation(it) }
                } catch (e: Exception) { toast("Could not import image.") }
            }
            REQ_EXPORT_BACKUP -> {
                val uri = data?.data ?: return
                try {
                    contentResolver.openOutputStream(uri)?.use { store.writeBackup(it) }
                    toast("Backup saved.")
                } catch (e: Exception) { toast("Backup failed: ${e.message}") }
            }
            REQ_IMPORT_BACKUP -> {
                val uri = data?.data ?: return
                try {
                    contentResolver.openInputStream(uri)?.use { store.readBackup(it) }
                    toast("Backup imported.")
                    showHome()
                } catch (e: Exception) { toast("Import failed: ${e.message}") }
            }
        }
    }

    // ---------- helpers ----------

    private fun closestIndex(values: List<Double>, target: Double): Int {
        var best = 0; var bestD = Double.MAX_VALUE
        values.forEachIndexed { i, v -> val d = Math.abs(v - target); if (d < bestD) { bestD = d; best = i } }
        return best
    }

    private fun valueOf(e: EditText) = e.text.toString().trim()

    private fun labeledInput(value: String): EditText = EditText(this).apply {
        setText(value); setSingleLine(); typeface = mono; textSize = 15f
        background = rounded(CARD, HAIRLINE, 10, 1); setPadding(dp(12), dp(9), dp(12), dp(9))
    }

    private fun addLabeled(page: LinearLayout, label: String, field: EditText) {
        page.addView(text(label, 11f, MUTED, true))
        page.addView(space(3))
        page.addView(field, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)))
        page.addView(space(10))
    }

    private fun sectionLabel(s: String) = TextView(this).apply {
        text = s; textSize = 12f; setTextColor(Color.parseColor(MUTED))
        letterSpacing = 0.1f; typeface = Typeface.create(mono, Typeface.BOLD)
    }

    private fun pill(label: String, fill: String, textColor: String) = TextView(this).apply {
        text = label; textSize = 11f; typeface = Typeface.create(mono, Typeface.BOLD)
        setTextColor(Color.parseColor(textColor))
        setPadding(dp(10), dp(5), dp(10), dp(5))
        background = rounded(fill, fill, 20, 0)
    }

    private fun showPage(view: View, home: Boolean = false) {
        onHome = home
        if (!home && !topBar.text.startsWith("‹")) topBar.text = "‹  ${topBar.text}"
        contentHolder.removeAllViews()
        val scroll = ScrollView(this).apply { isFillViewport = true; setBackgroundColor(Color.parseColor(BG)) }
        scroll.addView(view)
        contentHolder.addView(scroll, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun column() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(18), dp(18), dp(44))
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16))
        background = rounded(CARD, HAIRLINE, 16, 1)
    }

    private fun bigButton(label: String, color: String, action: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 15f; setTextColor(Color.WHITE)
        letterSpacing = 0.04f
        typeface = Typeface.create(mono, Typeface.BOLD); background = rounded(color, color, 14, 0)
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56))
    }

    private fun smallButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 13f; setTextColor(Color.parseColor(NAVY))
        typeface = Typeface.create(mono, Typeface.BOLD); background = rounded(BTNMUTE, HAIRLINE, 14, 1)
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48))
    }

    private fun stepperButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 22f; setTextColor(Color.parseColor(TEAL))
        typeface = Typeface.create(mono, Typeface.BOLD); background = rounded(SOFTTEAL, TEAL, 10, 1)
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
