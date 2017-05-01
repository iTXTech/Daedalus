package org.itxtech.daedalus.fragment;

import android.app.Fragment;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import org.itxtech.daedalus.Daedalus;
import org.itxtech.daedalus.R;
import org.itxtech.daedalus.activity.DnsServerConfigActivity;
import org.itxtech.daedalus.util.CustomDnsServer;

/**
 * Daedalus Project
 *
 * @author iTXTech
 * @link https://itxtech.org
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 */
public class DnsServersFragment extends Fragment {
    private DnsServersFragment.DnsServerAdapter adapter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_dns_servers, container, false);
        RecyclerView recyclerView = (RecyclerView) view.findViewById(R.id.recyclerView_dns_servers);
        LinearLayoutManager manager = new LinearLayoutManager(getActivity());
        recyclerView.setLayoutManager(manager);
        adapter = new DnsServerAdapter();
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
                adapter.notifyItemRemoved(position);
                Daedalus.configurations.getCustomDnsServers().remove(position);
            }
        });
        itemTouchHelper.attachToRecyclerView(recyclerView);

        FloatingActionButton fab = (FloatingActionButton) view.findViewById(R.id.fab_add_server);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(getActivity(), DnsServerConfigActivity.class).putExtra(DnsServerConfigActivity.LAUNCH_ACTION_CUSTOM_DNS_SERVER_ID,
                        DnsServerConfigActivity.CUSTOM_DNS_SERVER_ID_NONE));
            }
        });
        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        Daedalus.configurations.save();
        adapter = null;
    }

    @Override
    public void onResume() {
        super.onResume();

        adapter.notifyDataSetChanged();
    }

    private class DnsServerAdapter extends RecyclerView.Adapter<ViewHolder> {
        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            CustomDnsServer server = Daedalus.configurations.getCustomDnsServers().get(position);
            holder.setIndex(position);
            holder.textViewName.setText(server.getName());
            holder.textViewAddress.setText(server.getAddress() + ":" + server.getPort());
        }

        @Override
        public int getItemCount() {
            return Daedalus.configurations.getCustomDnsServers().size();
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_dns_servers, parent, false);
            return new ViewHolder(view);
        }
    }

    private static class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        private final TextView textViewName;
        private final TextView textViewAddress;
        private int index;

        ViewHolder(View view) {
            super(view);
            textViewName = (TextView) view.findViewById(R.id.textView_custom_dns_name);
            textViewAddress = (TextView) view.findViewById(R.id.textView_custom_dns_address);
            view.setOnClickListener(this);
        }

        void setIndex(int index) {
            this.index = index;
        }

        @Override
        public void onClick(View v) {
            Daedalus.getInstance().startActivity(new Intent(Daedalus.getInstance(), DnsServerConfigActivity.class)
                    .putExtra(DnsServerConfigActivity.LAUNCH_ACTION_CUSTOM_DNS_SERVER_ID, index));
        }
    }
}
