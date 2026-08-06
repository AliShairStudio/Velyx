package com.alert.beacon.services;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import com.alert.beacon.data.TriggerManager;

public class VelyxAccessibilityService extends AccessibilityService implements SensorEventListener {
    private static final String TAG = "VelyxAccessibility";
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private PowerManager.WakeLock wakeLock;
    private TriggerManager triggerManager;

    // Shake detection variables
    private long lastUpdate = 0;
    private float last_x, last_y, last_z;
    private static final int SHAKE_THRESHOLD = 800;

    private final android.os.Handler heartbeatHandler = new android.os.Handler();
    private final Runnable heartbeatRunnable = new Runnable() {
        @Override
        public void run() {
            Log.d(TAG, "Guardian Heartbeat: Ensuring monitoring is active");
            startBeaconService();
            heartbeatHandler.postDelayed(this, 5000); // Check every 5 seconds
        }
    };

    @Override
    protected void onServiceConnected() {
        Log.d(TAG, "Accessibility Service Connected");
        triggerManager = new TriggerManager(this);
        
        // Keep CPU awake
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Velyx::AccessibilityWakeLock");
            wakeLock.acquire(10 * 60 * 60 * 1000L); // 10 hours
        }

        // Initialize Shake Sensor
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        }

        // Start the heartbeat guardian
        heartbeatHandler.post(heartbeatRunnable);
    }

    private void startBeaconService() {
        Intent intent = new Intent(this, BeaconService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (triggerManager != null && triggerManager.isPowerTriggerEnabled()) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_POWER && event.getAction() == KeyEvent.ACTION_DOWN) {
                Log.d(TAG, "Global Power Button Detected via Accessibility");
                triggerSos("Power Button (Accessibility)");
                // We return false to allow the system to still handle the power button (screen lock/unlock)
                // unless the user specifically wants to block it, but usually we just want to monitor.
                return false;
            }
        }
        return super.onKeyEvent(event);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (triggerManager != null && !triggerManager.isShakeTriggerEnabled()) return;

        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            long curTime = System.currentTimeMillis();
            if ((curTime - lastUpdate) > 100) {
                long diffTime = (curTime - lastUpdate);
                lastUpdate = curTime;

                float x = event.values[0];
                float y = event.values[1];
                float z = event.values[2];

                float speed = Math.abs(x + y + z - last_x - last_y - last_z) / diffTime * 10000;

                if (speed > SHAKE_THRESHOLD) {
                    Log.d(TAG, "Global Shake Detected via Accessibility");
                    triggerSos("Shake Trigger (Accessibility)");
                }
                last_x = x;
                last_y = y;
                last_z = z;
            }
        }
    }

    private void triggerSos(String type) {
        Intent intent = new Intent(this, BeaconService.class);
        intent.setAction(BeaconService.ACTION_TRIGGER_SOS);
        intent.putExtra(BeaconService.EXTRA_TRIGGER_TYPE, type);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not used
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Not used but required
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "Accessibility Service Interrupted");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }
}
