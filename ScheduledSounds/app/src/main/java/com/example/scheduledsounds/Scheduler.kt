package com.example.scheduledsounds

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/** Sets one exact alarm per saved time. Each alarm reschedules itself for the next day when it fires. */
object Scheduler {

    private fun am(c: Context) = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun canScheduleExact(c: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || am(c).canScheduleExactAlarms()

    private fun pending(c: Context, key: String, flags: Int): PendingIntent? {
        val (h, m) = key.split(":").map { it.toInt() }
        val intent = Intent(c, AlarmReceiver::class.java).putExtra("key", key)
        return PendingIntent.getBroadcast(c, h * 60 + m, intent, flags or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun nextTrigger(key: String, weekdaysOnly: Boolean): Long {
        val (h, m) = key.split(":").map { it.toInt() }
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, h)
            set(Calendar.MINUTE, m)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR, 1)
        if (weekdaysOnly) {
            while (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY ||
                cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
            ) cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    fun scheduleOne(c: Context, key: String) {
        val whenMs = nextTrigger(key, Prefs.weekdaysOnly(c))
        val pi = pending(c, key, PendingIntent.FLAG_UPDATE_CURRENT)!!
        if (canScheduleExact(c)) {
            am(c).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, pi)
        } else {
            // Fallback if the "Alarms & reminders" permission is not granted: may fire a few minutes late.
            am(c).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, pi)
        }
    }

    fun scheduleAll(c: Context) {
        Prefs.times(c).forEach { scheduleOne(c, it) }
    }

    fun cancel(c: Context, key: String) {
        pending(c, key, PendingIntent.FLAG_NO_CREATE)?.let {
            am(c).cancel(it)
            it.cancel()
        }
    }
}
