package org.itxtech.daedalus.fragment;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import org.itxtech.daedalus.R;
import org.itxtech.daedalus.util.QueryLog;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
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
 * Query Log page: the recent DNS queries with the rule that selected the servers, the
 * server that answered and the answer. Newest first, filtered by domain, refreshed every
 * few seconds while visible.
 */
public class QueryLogFragment extends ToolbarFragment implements Toolbar.OnMenuItemClickListener {
    private static final long REFRESH_INTERVAL = 2000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresher = new Runnable() {
        @Override
        public void run() {
            refresh();
            handler.postDelayed(this, REFRESH_INTERVAL);
        }
    };
    private final DateFormat timeFormat = DateFormat.getTimeInstance(DateFormat.MEDIUM);
    private ArrayAdapter<QueryLog.Entry> adapter = null;
    private String filter = "";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_query_log, container, false);

        ListView list = view.findViewById(R.id.listView_query_log);
        list.setEmptyView(view.findViewById(R.id.textView_query_log_empty));
        adapter = new ArrayAdapter<QueryLog.Entry>(getActivity(), android.R.layout.simple_list_item_2, android.R.id.text1) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                View row = super.getView(position, convertView, parent);
                QueryLog.Entry entry = getItem(position);
                if (entry != null) {
                    ((TextView) row.findViewById(android.R.id.text1)).setText(entry.question + "  →  " + entry.result);
                    ((TextView) row.findViewById(android.R.id.text2)).setText(describe(entry));
                }
                return row;
            }
        };
        list.setAdapter(adapter);

        EditText filterView = view.findViewById(R.id.editText_query_filter);
        filterView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                filter = s.toString().trim().toLowerCase();
                refresh();
            }
        });
        return view;
    }

    private String describe(QueryLog.Entry entry) {
        StringBuilder text = new StringBuilder(timeFormat.format(new Date(entry.time)))
                .append(" · ").append(entry.selection);
        if (!entry.server.isEmpty()) {
            text.append(" · ").append(entry.server);
        }
        if (entry.elapsed > 0) {
            text.append(" · ").append(entry.elapsed).append(" ms");
        }
        if (entry.note != null && !entry.note.isEmpty()) {
            text.append("\n").append(entry.note);
        }
        return text.toString();
    }

    private void refresh() {
        if (adapter == null) {
            return;
        }
        List<QueryLog.Entry> entries = QueryLog.getEntries();
        ArrayList<QueryLog.Entry> shown = new ArrayList<>(entries.size());
        for (int i = entries.size() - 1; i >= 0; i--) {
            QueryLog.Entry entry = entries.get(i);
            if (filter.isEmpty() || entry.question.toLowerCase().contains(filter)) {
                shown.add(entry);
            }
        }
        adapter.setNotifyOnChange(false);
        adapter.clear();
        adapter.addAll(shown);
        adapter.notifyDataSetChanged();
    }

    @Override
    public void onResume() {
        super.onResume();
        handler.post(refresher);
    }

    @Override
    public void onPause() {
        super.onPause();
        handler.removeCallbacks(refresher);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacks(refresher);
        adapter = null;
    }

    @Override
    public void checkStatus() {
        menu.findItem(R.id.nav_query_log).setChecked(true);
        toolbar.setTitle(R.string.action_query_log);
        toolbar.inflateMenu(R.menu.query_log);
        toolbar.setOnMenuItemClickListener(this);
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_delete) {
            QueryLog.clear();
            refresh();
        } else if (id == R.id.action_refresh) {
            refresh();
        }
        return true;
    }
}
