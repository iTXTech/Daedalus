package org.itxtech.daedalus.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import org.itxtech.daedalus.Daedalus;
import org.itxtech.daedalus.R;
import org.itxtech.daedalus.activity.ConfigActivity;
import org.itxtech.daedalus.server.CustomDnsServer;
import org.itxtech.daedalus.server.DnsServerHelper;

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
public class DnsServersFragment extends ToolbarFragment {
    private DNSServerAdapter adapter;
    private CustomDnsServer server = null;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_dns_servers, container, false);
        RecyclerView recyclerView = view.findViewById(R.id.recyclerView_dns_servers);
        LinearLayoutManager manager = new LinearLayoutManager(getActivity());
        recyclerView.setLayoutManager(manager);
        adapter = new DNSServerAdapter();
        recyclerView.setAdapter(adapter);
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.Callback() {
            @Override
            public int getMovementFlags(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
                if (viewHolder instanceof ViewHolder) {
                    int index = ((ViewHolder) viewHolder).getIndex();
                    if (index < Daedalus.configurations.getCustomDNSServers().size() &&
                            DnsServerHelper.isInUsing(Daedalus.configurations.getCustomDNSServers().get(index))) {
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
                server = Daedalus.configurations.getCustomDNSServers().get(position);
                Daedalus.configurations.getCustomDNSServers().remove(position);
                Snackbar.make(view, R.string.action_removed, Snackbar.LENGTH_LONG)
                        .setAction(R.string.action_undo, new SnackbarClickListener(position)).show();
                adapter.notifyItemRemoved(position);
            }
        });
        itemTouchHelper.attachToRecyclerView(recyclerView);

        FloatingActionButton fab = view.findViewById(R.id.fab_add_server);
        fab.setOnClickListener(v -> startActivity(new Intent(getActivity(), ConfigActivity.class)
                .putExtra(ConfigActivity.LAUNCH_ACTION_ID, ConfigActivity.ID_NONE)
                .putExtra(ConfigActivity.LAUNCH_ACTION_FRAGMENT, ConfigActivity.LAUNCH_FRAGMENT_DNS_SERVER)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
        return view;
    }

    @Override
    public void checkStatus() {
        menu.findItem(R.id.nav_dns_server).setChecked(true);
        toolbar.setTitle(R.string.action_dns_servers);
    }

    private class SnackbarClickListener implements View.OnClickListener {
        private final int position;

        private SnackbarClickListener(int position) {
            this.position = position;
        }

        @Override
        public void onClick(View v) {
            Daedalus.configurations.getCustomDNSServers().add(position, server);
            adapter.notifyItemInserted(position);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        Daedalus.configurations.save();
        adapter = null;
        server = null;
    }

    @Override
    public void onResume() {
        super.onResume();

        adapter.notifyDataSetChanged();
    }

    private class DNSServerAdapter extends RecyclerView.Adapter<ViewHolder> {
        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            CustomDnsServer server = Daedalus.configurations.getCustomDNSServers().get(position);
            holder.setIndex(position);
            holder.textViewName.setText(server.getName());
            holder.textViewAddress.setText(server.getRealName());
        }

        @Override
        public int getItemCount() {
            return Daedalus.configurations.getCustomDNSServers().size();
        }

        @Override
        @NonNull
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.card_server, parent, false);
            return new ViewHolder(view);
        }
    }

    private static class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        private final TextView textViewName;
        private final TextView textViewAddress;
        private int index;

        ViewHolder(View view) {
            super(view);
            textViewName = view.findViewById(R.id.textView_custom_dns_name);
            textViewAddress = view.findViewById(R.id.textView_custom_dns_address);
            view.setOnClickListener(this);
        }

        void setIndex(int index) {
            this.index = index;
        }

        int getIndex() {
            return index;
        }

        @Override
        public void onClick(View v) {
            if (!DnsServerHelper.isInUsing(Daedalus.configurations.getCustomDNSServers().get(index))) {
                Daedalus.getInstance().startActivity(new Intent(Daedalus.getInstance(), ConfigActivity.class)
                        .putExtra(ConfigActivity.LAUNCH_ACTION_ID, index)
                        .putExtra(ConfigActivity.LAUNCH_ACTION_FRAGMENT, ConfigActivity.LAUNCH_FRAGMENT_DNS_SERVER)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
        }
    }
}
