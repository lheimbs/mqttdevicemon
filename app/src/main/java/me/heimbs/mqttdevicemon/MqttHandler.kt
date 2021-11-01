package me.heimbs.mqttdevicemon;

import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client;
import com.hivemq.client.mqtt.mqtt3.Mqtt3ClientBuilder;

import java.util.UUID;

public class MqttHandler {
    private Mqtt3AsyncClient client;
    private String username = null;
    private String password = null;

    public void initMqtt(String broker, int port) {
        client = Mqtt3Client.builder()
            .identifier("Android_" + UUID.randomUUID().toString())
            .serverHost(broker)
            .serverPort(port)
            .buildAsync();
    }

    public void setAuth(String user, String pass) {
        username = user;
        password = pass;
    }

    public void connectAsync() {

    }

    public void connectBlocking(){

    }
}
