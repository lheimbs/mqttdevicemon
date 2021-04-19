package me.heimbs.mqttdevicemon;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;

import com.google.android.material.snackbar.Snackbar;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import com.hivemq.client.mqtt.mqtt3.Mqtt3BlockingClient;
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    public static boolean BAD_SETTINGS = false;
    public Mqtt3AsyncClient client = null;
    private Mqtt3BlockingClient blockingClient = null;
    private Handler handler = null;
    private Runnable publishMsg = null;
    public TextView loggingText;
    private Button clientBtn;
    private Button serviceBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        loggingText = findViewById(R.id.logging_text);
        loggingText.setMovementMethod(new ScrollingMovementMethod());

        clientBtn = findViewById(R.id.btn_launch_client);
        serviceBtn = findViewById(R.id.btn_launch_srv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        MainActivity.BAD_SETTINGS = false;

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String broker = prefs.getString("connection_broker", "");
        String topic = prefs.getString("message_topic", "");
        String data_type = prefs.getString("message_data", "");
        int interval = prefs.getInt("message_interval", 0);
        int port =  Integer.parseInt(prefs.getString("connection_port", "1883"));

        if (broker.equals("") || topic.equals("")) {
            MainActivity.BAD_SETTINGS = true;
            startActivity(new Intent(this, SettingsActivity.class));
            return;
        }

        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = getApplicationContext().registerReceiver(null, ifilter);
        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        float batteryPct = level * 100 / (float) scale;
        String batteryPctStr = Float.toString(batteryPct);

        final TextView connection_text = findViewById(R.id.connection_text);
        connection_text.setText(String.format(getResources().getString(R.string.connection_text), broker, port));
        final TextView message_text = findViewById(R.id.message_text);
        message_text.setText(String.format(getResources().getString(R.string.message_text), data_type, topic, interval));

        if (client == null) {
            client = Mqtt3Client.builder()
                    .identifier("Android_" + UUID.randomUUID().toString())
                    .serverHost(broker)
                    .serverPort(port)
                    .buildAsync();
            blockingClient = client.toBlocking();
        }

        handler = new Handler();
        publishMsg = new Runnable() {
            @Override
            public void run() {
                int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                float batteryPct = level * 100 / (float) scale;
                String batteryPctStr = Float.toString(batteryPct);

                loggingText.append(String.format(getResources().getString(R.string.logging_publish), batteryPctStr, topic));
                blockingClient.publishWith().topic(topic).payload(batteryPctStr.getBytes()).send();
                handler.postDelayed(this, interval * 1000);
            }
        };

        if (client == null || !client.getState().isConnectedOrReconnect()) {
            clientBtn.setText(R.string.btn_launch_client);
        }
        clientBtn.setOnClickListener(v -> {
            if (blockingClient.getState().isConnectedOrReconnect()) {
                clientBtn.setText(R.string.btn_launch_client);
                loggingText.append(String.format(getResources().getString(R.string.logging_disconnect), broker));
                handler.removeCallbacks(publishMsg);
                blockingClient.disconnect();
            } else {
                clientBtn.setText(R.string.btn_kill_client);
                try {
                    blockingClient.connect();
                    loggingText.append(String.format(getResources().getString(R.string.logging_connect), broker, port));
                } catch (Exception e) {
                    Log.e(TAG, e.toString());
                    loggingText.append(String.format(getResources().getString(R.string.logging_could_not_connect), broker, port));
                    loggingText.append(e.toString());
                }
                handler.post(publishMsg);
            }
        });


        if (isPubServiceRunning()) {
            Log.d(TAG, "Publisher is up");
            serviceBtn.setText(R.string.btn_kill_service);
        } else {
            Log.d(TAG, "Publisher is down");
            serviceBtn.setText(R.string.btn_launch_service);
        }
        serviceBtn.setOnClickListener(v -> {
            Log.d(TAG, "Publish button clicked");
//            if (isAlarmUp()) {
            if (isPubServiceRunning()) {
                Log.d(TAG, "Alarm is up. cancelling Alarm");
//                cancelAlarm();
                stopService();
                serviceBtn.setText(R.string.btn_launch_service);
            } else {
                Log.d(TAG, "Alarm is down. Starting");
//                scheduleAlarm(interval);
                startService();
                serviceBtn.setText(R.string.btn_kill_service);
            }

//                stopService(new Intent(getApplicationContext(), MqttPublishService.class));
//            } else {
//                startService(new Intent(getApplicationContext(), MqttPublishService.class));
//            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (blockingClient != null && blockingClient.getState().isConnectedOrReconnect()) {
            blockingClient.disconnect();
            loggingText.append(getResources().getString(R.string.logging_disconnect_any));
        }
        if (handler != null && publishMsg != null) {
            handler.removeCallbacks(publishMsg);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        } else {
            // If we got here, the user's action was not recognized.
            // Invoke the superclass to handle it.
            return super.onOptionsItemSelected(item);
        }
    }

    private boolean isPubServiceRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (MqttPublishService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    public void startService() {
        Intent serviceIntent = new Intent(this, MqttPublishService.class);
        ContextCompat.startForegroundService(this, serviceIntent);
    }
    public void stopService() {
        Intent serviceIntent = new Intent(this, MqttPublishService.class);
        stopService(serviceIntent);
    }

    public void scheduleAlarm(int interval) {
        Log.d(TAG, "Schedule Alarm for interval " + interval);

        // Construct an intent that will execute the AlarmReceiver
        Intent intent = new Intent(getApplicationContext(), MyAlarmReceiver.class);
        // Create a PendingIntent to be triggered when the alarm goes off
        final PendingIntent pIntent = PendingIntent.getBroadcast(this, MyAlarmReceiver.REQUEST_CODE,
                intent, PendingIntent.FLAG_UPDATE_CURRENT);
        // Setup periodic alarm
        long firstMillis = System.currentTimeMillis(); // alarm is set right away
        AlarmManager alarm = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
        // First parameter is the type: ELAPSED_REALTIME, ELAPSED_REALTIME_WAKEUP, RTC_WAKEUP
        // Interval can be INTERVAL_FIFTEEN_MINUTES, INTERVAL_HALF_HOUR, INTERVAL_HOUR, INTERVAL_DAY
        alarm.setRepeating(AlarmManager.RTC_WAKEUP, firstMillis,
                interval * 1000, pIntent);
    }

    public void cancelAlarm() {
        Log.d(TAG, "Cancelling Alarm");
        Intent intent = new Intent(getApplicationContext(), MyAlarmReceiver.class);
        final PendingIntent pIntent = PendingIntent.getBroadcast(this, MyAlarmReceiver.REQUEST_CODE,
                intent, PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManager alarm = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
        alarm.cancel(pIntent);
    }

    public boolean isAlarmUp() {
        Log.d(TAG, "checking if alarm is up");
        boolean isUp = (PendingIntent.getBroadcast(
                getApplicationContext(), 0,
                new Intent(getApplicationContext(), MyAlarmReceiver.class),
                PendingIntent.FLAG_NO_CREATE) != null);
        Log.d(TAG, "Alarm is up? : " + isUp);
        return isUp;
    }
}