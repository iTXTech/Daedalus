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
import android.view.MenuItem;
import android.widget.TextView;
import org.itxtech.daedalus.BuildConfig;
import org.itxtech.daedalus.Daedalus;
import org.itxtech.daedalus.R;
import org.itxtech.daedalus.fragment.DNSTestFragment;
import org.itxtech.daedalus.fragment.MainFragment;

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

    private static final int FRAGMENT_MAIN = 0;
    private static final int FRAGMENT_DNS_TEST = 1;

    private static int currentFragment = FRAGMENT_MAIN;

    private static MainActivity instance = null;

    private MainFragment mMain;
    private DNSTestFragment mDnsTest;

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

        FragmentManager fm = getFragmentManager();
        FragmentTransaction transaction = fm.beginTransaction();
        if (currentFragment == FRAGMENT_MAIN) {
            mMain = new MainFragment();
            transaction.replace(R.id.id_content, mMain);
        }
        if (currentFragment == FRAGMENT_DNS_TEST) {
            mDnsTest = new DNSTestFragment();
            transaction.replace(R.id.id_content, mDnsTest);
        }
        transaction.commit();

        updateUserInterface(getIntent());
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.main_drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        Log.d("DMainActivity", "onDestroy");
        mMain = null;
        mDnsTest = null;
        instance = null;
        System.gc();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        updateUserInterface(intent);
    }

    private void updateUserInterface(Intent intent) {
        Log.d("MainActivity", "Updating user interface");
        int launchAction = intent.getIntExtra(LAUNCH_ACTION, LAUNCH_ACTION_NONE);
        if (launchAction == LAUNCH_ACTION_ACTIVATE) {
            Daedalus.updateShortcut(this.getApplicationContext());
            mMain.activateService();
        } else if (launchAction == LAUNCH_ACTION_DEACTIVATE) {
            Daedalus.getInstance().deactivateService();
        } else {
            Daedalus.updateShortcut(this.getApplicationContext());
        }
    }

    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
        }

        if (id == R.id.nav_about) {
            startActivity(new Intent(this, AboutActivity.class));
        }

        if (id == R.id.nav_dns_test) {
            if (mDnsTest == null) {
                mDnsTest = new DNSTestFragment();
            }
            FragmentManager fm = getFragmentManager();
            FragmentTransaction transaction = fm.beginTransaction();
            transaction.replace(R.id.id_content, mDnsTest);
            transaction.commit();
            Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
            toolbar.setTitle(R.string.action_dns_test);
            currentFragment = FRAGMENT_DNS_TEST;
            item.setChecked(true);
        }

        if (id == R.id.nav_home) {
            if (mMain == null) {
                mMain = new MainFragment();
            }
            FragmentManager fm = getFragmentManager();
            FragmentTransaction transaction = fm.beginTransaction();
            transaction.replace(R.id.id_content, mMain);
            transaction.commit();
            Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
            toolbar.setTitle(R.string.app_name);
            currentFragment = FRAGMENT_MAIN;
            item.setChecked(true);
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
