package me.heimbs.mqttdevicemon

import android.annotation.SuppressLint
import androidx.appcompat.app.AppCompatActivity
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import com.hivemq.client.mqtt.mqtt3.Mqtt3BlockingClient
import android.widget.TextView
import android.text.method.ScrollingMovementMethod
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.*
import android.content.pm.PackageManager
import android.os.*
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import androidx.preference.PreferenceManager
import java.lang.Exception
import java.util.*

class MainActivity : AppCompatActivity() {
    var client: Mqtt3AsyncClient? = null
    private var blockingClient: Mqtt3BlockingClient? = null
    private var handler: Handler? = null
    private var publishMsg: Runnable? = null
    var loggingText: TextView? = null
    private var clientBtn: Button? = null
    private var serviceStartBtn: Button? = null
    private var serviceStopBtn: Button? = null
    private var batteryStatus: Intent? = null
    private var prefs: SharedPreferences? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        loggingText = findViewById(R.id.logging_text)
        loggingText?.movementMethod = ScrollingMovementMethod()
        logToUser("\nApp created")
        clientBtn = findViewById(R.id.btn_launch_client)
        serviceStartBtn = findViewById(R.id.btn_launch_srv)
        serviceStopBtn = findViewById(R.id.btn_stop_srv)
        val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        batteryStatus = applicationContext.registerReceiver(null, intentFilter)
//        handler = Handler()
        val loggingTextUpdate: Runnable = object : Runnable {
            override fun run() {
                updateUserLog()
                Handler(Looper.getMainLooper()).postDelayed(this, 1000)
            }
        }
        Handler(Looper.getMainLooper()).post(loggingTextUpdate)
    }

    override fun onResume() {
        super.onResume()
        BAD_SETTINGS = false
        val broker = prefs!!.getString("connection_broker", "")
        val topic = prefs!!.getString("message_topic", "")
        val dataType = prefs!!.getString("message_data", "")
        val interval = prefs!!.getInt("message_interval", 60)
        val port = prefs!!.getString("connection_port", "1883")!!.toInt()
        if (broker == "" || topic == "") {
            BAD_SETTINGS = true
            startActivity(Intent(this, SettingsActivity::class.java))
            return
        }
        val connectionText = findViewById<TextView>(R.id.connection_text)
        connectionText.text =
            String.format(resources.getString(R.string.connection_text), broker, port)
        val message_text = findViewById<TextView>(R.id.message_text)
        message_text.text =
            String.format(resources.getString(R.string.message_text), dataType, topic, interval)
        if (client == null) {
            client = Mqtt3Client.builder()
                .identifier("Android_" + UUID.randomUUID().toString())
                .serverHost(broker!!)
                .serverPort(port)
                .buildAsync()
            blockingClient = client!!.toBlocking()
        }
        publishMsg = object : Runnable {
            override fun run() {
                val batteryPctStr = getBatteryLevel(batteryStatus)
                logToUser(
                    String.format(
                        resources.getString(R.string.logging_publish),
                        batteryPctStr,
                        topic
                    )
                )
                blockingClient!!.publishWith().topic(topic!!).payload(batteryPctStr.toByteArray())
                    .send()
                handler!!.postDelayed(this, (interval * 60 * 1000).toLong())
            }
        }
        if (client == null || !client!!.state.isConnectedOrReconnect) {
            clientBtn!!.setText(R.string.btn_launch_client)
        }
        clientBtn!!.setOnClickListener { v: View? ->
            if (blockingClient!!.state.isConnectedOrReconnect) {
                clientBtn!!.setText(R.string.btn_launch_client)
                logToUser(String.format(resources.getString(R.string.logging_disconnect), broker))
                handler!!.removeCallbacks(publishMsg as Runnable)
                blockingClient!!.disconnect()
            } else {
                clientBtn!!.setText(R.string.btn_launch_client_kill)
                try {
                    blockingClient!!.connect()
                    logToUser(
                        String.format(
                            resources.getString(R.string.logging_connect),
                            broker,
                            port
                        )
                    )
                } catch (e: Exception) {
                    Log.e(TAG, e.toString())
                    logToUser(
                        String.format(
                            resources.getString(R.string.logging_could_not_connect),
                            broker,
                            port
                        )
                    )
                    logToUser(e.toString())
                }
                handler!!.post(publishMsg as Runnable)
            }
        }
        serviceStartBtn!!.setOnClickListener { v: View? ->
            Log.d(TAG, "serviceStartBtn clicked")
            schedulePublishService(0, this)
            enableBootReceiver(this)
            logToUser(getString(R.string.msg_service_started))
        }
        serviceStopBtn!!.setOnClickListener { v: View? ->
            Log.d(TAG, "serviceStopBtn clicked")
            stopPublishService()
        }
    }

    override fun onPause() {
        super.onPause()
        if (blockingClient != null && blockingClient!!.state.isConnectedOrReconnect) {
            blockingClient!!.disconnect()
            logToUser(resources.getString(R.string.logging_disconnect_any))
        }
        if (handler != null) {
            handler!!.removeCallbacksAndMessages(null)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == R.id.action_settings) {
            startActivity(Intent(this, SettingsActivity::class.java))
            true
        } else {
            // If we got here, the user's action was not recognized.
            // Invoke the superclass to handle it.
            super.onOptionsItemSelected(item)
        }
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    private fun stopPublishService() {
        val context = applicationContext
        val alarmManager = context.getSystemService(ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, PublisherAlarmReceiver::class.java)
        val alarmIntent = PendingIntent.getBroadcast(
            context,
            PublisherAlarmReceiver.REQUEST_CODE,
            intent,
            PendingIntent.FLAG_NO_CREATE
        )
        if (alarmIntent != null) {
            Log.d(TAG, "Cancelling alarm")
            alarmManager.cancel(alarmIntent)
            logToUser(getString(R.string.msg_service_stopped))
        } else {
            logToUser(getString(R.string.msg_service_not_running))
        }
        disableBootReceiver(context)
    }

    private fun logToUser(logString: String) {
        Log.d(TAG, "Log to user")
        val saved = prefs!!.getString(LOG_KEY, "")
        val newLog = appendLog(saved, logString)
        prefs!!.edit().putString(LOG_KEY, newLog).apply()
        Log.d(TAG, "Updated user log")
    }

    private fun updateUserLog() {
        if (loggingText != null) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(
                applicationContext
            )
            val saved = prefs.getString(LOG_KEY, "")
            loggingText!!.text = saved
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        const val LOG_KEY = "USERLOG"
        const val MAX_LOG_BYTES = 1000
        @JvmField
        var BAD_SETTINGS = false
        @JvmStatic
        fun getBatteryLevel(batteryStatus: Intent?): String {
            val level = batteryStatus!!.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val batteryPct = (level * 100 / scale.toFloat()).toInt()
            return batteryPct.toString()
        }

        @SuppressLint("UnspecifiedImmutableFlag")
        @JvmStatic
        fun schedulePublishService(interval: Int, context: Context) {
            val alarmMgr = context.getSystemService(ALARM_SERVICE) as AlarmManager
            val intent = Intent(
                context,
                PublisherAlarmReceiver::class.java
            )
            val alarmIntent =
                PendingIntent.getBroadcast(context, PublisherAlarmReceiver.REQUEST_CODE, intent, 0)
            alarmMgr.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + interval * 60 * 1000, alarmIntent
            )
            Log.i(TAG, String.format("Scheduled PublishService to run in %d minutes...", interval))
        }

        @JvmStatic
        fun appendLog(saved: String?, logString: String): String {
            var saved = saved
            val newStringLength = logString.length
            Log.d(TAG, "orig " + newStringLength + saved!!.length)
            while (newStringLength + saved!!.length > MAX_LOG_BYTES) {
                saved = saved.substring(saved.indexOf("\n") + 1)
                Log.d(TAG, "new " + newStringLength + saved.length)
            }
            saved += logString
            return saved
        }

        private fun enableBootReceiver(context: Context) {
            val receiver = ComponentName(context, PublisherBootReceiver::class.java)
            val pm = context.packageManager
            pm.setComponentEnabledSetting(
                receiver,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            Log.d(TAG, "Enabled boot receiver...")
        }

        private fun disableBootReceiver(context: Context) {
            val receiver = ComponentName(context, PublisherBootReceiver::class.java)
            val pm = context.packageManager
            pm.setComponentEnabledSetting(
                receiver,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            Log.d(TAG, "Disabled boot receiver...")
        }
    }
}