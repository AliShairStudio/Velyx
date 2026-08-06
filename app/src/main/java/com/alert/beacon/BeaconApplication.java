package com.alert.beacon;

import android.app.Application;
import androidx.appcompat.app.AppCompatDelegate;
import com.alert.beacon.data.TriggerManager;
import com.alert.beacon.services.GuardianWorker;
import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory;
import com.google.firebase.database.FirebaseDatabase;
import com.vanniktech.emoji.EmojiManager;
import com.vanniktech.emoji.google.GoogleEmojiProvider;

import java.util.concurrent.TimeUnit;

import android.app.ActivityManager;
import android.os.Build;
import android.os.Process;
import java.util.List;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

public class BeaconApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        
        // Initialize Emoji Manager
        EmojiManager.install(new GoogleEmojiProvider());

        // Explicitly initialize Firebase
        FirebaseApp.initializeApp(this);
        try {
            FirebaseDatabase.getInstance("https://beaconsos-a2dba-default-rtdb.asia-southeast1.firebasedatabase.app/").setPersistenceEnabled(true);
        } catch (Exception ignored) {}

        // Initialize App Check with Debug Provider for local testing
        FirebaseAppCheck firebaseAppCheck = FirebaseAppCheck.getInstance();
        firebaseAppCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance());
        
        TriggerManager triggerManager = new TriggerManager(this);
        int themeMode = triggerManager.getThemeMode();
        
        switch (themeMode) {
            case 1:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case 2:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }

        if (isMainProcess()) {
            scheduleGuardianWorker();
        }
    }

    private boolean isMainProcess() {
        String processName = "";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            processName = getProcessName();
        } else {
            int pid = Process.myPid();
            ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            if (am != null) {
                List<ActivityManager.RunningAppProcessInfo> processes = am.getRunningAppProcesses();
                if (processes != null) {
                    for (ActivityManager.RunningAppProcessInfo info : processes) {
                        if (info.pid == pid) {
                            processName = info.processName;
                            break;
                        }
                    }
                }
            }
        }
        return processName.isEmpty() || getPackageName().equals(processName);
    }

    private void scheduleGuardianWorker() {
        try {
            PeriodicWorkRequest guardianWork = new PeriodicWorkRequest.Builder(GuardianWorker.class, 15, TimeUnit.MINUTES)
                    .addTag("GuardianWorker")
                    .build();
            
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                    "VelyxGuardian",
                    ExistingPeriodicWorkPolicy.KEEP,
                    guardianWork
            );
        } catch (Exception ignored) {}
    }
}
