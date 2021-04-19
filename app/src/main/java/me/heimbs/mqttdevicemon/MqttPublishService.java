package me.heimbs.mqttdevicemon;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.preference.PreferenceManager;

import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client;

import java.util.ArrayList;
import java.util.UUID;


public class MqttPublishService extends Service {
    private static final String TAG = "MqttPublishService";

    public final String CHANNEL_ID = "publish_service";
    public final int ONGOING_NOTIFICATION_ID = 2001;

    private Mqtt3AsyncClient client = null;
    private Intent batteryStatus = null;
    private SharedPreferences prefs;
    private Handler handler;
    private Runnable publishMsg;

    public MqttPublishService() {
//        super("MqttPublishService");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Create service");
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String broker = prefs.getString("connection_broker", "");
        int port =  Integer.parseInt(prefs.getString("connection_port", "1883"));

        String topic = prefs.getString("message_topic", "");
        String data_type = prefs.getString("message_data", "");
        int interval = prefs.getInt("message_interval", 0);

        Log.i(TAG, String.format("Create client at %s:%d", broker, port));
        client = Mqtt3Client.builder()
                .identifier("Android_" + UUID.randomUUID().toString())
                .serverHost(broker)
                .serverPort(port)
                .buildAsync();

        Log.i(TAG, "Register battery handler");
        IntentFilter batteryFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        batteryStatus = getApplicationContext().registerReceiver(null, batteryFilter);

        Log.i(TAG, "Build handler and runable");

        handler = new Handler();
        publishMsg = new Runnable() {
            @Override
            public void run() {
                int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                float batteryPct = level * 100 / (float) scale;
                String batteryPctStr = Float.toString(batteryPct);

                Log.i(TAG, String.format(getResources().getString(R.string.logging_publish), batteryPctStr, topic));
                client.publishWith().topic(topic).payload(batteryPctStr.getBytes()).send();
                handler.postDelayed(this, interval * 1000);
            }
        };
    }

    @Override
    @RequiresApi(api = Build.VERSION_CODES.O)
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand called...");
        Intent notificationIntent = new Intent(this, MqttPublishService.class);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel();
            PendingIntent pendingIntent =
                    PendingIntent.getActivity(this, 0, notificationIntent, 0);
            Notification notification =
                    new Notification.Builder(this, CHANNEL_ID)
                            .setContentTitle(getText(R.string.notification_title))
                            .setContentText(getText(R.string.notification_message))
                            .setContentIntent(pendingIntent)
                            .build();
            startForeground(ONGOING_NOTIFICATION_ID, notification);
        } else {
            startService(notificationIntent);
        }

        if (handler.hasCallbacks(publishMsg))
        singleAllInOnePublish();

        return START_STICKY;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void createNotificationChannel() {
        Log.d(TAG, "createNotificationChannel called...");
        CharSequence name = getString(R.string.channel_name);
        int importance = NotificationManager.IMPORTANCE_LOW;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
        // Register the channel with the system; you can't change the importance
        // or other notification behaviors after this
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    private void runPublisher() {

        Log.d(TAG, "runPublisher called...");

        Handler handler = new Handler();
        Runnable publishMsg = new Runnable() {
            @Override
            public void run() {
                int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                float batteryPct = level * 100 / (float) scale;
                String batteryPctStr = Float.toString(batteryPct);

                Log.i(TAG, String.format(getResources().getString(R.string.logging_publish), batteryPctStr, topic));
                client.publishWith().topic(topic).payload(batteryPctStr.getBytes()).send();
                handler.postDelayed(this, interval * 1000);
            }
        };

        try {
            Log.d(TAG, "Trying to connect to broker...");
            client.connect()
                .whenComplete((mqtt3ConnAck, throwable) -> {
                    if (throwable != null) {
                        Log.e(TAG, "Unable to connect to broker.", throwable);
                    } else {
                        Log.i(TAG, "Connected.");
                    }
                });
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
        handler.post(publishMsg);
    }

    private void singleAllInOnePublish() {
        Log.d(TAG, "singleAllInOnePublish called...");
        String topic = prefs.getString("message_topic", "");
        String data_type = prefs.getString("message_data", "");
        int interval = prefs.getInt("message_interval", 0);

        if (client == null || !client.getState().isConnectedOrReconnect()) {
            try {
                Log.d(TAG, "Trying to connect to broker...");
                client.connect()
                        .whenComplete((mqtt3ConnAck, throwable) -> {
                            if (throwable != null) {
                                Log.e(TAG, "Unable to connect to broker.", throwable);
                            } else {
                                Log.i(TAG, "Connected.");
                            }
                        });
            } catch (Exception e) {
                Log.e(TAG, e.toString());
            }
        }
        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        float batteryPct = level * 100 / (float) scale;
        String batteryPctStr = Float.toString(batteryPct);

        Log.i(TAG, String.format(getResources().getString(R.string.logging_publish), batteryPctStr, topic));
        client.publishWith().topic(topic).payload(batteryPctStr.getBytes()).send();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Destroying Service...");
    }
}