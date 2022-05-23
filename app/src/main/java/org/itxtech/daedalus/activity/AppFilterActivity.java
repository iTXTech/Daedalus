package org.itxtech.daedalus.activity;

import android.annotation.SuppressLint;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
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

import java.util.ArrayList;
import java.util.HashMap;
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
public class AppFilterActivity extends AppCompatActivity {
    private RecyclerViewAdapter adapter;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        if (Daedalus.isDarkTheme()) {
            setTheme(R.style.AppTheme_Dark_NoActionBar);
        }
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_app_filter);
        Toolbar toolbar = findViewById(R.id.toolbar_filter);
        Drawable drawable = ContextCompat.getDrawable(this, R.drawable.ic_clear);
        RecyclerView recyclerView = findViewById(R.id.recyclerView_app_filter_list);
        LinearLayoutManager manager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(manager);
        Drawable wrappedDrawable = DrawableCompat.wrap(Objects.requireNonNull(drawable));
        DrawableCompat.setTint(wrappedDrawable, Color.WHITE);
        toolbar.setNavigationIcon(drawable);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
        toolbar.setTitle(R.string.settings_app_filter);
        adapter = new RecyclerViewAdapter();
        recyclerView.setAdapter(adapter);
        new Thread(() -> adapter.updateList(getAppList())).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Daedalus.configurations.save();
    }

    @Override
    public void onResume() {
        super.onResume();
        adapter.notifyDataSetChanged();
    }

    private static class AppObject {
        private String appName;
        private String appPackageName;
        private Drawable appIcon;

        AppObject(String appName, String packageName, Drawable appIcon) {
            this.appName = appName;
            this.appPackageName = packageName;
            this.appIcon = appIcon;
        }
    }

    private ArrayList<AppObject> getAppList() {
        PackageManager packageManager = getBaseContext().getPackageManager();
        List<PackageInfo> packages = packageManager.getInstalledPackages(PackageManager.GET_PERMISSIONS);
        ArrayList<AppObject> appObjects = new ArrayList<>();
        for (PackageInfo pkg : packages) {
            appObjects.add(new AppObject(
                    pkg.applicationInfo.loadLabel(packageManager).toString(),
                    pkg.packageName,
                    pkg.applicationInfo.loadIcon(packageManager)
            ));
        }
        return appObjects;
    }

    private class RecyclerViewAdapter extends RecyclerView.Adapter<RecyclerViewHolder> {
        private ArrayList<AppObject> appList = new ArrayList<>();
        @SuppressLint("UseSparseArrays")
        private HashMap<Integer, Boolean> checkStatus = new HashMap<>();

        void updateList(ArrayList<AppObject> appObjects) {
            appList = appObjects;
            for (int i = 0; i < appObjects.size(); i++) {
                if (Daedalus.configurations.getAppObjects().contains(appObjects.get(i).appPackageName)) {
                    checkStatus.put(i, true);
                }
            }
            runOnUiThread(this::notifyDataSetChanged);
        }

        @NonNull
        @Override
        public RecyclerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.card_appview, parent, false);
            return new RecyclerViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerViewHolder holder, int position) {
            String packageName = appList.get(position).appPackageName;
            holder.appName.setText(appList.get(position).appName);
            holder.appIcon.setImageDrawable(appList.get(position).appIcon);
            holder.appPackageName = packageName;
            holder.appCheck.setOnCheckedChangeListener(null);
            if (Daedalus.configurations.getAppObjects().contains(packageName)) {
                holder.appCheck.setChecked(true);
            }
            holder.appCheck.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    checkStatus.put(position, true);
                } else {
                    checkStatus.remove(position);
                }
            });
            if (checkStatus != null && checkStatus.containsKey(position)) {
                holder.appCheck.setChecked(true);
            } else {
                holder.appCheck.setChecked(false);
            }
        }

        @Override
        public int getItemCount() {
            return appList.size();
        }
    }

    private static class RecyclerViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        private ImageView appIcon;
        private TextView appName;
        private CheckBox appCheck;
        private String appPackageName;

        RecyclerViewHolder(@NonNull View itemView) {
            super(itemView);
            appIcon = itemView.findViewById(R.id.app_icon);
            appName = itemView.findViewById(R.id.app_name);
            appCheck = itemView.findViewById(R.id.app_check);
            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            if (appCheck.isChecked()) {
                appCheck.setChecked(false);
                Daedalus.configurations.getAppObjects().remove(appPackageName);
            } else {
                appCheck.setChecked(true);
                Daedalus.configurations.getAppObjects().add(appPackageName);
            }
        }
    }
}
