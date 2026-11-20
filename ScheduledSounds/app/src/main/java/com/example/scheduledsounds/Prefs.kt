package com.example.scheduledsounds

import android.content.Context

/** Simple settings storage. Times are stored as "HH:MM" strings (24-hour). */
object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    fun times(c: Context): List<String> =
        sp(c).getStringSet("times", emptySet())!!.toList().sorted()

    fun addTime(c: Context, t: String) {
        val s = sp(c).getStringSet("times", emptySet())!!.toMutableSet()
        s.add(t)
        sp(c).edit().putStringSet("times", s).apply()
    }

    fun removeTime(c: Context, t: String) {
        val s = sp(c).getStringSet("times", emptySet())!!.toMutableSet()
        s.remove(t)
        sp(c).edit().putStringSet("times", s).apply()
    }

    fun folder(c: Context): String? = sp(c).getString("folder", null)
    fun setFolder(c: Context, uri: String) = sp(c).edit().putString("folder", uri).apply()

    fun weekdaysOnly(c: Context): Boolean = sp(c).getBoolean("weekdays", true)
    fun setWeekdaysOnly(c: Context, v: Boolean) = sp(c).edit().putBoolean("weekdays", v).apply()

    fun minutes(c: Context): Int = sp(c).getInt("minutes", 4)
    fun setMinutes(c: Context, v: Int) = sp(c).edit().putInt("minutes", v).apply()

    fun lastError(c: Context): String? = sp(c).getString("lastError", null)
    fun setLastError(c: Context, v: String?) = sp(c).edit().putString("lastError", v).commit()
}
