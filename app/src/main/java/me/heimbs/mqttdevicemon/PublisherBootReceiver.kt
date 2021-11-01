package me.heimbs.mqttdevicemon

import android.content.BroadcastReceiver
import android.content.Context
import me.heimbs.mqttdevicemon.MainActivity.Companion.schedulePublishService
import android.content.Intent
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import me.heimbs.mqttdevicemon.MainActivity
import me.heimbs.mqttdevicemon.PublisherBootReceiver

class PublisherBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.intent.action.BOOT_COMPLETED") {
            // Set the alarm here.
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            prefs.edit().putString(MainActivity.LOG_KEY, "Device rebooted...").apply()
            schedulePublishService(0, context)
        }
    }

    companion object {
        private val TAG = PublisherBootReceiver::class.java.simpleName
    }
}