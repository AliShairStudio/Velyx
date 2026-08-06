package com.alert.beacon.location;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

public class LocationHelper {

    private final Context context;
    private final FusedLocationProviderClient fusedLocationClient;

    public interface OnLocationResultListener {
        void onLocationReceived(String locationUrl);
    }

    public LocationHelper(Context context) {
        this.context = context;
        this.fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
    }

    public void getLastLocation(OnLocationResultListener listener) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            listener.onLocationReceived(null);
            return;
        }

        // Check if location services are enabled
        android.location.LocationManager lm = (android.location.LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        boolean gps_enabled = false;
        boolean network_enabled = false;
        try {
            gps_enabled = lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER);
            network_enabled = lm.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER);
        } catch (Exception ignored) {}

        if (!gps_enabled && !network_enabled) {
            listener.onLocationReceived(null);
            return;
        }

        // Try getting last known location first for speed
        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                String url = "https://www.google.com/maps/search/?api=1&query=" + 
                             location.getLatitude() + "," + location.getLongitude();
                listener.onLocationReceived(url);
            } else {
                requestFreshLocation(listener);
            }
        }).addOnFailureListener(e -> requestFreshLocation(listener));
    }

    private void requestFreshLocation(OnLocationResultListener listener) {
        final boolean[] notified = {false};
        
        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
                .setMinUpdateIntervalMillis(2000)
                .setMaxUpdates(1)
                .build();

        LocationCallback callback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                if (notified[0]) return;
                notified[0] = true;
                
                Location location = locationResult.getLastLocation();
                if (location != null) {
                    String url = "https://www.google.com/maps/search/?api=1&query=" + 
                                 location.getLatitude() + "," + location.getLongitude();
                    listener.onLocationReceived(url);
                } else {
                    listener.onLocationReceived(null);
                }
                fusedLocationClient.removeLocationUpdates(this);
            }
        };

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, callback, Looper.getMainLooper());
            
            // Safety timeout: if no location in 8 seconds, send SMS without it
            new android.os.Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (!notified[0]) {
                    notified[0] = true;
                    fusedLocationClient.removeLocationUpdates(callback);
                    listener.onLocationReceived(null);
                }
            }, 8000);
            
        } catch (SecurityException e) {
            if (!notified[0]) {
                notified[0] = true;
                listener.onLocationReceived(null);
            }
        }
    }
}
