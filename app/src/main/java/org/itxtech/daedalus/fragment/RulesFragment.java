package org.itxtech.daedalus.fragment;

import android.app.Fragment;
import android.os.Bundle;
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
import org.itxtech.daedalus.util.Rule;

/**
 * @author PeratX
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
        }

        @Override
        public int getItemCount() {
            return Daedalus.configurations.getCustomDnsServers().size();
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
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
            textViewName = (TextView) view.findViewById(R.id.textView_rule_name);
            textViewAddress = (TextView) view.findViewById(R.id.textView_rule_detail);
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
            /*if (!DnsServerHelper.isInUsing(Daedalus.configurations.getCustomDnsServers().get(index))) {
                Daedalus.getInstance().startActivity(new Intent(Daedalus.getInstance(), DnsServerConfigActivity.class)
                        .putExtra(DnsServerConfigActivity.LAUNCH_ACTION_CUSTOM_DNS_SERVER_ID, index));
            }*/
        }
    }
}
