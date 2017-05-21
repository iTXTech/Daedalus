package org.itxtech.daedalus.activity;

import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import org.itxtech.daedalus.BuildConfig;
import org.itxtech.daedalus.Daedalus;
import org.itxtech.daedalus.R;
import org.itxtech.daedalus.fragment.*;

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
    public static final int LAUNCH_ACTION_AFTER_DEACTIVATE = 3;

    public static final String LAUNCH_FRAGMENT = "org.itxtech.daedalus.activity.MainActivity.LAUNCH_FRAGMENT";
    public static final int FRAGMENT_NONE = -1;
    public static final int FRAGMENT_MAIN = 0;
    public static final int FRAGMENT_DNS_TEST = 1;
    public static final int FRAGMENT_SETTINGS = 2;
    public static final int FRAGMENT_ABOUT = 3;
    public static final int FRAGMENT_RULES = 4;
    public static final int FRAGMENT_DNS_SERVERS = 5;

    private static MainActivity instance = null;

    private MainFragment mMain;
    private DnsTestFragment mDnsTest;
    private SettingsFragment mSettings;
    private AboutFragment mAbout;
    private RulesFragment mHosts;
    private DnsServersFragment mDnsServers;
    private int currentFragment = FRAGMENT_NONE;

    public static MainActivity getInstance() {
        return instance;
    }

    public int getCurrentFragment() {
        return currentFragment;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.AppTheme_NoActionBar_TransparentStatusBar);
        super.onCreate(savedInstanceState);

        instance = this;

        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.main_drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        ((TextView) navigationView.getHeaderView(0).findViewById(R.id.textView_nav_version)).setText(getString(R.string.nav_version) + " " + BuildConfig.VERSION_NAME);
        ((TextView) navigationView.getHeaderView(0).findViewById(R.id.textView_nav_git_commit)).setText(getString(R.string.nav_git_commit) + " " + BuildConfig.GIT_COMMIT);

        if (getIntent().getIntExtra(LAUNCH_FRAGMENT, FRAGMENT_NONE) == FRAGMENT_NONE) {
            switchFragment(FRAGMENT_MAIN);
        }

        updateUserInterface(getIntent());
        Log.d(TAG, "onCreate");
    }

    @Override
    public void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        updateTitle();
        updateNavigationMenu();
    }

    private void updateNavigationMenu() {
        Menu menu = ((NavigationView) findViewById(R.id.nav_view)).getMenu();
        switch (currentFragment) {
            case FRAGMENT_MAIN:
                menu.findItem(R.id.nav_home).setChecked(true);
                break;
            case FRAGMENT_DNS_TEST:
                menu.findItem(R.id.nav_dns_test).setChecked(true);
                break;
            case FRAGMENT_SETTINGS:
                menu.findItem(R.id.nav_settings).setChecked(true);
                break;
            case FRAGMENT_ABOUT:
                menu.findItem(R.id.nav_about).setChecked(true);
                break;
            case FRAGMENT_RULES:
                menu.findItem(R.id.nav_rules).setChecked(true);
                break;
            case FRAGMENT_DNS_SERVERS:
                menu.findItem(R.id.nav_dns_server).setChecked(true);
        }
    }

    private void updateTitle() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        switch (currentFragment) {
            case FRAGMENT_MAIN:
                toolbar.setTitle(R.string.action_home);
                break;
            case FRAGMENT_DNS_TEST:
                toolbar.setTitle(R.string.action_dns_test);
                break;
            case FRAGMENT_SETTINGS:
                toolbar.setTitle(R.string.action_settings);
                break;
            case FRAGMENT_ABOUT:
                toolbar.setTitle(R.string.action_about);
                break;
            case FRAGMENT_RULES:
                toolbar.setTitle(R.string.action_rules);
                break;
            case FRAGMENT_DNS_SERVERS:
                toolbar.setTitle(R.string.action_dns_servers);
                break;
        }
    }

    private void switchFragment(int fragment) {
        FragmentManager fm = getFragmentManager();
        FragmentTransaction transaction = fm.beginTransaction();
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        switch (fragment) {
            case FRAGMENT_MAIN:
                if (mMain == null) {
                    mMain = new MainFragment();
                }
                transaction.replace(R.id.id_content, mMain);
                toolbar.setTitle(R.string.action_home);
                currentFragment = FRAGMENT_MAIN;
                break;
            case FRAGMENT_DNS_TEST:
                if (mDnsTest == null) {
                    mDnsTest = new DnsTestFragment();
                }
                transaction.replace(R.id.id_content, mDnsTest);
                toolbar.setTitle(R.string.action_dns_test);
                currentFragment = FRAGMENT_DNS_TEST;
                break;
            case FRAGMENT_SETTINGS:
                if (mSettings == null) {
                    mSettings = new SettingsFragment();
                }
                transaction.replace(R.id.id_content, mSettings);
                toolbar.setTitle(R.string.action_settings);
                currentFragment = FRAGMENT_SETTINGS;
                break;
            case FRAGMENT_ABOUT:
                if (mAbout == null) {
                    mAbout = new AboutFragment();
                }
                transaction.replace(R.id.id_content, mAbout);
                toolbar.setTitle(R.string.action_about);
                currentFragment = FRAGMENT_ABOUT;
                break;
            case FRAGMENT_RULES:
                if (mHosts == null) {
                    mHosts = new RulesFragment();
                }
                transaction.replace(R.id.id_content, mHosts);
                toolbar.setTitle(R.string.action_rules);
                currentFragment = FRAGMENT_RULES;
                break;
            case FRAGMENT_DNS_SERVERS:
                if (mDnsServers == null) {
                    mDnsServers = new DnsServersFragment();
                }
                transaction.replace(R.id.id_content, mDnsServers);
                toolbar.setTitle(R.string.action_dns_servers);
                currentFragment = FRAGMENT_DNS_SERVERS;
                break;
        }
        transaction.commit();
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.main_drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else if (currentFragment != FRAGMENT_MAIN) {
            switchFragment(FRAGMENT_MAIN);
            updateNavigationMenu();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        Log.d(TAG, "onDestroy");
        mMain = null;
        mDnsTest = null;
        mSettings = null;
        mAbout = null;
        mHosts = null;
        instance = null;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        updateUserInterface(intent);
    }

    private void updateUserInterface(Intent intent) {
        Log.d(TAG, "Updating user interface");
        int launchAction = intent.getIntExtra(LAUNCH_ACTION, LAUNCH_ACTION_NONE);
        if (launchAction == LAUNCH_ACTION_ACTIVATE) {
            mMain.activateService();
        } else if (launchAction == LAUNCH_ACTION_DEACTIVATE) {
            Daedalus.getInstance().deactivateService();
        } else if (launchAction == LAUNCH_ACTION_AFTER_DEACTIVATE) {
            Daedalus.updateShortcut(this.getApplicationContext());
            if (currentFragment == FRAGMENT_MAIN && MainFragment.mHandler != null) {
                MainFragment.mHandler.obtainMessage(MainFragment.MainFragmentHandler.MSG_REFRESH).sendToTarget();
            }
        }

        int fragment = intent.getIntExtra(LAUNCH_FRAGMENT, FRAGMENT_NONE);
        if (fragment != FRAGMENT_NONE) {
            switchFragment(fragment);
            updateNavigationMenu();
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        switch (id) {
            case R.id.nav_about:
                switchFragment(FRAGMENT_ABOUT);
                break;
            case R.id.nav_dns_server:
                switchFragment(FRAGMENT_DNS_SERVERS);
                break;
            case R.id.nav_dns_test:
                switchFragment(FRAGMENT_DNS_TEST);
                break;
            case R.id.nav_github:
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/iTXTech/Daedalus")));
                break;
            case R.id.nav_home:
                switchFragment(FRAGMENT_MAIN);
                break;
            case R.id.nav_rules:
                switchFragment(FRAGMENT_RULES);
                break;
            case R.id.nav_settings:
                switchFragment(FRAGMENT_SETTINGS);
                break;
        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.main_drawer_layout);
        drawer.closeDrawer(GravityCompat.START);

        InputMethodManager imm = (InputMethodManager) Daedalus.getInstance().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(findViewById(R.id.id_content).getWindowToken(), 0);
        return true;
    }
}
