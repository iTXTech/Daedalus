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

import org.itxtech.daedalus.Daedalus;
import org.itxtech.daedalus.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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
public class FilterAppProxyActivity extends AppCompatActivity {

    private RecyclerViewAdapter adapter;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        if (Daedalus.isDarkTheme()) {
            setTheme(R.style.AppTheme_Dark_NoActionBar);
        }
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_filter_app);
        Toolbar toolbar = findViewById(R.id.toolbar_filter);
        Drawable drawable = ContextCompat.getDrawable(this, R.drawable.ic_clear);
        RecyclerView recyclerView = findViewById(R.id.recyclerView_app_filter_list);
        LinearLayoutManager manager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(manager);
        Drawable wrappedDrawable = DrawableCompat.wrap(Objects.requireNonNull(drawable));
        DrawableCompat.setTint(wrappedDrawable, Color.WHITE);
        toolbar.setNavigationIcon(drawable);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
        adapter = new RecyclerViewAdapter(getAppList());
        recyclerView.setAdapter(adapter);
    }

    @Override
    public void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        Toolbar toolbar = findViewById(R.id.toolbar_filter);
        toolbar.setTitle(R.string.settings_app_filter);
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

    private class AppObject {
        private String app_name;
        private String app_package_name;
        private Drawable app_icon;

        AppObject(String appName, String packageName, Drawable appIcon) {
            this.app_name = appName;
            this.app_package_name = packageName;
            this.app_icon = appIcon;
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
        private ArrayList<AppObject> appList;
        @SuppressLint("UseSparseArrays")
        private Map<Integer, Boolean> check_status = new HashMap<>();

        RecyclerViewAdapter(ArrayList<AppObject> appObjects) {
            appList = appObjects;

            for (int i = 0; i < appObjects.size(); i++) {
                if (Daedalus.configurations.getFilterAppObjects().contains(appObjects.get(i).app_package_name)) {
                    check_status.put(i, true);
                }
            }
        }


        @NonNull
        @Override
        public RecyclerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.card_appview, parent, false);
            return new RecyclerViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerViewHolder holder, int position) {
            String package_name = appList.get(position).app_package_name;
            holder.app_name.setText(appList.get(position).app_name);
            holder.app_icon.setImageDrawable(appList.get(position).app_icon);
            holder.app_package_name = package_name;
            holder.app_check.setOnCheckedChangeListener(null);
            if (Daedalus.configurations.getFilterAppObjects().contains(package_name)) {
                holder.app_check.setChecked(true);
                //check_status.put(position, true);
            }
            holder.app_check.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    check_status.put(position, true);
                } else {
                    check_status.remove(position);
                }
            });
            if (check_status != null && check_status.containsKey(position)) {
                holder.app_check.setChecked(true);
            } else {
                holder.app_check.setChecked(false);
            }
        }

        @Override
        public int getItemCount() {
            return appList.size();
        }
    }

    private class RecyclerViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        private ImageView app_icon;
        private TextView app_name;
        private CheckBox app_check;
        private String app_package_name;

        RecyclerViewHolder(@NonNull View itemView) {
            super(itemView);
            app_icon = itemView.findViewById(R.id.app_icon);
            app_name = itemView.findViewById(R.id.app_name);
            app_check = itemView.findViewById(R.id.app_check);
            itemView.setOnClickListener(this);
        }


        @Override
        public void onClick(View v) {
            if (app_check.isChecked()) {
                app_check.setChecked(false);
                Daedalus.configurations.getFilterAppObjects().remove(app_package_name);
            } else {
                app_check.setChecked(true);
                Daedalus.configurations.getFilterAppObjects().add(app_package_name);
            }
        }
    }
}
