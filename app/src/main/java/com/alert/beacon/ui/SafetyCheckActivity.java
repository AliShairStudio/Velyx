package com.alert.beacon.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.alert.beacon.R;
import com.alert.beacon.services.BeaconService;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import java.util.Locale;

public class SafetyCheckActivity extends BaseActivity {

    private NumberPicker pickerHours, pickerMinutes, pickerSeconds;
    private TextView tvRunningTimer;
    private CircularProgressIndicator progressTimer;
    private MaterialButton btnStartTimer, btnPauseResumeTimer;
    private View layoutPicker, layoutRunning, layoutTimerControls;
    
    private boolean isTimerRunning = false;
    private boolean isTimerPaused = false;

    private final OnBackPressedCallback backCallback = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            Toast.makeText(SafetyCheckActivity.this, "Please stop the timer first", Toast.LENGTH_SHORT).show();
        }
    };

    private final BroadcastReceiver timerReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;

            switch (action) {
                case BeaconService.ACTION_TIMER_TICK:
                    long remainingMs = intent.getLongExtra(BeaconService.EXTRA_REMAINING_MS, 0);
                    long totalMs = intent.getLongExtra(BeaconService.EXTRA_TOTAL_MS, 0);
                    updateTimerUI(remainingMs, totalMs);
                    break;
                case BeaconService.ACTION_TIMER_FINISHED:
                    handleTimerFinished();
                    break;
                case BeaconService.ACTION_TIMER_PAUSED:
                    isTimerPaused = true;
                    updateButtonStates();
                    break;
                case BeaconService.ACTION_TIMER_RESUMED:
                    isTimerPaused = false;
                    updateButtonStates();
                    break;
                case BeaconService.ACTION_TIMER_STOPPED:
                    isTimerRunning = false;
                    isTimerPaused = false;
                    updateButtonStates();
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_safety_check);

        setupToolbar();
        initViews();
        setupPickers();
        
        getOnBackPressedDispatcher().addCallback(this, backCallback);
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        isTimerRunning = BeaconService.isSafetyTimerRunning;
        isTimerPaused = BeaconService.isTimerPaused;
        
        if (isTimerRunning) {
            updateTimerUI(BeaconService.remainingTimeMs, BeaconService.originalDurationMs);
        }
        
        updateButtonStates();

        IntentFilter filter = new IntentFilter();
        filter.addAction(BeaconService.ACTION_TIMER_TICK);
        filter.addAction(BeaconService.ACTION_TIMER_FINISHED);
        filter.addAction(BeaconService.ACTION_TIMER_PAUSED);
        filter.addAction(BeaconService.ACTION_TIMER_RESUMED);
        filter.addAction(BeaconService.ACTION_TIMER_STOPPED);
        
        ContextCompat.registerReceiver(this, timerReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(timerReceiver);
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbarSafety);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("");
        }
        toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
    }

    private void initViews() {
        pickerHours = findViewById(R.id.pickerHours);
        pickerMinutes = findViewById(R.id.pickerMinutes);
        pickerSeconds = findViewById(R.id.pickerSeconds);
        
        tvRunningTimer = findViewById(R.id.tvRunningTimer);
        progressTimer = findViewById(R.id.progressTimer);
        btnStartTimer = findViewById(R.id.btnStartTimer);
        MaterialButton btnStopTimer = findViewById(R.id.btnStopTimer);
        btnPauseResumeTimer = findViewById(R.id.btnPauseResumeTimer);
        
        View btnSafetySettings = findViewById(R.id.btnSafetySettings);
        if (btnSafetySettings != null) {
            btnSafetySettings.setOnClickListener(v -> startActivity(new Intent(this, SafetySettingsActivity.class)));
        }
        
        layoutPicker = findViewById(R.id.layoutPicker);
        layoutRunning = findViewById(R.id.layoutRunning);
        layoutTimerControls = findViewById(R.id.layoutTimerControls);
        View btnSchedule = findViewById(R.id.btnSchedule);

        btnStartTimer.setOnClickListener(v -> startSafetyTimer());
        btnStopTimer.setOnClickListener(v -> stopSafetyTimer());
        btnPauseResumeTimer.setOnClickListener(v -> togglePauseResume());
        
        if (btnSchedule != null) {
            btnSchedule.setOnClickListener(v -> startActivity(new Intent(this, ScheduleSafetyActivity.class)));
        }
    }

    private void setupPickers() {
        pickerHours.setMinValue(0);
        pickerHours.setMaxValue(23);
        pickerHours.setFormatter(i -> String.format(Locale.getDefault(), "%02d", i));

        pickerMinutes.setMinValue(0);
        pickerMinutes.setMaxValue(59);
        pickerMinutes.setFormatter(i -> String.format(Locale.getDefault(), "%02d", i));
        pickerMinutes.setValue(3); // Default to 3 minutes

        pickerSeconds.setMinValue(0);
        pickerSeconds.setMaxValue(59);
        pickerSeconds.setFormatter(i -> String.format(Locale.getDefault(), "%02d", i));
    }

    private void startSafetyTimer() {
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            Toast.makeText(this, "Please allow 'Display over other apps' for the floating timer", Toast.LENGTH_LONG).show();
            return;
        }

        int hours = pickerHours.getValue();
        int minutes = pickerMinutes.getValue();
        int seconds = pickerSeconds.getValue();

        long totalMillis = (hours * 3600L + minutes * 60L + seconds) * 1000L;

        if (totalMillis <= 0) {
            Toast.makeText(this, "Please set a duration", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, BeaconService.class);
        intent.setAction(BeaconService.ACTION_START_SAFETY_TIMER);
        intent.putExtra(BeaconService.EXTRA_TIMER_DURATION_MS, totalMillis);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }

        isTimerRunning = true;
        isTimerPaused = false;
        updateButtonStates();
        updateTimerUI(totalMillis, totalMillis);

        Toast.makeText(this, "Safety timer starting...", Toast.LENGTH_SHORT).show();
    }

    private void updateTimerUI(long millisUntilFinished, long totalMillis) {
        int totalSeconds = (int) (millisUntilFinished / 1000);
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;
        
        tvRunningTimer.setText(String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds));
        
        if (totalMillis > 0) {
            int progress = (int) ((millisUntilFinished * 1000) / totalMillis);
            if (progressTimer != null) {
                progressTimer.setProgress(progress, true);
            }
        }
    }

    private void togglePauseResume() {
        Intent intent = new Intent(this, BeaconService.class);
        if (isTimerPaused) {
            intent.setAction(BeaconService.ACTION_RESUME_SAFETY_TIMER);
        } else {
            intent.setAction(BeaconService.ACTION_PAUSE_SAFETY_TIMER);
        }
        startService(intent);
    }

    private void updateButtonStates() {
        backCallback.setEnabled(isTimerRunning);
        if (isTimerRunning) {
            layoutPicker.setVisibility(View.GONE);
            layoutRunning.setVisibility(View.VISIBLE);
            btnStartTimer.setVisibility(View.GONE);
            layoutTimerControls.setVisibility(View.VISIBLE);
            
            btnPauseResumeTimer.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
            if (isTimerPaused) {
                btnPauseResumeTimer.setIconResource(R.drawable.play);
                btnPauseResumeTimer.setPadding((int)(4 * getResources().getDisplayMetrics().density), 0, 0, 0);
            } else {
                btnPauseResumeTimer.setIconResource(R.drawable.pause);
                btnPauseResumeTimer.setPadding(0, 0, 0, 0);
            }
        } else {
            layoutPicker.setVisibility(View.VISIBLE);
            layoutRunning.setVisibility(View.GONE);
            btnStartTimer.setVisibility(View.VISIBLE);
            layoutTimerControls.setVisibility(View.GONE);
        }
    }

    private void stopSafetyTimer() {
        Intent intent = new Intent(this, BeaconService.class);
        intent.setAction(BeaconService.ACTION_STOP_SAFETY_TIMER);
        startService(intent);

        isTimerRunning = false;
        isTimerPaused = false;
        updateButtonStates();
        
        Toast.makeText(this, R.string.msg_timer_stopped, Toast.LENGTH_SHORT).show();
    }

    private void handleTimerFinished() {
        isTimerRunning = false;
        isTimerPaused = false;
        tvRunningTimer.setText(R.string.initial_timer_value);
        updateButtonStates();
        Toast.makeText(this, R.string.emergency_triggered, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
