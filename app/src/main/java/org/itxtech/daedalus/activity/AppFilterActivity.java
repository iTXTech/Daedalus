package org.itxtech.daedalus.activity;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.itxtech.daedalus.Daedalus;
import org.itxtech.daedalus.R;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

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
 * Picks the apps of the app filter. Every installed app is listed (user apps first,
 * then system apps, each sorted by name) and can be searched by name or package.
 */
public class AppFilterActivity extends AppCompatActivity {
    private final ArrayList<AppObject> allApps = new ArrayList<>();
    private RecyclerViewAdapter adapter;
    private Toolbar toolbar;
    private String filter = "";

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        if (Daedalus.isDarkTheme()) {
            setTheme(R.style.AppTheme_Dark_NoActionBar);
        }
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_app_filter);
        toolbar = findViewById(R.id.toolbar_filter);
        Drawable drawable = ContextCompat.getDrawable(this, R.drawable.ic_clear);
        Drawable wrappedDrawable = DrawableCompat.wrap(Objects.requireNonNull(drawable));
        DrawableCompat.setTint(wrappedDrawable, Color.WHITE);
        toolbar.setNavigationIcon(drawable);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
        toolbar.setTitle(R.string.settings_app_filter);
        toolbar.setSubtitle(R.string.app_filter_loading);

        RecyclerView recyclerView = findViewById(R.id.recyclerView_app_filter_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RecyclerViewAdapter();
        recyclerView.setAdapter(adapter);

        EditText search = findViewById(R.id.editText_app_filter);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                filter = s.toString().trim().toLowerCase();
                adapter.applyFilter();
            }
        });

        new Thread(() -> {
            ArrayList<AppObject> apps = getAppList();
            runOnUiThread(() -> {
                allApps.clear();
                allApps.addAll(apps);
                adapter.applyFilter();
                updateSubtitle();
            });
        }, "AppList").start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Daedalus.configurations.save();
    }

    private void updateSubtitle() {
        toolbar.setSubtitle(getString(R.string.app_filter_selected, Daedalus.configurations.getAppObjects().size()));
    }

    private static class AppObject {
        private final String name;
        private final String packageName;
        private final Drawable icon;
        private final boolean system;
        private final String searchKey;

        AppObject(String name, String packageName, Drawable icon, boolean system) {
            this.name = name;
            this.packageName = packageName;
            this.icon = icon;
            this.system = system;
            this.searchKey = (name + "\n" + packageName).toLowerCase();
        }
    }

    /**
     * Every installed app except this one: user apps first, then system apps, each
     * sorted by name in the current locale.
     */
    private ArrayList<AppObject> getAppList() {
        PackageManager packageManager = getPackageManager();
        List<ApplicationInfo> installed = packageManager.getInstalledApplications(0);
        ArrayList<AppObject> apps = new ArrayList<>(installed.size());
        for (ApplicationInfo info : installed) {
            if (getPackageName().equals(info.packageName)) {
                continue;
            }
            apps.add(new AppObject(
                    info.loadLabel(packageManager).toString(),
                    info.packageName,
                    info.loadIcon(packageManager),
                    (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0));
        }
        final Collator collator = Collator.getInstance();
        Collections.sort(apps, (a, b) -> {
            if (a.system != b.system) {
                return a.system ? 1 : -1;
            }
            return collator.compare(a.name, b.name);
        });
        return apps;
    }

    private class RecyclerViewAdapter extends RecyclerView.Adapter<RecyclerViewHolder> {
        private final ArrayList<AppObject> shown = new ArrayList<>();

        void applyFilter() {
            shown.clear();
            for (AppObject app : allApps) {
                if (filter.isEmpty() || app.searchKey.contains(filter)) {
                    shown.add(app);
                }
            }
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public RecyclerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.card_appview, parent, false);
            return new RecyclerViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerViewHolder holder, int position) {
            AppObject app = shown.get(position);
            holder.packageName = app.packageName;
            holder.appName.setText(app.name);
            holder.appPackage.setText(app.system
                    ? app.packageName + " · " + getString(R.string.app_filter_system) : app.packageName);
            holder.appIcon.setImageDrawable(app.icon);
            holder.appCheck.setChecked(Daedalus.configurations.getAppObjects().contains(app.packageName));
        }

        @Override
        public int getItemCount() {
            return shown.size();
        }
    }

    private class RecyclerViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        private final ImageView appIcon;
        private final TextView appName;
        private final TextView appPackage;
        private final CheckBox appCheck;
        private String packageName;

        RecyclerViewHolder(@NonNull View itemView) {
            super(itemView);
            appIcon = itemView.findViewById(R.id.app_icon);
            appName = itemView.findViewById(R.id.app_name);
            appPackage = itemView.findViewById(R.id.app_package);
            appCheck = itemView.findViewById(R.id.app_check);
            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            List<String> selected = Daedalus.configurations.getAppObjects();
            if (selected.contains(packageName)) {
                selected.remove(packageName);
                appCheck.setChecked(false);
            } else {
                selected.add(packageName);
                appCheck.setChecked(true);
            }
            updateSubtitle();
        }
    }
}
