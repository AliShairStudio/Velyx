package com.alert.beacon.services;

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.alert.beacon.R;
import com.alert.beacon.audio.AudioDetector;
import com.alert.beacon.data.ChatMessage;
import com.alert.beacon.data.Contact;
import com.alert.beacon.data.SosHistoryEntry;
import com.alert.beacon.data.TriggerManager;
import com.alert.beacon.location.LocationHelper;
import com.alert.beacon.utils.ShakeDetector;
import com.alert.beacon.utils.SmsHelper;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class BeaconService extends Service implements AudioDetector.OnTriggerDetectedListener, ShakeDetector.OnShakeListener {

    private static final String TAG = "VelyxService";
    private static final String CHANNEL_ID = "VelyxServiceChannel";
    private static final String MONITORING_CHANNEL_ID = "VelyxMonitoringChannel";
    private static final String ALARM_CHANNEL_ID = "VelyxAlarmChannel";
    private static final String INFO_CHANNEL_ID = "VelyxInfoChannel";
    private static final String CHAT_CHANNEL_ID = "VelyxChatChannel";
    private AudioDetector audioDetector;
    private LocationHelper locationHelper;
    private SmsHelper smsHelper;
    private TriggerManager triggerManager;
    
    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private ShakeDetector mShakeDetector;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private PowerManager.WakeLock wakeLock;
    private boolean isPhoneCallActive = false;

    // Power Button Trigger Variables
    private long lastScreenToggleTime = 0;
    private int screenToggleCount = 0;
    private static final long POWER_WINDOW = 3000; // 3 seconds window for 3 clicks
    private boolean isSendingSos = false;
    private MediaSession mediaSession;
    private CountDownTimer safetyCountDownTimer;
    public static boolean isSafetyTimerRunning = false;

    private WindowManager windowManager;
    private View floatingIslandView;
    private View layoutCollapsed, layoutExpanded;
    private TextView tvFloatingTimerCollapsed, tvFloatingTimerExpanded;
    private android.widget.ImageButton btnPausePlayFloating;
    private boolean isIslandExpanded = false;
    public static long remainingTimeMs = 0;
    public static long originalDurationMs = 0;
    public static boolean isTimerPaused = false;
    private MediaPlayer alarmPlayer;

    private DatabaseReference globalGroupsRef;
    private ValueEventListener globalGroupsListener;
    private final java.util.Set<String> myGroupIds = new java.util.HashSet<>();
    private boolean isInitialGroupsLoad = true;
    public static String activeChatId = null;

    private final Handler islandHandler = new Handler(Looper.getMainLooper());
    private final Runnable contractIslandRunnable = () -> {
        if (isIslandExpanded && floatingIslandView != null && windowManager != null) {
            WindowManager.LayoutParams params = (WindowManager.LayoutParams) floatingIslandView.getLayoutParams();
            toggleIsland(params);
        }
    };

    private void resetIslandTimer() {
        islandHandler.removeCallbacks(contractIslandRunnable);
        if (isIslandExpanded) {
            islandHandler.postDelayed(contractIslandRunnable, 5000);
        }
    }

    private boolean isSafetyCheckActivityVisible = false;
    private final Application.ActivityLifecycleCallbacks lifecycleCallbacks = new Application.ActivityLifecycleCallbacks() {
        @Override public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {}
        @Override public void onActivityStarted(@NonNull Activity activity) {
            if (activity instanceof com.alert.beacon.ui.SafetyCheckActivity) {
                isSafetyCheckActivityVisible = true;
            }
            updateIslandVisibility();
        }
        @Override public void onActivityResumed(@NonNull Activity activity) {}
        @Override public void onActivityPaused(@NonNull Activity activity) {}
        @Override public void onActivityStopped(@NonNull Activity activity) {
            if (activity instanceof com.alert.beacon.ui.SafetyCheckActivity) {
                isSafetyCheckActivityVisible = false;
            }
            updateIslandVisibility();
        }
        @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {}
        @Override public void onActivityDestroyed(@NonNull Activity activity) {}
    };

    private void updateIslandVisibility() {
        if (isSafetyTimerRunning && !isSafetyCheckActivityVisible) {
            showFloatingIsland();
        } else {
            removeFloatingIsland();
        }
    }

    private final BroadcastReceiver callReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
            if (TelephonyManager.EXTRA_STATE_RINGING.equals(state) || TelephonyManager.EXTRA_STATE_OFFHOOK.equals(state)) {
                isPhoneCallActive = true;
                if (audioDetector != null) audioDetector.stopListening();
            } else if (Objects.equals(TelephonyManager.EXTRA_STATE_IDLE, state)) {
                isPhoneCallActive = false;
                checkAndStartMic();
            }
        }
    };

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action) || Intent.ACTION_SCREEN_ON.equals(action)) {
                if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                    Log.d(TAG, "Screen OFF. Reinforcing detection.");
                    if (wakeLock != null && !wakeLock.isHeld()) {
                        wakeLock.acquire(10 * 60 * 1000L); // 10 mins initially
                    }
                    // Re-register sensors to ensure they aren't killed by OS
                    reRegisterSensors();
                }

                if (triggerManager.isPowerTriggerEnabled()) {
                    handlePowerButtonPress();
                }
            }
        }
    };

    private void reRegisterSensors() {
        if (mAccelerometer != null && mSensorManager != null) {
            mSensorManager.unregisterListener(mShakeDetector);
            if (triggerManager.isShakeTriggerEnabled()) {
                mSensorManager.registerListener(mShakeDetector, mAccelerometer, SensorManager.SENSOR_DELAY_GAME);
                Log.d(TAG, "Shake detector re-registered with GAME delay");
            }
        }
    }

    private void handlePowerButtonPress() {
        long currentTime = System.currentTimeMillis();
        
        // Immediate WakeLock acquisition to ensure the CPU doesn't sleep during the logic
        if (wakeLock != null && !wakeLock.isHeld()) {
            wakeLock.acquire(5000); // 5 seconds just to process the clicks
        }

        if (currentTime - lastScreenToggleTime > POWER_WINDOW) {
            screenToggleCount = 1;
        } else {
            screenToggleCount++;
        }
        
        lastScreenToggleTime = currentTime;
        Log.d(TAG, "Power button toggle count: " + screenToggleCount);

        // 3 screen toggles within the window = 3 power button presses (approximately)
        if (screenToggleCount >= 3) {
            if (!isSendingSos) {
                isSendingSos = true;
                Log.d(TAG, "TRIPLE POWER BUTTON PRESS DETECTED! Triggering SOS.");
                
                // Extra vibration pattern for confirmation
                Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                if (v != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        v.vibrate(VibrationEffect.createWaveform(new long[]{0, 200, 100, 200}, -1));
                    } else {
                        v.vibrate(500);
                    }
                }
                
                triggerSosProcedure("Power Button Trigger");
                // Reset count after successful trigger
                screenToggleCount = 0;
                
                // Allow future triggers after 10 seconds
                mHandler.postDelayed(() -> isSendingSos = false, 10000);
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        triggerManager = new TriggerManager(this);
        
        // Start foreground immediately in onCreate to prevent OS kill
        startServiceInForeground();
        
        locationHelper = new LocationHelper(this);
        smsHelper = new SmsHelper(this);
        
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mShakeDetector = new ShakeDetector();
        mShakeDetector.setOnShakeListener(this);

        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "velyx:TriggerWakeLock");
            wakeLock.setReferenceCounted(false);
        }

        // 1. Receivers for Lifecycle/Phone State
        IntentFilter callFilter = new IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
        registerReceiver(callReceiver, callFilter);

        IntentFilter screenFilter = new IntentFilter();
        screenFilter.addAction(Intent.ACTION_SCREEN_OFF);
        screenFilter.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(screenReceiver, screenFilter);

        // 2. Media Session (Keeps service "Active" in system's eye)
        setupMediaSession();
        
        getApplication().registerActivityLifecycleCallbacks(lifecycleCallbacks);

        setupGlobalChatListener();
    }

    private void setupGlobalChatListener() {
        String myPhone = triggerManager.getUserPhone();
        if (myPhone.isEmpty()) return;

        if (globalGroupsRef != null && globalGroupsListener != null) {
            globalGroupsRef.removeEventListener(globalGroupsListener);
        }

        globalGroupsRef = FirebaseDatabase.getInstance("https://beaconsos-a2dba-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference("groups");
        globalGroupsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    isInitialGroupsLoad = false;
                    return;
                }
                
                for (DataSnapshot groupSnap : snapshot.getChildren()) {
                    String groupId = groupSnap.getKey();
                    boolean isMember = false;
                    DataSnapshot membersSnap = groupSnap.child("memberPhones");
                    for (DataSnapshot m : membersSnap.getChildren()) {
                        if (TriggerManager.isSamePhone(myPhone, m.getValue(String.class))) {
                            isMember = true;
                            break;
                        }
                    }

                    if (isMember) {
                        if (!myGroupIds.contains(groupId)) {
                            myGroupIds.add(groupId);
                            // New group added! Notify if not initial load
                            if (!isInitialGroupsLoad) {
                                String groupName = groupSnap.child("name").getValue(String.class);
                                showChatNotification("New Group", "You've been added to " + groupName, groupId);
                            }
                        }
                        
                        // Process messages in this group
                        listenForGroupMessages(groupSnap, isInitialGroupsLoad);
                    }
                }
                isInitialGroupsLoad = false;
            }

            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        globalGroupsRef.addValueEventListener(globalGroupsListener);
    }

    private final java.util.Set<String> notifiedMessageIds = new java.util.HashSet<>();
    private void listenForGroupMessages(DataSnapshot groupSnap, boolean isInitial) {
        String groupId = groupSnap.getKey();
        String groupName = groupSnap.child("name").getValue(String.class);
        String myPhone = triggerManager.getUserPhone();
        String myNormalizedPhone = TriggerManager.normalizePhone(myPhone);
        
        List<String> members = new ArrayList<>();
        DataSnapshot membersSnap = groupSnap.child("memberPhones");
        for (DataSnapshot m : membersSnap.getChildren()) {
            String p = m.getValue(String.class);
            if (p != null) members.add(p);
        }
        int membersCount = members.size();

        DataSnapshot msgsSnap = groupSnap.child("messages");
        if (msgsSnap.exists()) {
            boolean groupHasNewIncoming = false;
            for (DataSnapshot mSnap : msgsSnap.getChildren()) {
                String msgId = mSnap.getKey();
                
                String senderPhone = mSnap.child("senderPhone").getValue(String.class);
                Long statusLong = mSnap.child("status").getValue(Long.class);
                int status = statusLong != null ? statusLong.intValue() : 0;
                
                if (senderPhone != null && !TriggerManager.isSamePhone(senderPhone, myPhone)) {
                    // Mark as DELIVERED in the deliveredTo map
                    if (!mSnap.child("deliveredTo").hasChild(myNormalizedPhone)) {
                        mSnap.getRef().child("deliveredTo").child(myNormalizedPhone).setValue(System.currentTimeMillis());
                    }

                    if (status < 2 && !notifiedMessageIds.contains(msgId)) { // Unseen
                        notifiedMessageIds.add(msgId);
                        
                        Long timestamp = mSnap.child("timestamp").getValue(Long.class);
                        boolean isVeryRecent = timestamp != null && (System.currentTimeMillis() - timestamp < 60000);

                        if ((!isInitial || isVeryRecent) && !Objects.equals(groupId, activeChatId)) {
                            String senderName = mSnap.child("senderName").getValue(String.class);
                            String messageText = mSnap.child("message").getValue(String.class);
                            String type = mSnap.child("type").getValue(String.class);
                            
                            String displayMsg = "New message";
                            if ("text".equals(type)) displayMsg = messageText;
                            else if ("image".equals(type)) displayMsg = "📷 Photo";
                            else if ("audio".equals(type)) displayMsg = "🎤 Audio message";
                            else if ("system".equals(type)) displayMsg = messageText;
                            
                            showChatNotification(groupName, senderName + ": " + displayMsg, groupId);
                            groupHasNewIncoming = true;
                        }
                    }
                } else if (senderPhone != null && TriggerManager.isSamePhone(senderPhone, myPhone)) {
                    // This is my own message, aggregate status
                    notifiedMessageIds.add(msgId);
                    if (membersCount > 1) {
                        int deliveredCount = (int) mSnap.child("deliveredTo").getChildrenCount();
                        int readCount = (int) mSnap.child("readBy").getChildrenCount();

                        if (readCount >= membersCount - 1) {
                            if (status != 2) mSnap.getRef().child("status").setValue(2);
                        } else if (deliveredCount >= membersCount - 1) {
                            if (status != 1) mSnap.getRef().child("status").setValue(1);
                        }
                    }
                } else {
                    notifiedMessageIds.add(msgId);
                }
            }
            if (groupHasNewIncoming) {
                playMessageNotificationSound();
            }
        }
    }

    private void showChatNotification(String title, String content, String groupId) {
        Intent intent = new Intent(this, com.alert.beacon.ui.GroupChatActivity.class);
        intent.putExtra("GROUP_ID", groupId);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        
        android.app.PendingIntent pendingIntent = android.app.PendingIntent.getActivity(this, groupId.hashCode(),
                intent, android.app.PendingIntent.FLAG_IMMUTABLE | android.app.PendingIntent.FLAG_UPDATE_CURRENT);

        Notification notification = new NotificationCompat.Builder(this, CHAT_CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(R.drawable.message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build();

        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(groupId.hashCode(), notification);
    }

    private void setupMediaSession() {
        mediaSession = new MediaSession(this, "velyx");
        mediaSession.setPlaybackState(new PlaybackState.Builder()
                .setState(PlaybackState.STATE_PLAYING, 0, 1.0f)
                .build());
        mediaSession.setActive(true);
    }

    public static final String ACTION_TRIGGER_SOS = "com.alert.beacon.ACTION_TRIGGER_SOS";
    public static final String ACTION_START_SAFETY_TIMER = "com.alert.beacon.ACTION_START_SAFETY_TIMER";
    public static final String ACTION_STOP_SAFETY_TIMER = "com.alert.beacon.ACTION_STOP_SAFETY_TIMER";
    public static final String ACTION_PAUSE_SAFETY_TIMER = "com.alert.beacon.ACTION_PAUSE_SAFETY_TIMER";
    public static final String ACTION_RESUME_SAFETY_TIMER = "com.alert.beacon.ACTION_RESUME_SAFETY_TIMER";
    public static final String ACTION_TIMER_TICK = "com.alert.beacon.ACTION_TIMER_TICK";
    public static final String ACTION_TIMER_FINISHED = "com.alert.beacon.ACTION_TIMER_FINISHED";
    public static final String ACTION_TIMER_PAUSED = "com.alert.beacon.ACTION_TIMER_PAUSED";
    public static final String ACTION_TIMER_RESUMED = "com.alert.beacon.ACTION_TIMER_RESUMED";
    public static final String ACTION_TIMER_STOPPED = "com.alert.beacon.ACTION_TIMER_STOPPED";
    public static final String EXTRA_TRIGGER_TYPE = "trigger_type";
    public static final String EXTRA_TIMER_DURATION_MS = "timer_duration_ms";
    public static final String EXTRA_REMAINING_MS = "remaining_ms";
    public static final String EXTRA_TOTAL_MS = "total_ms";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Refresh settings from SharedPreferences
        triggerManager = new TriggerManager(this);
        
        Log.i(TAG, "onStartCommand called in process " + android.os.Process.myPid() + " with action: " + (intent != null ? intent.getAction() : "null"));
        
        startServiceInForeground();

        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_TRIGGER_SOS.equals(action)) {
                String type = intent.getStringExtra(EXTRA_TRIGGER_TYPE);
                if (type == null) type = "Manual Trigger";
                triggerSosProcedure(type);
            } else if (ACTION_START_SAFETY_TIMER.equals(action)) {
                long duration = intent.getLongExtra(EXTRA_TIMER_DURATION_MS, 0);
                Log.i(TAG, "Starting safety timer: " + duration + "ms");
                if (duration > 0) {
                    originalDurationMs = duration;
                    playSafetyNotificationSound();
                    startSafetyTimer(duration);
                }
            } else if (ACTION_STOP_SAFETY_TIMER.equals(action)) {
                Log.i(TAG, "Stopping safety timer");
                stopSafetyTimer();
            } else if (ACTION_PAUSE_SAFETY_TIMER.equals(action)) {
                pauseSafetyTimer();
            } else if (ACTION_RESUME_SAFETY_TIMER.equals(action)) {
                resumeSafetyTimer();
            }
        } else {
            // Intent is null, service was restarted by START_STICKY
            Log.d(TAG, "Service restarted by OS (Sticky)");
        }

        if (wakeLock != null && !wakeLock.isHeld()) {
            wakeLock.acquire(10 * 60 * 60 * 1000L); // 10 hours
        }

        if (audioDetector == null) {
            audioDetector = new AudioDetector(this, this);
        }
        
        checkAndStartMic();

        // Only register sensor if shake trigger is enabled to save battery
        if (mAccelerometer != null) {
            mSensorManager.unregisterListener(mShakeDetector);
            if (triggerManager.isShakeTriggerEnabled()) {
                mSensorManager.registerListener(mShakeDetector, mAccelerometer, SensorManager.SENSOR_DELAY_GAME);
                Log.d(TAG, "Shake detector registered");
            } else {
                Log.d(TAG, "Shake detector NOT registered (disabled in settings)");
            }
        }
        
        return START_STICKY;
    }

    private void startServiceInForeground() {
        // Refresh trigger states
        boolean isShakeEnabled = triggerManager.isShakeTriggerEnabled();
        boolean isMicEnabled = triggerManager.isMicPermissionEnabled();
        boolean isPowerEnabled = triggerManager.isPowerTriggerEnabled();
        boolean isMonitoring = isShakeEnabled || isMicEnabled || isPowerEnabled;

        if (isSafetyTimerRunning) {
            String title = "Safety Timer Active";
            String content = "Countdown in progress...";
            Notification notification = createBaseNotification(title, CHANNEL_ID)
                    .setContentText(content)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setOngoing(true)
                    .build();
            doStartForeground(notification);
        } else {
            String title = isMonitoring ? "Velyx Security Active" : "Velyx background services";
            String content = isMonitoring ? "Listening for SOS triggers..." : "Listening for messages...";
            
            Notification notification = createBaseNotification(title, MONITORING_CHANNEL_ID)
                    .setContentText(content)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setOngoing(true)
                    .setCategory(NotificationCompat.CATEGORY_SERVICE)
                    .build();
            doStartForeground(notification);
        }
    }

    private void doStartForeground(Notification notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                int type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                        | ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                        | ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE;
                startForeground(1, notification, type);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE | ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            } else {
                startForeground(1, notification);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting foreground service", e);
        }
    }

    private void checkAndStartMic() {
        boolean micPermGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        boolean micEnabledInApp = triggerManager.isMicPermissionEnabled();
        if (micPermGranted && micEnabledInApp && !isPhoneCallActive) {
            if (audioDetector != null) audioDetector.startListening();
        } else {
            if (audioDetector != null) audioDetector.stopListening();
        }
    }

    @Override
    public void onShake(int count) {
        if (triggerManager.isShakeTriggerEnabled() && count >= 2) {
            if (!isSendingSos) {
                isSendingSos = true;
                vibrateFeedback();
                triggerSosProcedure("Shake Trigger");
                mHandler.postDelayed(() -> isSendingSos = false, 5000);
            }
        }
    }

    private void vibrateFeedback() {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                v.vibrate(500);
            }
        }
    }

    @Override
    public void onTriggerDetected() {
        if (!isSendingSos) {
            isSendingSos = true;
            vibrateFeedback();
            triggerSosProcedure("Voice Trigger");
            mHandler.postDelayed(() -> isSendingSos = false, 5000);
        }
    }

    private void triggerSosProcedure(String triggerType) {
        if (wakeLock != null && !wakeLock.isHeld()) {
            wakeLock.acquire(45000); // 45 seconds to ensure GPS/Network finishes
        }
        sendEmergencySms(triggerType);
    }

    private void sendEmergencySms(String triggerType) {
        List<Contact> allContacts = triggerManager.getContacts();
        Set<String> selectedIds = triggerManager.getSelectedContactIds();
        Set<String> selectedGroupIds = triggerManager.getSelectedGroupIds();
        
        Log.d(TAG, "Preparing SOS SMS. Contacts: " + allContacts.size() + ", Groups: " + selectedGroupIds.size());

        if (allContacts.isEmpty() && selectedGroupIds.isEmpty()) {
            isSendingSos = false;
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
            return;
        }

        locationHelper.getLastLocation(locationUrl -> {
            Log.d(TAG, "Location for SOS: " + locationUrl);
            
            String customMessage = triggerManager.getEmergencyMessage();
            String locationPart = (locationUrl != null) ? (" " + locationUrl) : " [Location Unavailable]";
            String fullMessage = customMessage + locationPart;
            
            Set<String> recipientPhones = new java.util.HashSet<>();
            List<String> sentToNames = new java.util.ArrayList<>();
            
            // Add individual contacts
            for (Contact c : allContacts) {
                if (selectedIds.contains(c.getId())) {
                    recipientPhones.add(c.getPhoneNumber());
                    sentToNames.add(c.getName() + " (Contact)");
                }
            }
            
            int selectedContactCount = 0;
            for (Contact c : allContacts) {
                if (selectedIds.contains(c.getId())) {
                    selectedContactCount++;
                }
            }

            int selectedGroupCount = 0;
            List<com.alert.beacon.data.Group> allGroups = triggerManager.getGroups();
            for (com.alert.beacon.data.Group group : allGroups) {
                if (selectedGroupIds.contains(group.getId())) {
                    selectedGroupCount++;
                    List<String> memberPhones = group.getMemberPhones();
                    if (memberPhones != null && !memberPhones.isEmpty()) {
                        for (String phone : memberPhones) {
                            if (phone != null && !phone.isEmpty() && !com.alert.beacon.data.TriggerManager.isSamePhone(phone, triggerManager.getUserPhone())) {
                                recipientPhones.add(phone);
                            }
                        }
                    }
                    sentToNames.add(group.getName() + " (Group)");
                }
            }

            Log.d(TAG, "Sending SOS SMS to " + recipientPhones.size() + " unique recipients across " + selectedIds.size() + " contacts and " + selectedGroupIds.size() + " groups");
            for (String phone : recipientPhones) {
                smsHelper.sendSms(phone, fullMessage);
            }
            
            SosHistoryEntry entry = new SosHistoryEntry(
                UUID.randomUUID().toString(),
                System.currentTimeMillis(),
                fullMessage,
                sentToNames,
                triggerType
            );
            triggerManager.addSosHistoryEntry(entry);
            
            // Sync to Firebase if online
            try {
                String myPhone = triggerManager.getUserPhone();
                String myName = triggerManager.getUserName();
                FirebaseDatabase database = FirebaseDatabase.getInstance("https://beaconsos-a2dba-default-rtdb.asia-southeast1.firebasedatabase.app/");
                
                if (!myPhone.isEmpty()) {
                    database.getReference("sos_history")
                        .child(myPhone)
                        .child(entry.getId())
                        .setValue(entry);
                }

                // Also send to Firebase Group Chats
                for (String groupId : selectedGroupIds) {
                    DatabaseReference groupMsgRef = database.getReference("groups").child(groupId).child("messages");
                    String msgKey = groupMsgRef.push().getKey();
                    if (msgKey != null) {
                        ChatMessage groupMsg = new ChatMessage(myPhone, myName, fullMessage, System.currentTimeMillis());
                        groupMsgRef.child(msgKey).setValue(groupMsg);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to sync SOS/Group messages to Firebase", e);
            }
            
            final int finalContactCount = selectedContactCount;
            final int finalGroupCount = selectedGroupCount;
            final int finalRecipientCount = recipientPhones.size();

            mHandler.post(() -> {
                String msg = String.format(java.util.Locale.getDefault(),
                    "SOS sent to %d contacts and %d groups (%d unique recipients)",
                    finalContactCount, finalGroupCount, finalRecipientCount);
                Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
                showSosSentNotification(triggerType);
                isSendingSos = false;
                if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
            });
        });
    }

    private void showSosSentNotification(String triggerType) {
        Notification notification = new NotificationCompat.Builder(this, INFO_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_app_logo)
                .setContentTitle("Emergency Alert Sent")
                .setContentText("SOS triggered via " + triggerType)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .build();
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.notify((int) System.currentTimeMillis(), notification);
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.i(TAG, "Task removed (App Swiped), triggering extreme persistence...");
        
        Intent restartServiceIntent = new Intent(getApplicationContext(), RestartServiceReceiver.class);
        restartServiceIntent.setAction("com.alert.beacon.RESTART_SERVICE");
        restartServiceIntent.setPackage(getPackageName());

        android.app.PendingIntent restartServicePendingIntent = android.app.PendingIntent.getBroadcast(
                getApplicationContext(), 1, restartServiceIntent, 
                android.app.PendingIntent.FLAG_ONE_SHOT | android.app.PendingIntent.FLAG_IMMUTABLE);
        
        android.app.AlarmManager alarmService = (android.app.AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE);
        if (alarmService != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                android.app.AlarmManager.AlarmClockInfo info = new android.app.AlarmManager.AlarmClockInfo(System.currentTimeMillis() + 100, restartServicePendingIntent);
                alarmService.setAlarmClock(info, restartServicePendingIntent);
            } else {
                alarmService.setExact(android.app.AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 100, restartServicePendingIntent);
            }
        }

        sendBroadcast(restartServiceIntent);
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        getApplication().unregisterActivityLifecycleCallbacks(lifecycleCallbacks);
        if (globalGroupsRef != null && globalGroupsListener != null) {
            globalGroupsRef.removeEventListener(globalGroupsListener);
        }
        try { unregisterReceiver(callReceiver); } catch (Exception ignored) {}
        try { unregisterReceiver(screenReceiver); } catch (Exception ignored) {}
        mSensorManager.unregisterListener(mShakeDetector);
        if (audioDetector != null) audioDetector.stopListening();
        if (mediaSession != null) mediaSession.release();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        stopAlarm();
        removeFloatingIsland();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    private void startSafetyTimer(long durationMs) {
        if (safetyCountDownTimer != null) {
            safetyCountDownTimer.cancel();
            safetyCountDownTimer = null;
        }
        isSafetyTimerRunning = true;
        remainingTimeMs = durationMs;
        
        // Update notification immediately when timer starts
        startServiceInForeground();
        updateIslandVisibility();
        
        safetyCountDownTimer = new CountDownTimer(durationMs, 100) { 
            @Override
            public void onTick(long millisUntilFinished) {
                remainingTimeMs = millisUntilFinished;
                updateNotificationWithTimer(millisUntilFinished);
                updateFloatingIslandTimer(millisUntilFinished);
                Intent tickIntent = new Intent(ACTION_TIMER_TICK);
                tickIntent.putExtra(EXTRA_REMAINING_MS, millisUntilFinished);
                tickIntent.putExtra(EXTRA_TOTAL_MS, originalDurationMs);
                tickIntent.setPackage(getPackageName());
                sendBroadcast(tickIntent);
                Log.d(TAG, "Timer tick: " + millisUntilFinished);
            }

            @Override
            public void onFinish() {
                Log.i(TAG, "Safety timer finished!");
                isSafetyTimerRunning = false;
                updateIslandVisibility();
                updateNotificationWithTimer(0);
                updateFloatingIslandTimer(0);
                Intent finishIntent = new Intent(ACTION_TIMER_FINISHED);
                finishIntent.setPackage(getPackageName());
                sendBroadcast(finishIntent);
                
                showAlarmNotification();
                playAlarm();
                
                if (triggerManager != null) {
                    triggerManager.addSafetyHistoryEntry(new com.alert.beacon.data.SafetyHistoryEntry(
                            System.currentTimeMillis(), originalDurationMs, true));
                }

                triggerSosProcedure("Safety Check Timer");
            }
        }.start();
    }

    private void stopSafetyTimer() {
        if (safetyCountDownTimer != null) {
            safetyCountDownTimer.cancel();
            safetyCountDownTimer = null;
            
            if (triggerManager != null && isSafetyTimerRunning) {
                triggerManager.addSafetyHistoryEntry(new com.alert.beacon.data.SafetyHistoryEntry(
                        System.currentTimeMillis(), originalDurationMs, false));
            }
        }
        isSafetyTimerRunning = false;
        isTimerPaused = false;
        remainingTimeMs = 0;
        
        Intent intent = new Intent(ACTION_TIMER_STOPPED);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);

        stopAlarm();
        removeFloatingIsland();
        updateNotification(); // Reset to default notification
        
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.cancel(2); // Cancel alarm notification
        }
    }

    private void pauseSafetyTimer() {
        if (safetyCountDownTimer != null) {
            safetyCountDownTimer.cancel();
            safetyCountDownTimer = null;
        }
        isTimerPaused = true;
        if (btnPausePlayFloating != null) {
            btnPausePlayFloating.setImageResource(R.drawable.play);
            btnPausePlayFloating.setColorFilter(Color.WHITE);
        }
        
        Intent intent = new Intent(ACTION_TIMER_PAUSED);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
        
        updateNotificationWithTimer(remainingTimeMs);
    }

    private void resumeSafetyTimer() {
        if (remainingTimeMs > 0) {
            isTimerPaused = false;
            startSafetyTimer(remainingTimeMs);
            if (btnPausePlayFloating != null) {
                btnPausePlayFloating.setImageResource(R.drawable.pause);
                btnPausePlayFloating.setColorFilter(Color.WHITE);
            }
            
            Intent intent = new Intent(ACTION_TIMER_RESUMED);
            intent.setPackage(getPackageName());
            sendBroadcast(intent);
        }
    }

    private void updateNotificationWithTimer(long millisUntilFinished) {
        int totalSeconds = (int) (millisUntilFinished / 1000);
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;
        
        // Format as HH:MM:SS
        String timeStr = String.format(java.util.Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds);
        String status = isTimerPaused ? " (Paused)" : "";

        Notification notification = createBaseNotification("Safety Timer: " + timeStr + status, CHANNEL_ID)
                .setContentText(isTimerPaused ? "Timer is paused. Tap to resume." : "Monitoring for your safety. Tap to open.")
                .setOnlyAlertOnce(true)
                .setSmallIcon(R.mipmap.ic_app_logo)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
        
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(1, notification);
    }

    private void updateNotification() {
        startServiceInForeground();
    }

    private NotificationCompat.Builder createBaseNotification(String title, String channelId) {
        Intent intent = new Intent(this, com.alert.beacon.ui.MainActivity.class);
        if (isSafetyTimerRunning) {
            intent = new Intent(this, com.alert.beacon.ui.SafetyCheckActivity.class);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        
        android.app.PendingIntent pendingIntent = android.app.PendingIntent.getActivity(this, 0,
                intent, android.app.PendingIntent.FLAG_IMMUTABLE | android.app.PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Builder(this, channelId)
                .setContentTitle(title)
                .setSmallIcon(R.mipmap.ic_app_logo)
                .setOngoing(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setContentIntent(pendingIntent)
                .setFullScreenIntent(pendingIntent, false);
    }

    private void showFloatingIsland() {
        if (!Settings.canDrawOverlays(this)) {
            return;
        }

        if (floatingIslandView != null) {
            return; // Already showing
        }

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        ContextThemeWrapper wrapper = new ContextThemeWrapper(this, R.style.Theme_Beacon);
        LayoutInflater inflater = LayoutInflater.from(wrapper);
        floatingIslandView = inflater.inflate(R.layout.layout_floating_island, null, false);
        
        layoutCollapsed = floatingIslandView.findViewById(R.id.layoutCollapsed);
        layoutExpanded = floatingIslandView.findViewById(R.id.layoutExpanded);
        tvFloatingTimerCollapsed = floatingIslandView.findViewById(R.id.tvFloatingTimerCollapsed);
        tvFloatingTimerExpanded = floatingIslandView.findViewById(R.id.tvFloatingTimerExpanded);
        View btnStop = floatingIslandView.findViewById(R.id.btnStopFloating);
        View btnOpen = floatingIslandView.findViewById(R.id.btnOpenAppFloating);
        btnPausePlayFloating = floatingIslandView.findViewById(R.id.btnPausePlayFloating);

        if (btnPausePlayFloating != null) {
            btnPausePlayFloating.setColorFilter(Color.WHITE);
            btnPausePlayFloating.setImageResource(isTimerPaused ? R.drawable.play : R.drawable.pause);
            btnPausePlayFloating.setOnClickListener(v -> {
                resetIslandTimer();
                if (isTimerPaused) {
                    resumeSafetyTimer();
                } else {
                    pauseSafetyTimer();
                }
            });
            btnPausePlayFloating.setOnTouchListener((v, event) -> {
                if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                    v.setAlpha(0.6f);
                } else if (event.getAction() == android.view.MotionEvent.ACTION_UP || event.getAction() == android.view.MotionEvent.ACTION_CANCEL) {
                    v.setAlpha(1.0f);
                    if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                        v.performClick();
                    }
                }
                return true;
            });
        }

        int layoutType;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutType = WindowManager.LayoutParams.TYPE_PHONE;
        }

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                android.graphics.PixelFormat.TRANSLUCENT
        );

        params.gravity = android.view.Gravity.TOP | android.view.Gravity.START;
        params.x = 300; // Fixed start point to ensure it's on screen
        params.y = 200;

        View.OnTouchListener dragListener = new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;
            private long startTime;
            private boolean isDragging = false;

            @Override
            public boolean onTouch(View v, android.view.MotionEvent event) {
                switch (event.getAction()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        startTime = System.currentTimeMillis();
                        isDragging = false;
                        return true;

                    case android.view.MotionEvent.ACTION_MOVE:
                        int dx = (int) (event.getRawX() - initialTouchX);
                        int dy = (int) (event.getRawY() - initialTouchY);
                        
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                            isDragging = true;
                            resetIslandTimer();
                            params.x = initialX + dx;
                            params.y = initialY + dy;
                            try {
                                windowManager.updateViewLayout(floatingIslandView, params);
                            } catch (Exception ignored) {}
                        }
                        return true;

                    case android.view.MotionEvent.ACTION_UP:
                        if (!isDragging) {
                            long duration = System.currentTimeMillis() - startTime;
                            if (duration < 500) {
                                v.performClick();
                            }
                        } else {
                            resetIslandTimer();
                        }
                        return true;
                }
                return false;
            }
        };

        floatingIslandView.setOnClickListener(v -> {
            Log.d(TAG, "Island clicked, toggling...");
            toggleIsland(params);
        });
        
        layoutCollapsed.setOnClickListener(v -> {
            Log.d(TAG, "Collapsed layout clicked, toggling...");
            toggleIsland(params);
        });

        layoutExpanded.setOnClickListener(v -> {
            Log.d(TAG, "Expanded layout clicked, toggling...");
            if (isIslandExpanded) {
                toggleIsland(params);
            }
        });

        floatingIslandView.setOnTouchListener(dragListener);
        layoutCollapsed.setOnTouchListener(dragListener);
        layoutExpanded.setOnTouchListener(dragListener);

        if (btnStop != null) {
            if (btnStop instanceof android.widget.ImageButton) {
                ((android.widget.ImageButton) btnStop).setColorFilter(ContextCompat.getColor(this, R.color.success_green));
            }
            btnStop.setOnClickListener(v -> {
                Log.d(TAG, "Stop button clicked on floating island");
                stopSafetyTimer();
            });
            btnStop.setOnTouchListener((v, event) -> {
                if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                    v.setAlpha(0.6f);
                } else if (event.getAction() == android.view.MotionEvent.ACTION_UP || event.getAction() == android.view.MotionEvent.ACTION_CANCEL) {
                    v.setAlpha(1.0f);
                    if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                        v.performClick();
                    }
                }
                return true;
            });
        }

        if (btnOpen != null) {
            if (btnOpen instanceof android.widget.ImageButton) {
                ((android.widget.ImageButton) btnOpen).setColorFilter(Color.WHITE);
            }
            btnOpen.setOnClickListener(v -> {
                Log.d(TAG, "Open app button clicked on floating island");
                Intent intent = new Intent(this, com.alert.beacon.ui.SafetyCheckActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
            });
            // Open button touch should NOT trigger drag
            btnOpen.setOnTouchListener((v, event) -> {
                if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                    v.performClick();
                }
                return true;
            });
        }

        windowManager.addView(floatingIslandView, params);
    }

    private void toggleIsland(WindowManager.LayoutParams params) {
        if (floatingIslandView == null) return;

        isIslandExpanded = !isIslandExpanded;
        layoutCollapsed.setVisibility(isIslandExpanded ? View.GONE : View.VISIBLE);
        layoutExpanded.setVisibility(isIslandExpanded ? View.VISIBLE : View.GONE);
        
        if (isIslandExpanded) {
            resetIslandTimer();
        } else {
            islandHandler.removeCallbacks(contractIslandRunnable);
        }

        try {
            windowManager.updateViewLayout(floatingIslandView, params);
        } catch (Exception e) {
            Log.e(TAG, "Error updating floating island layout", e);
        }
    }

    private void updateFloatingIslandTimer(long millisUntilFinished) {
        int totalSeconds = (int) (millisUntilFinished / 1000);
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;
        String timeStr = String.format(java.util.Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds);
        
        if (tvFloatingTimerCollapsed != null) {
            tvFloatingTimerCollapsed.setText(timeStr);
        }
        if (tvFloatingTimerExpanded != null) {
            tvFloatingTimerExpanded.setText(timeStr);
        }
    }

    private void removeFloatingIsland() {
        if (windowManager != null && floatingIslandView != null) {
            try {
                windowManager.removeView(floatingIslandView);
            } catch (Exception ignored) {}
            floatingIslandView = null;
            layoutCollapsed = null;
            layoutExpanded = null;
            tvFloatingTimerCollapsed = null;
            tvFloatingTimerExpanded = null;
            btnPausePlayFloating = null;
            isIslandExpanded = false;
        }
    }

    private void playAlarm() {
        if (alarmPlayer != null && alarmPlayer.isPlaying()) return;
        try {
            String soundUriString = triggerManager.getSafetySoundUri();
            Uri alert;
            if (soundUriString == null) {
                alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                if (alert == null) {
                    alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
                }
            } else {
                alert = Uri.parse(soundUriString);
            }

            alarmPlayer = MediaPlayer.create(this, alert);
            if (alarmPlayer != null) {
                alarmPlayer.setLooping(true);
                alarmPlayer.start();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error playing alarm", e);
        }
    }

    private void stopAlarm() {
        if (alarmPlayer != null) {
            try {
                if (alarmPlayer.isPlaying()) {
                    alarmPlayer.stop();
                }
                alarmPlayer.release();
            } catch (Exception ignored) {}
            alarmPlayer = null;
        }
    }

    private void playSafetyNotificationSound() {
        try {
            String soundUriStr = triggerManager.getSafetySoundUri();
            Uri soundUri;
            if (soundUriStr != null) {
                soundUri = Uri.parse(soundUriStr);
            } else {
                soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            }
            Ringtone r = RingtoneManager.getRingtone(this, soundUri);
            if (r != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    AudioAttributes aa = new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build();
                    r.setAudioAttributes(aa);
                }
                r.play();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error playing safety notification sound", e);
        }
    }

    private void playMessageNotificationSound() {
        try {
            String soundUriStr = triggerManager.getNotificationSoundUri();
            Uri soundUri;
            if (soundUriStr != null) {
                soundUri = Uri.parse(soundUriStr);
            } else {
                soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            }
            Ringtone r = RingtoneManager.getRingtone(this, soundUri);
            if (r != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    AudioAttributes aa = new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build();
                    r.setAudioAttributes(aa);
                }
                r.play();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error playing message notification sound", e);
        }
    }

    private void showAlarmNotification() {
        Intent stopIntent = new Intent(this, BeaconService.class);
        stopIntent.setAction(ACTION_STOP_SAFETY_TIMER);
        android.app.PendingIntent stopPendingIntent = android.app.PendingIntent.getService(this, 1,
                stopIntent, android.app.PendingIntent.FLAG_IMMUTABLE | android.app.PendingIntent.FLAG_UPDATE_CURRENT);

        Intent openIntent = new Intent(this, com.alert.beacon.ui.SafetyCheckActivity.class);
        android.app.PendingIntent openPendingIntent = android.app.PendingIntent.getActivity(this, 0,
                openIntent, android.app.PendingIntent.FLAG_IMMUTABLE | android.app.PendingIntent.FLAG_UPDATE_CURRENT);

        Notification notification = new NotificationCompat.Builder(this, ALARM_CHANNEL_ID)
                .setContentTitle("SAFETY ALARM TRIGGERED!")
                .setContentText("Emergency contacts are being notified.")
                .setSmallIcon(R.mipmap.ic_app_logo)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(openPendingIntent, true)
                .setOngoing(true)
                .setAutoCancel(false)
                .addAction(R.drawable.stop, "STOP ALARM", stopPendingIntent)
                .build();

        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(2, notification);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager == null) return;
            
            // 1. Service Channel (Active Safety Timer)
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID, "Safety Timer Active", NotificationManager.IMPORTANCE_LOW
            );
            serviceChannel.setSound(null, null); 
            serviceChannel.enableVibration(false);
            manager.createNotificationChannel(serviceChannel);

            // 2. Monitoring Channel (Background - Important)
            NotificationChannel monitoringChannel = new NotificationChannel(
                    MONITORING_CHANNEL_ID, "Background Monitoring", NotificationManager.IMPORTANCE_HIGH
            );
            monitoringChannel.setDescription("Ensures background safety monitoring remains active");
            monitoringChannel.setSound(null, null);
            monitoringChannel.enableVibration(false);
            monitoringChannel.setShowBadge(true);
            monitoringChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            manager.createNotificationChannel(monitoringChannel);
            
            // 3. Alarm Channel (High Priority Alarms)
            NotificationChannel alarmChannel = new NotificationChannel(
                    ALARM_CHANNEL_ID, "Emergency Alarms", NotificationManager.IMPORTANCE_HIGH
            );
            alarmChannel.setDescription("High priority alarms for safety check");
            alarmChannel.enableLights(true);
            alarmChannel.setLightColor(Color.RED);
            alarmChannel.enableVibration(true);
            manager.createNotificationChannel(alarmChannel);

            // 4. Info Channel (SOS Sent Notifications)
            NotificationChannel infoChannel = new NotificationChannel(
                    INFO_CHANNEL_ID, "Safety Updates", NotificationManager.IMPORTANCE_DEFAULT
            );
            infoChannel.setDescription("Notifications for SOS status and history");
            manager.createNotificationChannel(infoChannel);

            // 5. Chat Channel (Incoming Messages)
            NotificationChannel chatChannel = new NotificationChannel(
                    CHAT_CHANNEL_ID, "Messages", NotificationManager.IMPORTANCE_HIGH
            );
            chatChannel.setDescription("Notifications for new chat messages");
            chatChannel.enableVibration(true);
            // Set sound to null because we play it manually to support custom in-app selection
            chatChannel.setSound(null, null);
            manager.createNotificationChannel(chatChannel);
        }
    }
}

