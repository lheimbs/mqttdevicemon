package me.heimbs.mqttdevicemon;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class PublisherAlarmReceiver extends BroadcastReceiver {
    public static final int REQUEST_CODE = 12345;

    // Triggered by the Alarm periodically (starts the service to run task)
    @Override
    public void onReceive(Context context, Intent intent) {
        Intent i = new Intent(context, PublishService.class);
        context.startService(i);
    }
}

