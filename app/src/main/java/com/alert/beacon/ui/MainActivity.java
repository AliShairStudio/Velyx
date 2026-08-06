package com.alert.beacon.ui;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.alert.beacon.R;
import com.alert.beacon.data.TriggerManager;
import com.alert.beacon.services.BeaconService;
import com.alert.beacon.services.VelyxAccessibilityService;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.List;

public class MainActivity extends BaseActivity {

    private static final String TAG = "MainActivity";
    private TriggerManager triggerManager;
    private BottomNavigationView bottomNavigationView;
    private ViewPager2 viewPager;
    private DatabaseReference userPresenceRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        triggerManager = new TriggerManager(this);
        
        if (!triggerManager.isLoggedIn()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        viewPager = findViewById(R.id.viewPager);
        bottomNavigationView = findViewById(R.id.bottom_navigation);

        setupViewPager();

        bottomNavigationView.setOnItemSelectedListener(item -> {
            int position = getPositionFromItemId(item.getItemId());
            if (viewPager.getCurrentItem() != position) {
                viewPager.setCurrentItem(position, false);
            }
            animateNavIconPulse(item.getItemId());
            return true;
        });

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                int itemId = getItemIdFromPosition(position);
                if (bottomNavigationView.getSelectedItemId() != itemId) {
                    bottomNavigationView.setSelectedItemId(itemId);
                }
                viewPager.setUserInputEnabled(position != 2);
            }
        });

        int targetId = getIntent().getIntExtra("TARGET_NAV_ID", R.id.nav_home);
        bottomNavigationView.post(() -> {
            if (!isFinishing() && !isDestroyed()) {
                bottomNavigationView.setSelectedItemId(targetId);
            }
        });

        startVelyxService();
        checkBatteryOptimizations();
        checkOverlayPermission();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (bottomNavigationView.getSelectedItemId() != R.id.nav_home) {
                    bottomNavigationView.setSelectedItemId(R.id.nav_home);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        String phone = triggerManager.getUserPhone();
        if (!phone.isEmpty()) {
            userPresenceRef = FirebaseDatabase.getInstance("https://beaconsos-a2dba-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference("users").child(phone).child("online");
            userPresenceRef.setValue(true);
            userPresenceRef.onDisconnect().setValue(false);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (userPresenceRef != null) {
            userPresenceRef.setValue(true);
        }
    }

    private void setupViewPager() {
        viewPager.setOffscreenPageLimit(3);
        FragmentStateAdapter adapter = new FragmentStateAdapter(this) {
            @NonNull
            @Override
            public Fragment createFragment(int position) {
                switch (position) {
                    case 0: return new HomeFragment();
                    case 1: return new ChatFragment();
                    case 2: return new MapFragment();
                    case 3: return new ProfileFragment();
                    default: return new HomeFragment();
                }
            }

            @Override
            public int getItemCount() {
                return 4;
            }
        };
        viewPager.setAdapter(adapter);
    }

    private int getPositionFromItemId(int itemId) {
        if (itemId == R.id.nav_home) return 0;
        if (itemId == R.id.nav_message) return 1;
        if (itemId == R.id.nav_map) return 2;
        if (itemId == R.id.nav_profile) return 3;
        return 0;
    }

    private int getItemIdFromPosition(int position) {
        switch (position) {
            case 0: return R.id.nav_home;
            case 1: return R.id.nav_message;
            case 2: return R.id.nav_map;
            case 3: return R.id.nav_profile;
            default: return R.id.nav_home;
        }
    }

    private void animateNavIconPulse(int itemId) {
        View itemView = bottomNavigationView.findViewById(itemId);
        if (itemView != null) {
            itemView.animate()
                    .scaleX(1.12f)
                    .scaleY(1.12f)
                    .setDuration(250)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .withEndAction(() -> 
                        itemView.animate()
                                .scaleX(1.0f)
                                .scaleY(1.0f)
                                .setDuration(450)
                                .setInterpolator(new OvershootInterpolator(1.5f))
                                .start()
                    ).start();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        int targetId = intent.getIntExtra("TARGET_NAV_ID", R.id.nav_home);
        bottomNavigationView.setSelectedItemId(targetId);
    }

    private void startVelyxService() {
        Intent intent = new Intent(this, BeaconService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!android.provider.Settings.canDrawOverlays(this)) {
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(this, R.style.GlassyDialog)
                        .setTitle("System Shield Required")
                        .setMessage("To ensure Velyx stays active after you swipe it away, please allow the 'Display over other apps' permission.")
                        .setPositiveButton("Go to Settings", (dialog, which) -> {
                            Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:" + getPackageName()));
                            startActivity(intent);
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        }
    }

    private void checkBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.os.PowerManager pm = (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(this, R.style.GlassyDialog)
                        .setTitle("Keep Velyx Active")
                        .setMessage("To ensure Velyx stays running in the background and works when swiped away, please disable battery optimizations.")
                        .setPositiveButton("Go to Settings", (dialog, which) -> {
                            Intent intent = new Intent();
                            intent.setAction(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                            intent.setData(Uri.parse("package:" + getPackageName()));
                            startActivity(intent);
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        }
    }
}
