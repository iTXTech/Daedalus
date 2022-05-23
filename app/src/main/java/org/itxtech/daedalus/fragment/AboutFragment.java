package org.itxtech.daedalus.fragment;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import org.itxtech.daedalus.BuildConfig;
import org.itxtech.daedalus.Daedalus;
import org.itxtech.daedalus.R;
import org.itxtech.daedalus.util.Logger;

import java.util.Locale;

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
public class AboutFragment extends ToolbarFragment {
    private WebView mWebView = null;

    @SuppressLint({"JavascriptInterface", "SetJavaScriptEnabled", "addJavascriptInterface"})
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_about, container, false);

        mWebView = new WebView(Daedalus.getInstance());
        ((ViewGroup) view.findViewById(R.id.fragment_about)).addView(mWebView);

        mWebView.getSettings().setJavaScriptEnabled(true);
        mWebView.setBackgroundColor(0);
        mWebView.addJavascriptInterface(this, "JavascriptInterface");

        mWebView.setOnLongClickListener(v -> true);

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
                    mWebView.loadUrl("javascript:changeColor('"+(Daedalus.isDarkTheme() ? "#FFFFFF" : "#000000")+"')");
                    mWebView.loadUrl("javascript:changeVersionInfo('" + Daedalus.getInstance().getPackageManager().getPackageInfo(Daedalus.getInstance().getPackageName(), 0).versionName + "', '" + BuildConfig.BUILD_TIME + "', '" + BuildConfig.GIT_COMMIT + "')");
                } catch (Exception e) {
                    Logger.logException(e);
                }
            }
        });
        return view;
    }

    @Override
    public void checkStatus() {
        menu.findItem(R.id.nav_about).setChecked(true);
        toolbar.setTitle(R.string.action_about);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (mWebView != null) {
            mWebView.removeAllViews();
            mWebView.setWebViewClient(null);
            ((ViewGroup) mWebView.getParent()).removeView(mWebView);
            mWebView.setTag(null);
            mWebView.clearHistory();
            mWebView.destroy();
            mWebView = null;
        }
    }
}
