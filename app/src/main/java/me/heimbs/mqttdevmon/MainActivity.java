package me.heimbs.mqttdevmon;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

import com.google.android.material.snackbar.Snackbar;

public class MainActivity extends AppCompatActivity {
    public static boolean BAD_SETTINGS = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MainActivity.BAD_SETTINGS = false;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String broker = prefs.getString("connection_broker", "");
        String topic = prefs.getString("message_topic", "");

        final Button clientBtn = findViewById(R.id.btn_launch_client);
        clientBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

            }
        });

        if (broker.equals("") && topic.equals("")) {
            MainActivity.BAD_SETTINGS = true;
            startActivity(new Intent(this, SettingsActivity.class));
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
}