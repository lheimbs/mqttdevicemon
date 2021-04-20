package me.heimbs.mqttdevicemon;

import android.app.IntentService;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import com.hivemq.client.mqtt.mqtt3.Mqtt3BlockingClient;
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client;
import com.hivemq.client.mqtt.mqtt3.message.auth.Mqtt3SimpleAuth;
import com.hivemq.client.mqtt.mqtt3.message.connect.Mqtt3Connect;
import com.hivemq.client.mqtt.mqtt3.message.connect.connack.Mqtt3ConnAck;

import java.util.UUID;

public class PublishService extends IntentService {
    private static final String TAG = "PublishService";
    private Intent batteryStatus;
    private Mqtt3BlockingClient client;
    private Mqtt3SimpleAuth simpleAuth = null;
    private SharedPreferences prefs;

    public PublishService() {
        super("PublishService");
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate");
        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        batteryStatus = getApplicationContext().registerReceiver(null, intentFilter);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String broker = prefs.getString("connection_broker", "");
        String topic = prefs.getString("message_topic", "");
        String data_type = prefs.getString("message_data", "");
        int interval = prefs.getInt("message_interval", 0);
        int port =  Integer.parseInt(prefs.getString("connection_port", "1883"));

        client = Mqtt3Client.builder()
                .identifier("Android_" + UUID.randomUUID().toString())
                .serverHost(broker)
                .serverPort(port)
                .buildBlocking();

        String username = prefs.getString("authentication_user", null);
        String password = prefs.getString("authentication_password", null);
        if (username != null && password != null) {
            simpleAuth = Mqtt3SimpleAuth.builder()
                    .username(username)
                    .password(password.getBytes())
                    .build();
        }
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        super.onDestroy();
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        Log.d(TAG, "onHandleIntent");
        String batteryPercentage = MainActivity.getBatteryLevel(batteryStatus);
        String topic = prefs.getString("message_topic", "");
        try {
            Log.d(TAG, "Trying to connect to broker...");
            Mqtt3ConnAck connAckMessage = client.connectWith()
                    .simpleAuth(simpleAuth)
                    .send();
            Log.d(TAG, String.format("Client connect msg %s", connAckMessage.toString()));
            client.publishWith()
                    .topic(topic)
                    .payload(batteryPercentage.getBytes())
                    .send();
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }
}
