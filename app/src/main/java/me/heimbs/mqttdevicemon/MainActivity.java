package me.heimbs.mqttdevicemon;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
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
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.google.android.material.snackbar.Snackbar;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import com.hivemq.client.mqtt.mqtt3.Mqtt3BlockingClient;
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client;

import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    public static boolean BAD_SETTINGS = false;
    public final int requestId = 6666;
    public Mqtt3AsyncClient client = null;
    private Mqtt3BlockingClient blockingClient = null;
    private Handler handler = null;
    private Runnable publishMsg = null;
    public TextView loggingText;
    private Button clientBtn;
    private Button serviceStartBtn;
    private Button serviceStopBtn;
    private Intent batteryStatus;
    private AlarmManager alarmMgr;
    private PendingIntent alarmIntent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        loggingText = findViewById(R.id.logging_text);
        loggingText.setMovementMethod(new ScrollingMovementMethod());

        clientBtn = findViewById(R.id.btn_launch_client);
        serviceStartBtn = findViewById(R.id.btn_launch_srv);
        serviceStopBtn = findViewById(R.id.btn_stop_srv);

        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        batteryStatus = getApplicationContext().registerReceiver(null, intentFilter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        MainActivity.BAD_SETTINGS = false;

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String broker = prefs.getString("connection_broker", "");
        String topic = prefs.getString("message_topic", "");
        String data_type = prefs.getString("message_data", "");
        long interval = prefs.getInt("message_interval", 0) * 60 * 1000;
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

        handler = new Handler();
        publishMsg = new Runnable() {
            @Override
            public void run() {
                String batteryPctStr = getBatteryLevel(batteryStatus);
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
                clientBtn.setText(R.string.btn_launch_client_kill);
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

        Context context = getApplicationContext();
        alarmMgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, PublishService.class);
        alarmIntent = PendingIntent.getBroadcast(context, requestId, intent, 0);

        serviceStartBtn.setOnClickListener(v -> {
            Log.d(TAG, "serviceStartBtn clicked");
            alarmMgr.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime(),
                    interval, alarmIntent);
            Log.i(TAG, String.format("Run service every %d m/s", interval));
        });

        serviceStopBtn.setOnClickListener(v -> {
            Log.d(TAG, "serviceStopBtn clicked");
            AlarmManager alarmManager =
                    (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            PendingIntent pendingIntent =
                    PendingIntent.getService(context, requestId, intent,
                            PendingIntent.FLAG_NO_CREATE);
            if (pendingIntent != null && alarmManager != null) {
                alarmManager.cancel(pendingIntent);
            }
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

    public static String getBatteryLevel(Intent batteryStatus) {
        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int batteryPct = (int)( level * 100 / (float) scale);
        return Integer.toString(batteryPct);
    }
}