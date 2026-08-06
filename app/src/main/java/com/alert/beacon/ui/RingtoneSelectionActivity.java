package com.alert.beacon.ui;

import android.database.Cursor;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alert.beacon.R;
import com.alert.beacon.data.TriggerManager;

import java.util.ArrayList;
import java.util.List;

public class RingtoneSelectionActivity extends BaseActivity {

    public static final String EXTRA_SELECTION_TYPE = "selection_type";
    public static final int TYPE_ALARM = 0;
    public static final int TYPE_MESSAGE = 1;
    public static final int TYPE_SAFETY = 2;

    private RecyclerView rvRingtones;
    private RingtoneAdapter adapter;
    private TriggerManager triggerManager;
    private final List<RingtoneItem> ringtoneList = new ArrayList<>();
    private Ringtone currentPlayingRingtone;
    private int selectionType = TYPE_ALARM;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ringtone_selection);

        selectionType = getIntent().getIntExtra(EXTRA_SELECTION_TYPE, TYPE_ALARM);
        triggerManager = new TriggerManager(this);
        setupToolbar();
        initViews();
        loadRingtones();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbarRingtone);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("");
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        TextView tvTitle = findViewById(R.id.tvToolbarRingtoneTitle);
        if (tvTitle != null) {
            if (selectionType == TYPE_MESSAGE) {
                tvTitle.setText(R.string.msg_notification_title);
            } else if (selectionType == TYPE_SAFETY) {
                tvTitle.setText(R.string.safety_notification_title);
            } else {
                tvTitle.setText(R.string.alarm_ringtone_title);
            }
        }
    }

    private void initViews() {
        rvRingtones = findViewById(R.id.rvRingtones);
        rvRingtones.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RingtoneAdapter();
        rvRingtones.setAdapter(adapter);
    }

    private void loadRingtones() {
        ringtoneList.clear();
        
        // Add Default option
        ringtoneList.add(new RingtoneItem("Default", null));

        RingtoneManager manager = new RingtoneManager(this);
        if (selectionType == TYPE_ALARM || selectionType == TYPE_SAFETY) {
            manager.setType(RingtoneManager.TYPE_ALARM);
        } else {
            manager.setType(RingtoneManager.TYPE_NOTIFICATION);
        }
        
        Cursor cursor = manager.getCursor();
        
        String savedUri;
        if (selectionType == TYPE_MESSAGE) {
            savedUri = triggerManager.getNotificationSoundUri();
        } else if (selectionType == TYPE_SAFETY) {
            savedUri = triggerManager.getSafetySoundUri();
        } else {
            savedUri = triggerManager.getAlarmSoundUri();
        }

        while (cursor.moveToNext()) {
            String title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX);
            String uriString = cursor.getString(RingtoneManager.URI_COLUMN_INDEX) + "/" + cursor.getString(RingtoneManager.ID_COLUMN_INDEX);
            ringtoneList.add(new RingtoneItem(title, uriString));
        }

        adapter.setSelectedUri(savedUri);
        adapter.notifyDataSetChanged();
    }

    private void playRingtone(String uriString) {
        if (currentPlayingRingtone != null && currentPlayingRingtone.isPlaying()) {
            currentPlayingRingtone.stop();
        }

        Uri uri;
        if (uriString == null) {
            int type = (selectionType == TYPE_ALARM || selectionType == TYPE_SAFETY) ? RingtoneManager.TYPE_ALARM : RingtoneManager.TYPE_NOTIFICATION;
            uri = RingtoneManager.getDefaultUri(type);
        } else {
            uri = Uri.parse(uriString);
        }

        currentPlayingRingtone = RingtoneManager.getRingtone(this, uri);
        if (currentPlayingRingtone != null) {
            currentPlayingRingtone.play();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (currentPlayingRingtone != null && currentPlayingRingtone.isPlaying()) {
            currentPlayingRingtone.stop();
        }
    }

    private class RingtoneItem {
        String name;
        String uri;

        RingtoneItem(String name, String uri) {
            this.name = name;
            this.uri = uri;
        }
    }

    private class RingtoneAdapter extends RecyclerView.Adapter<RingtoneAdapter.ViewHolder> {
        private String selectedUri;

        void setSelectedUri(String uri) {
            this.selectedUri = uri;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_ringtone, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            RingtoneItem item = ringtoneList.get(position);
            holder.tvName.setText(item.name);
            
            boolean isSelected = (selectedUri == null && item.uri == null) || (selectedUri != null && selectedUri.equals(item.uri));
            holder.rbSelected.setChecked(isSelected);

            holder.itemView.setOnClickListener(v -> {
                selectedUri = item.uri;
                if (selectionType == TYPE_MESSAGE) {
                    triggerManager.saveNotificationSound(item.name, item.uri);
                } else if (selectionType == TYPE_SAFETY) {
                    triggerManager.saveSafetySound(item.name, item.uri);
                } else {
                    triggerManager.saveAlarmSound(item.name, item.uri);
                }
                notifyDataSetChanged();
                playRingtone(item.uri);
            });
        }

        @Override
        public int getItemCount() {
            return ringtoneList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName;
            RadioButton rbSelected;

            ViewHolder(View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tvRingtoneName);
                rbSelected = itemView.findViewById(R.id.rbSelected);
            }
        }
    }
}
