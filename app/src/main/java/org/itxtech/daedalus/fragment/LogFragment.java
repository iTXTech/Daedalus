package org.itxtech.daedalus.fragment;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.appcompat.widget.Toolbar;
import com.google.android.material.snackbar.Snackbar;
import org.itxtech.daedalus.R;
import org.itxtech.daedalus.util.Logger;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
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
public class LogFragment extends ToolbarFragment implements Toolbar.OnMenuItemClickListener {

    private static final int EXPORT_REQUEST_CODE = 21;
    private static final String EXPORT_MIME_TYPE = "text/plain";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_log, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        refresh();
    }

    private void refresh() {
        ((TextView) getView().findViewById(R.id.textView_log)).setText(getLogText());
    }

    /**
     * The log of the previous runs from the log file, the recorded crashes in front of it.
     * The in-memory buffer is only a fallback: it is lost with the process, which is exactly
     * what happens when the system kills the app.
     */
    private String getLogText() {
        String text = Logger.getLogFileContent();
        if (text == null) {
            text = Logger.getLog();
        }
        String crashes = Logger.getCrashLog();
        if (crashes != null) {
            text = getString(R.string.log_crashes) + "\n" + crashes + "\n" + text;
        }
        return text == null ? "" : text;
    }

    /**
     * Asks the system where to put the log. It used to be written next to the log files of the
     * app, that is under Android/data, which no file manager shows and which is wiped when the
     * app is uninstalled.
     */
    private void export() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(EXPORT_MIME_TYPE);
        intent.putExtra(Intent.EXTRA_TITLE, "daedalus-"
                + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + ".txt");
        startActivityForResult(intent, EXPORT_REQUEST_CODE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != EXPORT_REQUEST_CODE || resultCode != Activity.RESULT_OK
                || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        try (OutputStream out = requireContext().getContentResolver().openOutputStream(uri, "rwt")) {
            if (out == null) {
                throw new IllegalStateException("Cannot open " + uri);
            }
            out.write(getLogText().getBytes(StandardCharsets.UTF_8));
            showMessage(getString(R.string.notice_export_complete));
        } catch (Exception e) {
            Logger.logException(e);
            showMessage(getString(R.string.notice_export_failed, String.valueOf(e.getMessage())));
        }
    }

    private void showMessage(String message) {
        View view = getView();
        if (view != null) {
            Snackbar.make(view, message, Snackbar.LENGTH_LONG).setAction("Action", null).show();
        }
    }

    @Override
    public void checkStatus() {
        menu.findItem(R.id.nav_log).setChecked(true);
        toolbar.setTitle(R.string.action_log);
        toolbar.inflateMenu(R.menu.log);
        toolbar.setOnMenuItemClickListener(this);
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        int id = item.getItemId();

        switch (id) {
            case R.id.action_delete:
                Logger.init();
                Logger.clearCrashLog();
                Logger.clearLogFile();
                refresh();
                break;
            case R.id.action_refresh:
                refresh();
                break;
            case R.id.action_export:
                export();
                break;
        }

        return true;
    }
}
