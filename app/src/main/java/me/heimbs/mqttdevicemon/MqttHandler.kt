package me.heimbs.mqttdevicemon

import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client
import java.util.*

class MqttHandler {
    private var client: Mqtt3AsyncClient? = null
    private var username: String? = null
    private var password: String? = null
    fun initMqtt(broker: String?, port: Int) {
        client = Mqtt3Client.builder()
            .identifier("Android_" + UUID.randomUUID().toString())
            .serverHost(broker!!)
            .serverPort(port)
            .buildAsync()
    }

    fun setAuth(user: String?, pass: String?) {
        username = user
        password = pass
    }

    fun connectAsync() {}
    fun connectBlocking() {}
}