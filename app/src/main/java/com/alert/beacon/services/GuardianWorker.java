package com.alert.beacon.services;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class GuardianWorker extends Worker {
    public GuardianWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d("GuardianWorker", "Checking service status...");
        Intent serviceIntent = new Intent(getApplicationContext(), BeaconService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                getApplicationContext().startForegroundService(serviceIntent);
            } else {
                getApplicationContext().startService(serviceIntent);
            }
        } catch (Exception e) {
            Log.e("GuardianWorker", "Failed to restart service: " + e.getMessage());
        }
        return Result.success();
    }
}
