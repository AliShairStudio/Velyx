package com.alert.beacon.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class RestartServiceReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent != null ? intent.getAction() : "null";
        Log.d("RestartServiceReceiver", "Service restart triggered by action: " + action);
        
        Intent serviceIntent = new Intent(context, BeaconService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        } catch (Exception e) {
            Log.e("RestartServiceReceiver", "Failed to restart service: " + e.getMessage());
        }
    }
}
