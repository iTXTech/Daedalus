package org.itxtech.daedalus.util;

import android.util.Log;
import org.itxtech.daedalus.Daedalus;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
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
    private static final String CRASH_FILE = "crash.log";
    private static final int CRASH_LOG_LIMIT = 200 * 1024;
    /**
     * The log is mirrored to this file while the app runs. The buffer alone is lost with the
     * process, and a process killed by the system (ANR, low memory, force stop) leaves no
     * crash.log either, so the file is the only trace of what happened before.
     */
    private static final String LOG_FILE = "daedalus.log";
    private static final int LOG_FILE_LIMIT = 256 * 1024;
    private static final Object FILE_LOCK = new Object();

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

    /**
     * Records uncaught exceptions of every thread in logs/crash.log before the system
     * handles them, so that the reason of a crash survives the process.
     */
    public static void installCrashHandler() {
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                File file = getCrashFile();
                if (file != null) {
                    try (FileWriter writer = new FileWriter(file, true)) {
                        writer.write("=== " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())
                                + " thread \"" + thread.getName() + "\" ===\n" + getExceptionMessage(throwable) + "\n");
                    }
                }
            } catch (Exception ignored) {
            }
            if (previous != null) {
                previous.uncaughtException(thread, throwable);
            }
        });
    }

    private static File getCrashFile() {
        if (Daedalus.logPath != null) {
            return new File(Daedalus.logPath, CRASH_FILE);
        }
        Daedalus app = Daedalus.getInstance();
        return app == null ? null : new File(app.getFilesDir(), CRASH_FILE);
    }

    /**
     * The recorded crashes (oldest first, capped to the last 200 KB), or null when none.
     */
    public static String getCrashLog() {
        File file = getCrashFile();
        if (file == null || !file.isFile() || file.length() == 0) {
            return null;
        }
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long start = Math.max(0, raf.length() - CRASH_LOG_LIMIT);
            byte[] data = new byte[(int) (raf.length() - start)];
            raf.seek(start);
            raf.readFully(data);
            return new String(data, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    public static void clearCrashLog() {
        File file = getCrashFile();
        if (file != null && file.exists() && !file.delete()) {
            warning("Cannot delete " + file);
        }
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
            String date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss ").format(new Date());
            if (checkBufferSize()) {
                buffer.insert(0, "\n").insert(0, message).insert(0, date);
            }
            Log.d("Daedalus", message);
            appendToFile(date + message + "\n");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Appends the formatted line to the log file, trimming the oldest half once the file
     * grows past the limit. Never throws: losing a log line must not break logging.
     */
    private static void appendToFile(String line) {
        try {
            File file = getLogFile();
            if (file == null) {
                return;
            }
            synchronized (FILE_LOCK) {
                if (file.length() > LOG_FILE_LIMIT) {
                    trim(file);
                }
                try (FileWriter writer = new FileWriter(file, true)) {
                    writer.write(line);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static void trim(File file) {
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            long keep = Math.max(0, raf.length() - LOG_FILE_LIMIT / 2);
            byte[] data = new byte[(int) (raf.length() - keep)];
            raf.seek(keep);
            raf.readFully(data);
            raf.seek(0);
            raf.write(data);
            raf.setLength(data.length);
        } catch (Exception ignored) {
        }
    }

    /**
     * The log file of the current run, or null while the directories are not set up yet.
     */
    private static File getLogFile() {
        if (Daedalus.logPath != null) {
            return new File(Daedalus.logPath, LOG_FILE);
        }
        Daedalus app = Daedalus.getInstance();
        return app == null ? null : new File(app.getFilesDir(), LOG_FILE);
    }

    /**
     * The log of the previous run, or null when there is none.
     */
    public static String getLogFileContent() {
        File file = getLogFile();
        if (file == null || !file.isFile() || file.length() == 0) {
            return null;
        }
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long start = Math.max(0, raf.length() - LOG_FILE_LIMIT);
            byte[] data = new byte[(int) (raf.length() - start)];
            raf.seek(start);
            raf.readFully(data);
            return new String(data, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    public static void clearLogFile() {
        File file = getLogFile();
        if (file != null && file.exists() && !file.delete()) {
            warning("Cannot delete " + file);
        }
    }
}
