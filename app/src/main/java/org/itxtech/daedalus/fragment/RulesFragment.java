package org.itxtech.daedalus.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import org.itxtech.daedalus.Daedalus;
import org.itxtech.daedalus.R;
import org.itxtech.daedalus.activity.ConfigActivity;
import org.itxtech.daedalus.util.Rule;

import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;

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
public class RulesFragment extends ToolbarFragment implements Toolbar.OnMenuItemClickListener {
    private RuleAdapter adapter;
    private Rule rule = null;
    private int currentType;

    private ArrayList<Rule> getRules() {
        if (currentType == Rule.TYPE_HOSTS) {
            return Daedalus.configurations.getHostsRules();
        } else {
            return Daedalus.configurations.getDnsmasqRules();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_rules, container, false);
        currentType = Daedalus.configurations.getUsingRuleType();

        RecyclerView recyclerView = view.findViewById(R.id.recyclerView_rules);
        LinearLayoutManager manager = new LinearLayoutManager(getActivity());
        recyclerView.setLayoutManager(manager);
        adapter = new RuleAdapter();
        recyclerView.setAdapter(adapter);
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.Callback() {
            @Override
            public int getMovementFlags(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
                return makeMovementFlags(0, ItemTouchHelper.START | ItemTouchHelper.END);
            }

            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                rule = getRules().get(position);
                getRules().remove(position);
                Snackbar.make(getView(), R.string.action_removed, Snackbar.LENGTH_LONG)
                        .setAction(R.string.action_undo, new SnackbarClickListener(position)).show();
                adapter.notifyItemRemoved(position);
            }
        });
        itemTouchHelper.attachToRecyclerView(recyclerView);

        FloatingActionButton fab = view.findViewById(R.id.fab_add_rule);
        fab.setOnClickListener(v -> startActivity(new Intent(getActivity(), ConfigActivity.class)
                .putExtra(ConfigActivity.LAUNCH_ACTION_ID, ConfigActivity.ID_NONE)
                .putExtra(ConfigActivity.LAUNCH_ACTION_FRAGMENT, ConfigActivity.LAUNCH_FRAGMENT_RULE)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
        return view;
    }

    @Override
    public void checkStatus() {
        menu.findItem(R.id.nav_rules).setChecked(true);
        toolbar.inflateMenu(R.menu.rules);
        toolbar.setTitle(R.string.action_rules);
        toolbar.setOnMenuItemClickListener(this);
        toolbar.getMenu().findItem(R.id.action_change_type).setTitle(Rule.getTypeById(currentType));
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_change_type) {
            if (currentType == Rule.TYPE_HOSTS) {
                currentType = Rule.TYPE_DNAMASQ;
            } else if (currentType == Rule.TYPE_DNAMASQ) {
                currentType = Rule.TYPE_HOSTS;
            }
            toolbar.getMenu().findItem(R.id.action_change_type).setTitle(Rule.getTypeById(currentType));
            adapter.notifyDataSetChanged();
        }

        if (id == R.id.action_reload) {
            Daedalus.setRulesChanged();
        }
        return true;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        Daedalus.configurations.save();
        adapter = null;
        rule = null;
    }

    @Override
    public void onResume() {
        super.onResume();

        adapter.notifyDataSetChanged();
    }

    private class SnackbarClickListener implements View.OnClickListener {
        private final int position;

        private SnackbarClickListener(int position) {
            this.position = position;
        }

        @Override
        public void onClick(View v) {
            if (rule.getType() == Rule.TYPE_HOSTS) {
                Daedalus.configurations.getHostsRules().add(position, rule);
            } else if (rule.getType() == Rule.TYPE_DNAMASQ) {
                Daedalus.configurations.getDnsmasqRules().add(position, rule);
            }
            if (currentType == rule.getType()) {
                adapter.notifyItemInserted(position);
            }
        }
    }

    private class RuleAdapter extends RecyclerView.Adapter<ViewHolder> {
        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Rule rule = getRules().get(position);
            holder.setId(rule.getId());
            holder.textViewName.setText(rule.getName());
            holder.textViewAddress.setText(rule.getFileName());

            File file = new File(Daedalus.rulePath + rule.getFileName());
            StringBuilder builder = new StringBuilder();
            if (file.exists()) {
                builder.append(new DecimalFormat("0.00").format(((float) file.length() / 1024)));
            } else {
                builder.append("0");
            }
            holder.textViewSize.setText(builder.append(" KB").toString());
        }

        @Override
        public int getItemCount() {
            return getRules().size();
        }

        @Override
        @NonNull
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.card_rule, parent, false);
            return new ViewHolder(view);
        }
    }

    private class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {
        private final TextView textViewName;
        private final TextView textViewAddress;
        private final TextView textViewSize;
        private final View view;
        private String id;

        ViewHolder(View view) {
            super(view);
            this.view = view;
            textViewName = view.findViewById(R.id.textView_rule_name);
            textViewAddress = view.findViewById(R.id.textView_rule_detail);
            textViewSize = view.findViewById(R.id.textView_rule_size);
            view.setOnClickListener(this);
            view.setOnLongClickListener(this);
            if (Daedalus.isDarkTheme()) {
                view.findViewById(R.id.cardView_indicator).setBackgroundResource(R.drawable.bg_selectable_dark);
            } else {
                view.findViewById(R.id.cardView_indicator).setBackgroundResource(R.drawable.bg_selectable);
            }
        }

        void setId(String id) {
            this.id = id;
            Rule rule = Rule.getRuleById(id);
            if (rule != null) {
                view.setSelected(rule.isUsing());
            }
        }

        String getId() {
            return id;
        }

        @Override
        public void onClick(View v) {
                Rule rule = Rule.getRuleById(id);
                if (rule != null) {
                    rule.setUsing(!v.isSelected());
                    v.setSelected(!v.isSelected());
                    Daedalus.setRulesChanged();
                }
        }

        @Override
        public boolean onLongClick(View v) {
            Rule rule = Rule.getRuleById(id);
            if (rule != null) {
                Daedalus.getInstance().startActivity(new Intent(Daedalus.getInstance(), ConfigActivity.class)
                        .putExtra(ConfigActivity.LAUNCH_ACTION_ID, Integer.parseInt(id))
                        .putExtra(ConfigActivity.LAUNCH_ACTION_FRAGMENT, ConfigActivity.LAUNCH_FRAGMENT_RULE)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
            return true;
        }
    }
}
