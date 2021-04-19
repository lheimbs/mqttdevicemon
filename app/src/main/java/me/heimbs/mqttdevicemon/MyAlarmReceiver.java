package me.heimbs.mqttdevicemon;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.TextView;

public class MyAlarmReceiver extends BroadcastReceiver {
    public static final int REQUEST_CODE = 12345;
//    public static final String ACTION = "me.heimbs.mqttdevicemon.alarm";

    // Triggered by the Alarm periodically (starts the service to run task)
    @Override
    public void onReceive(Context context, Intent intent) {
        Intent i = new Intent(context, MqttPublishService.class);
        i.putExtra("foo", "bar");
        context.startService(i);
    }
}
