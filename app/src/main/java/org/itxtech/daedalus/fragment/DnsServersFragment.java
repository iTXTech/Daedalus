package org.itxtech.daedalus.fragment;

import android.app.Fragment;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import org.itxtech.daedalus.Daedalus;
import org.itxtech.daedalus.R;
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
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_dns_servers, container, false);
        RecyclerView recyclerView = (RecyclerView) view.findViewById(R.id.recyclerView_dns_servers);
        LinearLayoutManager manager = new LinearLayoutManager(getActivity());
        recyclerView.setLayoutManager(manager);
        DnsServerAdapter adapter = new DnsServerAdapter();
        recyclerView.setAdapter(adapter);

        FloatingActionButton fab = (FloatingActionButton) view.findViewById(R.id.fab_add_server);
        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        Daedalus.configurations.save();
    }

    private class DnsServerAdapter extends RecyclerView.Adapter<ViewHolder> {
        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            CustomDnsServer server = Daedalus.configurations.getCustomDnsServers().get(position);
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

    private static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView textViewName;
        private final TextView textViewAddress;

        ViewHolder(View view) {
            super(view);
            textViewName = (TextView) view.findViewById(R.id.textView_custom_dns_name);
            textViewAddress = (TextView) view.findViewById(R.id.textView_custom_dns_address);
        }
    }
}
