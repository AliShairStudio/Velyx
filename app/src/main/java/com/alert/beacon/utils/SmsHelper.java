package com.alert.beacon.utils;

import android.content.Context;
import android.os.Build;
import android.telephony.SmsManager;
import android.util.Log;

import java.util.ArrayList;

public class SmsHelper {
    private static final String TAG = "SmsHelper";
    private final Context context;

    public SmsHelper(Context context) {
        this.context = context;
    }

    public void sendSms(String phoneNumber, String message) {
        try {
            SmsManager smsManager;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                smsManager = context.getSystemService(SmsManager.class);
            } else {
                smsManager = SmsManager.getDefault();
            }

            if (smsManager == null) {
                Log.e(TAG, "SmsManager is null");
                return;
            }

            ArrayList<String> parts = smsManager.divideMessage(message);
            if (parts.size() > 1) {
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null);
            } else {
                smsManager.sendTextMessage(phoneNumber, null, message, null, null);
            }
            Log.d(TAG, "SMS sent to " + phoneNumber);
        } catch (Exception e) {
            Log.e(TAG, "Failed to send SMS: " + e.getMessage());
        }
    }
}
