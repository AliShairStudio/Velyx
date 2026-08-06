package com.alert.beacon.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.core.os.LocaleListCompat;

import com.alert.beacon.R;
import com.alert.beacon.data.TriggerManager;

public class SettingsActivity extends BaseActivity {

    private SwitchCompat swShakeTrigger, swPowerTrigger, swDarkMode;
    private TextView tvCurrentLanguage;
    private TriggerManager triggerManager;
    private boolean settingsChanged = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        triggerManager = new TriggerManager(this);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        swShakeTrigger = findViewById(R.id.swShakeTrigger);
        swPowerTrigger = findViewById(R.id.swPowerTrigger);
        swDarkMode = findViewById(R.id.swDarkMode);
        tvCurrentLanguage = findViewById(R.id.tvCurrentLanguage);

        // Setup Shake Trigger Toggle
        swShakeTrigger.setChecked(triggerManager.isShakeTriggerEnabled());
        swShakeTrigger.setOnCheckedChangeListener((buttonView, isChecked) -> {
            triggerManager.setShakeTriggerEnabled(isChecked);
            settingsChanged = true;
        });

        // Setup Power Trigger Toggle
        swPowerTrigger.setChecked(triggerManager.isPowerTriggerEnabled());
        swPowerTrigger.setOnCheckedChangeListener((buttonView, isChecked) -> {
            triggerManager.setPowerTriggerEnabled(isChecked);
            settingsChanged = true;
        });

        // Setup Dark Mode Toggle
        int savedMode = triggerManager.getThemeMode();
        if (savedMode == 2) {
            swDarkMode.setChecked(true);
        } else if (savedMode == 1) {
            swDarkMode.setChecked(false);
        } else {
            int systemMode = AppCompatDelegate.getDefaultNightMode();
            swDarkMode.setChecked(systemMode == AppCompatDelegate.MODE_NIGHT_YES);
        }

        swDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (triggerManager.getThemeMode() != 2) {
                    triggerManager.saveThemeMode(2);
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                }
            } else {
                if (triggerManager.getThemeMode() != 1) {
                    triggerManager.saveThemeMode(1);
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                }
            }
        });

        View optionMessageSound = findViewById(R.id.optionMessageSound);
        View optionSafetySound = findViewById(R.id.optionSafetySound);

        if (optionMessageSound != null) {
            optionMessageSound.setOnClickListener(v -> {
                Intent intent = new Intent(this, RingtoneSelectionActivity.class);
                intent.putExtra(RingtoneSelectionActivity.EXTRA_SELECTION_TYPE, RingtoneSelectionActivity.TYPE_MESSAGE);
                startActivity(intent);
            });
        }

        if (optionSafetySound != null) {
            optionSafetySound.setOnClickListener(v -> {
                Intent intent = new Intent(this, RingtoneSelectionActivity.class);
                intent.putExtra(RingtoneSelectionActivity.EXTRA_SELECTION_TYPE, RingtoneSelectionActivity.TYPE_SAFETY);
                startActivity(intent);
            });
        }

        View optionAlertMessage = findViewById(R.id.optionAlertMessage);
        if (optionAlertMessage != null) {
            optionAlertMessage.setOnClickListener(v -> {
                startActivity(new Intent(this, AlertMessageActivity.class));
            });
        }

        View optionLanguage = findViewById(R.id.optionLanguage);
        if (optionLanguage != null) {
            optionLanguage.setOnClickListener(v -> {
                startActivity(new Intent(this, LanguageSelectionActivity.class));
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (tvCurrentLanguage != null) {
            tvCurrentLanguage.setText(triggerManager.getLanguage());
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (settingsChanged) {
            restartService();
            settingsChanged = false;
        }
    }

    private void restartService() {
        Intent serviceIntent = new Intent(this, com.alert.beacon.services.BeaconService.class);
        // Using startService is enough to trigger onStartCommand and update foreground state
        // without the strict requirements of startForegroundService if we are potentially 
        // disabling foreground features.
        startService(serviceIntent);
    }
}
