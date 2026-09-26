package org.itxtech.daedalus.util;

import android.text.TextUtils;
import org.itxtech.daedalus.Daedalus;
import org.itxtech.daedalus.R;
import org.minidns.dnsmessage.DnsMessage;
import org.minidns.record.Data;
import org.minidns.record.Record;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

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

/**
 * The last few hundred DNS queries handled by the VPN: which rule selected the servers,
 * which server answered and what it answered. Shown on the Query Log page.
 */
public class QueryLog {
    private static final int CAPACITY = 300;

    public static class Entry {
        public final long time = System.currentTimeMillis();
        /** "example.com A" */
        public final String question;
        /** Network rule that selected the servers, or "local rule", "system DNS", ... */
        public final String selection;
        /** Server that answered with its protocol, empty for local rules and failures */
        public final String server;
        /** Addresses, other records, RCODE or error */
        public final String result;
        public final long elapsed;
        /** Failed attempts before the answer, or null */
        public final String note;

        Entry(String question, String selection, String server, String result, long elapsed, String note) {
            this.question = question;
            this.selection = selection;
            this.server = server;
            this.result = result;
            this.elapsed = elapsed;
            this.note = note;
        }
    }

    private static final ArrayDeque<Entry> entries = new ArrayDeque<>();

    public static void add(String question, String selection, String server, String result, long elapsed, String note) {
        Entry entry = new Entry(question, selection, server, result, elapsed, note);
        synchronized (entries) {
            if (entries.size() >= CAPACITY) {
                entries.removeFirst();
            }
            entries.addLast(entry);
        }
    }

    /**
     * All entries, oldest first.
     */
    public static List<Entry> getEntries() {
        synchronized (entries) {
            return new ArrayList<>(entries);
        }
    }

    public static void clear() {
        synchronized (entries) {
            entries.clear();
        }
    }

    /**
     * The addresses of a response, or its other records, RCODE or "no answer".
     */
    public static String summarize(DnsMessage message) {
        ArrayList<String> addresses = new ArrayList<>();
        ArrayList<String> others = new ArrayList<>();
        for (Record<? extends Data> record : message.answerSection) {
            if (record.type == Record.TYPE.A || record.type == Record.TYPE.AAAA) {
                addresses.add(record.getPayload().toString());
            } else {
                others.add(record.type.name() + " " + record.getPayload());
            }
        }
        if (!addresses.isEmpty()) {
            return TextUtils.join(", ", addresses);
        }
        if (!others.isEmpty()) {
            return TextUtils.join(", ", others);
        }
        if (message.responseCode != DnsMessage.RESPONSE_CODE.NO_ERROR) {
            return message.responseCode.name();
        }
        // NOERROR with an empty answer: the name exists but has no record of the asked type
        String type = message.getQuestion() != null ? message.getQuestion().type.name() : "";
        return Daedalus.getInstance().getString(R.string.query_log_no_record, type);
    }
}
