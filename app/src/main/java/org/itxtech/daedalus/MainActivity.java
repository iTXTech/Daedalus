package org.itxtech.daedalus;

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
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;

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
    private boolean serviceActivated = false;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        initConfig();

        serviceActivated = isServiceActivated();
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
        if (serviceActivated) {
            but.setText(R.string.deactivate);
        } else {
            but.setText(R.string.activate);
        }
        but.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                serviceActivated = isServiceActivated();
                if (serviceActivated) {
                    deactivateService();
                    but.setText(R.string.activate);
                } else {
                    activateService();
                    but.setText(R.string.deactivate);
                }
            }
        });
    }

    @Override
    protected void onRestart() {
        super.onRestart();

        final Button but = (Button) findViewById(R.id.button_activate);
        serviceActivated = isServiceActivated();
        if (serviceActivated) {
            but.setText(R.string.deactivate);
        } else {
            but.setText(R.string.activate);
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
            if ("org.itxtech.daedalus.DaedalusVpnService".equals(service.service.getClassName())) {
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
            DaedalusVpnService.primaryServer = DnsServers.getDnsServerAddress(prefs.getString("primary_server", "0"));
            DaedalusVpnService.secondaryServer = DnsServers.getDnsServerAddress(prefs.getString("secondary_server", "1"));

            startService(getServiceIntent().setAction(DaedalusVpnService.ACTION_ACTIVATE));
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
            Intent intent = new Intent();
            intent.setClass(this, SettingsActivity.class);
            startActivity(intent);
            return true;
        }

        if (id == R.id.action_about) {
            Intent intent = new Intent();
            intent.setClass(this, AboutActivity.class);
            startActivity(intent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
