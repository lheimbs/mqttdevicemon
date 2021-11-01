package me.heimbs.mqttdevicemon;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

public class PublisherBootReceiver extends BroadcastReceiver {
    private static final String TAG = PublisherBootReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals("android.intent.action.BOOT_COMPLETED")) {
            // Set the alarm here.
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            prefs.edit().putString(MainActivity.LOG_KEY, "Device rebooted...").apply();
            MainActivity.schedulePublishService(0, context);
        }
    }
}
