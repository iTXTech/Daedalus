package org.itxtech.daedalus.fragment;

import android.app.Fragment;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import org.itxtech.daedalus.Daedalus;
import org.itxtech.daedalus.R;
import org.itxtech.daedalus.activity.ConfigActivity;
import org.itxtech.daedalus.util.Rule;

import java.io.File;
import java.text.DecimalFormat;

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
public class RulesFragment extends Fragment {

    private View view = null;
    private RuleAdapter adapter;
    private Rule rule = null;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.fragment_rules, container, false);

        RecyclerView recyclerView = (RecyclerView) view.findViewById(R.id.recyclerView_rules);
        LinearLayoutManager manager = new LinearLayoutManager(getActivity());
        recyclerView.setLayoutManager(manager);
        adapter = new RuleAdapter();
        recyclerView.setAdapter(adapter);
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.Callback() {
            @Override
            public int getMovementFlags(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
                if (viewHolder instanceof RulesFragment.ViewHolder) {
                    if (Daedalus.configurations.getRules().get(((ViewHolder) viewHolder).getIndex()).isUsing()) {
                        return 0;
                    }
                }
                return makeMovementFlags(0, ItemTouchHelper.START | ItemTouchHelper.END);
            }

            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                rule = Daedalus.configurations.getRules().get(position);
                Daedalus.configurations.getRules().remove(position);
                Snackbar.make(view, R.string.action_removed, Snackbar.LENGTH_LONG)
                        .setAction(R.string.action_undo, new SnackbarClickListener(position)).show();
                adapter.notifyItemRemoved(position);
            }
        });
        itemTouchHelper.attachToRecyclerView(recyclerView);

        FloatingActionButton fab = (FloatingActionButton) view.findViewById(R.id.fab_add_rule);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(getActivity(), ConfigActivity.class)
                        .putExtra(ConfigActivity.LAUNCH_ACTION_ID, ConfigActivity.ID_NONE)
                        .putExtra(ConfigActivity.LAUNCH_ACTION_FRAGMENT, ConfigActivity.LAUNCH_FRAGMENT_RULE));
            }
        });
        return view;
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
            Daedalus.configurations.getRules().add(position, rule);
            adapter.notifyItemInserted(position);
        }
    }

    private class RuleAdapter extends RecyclerView.Adapter<ViewHolder> {
        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            Rule rule = Daedalus.configurations.getRules().get(position);
            holder.setIndex(position);
            holder.textViewName.setText(rule.getName());
            holder.textViewAddress.setText(rule.getFileName());

            File file = new File(Daedalus.rulesPath + rule.getFileName());
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
            return Daedalus.configurations.getRules().size();
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.card_rule, parent, false);
            return new ViewHolder(view);
        }
    }

    private static class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {
        private final TextView textViewName;
        private final TextView textViewAddress;
        private final TextView textViewSize;
        private final View view;
        private int index;

        ViewHolder(View view) {
            super(view);
            this.view = view;
            textViewName = (TextView) view.findViewById(R.id.textView_rule_name);
            textViewAddress = (TextView) view.findViewById(R.id.textView_rule_detail);
            textViewSize = (TextView) view.findViewById(R.id.textView_rule_size);
            view.setOnClickListener(this);
            view.setOnLongClickListener(this);
            view.setBackgroundResource(R.drawable.background_selectable);
        }

        void setIndex(int index) {
            this.index = index;
            view.setSelected(Daedalus.configurations.getRules().get(index).isUsing());
        }

        int getIndex() {
            return index;
        }

        @Override
        public void onClick(View v) {
            Daedalus.configurations.getRules().get(index).setUsing(!v.isSelected());
            v.setSelected(!v.isSelected());
        }

        @Override
        public boolean onLongClick(View v) {
            if (!Daedalus.configurations.getRules().get(index).isUsing()) {
                Daedalus.getInstance().startActivity(new Intent(Daedalus.getInstance(), ConfigActivity.class)
                        .putExtra(ConfigActivity.LAUNCH_ACTION_ID, index)
                        .putExtra(ConfigActivity.LAUNCH_ACTION_FRAGMENT, ConfigActivity.LAUNCH_FRAGMENT_RULE));
            }
            return true;
        }
    }
}
