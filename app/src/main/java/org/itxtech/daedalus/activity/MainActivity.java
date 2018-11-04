package org.itxtech.daedalus.activity;

import android.app.Activity;
import android.app.FragmentManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import com.google.android.material.navigation.NavigationView;
import org.itxtech.daedalus.BuildConfig;
import org.itxtech.daedalus.Daedalus;
import org.itxtech.daedalus.R;
import org.itxtech.daedalus.fragment.*;
import org.itxtech.daedalus.service.DaedalusVpnService;
import org.itxtech.daedalus.util.Logger;
import org.itxtech.daedalus.util.server.DNSServerHelper;

/**
 * Daedalus Project
 *
 * @author iTX Technologies
 * @link https://itxtech.org
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {
    private static final String TAG = "DMainActivity";

    public static final String LAUNCH_ACTION = "org.itxtech.daedalus.activity.MainActivity.LAUNCH_ACTION";
    public static final int LAUNCH_ACTION_NONE = 0;
    public static final int LAUNCH_ACTION_ACTIVATE = 1;
    public static final int LAUNCH_ACTION_DEACTIVATE = 2;
    public static final int LAUNCH_ACTION_SERVICE_DONE = 3;

    public static final String LAUNCH_FRAGMENT = "org.itxtech.daedalus.activity.MainActivity.LAUNCH_FRAGMENT";
    public static final int FRAGMENT_NONE = -1;
    public static final int FRAGMENT_HOME = 0;
    public static final int FRAGMENT_DNS_TEST = 1;
    public static final int FRAGMENT_SETTINGS = 2;
    public static final int FRAGMENT_ABOUT = 3;
    public static final int FRAGMENT_RULES = 4;
    public static final int FRAGMENT_DNS_SERVERS = 5;
    public static final int FRAGMENT_LOG = 6;

    public static final String LAUNCH_NEED_RECREATE = "org.itxtech.daedalus.activity.MainActivity.LAUNCH_NEED_RECREATE";

    private static MainActivity instance = null;

    private ToolbarFragment currentFragment;

    public static MainActivity getInstance() {
        return instance;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (Daedalus.isDarkTheme()) {
            setTheme(R.style.AppTheme_Dark_NoActionBar_TransparentStatusBar);
        }
        super.onCreate(savedInstanceState);

        instance = this;

        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        //setSupportActionBar(toolbar); //causes toolbar issues

        DrawerLayout drawer = findViewById(R.id.main_drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        ((TextView) navigationView.getHeaderView(0).findViewById(R.id.textView_nav_version)).setText(getString(R.string.nav_version) + " " + BuildConfig.VERSION_NAME);
        ((TextView) navigationView.getHeaderView(0).findViewById(R.id.textView_nav_git_commit)).setText(getString(R.string.nav_git_commit) + " " + BuildConfig.GIT_COMMIT);

        updateUserInterface(getIntent());
    }

    private void switchFragment(Class fragmentClass) {
        if (currentFragment == null || fragmentClass != currentFragment.getClass()) {
            try {
                ToolbarFragment fragment = (ToolbarFragment) fragmentClass.newInstance();
                FragmentManager fm = getFragmentManager();
                fm.beginTransaction().replace(R.id.id_content, fragment).commit();
                currentFragment = fragment;
            } catch (Exception e) {
                Logger.logException(e);
            }
        }
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = findViewById(R.id.main_drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else if (!(currentFragment instanceof HomeFragment)) {
            switchFragment(HomeFragment.class);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        instance = null;
        currentFragment = null;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        updateUserInterface(intent);
    }

    public void activateService() {
        Intent intent = VpnService.prepare(Daedalus.getInstance());
        if (intent != null) {
            startActivityForResult(intent, 0);
        } else {
            onActivityResult(0, Activity.RESULT_OK, null);
        }

        long activateCounter = Daedalus.configurations.getActivateCounter();
        if (activateCounter == -1) {
            return;
        }
        activateCounter++;
        Daedalus.configurations.setActivateCounter(activateCounter);
        if (activateCounter % 10 == 0) {
            new AlertDialog.Builder(this)
                    .setTitle("觉得还不错？")
                    .setMessage("您的支持是我动力来源！\n请考虑为我买杯咖啡醒醒脑，甚至其他…… ;)")
                    .setPositiveButton("为我买杯咖啡", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Daedalus.donate();
                            new AlertDialog.Builder(MainActivity.this)
                                    .setMessage("感谢您的支持！;)\n我会再接再厉！")
                                    .setPositiveButton("确认", null)
                                    .show();
                        }
                    })
                    .setNeutralButton("不再显示", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Daedalus.configurations.setActivateCounter(-1);
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();
        }
    }

    public void onActivityResult(int request, int result, Intent data) {
        if (result == Activity.RESULT_OK) {
            DaedalusVpnService.primaryServer = DNSServerHelper.getAddressById(DNSServerHelper.getPrimary());
            DaedalusVpnService.secondaryServer = DNSServerHelper.getAddressById(DNSServerHelper.getSecondary());
            Daedalus.getInstance().startService(Daedalus.getServiceIntent(getApplicationContext()).setAction(DaedalusVpnService.ACTION_ACTIVATE));
            updateMainButton(R.string.button_text_deactivate);
            Daedalus.updateShortcut(getApplicationContext());
        }
    }

    private void updateMainButton(int id) {
        if (currentFragment instanceof HomeFragment) {
            Button button = currentFragment.getView().findViewById(R.id.button_activate);
            button.setText(id);
        }
    }

    private void updateUserInterface(Intent intent) {
        int launchAction = intent.getIntExtra(LAUNCH_ACTION, LAUNCH_ACTION_NONE);
        Log.d(TAG, "Updating user interface with Launch Action " + String.valueOf(launchAction));
        if (launchAction == LAUNCH_ACTION_ACTIVATE) {
            this.activateService();
        } else if (launchAction == LAUNCH_ACTION_DEACTIVATE) {
            Daedalus.deactivateService(getApplicationContext());
        } else if (launchAction == LAUNCH_ACTION_SERVICE_DONE) {
            Daedalus.updateShortcut(getApplicationContext());
            if (DaedalusVpnService.isActivated()) {
                updateMainButton(R.string.button_text_deactivate);
            } else {
                updateMainButton(R.string.button_text_activate);
            }
        }

        int fragment = intent.getIntExtra(LAUNCH_FRAGMENT, FRAGMENT_NONE);

        if (intent.getBooleanExtra(LAUNCH_NEED_RECREATE, false)) {
            finish();
            overridePendingTransition(R.anim.start, R.anim.end);
            if (fragment != FRAGMENT_NONE) {
                startActivity(new Intent(this, MainActivity.class)
                        .putExtra(LAUNCH_FRAGMENT, fragment));
            } else {
                startActivity(new Intent(this, MainActivity.class));
            }
            return;
        }

        switch (fragment) {
            case FRAGMENT_ABOUT:
                switchFragment(AboutFragment.class);
                break;
            case FRAGMENT_DNS_SERVERS:
                switchFragment(DNSServersFragment.class);
                break;
            case FRAGMENT_DNS_TEST:
                switchFragment(DNSTestFragment.class);
                break;
            case FRAGMENT_HOME:
                switchFragment(HomeFragment.class);
                break;
            case FRAGMENT_RULES:
                switchFragment(RulesFragment.class);
                break;
            case FRAGMENT_SETTINGS:
                switchFragment(SettingsFragment.class);
                break;
            case FRAGMENT_LOG:
                switchFragment(LogFragment.class);
                break;
        }
        if (currentFragment == null) {
            switchFragment(HomeFragment.class);
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        switch (id) {
            case R.id.nav_about:
                switchFragment(AboutFragment.class);
                break;
            case R.id.nav_dns_server:
                switchFragment(DNSServersFragment.class);
                break;
            case R.id.nav_dns_test:
                switchFragment(DNSTestFragment.class);
                break;
            case R.id.nav_github:
                Daedalus.openUri("https://github.com/iTXTech/Daedalus");
                break;
            case R.id.nav_home:
                switchFragment(HomeFragment.class);
                break;
            case R.id.nav_rules:
                switchFragment(RulesFragment.class);
                break;
            case R.id.nav_settings:
                switchFragment(SettingsFragment.class);
                break;
            case R.id.nav_log:
                switchFragment(LogFragment.class);
                break;
        }

        DrawerLayout drawer = findViewById(R.id.main_drawer_layout);
        drawer.closeDrawer(GravityCompat.START);

        InputMethodManager imm = (InputMethodManager) Daedalus.getInstance().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(findViewById(R.id.id_content).getWindowToken(), 0);
        return true;
    }
}
