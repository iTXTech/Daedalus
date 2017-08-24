package org.itxtech.daedalus.util;

import android.util.Log;
import org.itxtech.daedalus.Daedalus;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

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
public class Logger {
    private static StringBuffer buffer = null;

    public static void init() {
        if (buffer != null) {
            buffer.setLength(0);
        } else {
            buffer = new StringBuffer();
        }
    }

    public static void shutdown() {
        buffer = null;
    }

    public static String getLog() {
        return buffer.toString();
    }

    public static void error(String message) {
        send("[ERROR] " + message);
    }

    public static void warning(String message) {
        send("[WARNING] " + message);
    }

    public static void info(String message) {
        send("[INFO] " + message);
    }

    public static void debug(String message) {
        send("[DEBUG] " + message);
    }

    public static void logException(Throwable e) {
        error(getExceptionMessage(e));
    }

    public static String getExceptionMessage(Throwable e) {
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        e.printStackTrace(printWriter);
        return stringWriter.toString();
    }

    private static int getLogSizeLimit() {
        return Integer.parseInt(Daedalus.getPrefs().getString("settings_log_size", "10000"));
    }

    private static boolean checkBufferSize() {
        int limit = getLogSizeLimit();
        if (limit == 0) {//DISABLED!
            return false;
        }
        if (limit == -1) {//N0 limit
            return true;
        }
        if (buffer.length() > limit) {//LET's clean it up!
            buffer.setLength(limit);
        }
        return true;
    }

    private static void send(String message) {
        try {
            if (checkBufferSize()) {
                String fileDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss ").format(new Date());
                buffer.insert(0, "\n").insert(0, message).insert(0, fileDateFormat);
            }
            Log.d("Daedalus", message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
