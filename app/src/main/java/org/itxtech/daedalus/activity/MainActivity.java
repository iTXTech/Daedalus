package org.itxtech.daedalus.activity;

import android.app.ActivityManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import org.itxtech.daedalus.R;
import org.itxtech.daedalus.service.DaedalusVpnService;
import org.itxtech.daedalus.util.DnsServer;

/**
 * Daedalus Project
 *
 * @author iTXTech
 * @link https://itxtech.org
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 */
public class MainActivity extends AppCompatActivity {
    private static MainActivity instance = null;
    private SharedPreferences prefs;

    public static MainActivity getInstance() {
        return instance;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        instance = this;

        initConfig();

        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(v.getContext(), ServerTestActivity.class);
                startActivity(intent);
            }
        });

        final Button but = (Button) findViewById(R.id.button_activate);
        if (isServiceActivated()) {
            but.setText(R.string.button_text_deactivate);
        } else {
            but.setText(R.string.button_text_activate);
        }
        but.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isServiceActivated()) {
                    but.setText(R.string.button_text_activate);
                    deactivateService();
                } else {
                    activateService();
                }
            }
        });
    }

    @Override
    protected void onRestart() {
        super.onRestart();

        final Button but = (Button) findViewById(R.id.button_activate);
        if (isServiceActivated()) {
            but.setText(R.string.button_text_deactivate);
        } else {
            but.setText(R.string.button_text_activate);
            NotificationManager notificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.cancelAll();
        }
    }

    private void initConfig() {
        PreferenceManager.setDefaultValues(this, R.xml.perf_settings, false);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
    }

    private void activateService() {
        Intent intent = VpnService.prepare(this);
        if (intent != null) {
            startActivityForResult(intent, 0);
        } else {
            onActivityResult(0, RESULT_OK, null);
        }
    }


    private void deactivateService() {
        startService(getServiceIntent().setAction(DaedalusVpnService.ACTION_DEACTIVATE));
        stopService(getServiceIntent());
    }

    private boolean isServiceActivated() {
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (DaedalusVpnService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private Intent getServiceIntent() {
        return new Intent(this, DaedalusVpnService.class);
    }

    protected void onActivityResult(int request, int result, Intent data) {
        if (result == RESULT_OK) {
            DaedalusVpnService.primaryServer = DnsServer.getDnsServerAddressById(prefs.getString("primary_server", "0"));
            DaedalusVpnService.secondaryServer = DnsServer.getDnsServerAddressById(prefs.getString("secondary_server", "1"));

            startService(getServiceIntent().setAction(DaedalusVpnService.ACTION_ACTIVATE));


            ((Button) findViewById(R.id.button_activate)).setText(R.string.button_text_deactivate);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }

        if (id == R.id.action_about) {
            startActivity(new Intent(this, AboutActivity.class));
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
