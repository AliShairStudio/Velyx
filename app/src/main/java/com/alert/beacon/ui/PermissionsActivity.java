package com.alert.beacon.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.alert.beacon.R;
import com.alert.beacon.data.TriggerManager;
import com.alert.beacon.services.BeaconService;

public class PermissionsActivity extends BaseActivity {

    private SwitchCompat swMic, swLoc, swSms, swNotif, swCam;
    private TriggerManager triggerManager;
    private boolean settingsChanged = false;

    private static final int REQ_MIC = 101;
    private static final int REQ_LOC = 102;
    private static final int REQ_SMS = 103;
    private static final int REQ_NOTIF = 104;
    private static final int REQ_CAM = 105;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permissions);

        triggerManager = new TriggerManager(this);

        Toolbar toolbar = findViewById(R.id.toolbarPermissions);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        swMic = findViewById(R.id.swMic);
        swLoc = findViewById(R.id.swLoc);
        swSms = findViewById(R.id.swSms);
        swCam = findViewById(R.id.swCam);
        swNotif = findViewById(R.id.swNotif);

        View btnOpenSettings = findViewById(R.id.btnOpenSettings);

        setupSwitchListeners();

        if (btnOpenSettings != null) {
            btnOpenSettings.setOnClickListener(v -> openSystemSettings());
        }
    }

    private void setupSwitchListeners() {
        swMic.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) {
                triggerManager.setMicPermissionEnabled(isChecked);
                settingsChanged = true;
                if (isChecked && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
                }
            }
        });

        swLoc.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) {
                triggerManager.setLocationPermissionEnabled(isChecked);
                settingsChanged = true;
                if (isChecked && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_LOC);
                }
            }
        });

        swSms.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) {
                triggerManager.setSmsPermissionEnabled(isChecked);
                settingsChanged = true;
                if (isChecked && ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.SEND_SMS}, REQ_SMS);
                }
            }
        });

        swCam.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) {
                settingsChanged = true;
                if (isChecked && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQ_CAM);
                }
            }
        });

        swNotif.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) {
                triggerManager.setNotificationPermissionEnabled(isChecked);
                settingsChanged = true;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (isChecked && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
                    }
                }
            }
        });
    }

    private void restartService() {
        Intent serviceIntent = new Intent(this, BeaconService.class);
        // Start/Update service. BeaconService.onStartCommand will handle 
        // whether to go into foreground or not based on new settings.
        startService(serviceIntent);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (settingsChanged) {
            restartService();
            settingsChanged = false;
        }
    }

    private void openSystemSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", getPackageName(), null);
        intent.setData(uri);
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePermissionStatuses();
    }

    private void updatePermissionStatuses() {
        boolean micGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        swMic.setChecked(micGranted && triggerManager.isMicPermissionEnabled());

        boolean locGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        swLoc.setChecked(locGranted && triggerManager.isLocationPermissionEnabled());

        boolean smsGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED;
        swSms.setChecked(smsGranted && triggerManager.isSmsPermissionEnabled());

        boolean camGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        swCam.setChecked(camGranted);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            boolean notifGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
            swNotif.setChecked(notifGranted && triggerManager.isNotificationPermissionEnabled());
        } else {
            swNotif.setChecked(triggerManager.isNotificationPermissionEnabled());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        
        if (requestCode == REQ_MIC) triggerManager.setMicPermissionEnabled(granted);
        if (requestCode == REQ_LOC) triggerManager.setLocationPermissionEnabled(granted);
        if (requestCode == REQ_SMS) triggerManager.setSmsPermissionEnabled(granted);
        if (requestCode == REQ_NOTIF) triggerManager.setNotificationPermissionEnabled(granted);

        updatePermissionStatuses();
        settingsChanged = true;

        if (!granted) {
            if (!ActivityCompat.shouldShowRequestPermissionRationale(this, permissions[0])) {
                Toast.makeText(this, "Permission denied. Please enable it in System Settings.", Toast.LENGTH_LONG).show();
            }
        }
    }
}
