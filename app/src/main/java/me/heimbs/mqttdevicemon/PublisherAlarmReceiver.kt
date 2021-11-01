package me.heimbs.mqttdevicemon

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import me.heimbs.mqttdevicemon.PublishService

class PublisherAlarmReceiver : BroadcastReceiver() {
    // Triggered by the Alarm periodically (starts the service to run task)
    override fun onReceive(context: Context, intent: Intent) {
        val i = Intent(context, PublishService::class.java)
        context.startService(i)
    }

    companion object {
        const val REQUEST_CODE = 12345
    }
}