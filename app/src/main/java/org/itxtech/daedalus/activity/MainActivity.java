package org.itxtech.daedalus.activity;

import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import org.itxtech.daedalus.BuildConfig;
import org.itxtech.daedalus.Daedalus;
import org.itxtech.daedalus.R;
import org.itxtech.daedalus.fragment.DNSTestFragment;
import org.itxtech.daedalus.fragment.MainFragment;
import org.itxtech.daedalus.fragment.SettingsFragment;

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
public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {
    public static final String LAUNCH_ACTION = "org.itxtech.daedalus.activity.MainActivity.LAUNCH_ACTION";
    public static final int LAUNCH_ACTION_NONE = 0;
    public static final int LAUNCH_ACTION_ACTIVATE = 1;
    public static final int LAUNCH_ACTION_DEACTIVATE = 2;
    public static final String LAUNCH_FRAGMENT = "org.itxtech.daedalus.activity.MainActivity.LAUNCH_FRAGMENT";

    private static final String TAG = "DMainActivity";

    public static final int FRAGMENT_NONE = -1;
    public static final int FRAGMENT_MAIN = 0;
    public static final int FRAGMENT_DNS_TEST = 1;
    public static final int FRAGMENT_SETTINGS = 2;

    private static MainActivity instance = null;

    private MainFragment mMain;
    private DNSTestFragment mDnsTest;
    private SettingsFragment mSettings;
    private int currentFragment = FRAGMENT_NONE;

    public static MainActivity getInstance() {
        return instance;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
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
            FragmentManager fm = getFragmentManager();
            FragmentTransaction transaction = fm.beginTransaction();
            if (mMain == null) {
                mMain = new MainFragment();
            }
            transaction.replace(R.id.id_content, mMain).commit();
            currentFragment = FRAGMENT_MAIN;
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
        }
    }

    private void changeFragment(int fragment) {
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
                    mDnsTest = new DNSTestFragment();
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
        }
        transaction.commit();
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.main_drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else if (currentFragment != FRAGMENT_MAIN) {
            changeFragment(FRAGMENT_MAIN);
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
        instance = null;
        System.gc();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        updateUserInterface(intent);
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
    }

    private void updateUserInterface(Intent intent) {
        Log.d(TAG, "Updating user interface");
        int launchAction = intent.getIntExtra(LAUNCH_ACTION, LAUNCH_ACTION_NONE);
        if (launchAction == LAUNCH_ACTION_ACTIVATE) {
            Daedalus.updateShortcut(this.getApplicationContext());
            mMain.activateService();
        } else if (launchAction == LAUNCH_ACTION_DEACTIVATE) {
            Daedalus.getInstance().deactivateService();
        } else {
            Daedalus.updateShortcut(this.getApplicationContext());
        }

        int fragment = intent.getIntExtra(LAUNCH_FRAGMENT, FRAGMENT_NONE);
        if (fragment != FRAGMENT_NONE) {
            changeFragment(fragment);
        }
    }

    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_settings) {
            changeFragment(FRAGMENT_SETTINGS);
        }

        if (id == R.id.nav_about) {
            startActivity(new Intent(this, AboutActivity.class));
            item.setChecked(false);
        }

        if (id == R.id.nav_dns_test) {
            changeFragment(FRAGMENT_DNS_TEST);
        }

        if (id == R.id.nav_home) {
            changeFragment(FRAGMENT_MAIN);
        }

        if (id == R.id.nav_check_update) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/iTXTech/Daedalus/releases")));
        }

        if (id == R.id.nav_bug_report) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/iTXTech/Daedalus/issues")));
        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.main_drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }
}
