package org.itxtech.daedalus.util;

import android.util.Log;

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
    private static StringBuilder builder;

    public static void init() {
        builder = new StringBuilder();
    }

    public static void shutdown() {
        builder = null;
    }

    public static String getLog() {
        return builder.toString();
    }

    public static void emergency(String message) {
        send("[EMERGENCY] " + message);
    }

    public static void alert(String message) {
        send("[ALERT] " + message);
    }

    public static void critical(String message) {
        send("[CRITICAL] " + message);
    }

    public static void error(String message) {
        send("[ERROR] " + message);
    }

    public static void warning(String message) {
        send("[WARNING] " + message);
    }

    public static void notice(String message) {
        send("[NOTICE] " + message);
    }

    public static void info(String message) {
        send("[INFO] " + message);
    }

    public static void debug(String message) {
        send("[DEBUG] " + message);
    }

    public static void logException(Throwable e) {
        alert(getExceptionMessage(e));
    }

    private static String getExceptionMessage(Throwable e) {
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        e.printStackTrace(printWriter);
        return stringWriter.toString();
    }

    private static void send(String message) {
        String fileDateFormat = new SimpleDateFormat("Y-M-d HH:mm:ss ").format(new Date());
        builder.insert(0, "\n").insert(0, message).insert(0, fileDateFormat);
        Log.d("Daedalus", message);
    }
}
