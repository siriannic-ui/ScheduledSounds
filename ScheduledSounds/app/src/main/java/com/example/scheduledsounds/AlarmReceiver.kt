package com.example.scheduledsounds

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra("key") ?: return
        // Only continue if this time is still in the list.
        if (key !in Prefs.times(context)) return
        // Schedule tomorrow's (or next weekday's) alarm first, then play now.
        Scheduler.scheduleOne(context, key)
        context.startForegroundService(Intent(context, PlayerService::class.java))
    }
}
