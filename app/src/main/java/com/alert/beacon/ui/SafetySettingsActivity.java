package com.alert.beacon.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.widget.Toolbar;

import com.alert.beacon.R;
import com.alert.beacon.data.TriggerManager;

public class SafetySettingsActivity extends BaseActivity {

    private TriggerManager triggerManager;
    private TextView tvCurrentRingtone;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_safety_settings);

        triggerManager = new TriggerManager(this);

        setupToolbar();
        initViews();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbarSettings);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("");
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void initViews() {
        tvCurrentRingtone = findViewById(R.id.tvCurrentRingtone);
        findViewById(R.id.btnSelectRingtone).setOnClickListener(v -> {
            Intent intent = new Intent(this, RingtoneSelectionActivity.class);
            intent.putExtra(RingtoneSelectionActivity.EXTRA_SELECTION_TYPE, RingtoneSelectionActivity.TYPE_SAFETY);
            startActivity(intent);
        });

        findViewById(R.id.btnViewHistory).setOnClickListener(v -> {
            startActivity(new Intent(this, SafetyHistoryActivity.class));
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateRingtoneName();
    }

    private void updateRingtoneName() {
        String ringtone = triggerManager.getSafetySoundName();
        if (ringtone == null || ringtone.isEmpty()) {
            ringtone = "Default";
        }
        tvCurrentRingtone.setText(ringtone);
    }
}
