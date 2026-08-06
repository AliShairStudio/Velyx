package com.alert.beacon.ui;

import android.app.AlarmManager;
import android.app.DatePickerDialog;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.Toolbar;

import com.alert.beacon.R;
import com.alert.beacon.data.TriggerManager;
import com.alert.beacon.services.SafetyScheduleReceiver;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class ScheduleSafetyActivity extends BaseActivity {

    private TextView tvStartDateTime, tvEndDateTime, tvDurationInfo;
    private Calendar startCalendar, endCalendar;
    private TriggerManager triggerManager;
    private final SimpleDateFormat dateTimeFormat = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_schedule_safety);

        triggerManager = new TriggerManager(this);
        startCalendar = Calendar.getInstance();
        endCalendar = Calendar.getInstance();
        endCalendar.add(Calendar.MINUTE, 30);

        setupToolbar();
        initViews();
        updateUI();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbarSchedule);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("");
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void initViews() {
        tvStartDateTime = findViewById(R.id.tvStartDateTime);
        tvEndDateTime = findViewById(R.id.tvEndDateTime);
        tvDurationInfo = findViewById(R.id.tvDurationInfo);

        findViewById(R.id.btnPickStart).setOnClickListener(v -> pickDateTime(true));
        findViewById(R.id.btnPickEnd).setOnClickListener(v -> pickDateTime(false));

        MaterialButton btnSave = findViewById(R.id.btnSaveSchedule);
        btnSave.setOnClickListener(v -> saveSchedule());
    }

    private void pickDateTime(boolean isStart) {
        Calendar currentCal = isStart ? startCalendar : endCalendar;
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            currentCal.set(Calendar.YEAR, year);
            currentCal.set(Calendar.MONTH, month);
            currentCal.set(Calendar.DAY_OF_MONTH, dayOfMonth);

            new TimePickerDialog(this, (timeView, hourOfDay, minute) -> {
                currentCal.set(Calendar.HOUR_OF_DAY, hourOfDay);
                currentCal.set(Calendar.MINUTE, minute);
                currentCal.set(Calendar.SECOND, 0);

                if (!isStart && endCalendar.before(startCalendar)) {
                    endCalendar.setTimeInMillis(startCalendar.getTimeInMillis());
                    endCalendar.add(Calendar.MINUTE, 5);
                    Toast.makeText(this, R.string.msg_end_after_start, Toast.LENGTH_SHORT).show();
                }
                updateUI();
            }, currentCal.get(Calendar.HOUR_OF_DAY), currentCal.get(Calendar.MINUTE), false).show();

        }, currentCal.get(Calendar.YEAR), currentCal.get(Calendar.MONTH), currentCal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void updateUI() {
        tvStartDateTime.setText(dateTimeFormat.format(startCalendar.getTime()));
        tvEndDateTime.setText(dateTimeFormat.format(endCalendar.getTime()));

        long diffMs = endCalendar.getTimeInMillis() - startCalendar.getTimeInMillis();
        long diffMin = diffMs / (60 * 1000);
        tvDurationInfo.setText(getString(R.string.label_duration_format, diffMin));
    }

    private void saveSchedule() {
        long startTime = startCalendar.getTimeInMillis();
        long endTime = endCalendar.getTimeInMillis();

        if (startTime <= System.currentTimeMillis()) {
            Toast.makeText(this, R.string.msg_start_future, Toast.LENGTH_SHORT).show();
            return;
        }

        if (endTime <= startTime) {
            Toast.makeText(this, R.string.msg_end_after_start, Toast.LENGTH_SHORT).show();
            return;
        }

        long duration = endTime - startTime;
        triggerManager.saveScheduledSafety(startTime, duration);

        scheduleAlarm(startTime);

        Toast.makeText(this, R.string.msg_safety_scheduled, Toast.LENGTH_SHORT).show();
        finish();
    }

    private void scheduleAlarm(long startTime) {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, SafetyScheduleReceiver.class);
        
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 1001, intent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        if (alarmManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, startTime, pendingIntent);
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, startTime, pendingIntent);
            }
        }
    }
}
