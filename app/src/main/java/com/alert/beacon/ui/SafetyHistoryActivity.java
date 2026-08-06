package com.alert.beacon.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alert.beacon.R;
import com.alert.beacon.data.SafetyHistoryEntry;
import com.alert.beacon.data.TriggerManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class SafetyHistoryActivity extends BaseActivity {

    private TriggerManager triggerManager;
    private RecyclerView rvHistory;
    private View tvEmpty;
    private HistoryAdapter adapter;
    private List<SafetyHistoryEntry> historyList;

    private View layoutSelection;
    private CheckBox cbSelectAll;
    private TextView tvSelectionCount;
    private Toolbar toolbar;
    
    private boolean isSelectionMode = false;
    private final Set<Integer> selectedPositions = new HashSet<>();

    private final OnBackPressedCallback backCallback = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            if (isSelectionMode) {
                exitSelectionMode();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_safety_history);

        getOnBackPressedDispatcher().addCallback(this, backCallback);

        triggerManager = new TriggerManager(this);
        initViews();
        setupToolbar();
        setupSelectionToolbar();

        loadHistory();
    }

    private void initViews() {
        rvHistory = findViewById(R.id.rvSafetyHistory);
        tvEmpty = findViewById(R.id.tvEmptyHistory);
        layoutSelection = findViewById(R.id.layoutSelection);
        cbSelectAll = findViewById(R.id.cbSelectAll);
        tvSelectionCount = findViewById(R.id.tvSelectionCount);
        
        rvHistory.setLayoutManager(new LinearLayoutManager(this));
    }

    private void setupToolbar() {
        toolbar = findViewById(R.id.toolbarHistory);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("");
        }
        toolbar.setNavigationOnClickListener(v -> {
            if (isSelectionMode) {
                exitSelectionMode();
            } else {
                finish();
            }
        });
    }

    private void setupSelectionToolbar() {
        cbSelectAll.setOnClickListener(v -> {
            if (cbSelectAll.isChecked()) {
                for (int i = 0; i < historyList.size(); i++) {
                    selectedPositions.add(i);
                }
            } else {
                selectedPositions.clear();
            }
            updateSelectionUI();
            adapter.notifyDataSetChanged();
        });

        findViewById(R.id.btnDeleteSelected).setOnClickListener(v -> {
            if (selectedPositions.isEmpty()) {
                Toast.makeText(this, "No items selected", Toast.LENGTH_SHORT).show();
                return;
            }

            showGlassyConfirmDialog(
                    getString(R.string.delete_confirm_title),
                    "Are you sure you want to delete the selected history entries?",
                    getString(R.string.delete),
                    this::deleteSelectedItems
            );
        });
    }

    private void loadHistory() {
        historyList = triggerManager.getSafetyHistory();
        if (historyList.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            rvHistory.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            rvHistory.setVisibility(View.VISIBLE);
            adapter = new HistoryAdapter(historyList);
            rvHistory.setAdapter(adapter);
        }
    }

    private void enterSelectionMode(int position) {
        isSelectionMode = true;
        backCallback.setEnabled(true);
        selectedPositions.clear();
        selectedPositions.add(position);
        
        toolbar.setVisibility(View.GONE);
        layoutSelection.setVisibility(View.VISIBLE);
        
        updateSelectionUI();
        adapter.notifyDataSetChanged();
    }

    private void exitSelectionMode() {
        isSelectionMode = false;
        backCallback.setEnabled(false);
        selectedPositions.clear();
        
        toolbar.setVisibility(View.VISIBLE);
        layoutSelection.setVisibility(View.GONE);
        cbSelectAll.setChecked(false);
        
        adapter.notifyDataSetChanged();
    }

    private void updateSelectionUI() {
        tvSelectionCount.setText(String.format(Locale.getDefault(), "%d selected", selectedPositions.size()));
        cbSelectAll.setChecked(selectedPositions.size() == historyList.size() && !historyList.isEmpty());
    }

    private void deleteSelectedItems() {
        if (selectedPositions.isEmpty()) {
            Toast.makeText(this, "No items selected", Toast.LENGTH_SHORT).show();
            return;
        }

        List<SafetyHistoryEntry> newList = new ArrayList<>();
        for (int i = 0; i < historyList.size(); i++) {
            if (!selectedPositions.contains(i)) {
                newList.add(historyList.get(i));
            }
        }

        triggerManager.saveSafetyHistory(newList);
        historyList = newList;
        
        exitSelectionMode();
        if (historyList.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            rvHistory.setVisibility(View.GONE);
        } else {
            adapter.updateList(historyList);
        }
        
        Toast.makeText(this, "Deleted successfully", Toast.LENGTH_SHORT).show();
    }

    private class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {
        private List<SafetyHistoryEntry> list;
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault());

        HistoryAdapter(List<SafetyHistoryEntry> list) {
            this.list = list;
        }

        void updateList(List<SafetyHistoryEntry> newList) {
            this.list = newList;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_safety_history, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            SafetyHistoryEntry entry = list.get(position);
            
            if (entry.isWasTriggered()) {
                holder.tvStatus.setText("Emergency Triggered");
                holder.tvStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                holder.statusIndicator.setBackgroundColor(getResources().getColor(android.R.color.holo_red_dark));
            } else {
                holder.tvStatus.setText("Timer Stopped Safely");
                holder.tvStatus.setTextColor(getResources().getColor(R.color.success_green));
                holder.statusIndicator.setBackgroundColor(getResources().getColor(R.color.success_green));
            }

            long seconds = entry.getDurationMs() / 1000;
            long h = seconds / 3600;
            long m = (seconds % 3600) / 60;
            long s = seconds % 60;
            holder.tvDetails.setText(String.format(Locale.getDefault(), "Set Duration: %02d:%02d:%02d", h, m, s));
            
            holder.tvTime.setText(dateFormat.format(new Date(entry.getTimestamp())));

            // Selection Logic
            holder.checkBox.setVisibility(isSelectionMode ? View.VISIBLE : View.GONE);
            holder.checkBox.setChecked(selectedPositions.contains(position));

            holder.itemView.setOnLongClickListener(v -> {
                if (!isSelectionMode) {
                    enterSelectionMode(position);
                    return true;
                }
                return false;
            });

            holder.itemView.setOnClickListener(v -> {
                if (isSelectionMode) {
                    toggleSelection(position);
                }
            });

            holder.checkBox.setOnClickListener(v -> toggleSelection(position));
        }

        private void toggleSelection(int position) {
            if (selectedPositions.contains(position)) {
                selectedPositions.remove(position);
            } else {
                selectedPositions.add(position);
            }
            
            if (selectedPositions.isEmpty()) {
                exitSelectionMode();
            } else {
                updateSelectionUI();
                notifyItemChanged(position);
            }
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvStatus, tvDetails, tvTime;
            View statusIndicator;
            CheckBox checkBox;

            ViewHolder(View v) {
                super(v);
                tvStatus = v.findViewById(R.id.tvHistoryStatus);
                tvDetails = v.findViewById(R.id.tvHistoryDetails);
                tvTime = v.findViewById(R.id.tvHistoryTime);
                statusIndicator = v.findViewById(R.id.statusIndicator);
                checkBox = v.findViewById(R.id.cbHistorySelect);
            }
        }
    }
}
