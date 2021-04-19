package me.heimbs.mqttdevicemon;

import android.util.Log;

import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client;
import com.hivemq.client.mqtt.mqtt3.Mqtt3ClientBuilder;
import com.hivemq.client.mqtt.mqtt3.message.auth.Mqtt3SimpleAuth;

import java.util.UUID;

public class MqttHandler {
    public final String TAG = "MqttHandler";
    private Mqtt3AsyncClient client;
    private Mqtt3SimpleAuth simpleAuth = null;
    private String username = null;
    private String password = null;

    public void initMqtt(String broker, int port) {
        client = Mqtt3Client.builder()
            .identifier("Android_" + UUID.randomUUID().toString())
            .serverHost(broker)
            .serverPort(port)
            .buildAsync();
        Log.i(TAG, "Build Async Mqtt3Client");
    }

    public void setAuth(String user, String pass) {
        simpleAuth = Mqtt3SimpleAuth.builder()
                .username(user)
                .password(pass.getBytes())
                .build();
    }

    public void connectAsync() {
        if (simpleAuth != null) {
//            client.publishWith()
//            client.connectWith()
//                    .simpleAuth(simpleAuth)
//                    .
//            ;
        } else {
            client.connect();
        }
    }

    public void connectBlocking(){

    }
}


//if (blockingClient.getState().isConnectedOrReconnect()) {
//        clientBtn.setText(R.string.btn_launch_client);
//        loggingText.append(String.format(getResources().getString(R.string.logging_disconnect), broker));
//        handler.removeCallbacks(publishMsg);
//        blockingClient.disconnect();
//        } else {
//        clientBtn.setText(R.string.btn_kill_client);
//        try {
//        blockingClient.connect();
//        loggingText.append(String.format(getResources().getString(R.string.logging_connect), broker, port));
//        } catch (Exception e) {
//        Log.e(TAG, e.toString());
//        loggingText.append(String.format(getResources().getString(R.string.logging_could_not_connect), broker, port));
//        loggingText.append(e.toString());
//        }
//        handler.post(publishMsg);
//        }