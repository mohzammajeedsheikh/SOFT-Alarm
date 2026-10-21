package com.example.hello

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.OpenableColumns
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    private val dayNames = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: SharedPreferences
    private var alarms: MutableList<Alarm> = mutableListOf()

    // Alarm tab
    private lateinit var clockTime: TextView
    private lateinit var clockDate: TextView
    private lateinit var alarmList: LinearLayout
    private lateinit var fab: TextView
    private lateinit var proBanner: TextView
    private val REQ_PICK = 77
    private var pickedUriCallback: ((String) -> Unit)? = null
    private val timeFmt = SimpleDateFormat("h:mm:ss a", Locale.US)
    private val dateFmt = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())

    // Tabs
    private val panels = mutableListOf<View>()
    private val tabButtons = mutableListOf<ImageView>()

    // Timer tab
    private lateinit var timerDisplay: TextView
    private lateinit var timerSetup: LinearLayout
    private lateinit var timerStartBtn: Button
    private lateinit var etHours: EditText
    private lateinit var etMinutes: EditText
    private lateinit var etSeconds: EditText

    // Stopwatch tab
    private lateinit var swDisplay: TextView
    private lateinit var swStartBtn: Button
    private lateinit var lapList: LinearLayout
    private var swRunning = false
    private var swStartedAt = 0L
    private var swAccum = 0L
    private var lapCount = 0

    // Runs every 250 ms while the screen is visible
    private val ticker = object : Runnable {
        override fun run() {
            updateClock()
            refreshTimer()
            updateStopwatch()
            handler.postDelayed(this, 250)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("softalarm_timer", MODE_PRIVATE)
        buildUi()
        askNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        alarms = AlarmStore.load(this)
        renderAlarms()
        refreshProBanner()
        if (!ProManager.isPro(this) && ProManager.isAwaiting(this)) checkPro(false)
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    // ------------------------------------------------------------------ UI

    private fun lp(w: Int, h: Int, top: Int = 0, bottom: Int = 0): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(w, h)
        p.topMargin = dp(top)
        p.bottomMargin = dp(bottom)
        return p
    }

    private fun buildUi() {
        val root = FrameLayout(this)
        root.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Colors.CREAM, Colors.PEACH, Colors.BLUSH)
        )

        val scroll = ScrollView(this)
        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(dp(16), dp(28), dp(16), dp(120))
        scroll.addView(content, FrameLayout.LayoutParams(MATCH, WRAP))
        root.addView(scroll, FrameLayout.LayoutParams(MATCH, MATCH))

        val title = TextView(this)
        title.text = "SoftAlarm"
        title.textSize = 28f
        title.typeface = Typeface.DEFAULT_BOLD
        title.setTextColor(Colors.PLUM)
        title.gravity = Gravity.CENTER
        content.addView(title, lp(MATCH, WRAP))

        val sub = TextView(this)
        sub.text = "Gentle reminders, wherever you are"
        sub.textSize = 13.5f
        sub.setTextColor(Colors.SOFT_TEXT)
        sub.gravity = Gravity.CENTER
        content.addView(sub, lp(MATCH, WRAP, 2, 18))

        // Icon tab bar: Alarm / Timer / Stopwatch
        val tabBar = LinearLayout(this)
        tabBar.orientation = LinearLayout.HORIZONTAL
        tabBar.gravity = Gravity.CENTER
        val icons = intArrayOf(R.drawable.ic_alarm, R.drawable.ic_timer, R.drawable.ic_stopwatch)
        val names = listOf("Alarm", "Timer", "Stopwatch")
        for (i in icons.indices) {
            val b = ImageView(this)
            b.setImageResource(icons[i])
            b.contentDescription = names[i]
            b.setPadding(dp(14), dp(14), dp(14), dp(14))
            b.setOnClickListener { selectTab(i) }
            tabButtons.add(b)
            val params = LinearLayout.LayoutParams(dp(52), dp(52))
            params.setMargins(dp(5), 0, dp(5), 0)
            tabBar.addView(b, params)
        }
        content.addView(tabBar, lp(MATCH, WRAP, 0, 20))

        panels.add(buildAlarmPanel())
        panels.add(buildTimerPanel())
        panels.add(buildStopwatchPanel())
        for (p in panels) content.addView(p, lp(MATCH, WRAP))

        // Floating + button (only on the Alarm tab)
        fab = TextView(this)
        fab.text = "+"
        fab.textSize = 30f
        fab.setTextColor(Colors.WHITE)
        fab.gravity = Gravity.CENTER
        val circle = GradientDrawable()
        circle.shape = GradientDrawable.OVAL
        circle.setColor(Colors.CORAL)
        fab.background = circle
        fab.elevation = dp(6).toFloat()
        fab.setOnClickListener { showAddDialog() }
        val fabParams = FrameLayout.LayoutParams(dp(58), dp(58), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        fabParams.bottomMargin = dp(28)
        root.addView(fab, fabParams)

        setContentView(root)
        selectTab(0)
    }

    private fun selectTab(index: Int) {
        for (i in panels.indices) {
            panels[i].visibility = if (i == index) View.VISIBLE else View.GONE
            val on = i == index
            tabButtons[i].background = rounded(if (on) Colors.CORAL else Colors.GLASS_LIGHT, 16)
            tabButtons[i].setColorFilter(if (on) Colors.WHITE else Colors.MAUVE)
        }
        fab.visibility = if (index == 0) View.VISIBLE else View.GONE
    }

    private fun smallCaption(text: String): TextView {
        val t = TextView(this)
        t.text = text.uppercase(Locale.US)
        t.textSize = 12f
        t.typeface = Typeface.DEFAULT_BOLD
        t.letterSpacing = 0.08f
        t.setTextColor(Colors.MAUVE)
        return t
    }

    private fun bigDisplay(text: String): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = 48f
        t.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        t.setTextColor(Colors.PLUM)
        t.gravity = Gravity.CENTER
        t.background = rounded(Colors.GLASS_LIGHT, 24)
        t.setPadding(dp(12), dp(34), dp(12), dp(34))
        return t
    }

    // ------------------------------------------------------------------ Alarm tab

    private fun buildAlarmPanel(): LinearLayout {
        val p = LinearLayout(this)
        p.orientation = LinearLayout.VERTICAL

        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.gravity = Gravity.CENTER_HORIZONTAL
        card.background = rounded(Colors.GLASS_LIGHT, 24)
        card.setPadding(dp(20), dp(24), dp(20), dp(24))

        clockTime = TextView(this)
        clockTime.textSize = 38f
        clockTime.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        clockTime.setTextColor(Colors.PLUM)
        card.addView(clockTime, lp(WRAP, WRAP))

        clockDate = TextView(this)
        clockDate.textSize = 13f
        clockDate.setTextColor(Colors.MAUVE)
        card.addView(clockDate, lp(WRAP, WRAP, 4, 0))

        proBanner = TextView(this)
        proBanner.text = "★  Unlock Pro voices"
        proBanner.textSize = 14f
        proBanner.typeface = Typeface.DEFAULT_BOLD
        proBanner.setTextColor(Colors.WHITE)
        proBanner.gravity = Gravity.CENTER
        proBanner.background = rounded(Colors.CORAL, 16)
        proBanner.setPadding(dp(16), dp(12), dp(16), dp(12))
        proBanner.setOnClickListener { showProDialog() }
        p.addView(proBanner, lp(MATCH, WRAP, 0, 14))

        p.addView(card, lp(MATCH, WRAP, 0, 24))
        p.addView(smallCaption("Your alarms"), lp(WRAP, WRAP, 0, 10))

        alarmList = LinearLayout(this)
        alarmList.orientation = LinearLayout.VERTICAL
        p.addView(alarmList, lp(MATCH, WRAP))
        return p
    }

    private fun updateClock() {
        val now = Date()
        clockTime.text = timeFmt.format(now)
        clockDate.text = dateFmt.format(now)
    }

    private fun describeDays(a: Alarm): String {
        if (a.days.isEmpty()) return "Once"
        if (a.days.size == 7) return "Every day"
        return a.days.sorted().joinToString(" ") { dayNames[it] }
    }

    private fun renderAlarms() {
        alarmList.removeAllViews()
        if (alarms.isEmpty()) {
            val empty = TextView(this)
            empty.text = "No alarms yet. Tap + to add a gentle wake-up."
            empty.textSize = 14f
            empty.gravity = Gravity.CENTER
            empty.setTextColor(Colors.MAUVE)
            empty.background = rounded(Colors.GLASS_LIGHT, 18)
            empty.setPadding(dp(16), dp(36), dp(16), dp(36))
            alarmList.addView(empty, lp(MATCH, WRAP))
            return
        }
        val sorted = alarms.sortedWith(compareBy<Alarm>({ it.hour }, { it.minute }))
        for (a in sorted) alarmList.addView(alarmCard(a), lp(MATCH, WRAP, 0, 10))
    }

    private fun alarmCard(a: Alarm): View {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.HORIZONTAL
        card.gravity = Gravity.CENTER_VERTICAL
        card.background = rounded(Colors.GLASS, 18)
        card.setPadding(dp(18), dp(14), dp(12), dp(14))
        card.alpha = if (a.enabled) 1f else 0.5f

        val info = LinearLayout(this)
        info.orientation = LinearLayout.VERTICAL

        val timeText = TextView(this)
        timeText.text = formatTime(a.hour, a.minute)
        timeText.textSize = 22f
        timeText.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        timeText.setTextColor(Colors.PLUM)
        info.addView(timeText)

        val meta = TextView(this)
        val labelPart = if (a.label.isBlank()) "" else a.label + " · "
        meta.text = labelPart + describeDays(a) + " · " + Voices.labelFor(a.voice)
        meta.textSize = 12.5f
        meta.setTextColor(Colors.MAUVE)
        info.addView(meta)

        card.addView(info, LinearLayout.LayoutParams(0, WRAP, 1f))

        val del = TextView(this)
        del.text = "✕"
        del.textSize = 18f
        del.setTextColor(Color.parseColor("#C98B84"))
        del.setPadding(dp(10), dp(6), dp(14), dp(6))
        del.setOnClickListener { deleteAlarm(a) }
        card.addView(del)

        val sw = Switch(this)
        sw.isChecked = a.enabled
        sw.setOnCheckedChangeListener { _, checked ->
            a.enabled = checked
            AlarmStore.save(this, alarms)
            if (checked) {
                AlarmScheduler.schedule(this, a)
                toastNextRing(a)
            } else {
                AlarmScheduler.cancel(this, a.id)
            }
            card.alpha = if (checked) 1f else 0.5f
        }
        card.addView(sw)
        return card
    }

    private fun deleteAlarm(a: Alarm) {
        AlarmScheduler.cancel(this, a.id)
        alarms.remove(a)
        AlarmStore.save(this, alarms)
        renderAlarms()
    }

    private fun toastNextRing(a: Alarm) {
        val t = AlarmScheduler.nextTrigger(a) ?: return
        val mins = (t - System.currentTimeMillis() + 59999) / 60000
        val d = mins / (60 * 24)
        val h = (mins / 60) % 24
        val m = mins % 60
        val parts = mutableListOf<String>()
        if (d > 0) parts.add("$d days")
        if (h > 0) parts.add("$h hours")
        parts.add("$m minutes")
        Toast.makeText(this, "Alarm rings in " + parts.joinToString(" "), Toast.LENGTH_SHORT).show()
    }

    private fun showAddDialog() {
        val now = Calendar.getInstance()
        var hour = now.get(Calendar.HOUR_OF_DAY)
        var minute = now.get(Calendar.MINUTE)
        val selectedDays = mutableSetOf<Int>()

        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(20), dp(8), dp(20), dp(8))

        box.addView(smallCaption("Time"), lp(WRAP, WRAP, 8, 4))
        val timeBtn = pillButton(formatTime(hour, minute), false)
        timeBtn.setOnClickListener {
            TimePickerDialog(this, { _, h, m ->
                hour = h
                minute = m
                timeBtn.text = formatTime(h, m)
            }, hour, minute, false).show()
        }
        box.addView(timeBtn, lp(MATCH, WRAP))

        box.addView(smallCaption("Label"), lp(WRAP, WRAP, 14, 0))
        val labelEt = EditText(this)
        labelEt.hint = "e.g. Morning walk"
        labelEt.setSingleLine(true)
        labelEt.inputType = InputType.TYPE_CLASS_TEXT
        box.addView(labelEt, lp(MATCH, WRAP))

        box.addView(smallCaption("Voice"), lp(WRAP, WRAP, 14, 0))
        val voiceSp = Spinner(this)
        val unlocked = ProManager.isPro(this)
        voiceSp.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            Voices.all.map { if (it.pro && !unlocked) it.label + "  🔒" else it.label }
        )
        box.addView(voiceSp, lp(MATCH, WRAP))

        val fileInfo = TextView(this)
        fileInfo.textSize = 12.5f
        fileInfo.setTextColor(Colors.MAUVE)
        fileInfo.visibility = View.GONE
        box.addView(fileInfo, lp(MATCH, WRAP, 4, 0))

        var pickedUri = ""
        var lastPos = 0
        voiceSp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position == lastPos) return
                val v = Voices.all[position]
                if (v.pro && !ProManager.isPro(this@MainActivity)) {
                    voiceSp.setSelection(lastPos)   // go back to the previous (free) choice
                    showProDialog()
                    return
                }
                lastPos = position
                if (v.key == "custom") {
                    fileInfo.visibility = View.VISIBLE
                    fileInfo.text = if (pickedUri.isEmpty()) "No file chosen yet" else "Selected: " + displayName(pickedUri)
                    pickFile { uri ->
                        pickedUri = uri
                        fileInfo.text = "Selected: " + displayName(uri)
                    }
                } else {
                    fileInfo.visibility = View.GONE
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        box.addView(smallCaption("Repeat"), lp(WRAP, WRAP, 14, 6))
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        val letters = listOf("S", "M", "T", "W", "T", "F", "S")
        for (i in letters.indices) {
            val chip = TextView(this)
            chip.text = letters[i]
            chip.gravity = Gravity.CENTER
            chip.typeface = Typeface.DEFAULT_BOLD
            chip.setTextColor(Colors.MAUVE)
            chip.background = rounded(Colors.SOFT_BTN, 10)
            chip.setPadding(0, dp(10), 0, dp(10))
            chip.setOnClickListener {
                if (selectedDays.contains(i)) {
                    selectedDays.remove(i)
                    chip.setTextColor(Colors.MAUVE)
                    chip.background = rounded(Colors.SOFT_BTN, 10)
                } else {
                    selectedDays.add(i)
                    chip.setTextColor(Colors.WHITE)
                    chip.background = rounded(Colors.CORAL, 10)
                }
            }
            val params = LinearLayout.LayoutParams(0, WRAP, 1f)
            params.setMargins(dp(2), 0, dp(2), 0)
            row.addView(chip, params)
        }
        box.addView(row, lp(MATCH, WRAP))

        val scroller = ScrollView(this)
        scroller.addView(box)

        AlertDialog.Builder(this)
            .setTitle("New alarm")
            .setView(scroller)
            .setPositiveButton("Save") { _, _ ->
                val chosen = Voices.all[voiceSp.selectedItemPosition]
                var voiceKey = chosen.key
                if (chosen.pro && !ProManager.isPro(this)) voiceKey = "default"
                if (voiceKey == "custom" && pickedUri.isEmpty()) voiceKey = "default"
                val alarm = Alarm(
                    AlarmStore.newId(), hour, minute,
                    labelEt.text.toString().trim(),
                    voiceKey,
                    selectedDays.sorted(), true,
                    if (voiceKey == "custom") pickedUri else ""
                )
                alarms.add(alarm)
                AlarmStore.save(this, alarms)
                AlarmScheduler.schedule(this, alarm)
                renderAlarms()
                toastNextRing(alarm)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ------------------------------------------------------------------ Pro unlock + file picker

    private fun refreshProBanner() {
        proBanner.visibility = if (ProManager.isPro(this)) View.GONE else View.VISIBLE
    }

    private fun showProDialog() {
        val proCount = Voices.all.count { it.pro }
        AlertDialog.Builder(this)
            .setTitle("Unlock Pro voices")
            .setMessage(
                "Free forever: the default tone, your own audio file, and 5 voices " +
                    "(Mother, Father, Sister, Son, Teacher) in English and Urdu.\n\n" +
                    "Pro: all " + proCount + " other voices, one-time payment of " + ProManager.PRICE_LABEL +
                    ". You can pay by card or PayPal, shown in your own currency."
            )
            .setPositiveButton("Buy Pro") { _, _ ->
                if (!ProManager.isConfigured()) {
                    Toast.makeText(this, "Payment server is not set up yet (see backend-stub).", Toast.LENGTH_LONG).show()
                } else {
                    ProManager.setAwaiting(this, true)
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ProManager.buyUrl(this))))
                    } catch (e: Exception) {
                        Toast.makeText(this, "No browser found.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("I've paid") { _, _ -> checkPro(true) }
            .setNeutralButton("Restore") { _, _ -> showRestoreDialog() }
            .show()
    }

    private fun checkPro(showMessages: Boolean) {
        if (!ProManager.isConfigured()) return
        ProManager.checkStatus(this) { paid ->
            if (paid == true) {
                ProManager.setPro(this, true)
                ProManager.setAwaiting(this, false)
                refreshProBanner()
                Toast.makeText(this, "Pro unlocked. Thank you!", Toast.LENGTH_LONG).show()
            } else if (showMessages) {
                val msg = if (paid == null) "Could not reach the server. Check your internet."
                else "Payment not found yet. Wait a minute and try again."
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showRestoreDialog() {
        val et = EditText(this)
        et.hint = "Transaction ID from your receipt (txn_...)"
        et.setSingleLine(true)
        val holder = LinearLayout(this)
        holder.setPadding(dp(20), dp(8), dp(20), 0)
        holder.addView(et, LinearLayout.LayoutParams(MATCH, WRAP))
        AlertDialog.Builder(this)
            .setTitle("Restore purchase")
            .setView(holder)
            .setPositiveButton("Restore") { _, _ ->
                val txn = et.text.toString()
                if (txn.isBlank() || !ProManager.isConfigured()) return@setPositiveButton
                ProManager.restore(this, txn) { ok ->
                    if (ok == true) {
                        ProManager.setPro(this, true)
                        refreshProBanner()
                        Toast.makeText(this, "Pro restored. Thank you!", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, "Could not restore with that ID.", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // "My own file": the user picks any audio file from the phone (free for everyone)
    private fun pickFile(onPicked: (String) -> Unit) {
        pickedUriCallback = onPicked
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "audio/*"
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        try {
            startActivityForResult(i, REQ_PICK)
        } catch (e: Exception) {
            Toast.makeText(this, "No file picker found.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PICK && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                // keep access so the alarm can play the file later, even after a restart
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
                // some pickers do not allow it; the file may stop working after a restart
            }
            pickedUriCallback?.invoke(uri.toString())
        }
    }

    private fun displayName(uriStr: String): String {
        try {
            contentResolver.query(Uri.parse(uriStr), null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (c.moveToFirst() && idx >= 0) return c.getString(idx)
            }
        } catch (e: Exception) {
            // ignore
        }
        return "audio file"
    }

    // ------------------------------------------------------------------ Timer tab
    // The timer end time is saved, and a real alarm is set with AlarmManager,
    // so it still rings if the screen is off or the app is closed.

    private fun buildTimerPanel(): LinearLayout {
        val p = LinearLayout(this)
        p.orientation = LinearLayout.VERTICAL

        timerDisplay = bigDisplay("00:00:00")
        p.addView(timerDisplay, lp(MATCH, WRAP, 0, 22))

        timerSetup = LinearLayout(this)
        timerSetup.orientation = LinearLayout.HORIZONTAL
        timerSetup.gravity = Gravity.CENTER
        etHours = timeInput("0")
        etMinutes = timeInput("5")
        etSeconds = timeInput("0")
        val fields = listOf(etHours, etMinutes, etSeconds)
        val captions = listOf("Hours", "Minutes", "Seconds")
        for (i in fields.indices) {
            val col = LinearLayout(this)
            col.orientation = LinearLayout.VERTICAL
            col.gravity = Gravity.CENTER_HORIZONTAL
            col.addView(fields[i], LinearLayout.LayoutParams(dp(78), WRAP))
            val cap = smallCaption(captions[i])
            cap.textSize = 10.5f
            col.addView(cap, lp(WRAP, WRAP, 4, 0))
            val params = LinearLayout.LayoutParams(WRAP, WRAP)
            params.setMargins(dp(5), 0, dp(5), 0)
            timerSetup.addView(col, params)
        }
        p.addView(timerSetup, lp(MATCH, WRAP, 0, 20))

        val controls = LinearLayout(this)
        controls.orientation = LinearLayout.HORIZONTAL
        controls.gravity = Gravity.CENTER
        timerStartBtn = pillButton("Start", true)
        timerStartBtn.setOnClickListener { onTimerStart() }
        val reset = pillButton("Reset", false)
        reset.setOnClickListener { onTimerReset() }
        val bp = LinearLayout.LayoutParams(WRAP, WRAP)
        bp.setMargins(dp(6), 0, dp(6), 0)
        controls.addView(timerStartBtn, bp)
        controls.addView(reset, bp)
        p.addView(controls, lp(MATCH, WRAP))
        return p
    }

    private fun timeInput(start: String): EditText {
        val e = EditText(this)
        e.setText(start)
        e.inputType = InputType.TYPE_CLASS_NUMBER
        e.filters = arrayOf<InputFilter>(InputFilter.LengthFilter(2))
        e.gravity = Gravity.CENTER
        e.textSize = 26f
        e.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        e.setTextColor(Colors.PLUM)
        e.setSelectAllOnFocus(true)
        e.background = rounded(Colors.GLASS, 14)
        e.setPadding(0, dp(10), 0, dp(10))
        return e
    }

    private fun timerEnd(): Long = prefs.getLong("timer_end", 0L)
    private fun timerPaused(): Long = prefs.getLong("timer_paused", 0L)

    private fun onTimerStart() {
        val end = timerEnd()
        val paused = timerPaused()
        val now = System.currentTimeMillis()
        if (end > now) {
            // running -> pause
            AlarmScheduler.cancelTimer(this)
            prefs.edit().putLong("timer_end", 0L).putLong("timer_paused", end - now).apply()
        } else if (paused > 0) {
            // paused -> resume
            val newEnd = now + paused
            prefs.edit().putLong("timer_end", newEnd).putLong("timer_paused", 0L).apply()
            AlarmScheduler.scheduleTimer(this, newEnd)
        } else {
            val h = etHours.text.toString().toLongOrNull() ?: 0L
            val m = etMinutes.text.toString().toLongOrNull() ?: 0L
            val s = etSeconds.text.toString().toLongOrNull() ?: 0L
            val totalMs = (h * 3600 + m * 60 + s) * 1000
            if (totalMs <= 0) {
                Toast.makeText(this, "Please set a time greater than zero.", Toast.LENGTH_SHORT).show()
                return
            }
            val newEnd = now + totalMs
            prefs.edit().putLong("timer_end", newEnd).putLong("timer_paused", 0L).apply()
            AlarmScheduler.scheduleTimer(this, newEnd)
        }
        refreshTimer()
    }

    private fun onTimerReset() {
        AlarmScheduler.cancelTimer(this)
        prefs.edit().putLong("timer_end", 0L).putLong("timer_paused", 0L).apply()
        refreshTimer()
    }

    private fun refreshTimer() {
        val end = timerEnd()
        val paused = timerPaused()
        val now = System.currentTimeMillis()
        if (end > 0 && end <= now) {
            // finished: the alarm service is already ringing
            prefs.edit().putLong("timer_end", 0L).apply()
            timerDisplay.text = formatHMS(0)
            timerSetup.visibility = View.VISIBLE
            timerStartBtn.text = "Start"
            return
        }
        when {
            end > now -> {
                timerDisplay.text = formatHMS((end - now + 999) / 1000)
                timerSetup.visibility = View.GONE
                timerStartBtn.text = "Pause"
            }
            paused > 0 -> {
                timerDisplay.text = formatHMS((paused + 999) / 1000)
                timerSetup.visibility = View.GONE
                timerStartBtn.text = "Resume"
            }
            else -> {
                timerDisplay.text = formatHMS(0)
                timerSetup.visibility = View.VISIBLE
                timerStartBtn.text = "Start"
            }
        }
    }

    // ------------------------------------------------------------------ Stopwatch tab

    private fun buildStopwatchPanel(): LinearLayout {
        val p = LinearLayout(this)
        p.orientation = LinearLayout.VERTICAL

        swDisplay = bigDisplay("00:00:00")
        p.addView(swDisplay, lp(MATCH, WRAP, 0, 22))

        val controls = LinearLayout(this)
        controls.orientation = LinearLayout.HORIZONTAL
        controls.gravity = Gravity.CENTER
        swStartBtn = pillButton("Start", true)
        swStartBtn.setOnClickListener { onSwStart() }
        val lap = pillButton("Lap", false)
        lap.setOnClickListener { onSwLap() }
        val reset = pillButton("Reset", false)
        reset.setOnClickListener { onSwReset() }
        val bp = LinearLayout.LayoutParams(WRAP, WRAP)
        bp.setMargins(dp(5), 0, dp(5), 0)
        controls.addView(swStartBtn, bp)
        controls.addView(lap, bp)
        controls.addView(reset, bp)
        p.addView(controls, lp(MATCH, WRAP))

        lapList = LinearLayout(this)
        lapList.orientation = LinearLayout.VERTICAL
        p.addView(lapList, lp(MATCH, WRAP, 18, 0))
        return p
    }

    private fun swElapsedMs(): Long {
        return swAccum + if (swRunning) SystemClock.elapsedRealtime() - swStartedAt else 0L
    }

    private fun updateStopwatch() {
        swDisplay.text = formatHMS(swElapsedMs() / 1000)
    }

    private fun onSwStart() {
        if (!swRunning) {
            swStartedAt = SystemClock.elapsedRealtime()
            swRunning = true
            swStartBtn.text = "Pause"
        } else {
            swAccum += SystemClock.elapsedRealtime() - swStartedAt
            swRunning = false
            swStartBtn.text = "Start"
        }
    }

    private fun onSwLap() {
        if (!swRunning) return
        lapCount++
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.background = rounded(Colors.GLASS, 12)
        row.setPadding(dp(16), dp(10), dp(16), dp(10))

        val name = TextView(this)
        name.text = "Lap $lapCount"
        name.textSize = 13f
        name.setTextColor(Colors.MAUVE)
        row.addView(name, LinearLayout.LayoutParams(0, WRAP, 1f))

        val time = TextView(this)
        time.text = formatHMS(swElapsedMs() / 1000)
        time.textSize = 14f
        time.typeface = Typeface.MONOSPACE
        time.setTextColor(Colors.PLUM)
        row.addView(time)

        lapList.addView(row, 0, lp(MATCH, WRAP, 0, 8))
    }

    private fun onSwReset() {
        swRunning = false
        swAccum = 0L
        swStartedAt = 0L
        lapCount = 0
        swStartBtn.text = "Start"
        lapList.removeAllViews()
        updateStopwatch()
    }
}
