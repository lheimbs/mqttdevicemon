package me.heimbs.mqttdevicemon

import me.heimbs.mqttdevicemon.MainActivity.Companion.getBatteryLevel
import me.heimbs.mqttdevicemon.MainActivity.Companion.schedulePublishService
import me.heimbs.mqttdevicemon.MainActivity.Companion.appendLog
import android.app.IntentService
import android.content.Context
import android.content.Intent
import com.hivemq.client.mqtt.mqtt3.Mqtt3BlockingClient
import com.hivemq.client.mqtt.mqtt3.message.auth.Mqtt3SimpleAuth
import android.content.SharedPreferences
import me.heimbs.mqttdevicemon.PublishService
import android.content.IntentFilter
import android.util.Log
import androidx.core.app.JobIntentService
import androidx.preference.PreferenceManager
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client
import me.heimbs.mqttdevicemon.MainActivity
import com.hivemq.client.mqtt.mqtt3.message.connect.connack.Mqtt3ConnAck
import com.hivemq.client.mqtt.mqtt3.message.connect.connack.Mqtt3ConnAckReturnCode
import me.heimbs.mqttdevicemon.R
import java.lang.Exception
import java.util.*

class PublishService : JobIntentService() {
    private var batteryStatus: Intent? = null
    private var client: Mqtt3BlockingClient? = null
    private var simpleAuth: Mqtt3SimpleAuth? = null
    private var prefs: SharedPreferences? = null
    override fun onCreate() {
        Log.d(TAG, "onCreate")
        val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        batteryStatus = applicationContext.registerReceiver(null, intentFilter)
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val broker = prefs?.getString("connection_broker", "")
        val topic = prefs?.getString("message_topic", "")
        val dataType = prefs?.getString("message_data", "")
        val interval = prefs?.getInt("message_interval", 60)
        val port = prefs?.getString("connection_port", "1883")!!.toInt()
        client = Mqtt3Client.builder()
            .identifier("Android_" + UUID.randomUUID().toString())
            .serverHost(broker!!)
            .serverPort(port)
            .buildBlocking()
        val username = prefs?.getString("authentication_user", null)
        val password = prefs?.getString("authentication_password", null)
        if (username != null && password != null) {
            simpleAuth = Mqtt3SimpleAuth.builder()
                .username(username)
                .password(password.toByteArray())
                .build()
        }
        super.onCreate()
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        super.onDestroy()
    }

    override fun onHandleWork(intent: Intent) {
        onHandleIntent(intent)
    }

    private fun onHandleIntent(intent: Intent?) {
        Log.d(TAG, "onHandleIntent")
        val batteryPercentage = getBatteryLevel(batteryStatus)
        val topic = prefs!!.getString("message_topic", "")
        Log.d(TAG, "$topic: $batteryPercentage")
        try {
            Log.d(TAG, "Trying to connect to broker...")
            val connAckMessage = client!!.connectWith()
                .simpleAuth(simpleAuth)
                .send()
            Log.d(TAG, String.format("Client connect msg %s", connAckMessage.toString()))
            if (connAckMessage.returnCode == Mqtt3ConnAckReturnCode.SUCCESS) {
                client!!.publishWith()
                    .topic(topic!!)
                    .payload(batteryPercentage.toByteArray())
                    .send()
            } else {
                Log.e(TAG, "Error connecting to broker: " + connAckMessage.returnCode.toString())
            }
            logToUser(
                String.format(getString(R.string.logging_publish), batteryPercentage, topic),
                this
            )
        } catch (e: Exception) {
            Log.e(TAG, e.toString())
        } finally {
            client!!.disconnect()
        }
        schedulePublishService(prefs!!.getInt("message_interval", 60), this)
    }

    companion object {

        fun enqueueWork(context: Context, intent: Intent){
            enqueueWork(context,PublishService::class.java, 1, intent)
        }

        private const val TAG = "PublishService"
        fun logToUser(logString: String?, context: Context?) {
            Log.d(TAG, String.format("Log \'%s\' to user", logString))
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val saved = prefs.getString(MainActivity.LOG_KEY, "")
            val newLog = appendLog(saved, logString!!)
            prefs.edit().putString(MainActivity.LOG_KEY, newLog).apply()
        }
    }
}