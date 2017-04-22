package org.itxtech.daedalus.activity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import org.itxtech.daedalus.BuildConfig;
import org.itxtech.daedalus.R;

import java.util.Locale;

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
public class AboutActivity extends AppCompatActivity {
    private WebView mWebView = null;

    @SuppressLint({"JavascriptInterface", "SetJavaScriptEnabled", "addJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        mWebView = new WebView(this.getApplicationContext());
        ((ViewGroup) findViewById(R.id.activity_about)).addView(mWebView);

        mWebView.getSettings().setJavaScriptEnabled(true);
        mWebView.setBackgroundColor(0);
        mWebView.addJavascriptInterface(this, "JavascriptInterface");

        mWebView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                return true;
            }
        });

        if (Locale.getDefault().getLanguage().equals("zh")) {
            mWebView.loadUrl("file:///android_asset/about_html/index_zh.html");
        } else {
            mWebView.loadUrl("file:///android_asset/about_html/index.html");
        }

        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {//for better compatibility
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                try {
                    mWebView.loadUrl("javascript:changeVersionInfo('" + getPackageManager().getPackageInfo(getPackageName(), 0).versionName + "', '" + BuildConfig.BUILD_TIME + "', '" + BuildConfig.GIT_COMMIT + "')");
                } catch (Exception e) {
                    Log.e("DAboutActivity", e.toString());
                }
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mWebView != null) {
            Log.d("DAboutActivity", "onDestroy");

            mWebView.removeAllViews();
            mWebView.setWebViewClient(null);
            ((ViewGroup) mWebView.getParent()).removeView(mWebView);
            mWebView.setTag(null);
            mWebView.clearHistory();
            mWebView.destroy();
            mWebView = null;
        }

        System.gc();

        //System.exit(0);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_about, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_visit_itxtech) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://itxtech.org")));
        }

        if (id == R.id.action_visit_github) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/iTXTech/Daedalus")));
        }

        if (id == R.id.action_license) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/iTXTech/Daedalus/blob/master/LICENSE")));
        }

        /*if (id == R.id.action_visit_cutedns) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.cutedns.cn")));
        }*/

        /*if (id == R.id.action_join_qqgroup) {
            joinQQGroup("q6Lfo_EhAEO1fP6Xg3fmKsP4pd6U5-RE");
        }*/

        return super.onOptionsItemSelected(item);
    }

    private boolean joinQQGroup(String key) {
        Intent intent = new Intent();
        intent.setData(Uri.parse("mqqopensdkapi://bizAgent/qm/qr?url=http%3A%2F%2Fqm.qq.com%2Fcgi-bin%2Fqm%2Fqr%3Ffrom%3Dapp%26p%3Dandroid%26k%3D" + key));
        // 此Flag可根据具体产品需要自定义，如设置，则在加群界面按返回，返回手Q主界面，不设置，按返回会返回到呼起产品界面    //intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(intent);
            return true;
        } catch (Exception e) {
            Snackbar.make(findViewById(R.id.activity_about), R.string.notice_join_group_failed, Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
            return false;
        }
    }
}
