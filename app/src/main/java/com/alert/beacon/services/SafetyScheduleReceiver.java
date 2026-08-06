package com.alert.beacon.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.alert.beacon.data.TriggerManager;

public class SafetyScheduleReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d("SafetyScheduleReceiver", "Scheduled alarm received!");
        
        TriggerManager triggerManager = new TriggerManager(context);
        long duration = triggerManager.getScheduledDuration();
        
        if (duration > 0) {
            Intent serviceIntent = new Intent(context, BeaconService.class);
            serviceIntent.setAction(BeaconService.ACTION_START_SAFETY_TIMER);
            serviceIntent.putExtra(BeaconService.EXTRA_TIMER_DURATION_MS, duration);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
            
            // Clear schedule after triggering
            triggerManager.clearScheduledSafety();
        }
    }
}
