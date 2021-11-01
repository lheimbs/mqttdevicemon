package me.heimbs.mqttdevicemon;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import com.hivemq.client.mqtt.mqtt3.Mqtt3BlockingClient;
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client;

import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    public static final String LOG_KEY = "USERLOG";
    public static final int MAX_LOG_BYTES = 1000;
    public static boolean BAD_SETTINGS = false;
    public Mqtt3AsyncClient client = null;
    private Mqtt3BlockingClient blockingClient = null;
    private Handler handler = null;
    private Runnable publishMsg = null;
    public TextView loggingText = null;
    private Button clientBtn;
    private Button serviceStartBtn;
    private Button serviceStopBtn;
    private Intent batteryStatus;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        loggingText = findViewById(R.id.logging_text);
        loggingText.setMovementMethod(new ScrollingMovementMethod());
        logToUser("\nApp created");

        clientBtn = findViewById(R.id.btn_launch_client);
        serviceStartBtn = findViewById(R.id.btn_launch_srv);
        serviceStopBtn = findViewById(R.id.btn_stop_srv);

        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        batteryStatus = getApplicationContext().registerReceiver(null, intentFilter);

        handler = new Handler();
        Runnable loggingTextUpdate = new Runnable() {
            @Override
            public void run() {
                updateUserLog();
                handler.postDelayed(this, 1000);
            }
        };
        handler.post(loggingTextUpdate);
    }

    @Override
    protected void onResume() {
        super.onResume();
        MainActivity.BAD_SETTINGS = false;

        String broker = prefs.getString("connection_broker", "");
        String topic = prefs.getString("message_topic", "");
        String data_type = prefs.getString("message_data", "");
        int interval = prefs.getInt("message_interval", 60);
        int port =  Integer.parseInt(prefs.getString("connection_port", "1883"));

        if (broker.equals("") || topic.equals("")) {
            MainActivity.BAD_SETTINGS = true;
            startActivity(new Intent(this, SettingsActivity.class));
            return;
        }

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

        publishMsg = new Runnable() {
            @Override
            public void run() {
                String batteryPctStr = getBatteryLevel(batteryStatus);
                logToUser(String.format(getResources().getString(R.string.logging_publish), batteryPctStr, topic));
                blockingClient.publishWith().topic(topic).payload(batteryPctStr.getBytes()).send();
                handler.postDelayed(this, interval * 60 * 1000);
            }
        };

        if (client == null || !client.getState().isConnectedOrReconnect()) {
            clientBtn.setText(R.string.btn_launch_client);
        }

        clientBtn.setOnClickListener(v -> {
            if (blockingClient.getState().isConnectedOrReconnect()) {
                clientBtn.setText(R.string.btn_launch_client);
                logToUser(String.format(getResources().getString(R.string.logging_disconnect), broker));
                handler.removeCallbacks(publishMsg);
                blockingClient.disconnect();
            } else {
                clientBtn.setText(R.string.btn_launch_client_kill);
                try {
                    blockingClient.connect();
                    logToUser(String.format(getResources().getString(R.string.logging_connect), broker, port));
                } catch (Exception e) {
                    Log.e(TAG, e.toString());
                    logToUser(String.format(getResources().getString(R.string.logging_could_not_connect), broker, port));
                    logToUser(e.toString());
                }
                handler.post(publishMsg);
            }
        });
        serviceStartBtn.setOnClickListener(v -> {
            Log.d(TAG, "serviceStartBtn clicked");
            schedulePublishService(0, this);
            enableBootReceiver(this);
            logToUser(getString(R.string.msg_service_started));
        });

        serviceStopBtn.setOnClickListener(v -> {
            Log.d(TAG, "serviceStopBtn clicked");
            stopPublishService();
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (blockingClient != null && blockingClient.getState().isConnectedOrReconnect()) {
            blockingClient.disconnect();
            logToUser(getResources().getString(R.string.logging_disconnect_any));
        }
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
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

    public static String getBatteryLevel(Intent batteryStatus) {
        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int batteryPct = (int)( level * 100 / (float) scale);
        return Integer.toString(batteryPct);
    }

    public static void schedulePublishService(int interval, Context context) {
        AlarmManager alarmMgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, PublisherAlarmReceiver.class);
        PendingIntent alarmIntent = PendingIntent.getBroadcast(context, PublisherAlarmReceiver.REQUEST_CODE, intent, 0);

        alarmMgr.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + interval * 60 * 1000, alarmIntent);
        Log.i(TAG, String.format("Scheduled PublishService to run in %d minutes...", interval));
    }

    private void stopPublishService() {
        Context context = getApplicationContext();
        AlarmManager alarmManager =
                (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, PublisherAlarmReceiver.class);
        PendingIntent alarmIntent = PendingIntent.getBroadcast(context, PublisherAlarmReceiver.REQUEST_CODE, intent, PendingIntent.FLAG_NO_CREATE);

        if (alarmIntent != null) {
            Log.d(TAG, "Cancelling alarm");
            alarmManager.cancel(alarmIntent);
            logToUser(getString(R.string.msg_service_stopped));
        } else {
            logToUser(getString(R.string.msg_service_not_running));
        }
        disableBootReceiver(context);
    }

    private void logToUser(String logString) {
        Log.d(TAG, "Log to user");
        String saved = prefs.getString(LOG_KEY, "");
        String newLog = appendLog(saved, logString);
        prefs.edit().putString(LOG_KEY, newLog).apply();
        Log.d(TAG, "Updated user log");
    }

    private void updateUserLog() {
        if (loggingText != null) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            String saved = prefs.getString(LOG_KEY, "");
            loggingText.setText(saved);
        }
    }

    public static String appendLog(String saved, String logString) {
        int newStringLength = logString.length();
        Log.d(TAG, "orig " + newStringLength + saved.length());
        while (newStringLength + saved.length() > MAX_LOG_BYTES) {
            saved = saved.substring(saved.indexOf("\n") + 1);
            Log.d(TAG, "new " + newStringLength + saved.length());
        }
        saved += logString;
        return saved;
    }

    private static void enableBootReceiver(Context context) {
        ComponentName receiver = new ComponentName(context, PublisherBootReceiver.class);
        PackageManager pm = context.getPackageManager();
        pm.setComponentEnabledSetting(receiver,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP);
        Log.d(TAG, "Enabled boot receiver...");
    }
    private static void disableBootReceiver(Context context) {
        ComponentName receiver = new ComponentName(context, PublisherBootReceiver.class);
        PackageManager pm = context.getPackageManager();
        pm.setComponentEnabledSetting(receiver,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
        Log.d(TAG, "Disabled boot receiver...");
    }
}