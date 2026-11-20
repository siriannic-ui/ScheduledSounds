package com.example.scheduledsounds

import android.Manifest
import android.app.Activity
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var folderText: TextView
    private lateinit var timesList: LinearLayout
    private lateinit var weekdaysBox: CheckBox
    private lateinit var minutesField: EditText
    private lateinit var errorText: TextView

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(32))
        }
        setContentView(ScrollView(this).apply { addView(col) })

        errorText = TextView(this).apply {
            setTextIsSelectable(true)
            setTextColor(0xFFB00020.toInt())
            textSize = 13f
        }
        col.addView(errorText)

        fun label(text: String, size: Float = 16f) = TextView(this).apply {
            this.text = text
            textSize = size
            setPadding(0, dp(12), 0, dp(4))
        }

        // 1. Folder
        col.addView(label("1. Music folder", 20f))
        folderText = TextView(this)
        col.addView(folderText)
        col.addView(Button(this).apply {
            text = "Choose folder"
            setOnClickListener {
                startActivityForResult(
                    Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                    ),
                    REQ_FOLDER
                )
            }
        })

        // 2. Times
        col.addView(label("2. Play times", 20f))
        timesList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(timesList)
        col.addView(Button(this).apply {
            text = "Add a time"
            setOnClickListener {
                TimePickerDialog(this@MainActivity, { _, h, m ->
                    val key = String.format("%02d:%02d", h, m)
                    Prefs.addTime(this@MainActivity, key)
                    Scheduler.scheduleOne(this@MainActivity, key)
                    askExactAlarmIfNeeded()
                    refresh()
                }, 12, 0, true).show()
            }
        })

        // 3. Settings
        col.addView(label("3. Settings", 20f))
        col.addView(label("Play length (minutes)"))
        minutesField = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(Prefs.minutes(this@MainActivity).toString())
        }
        col.addView(minutesField)
        weekdaysBox = CheckBox(this).apply {
            text = "Weekdays only (Mon-Fri)"
            isChecked = Prefs.weekdaysOnly(this@MainActivity)
        }
        col.addView(weekdaysBox)
        col.addView(Button(this).apply {
            text = "Save settings"
            setOnClickListener {
                val mins = minutesField.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 4
                Prefs.setMinutes(this@MainActivity, mins)
                Prefs.setWeekdaysOnly(this@MainActivity, weekdaysBox.isChecked)
                Prefs.times(this@MainActivity).forEach { Scheduler.cancel(this@MainActivity, it) }
                Scheduler.scheduleAll(this@MainActivity)
                askExactAlarmIfNeeded()
                Toast.makeText(this@MainActivity, "Saved", Toast.LENGTH_SHORT).show()
            }
        })

        // 4. Test
        col.addView(label("4. Test", 20f))
        col.addView(Button(this).apply {
            text = "Play a random track for 10 seconds"
            setOnClickListener {
                if (Prefs.folder(this@MainActivity) == null) {
                    Toast.makeText(this@MainActivity, "Choose a music folder first", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                try {
                    startForegroundService(
                        Intent(this@MainActivity, PlayerService::class.java).putExtra("seconds", 10)
                    )
                } catch (e: Throwable) {
                    Prefs.setLastError(this@MainActivity, "Could not start player: ${e.javaClass.simpleName}: ${e.message}")
                    refresh()
                }
            }
        })

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        refresh()
    }

    private fun askExactAlarmIfNeeded() {
        if (!Scheduler.canScheduleExact(this)) {
            Toast.makeText(this, "Please allow 'Alarms & reminders' for this app", Toast.LENGTH_LONG).show()
            startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))
            )
        }
    }

    private fun refresh() {
        val err = Prefs.lastError(this)
        errorText.text = if (err != null) "Last problem (long-press to copy):\n$err" else ""
        folderText.text = Prefs.folder(this)?.let { Uri.parse(it).lastPathSegment } ?: "(none chosen)"
        timesList.removeAllViews()
        val times = Prefs.times(this)
        if (times.isEmpty()) {
            timesList.addView(TextView(this).apply { text = "(no times yet)" })
        }
        times.forEach { t ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(
                TextView(this).apply { text = t; textSize = 18f },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )
            row.addView(Button(this).apply {
                text = "Remove"
                setOnClickListener {
                    Scheduler.cancel(this@MainActivity, t)
                    Prefs.removeTime(this@MainActivity, t)
                    refresh()
                }
            })
            timesList.addView(row)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_FOLDER && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            Prefs.setFolder(this, uri.toString())
            refresh()
        }
    }

    companion object {
        private const val REQ_FOLDER = 100
    }
}
