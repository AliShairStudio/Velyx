package com.alert.beacon.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.alert.beacon.R;
import com.alert.beacon.data.TriggerManager;
import com.alert.beacon.data.User;
import com.alert.beacon.utils.LoadingUtils;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;
import com.hbb20.CountryCodePicker;

import java.util.ArrayList;
import java.util.List;

public class LoginActivity extends BaseActivity {

    private static final String TAG = "LoginActivity";
    private static final int PERMISSION_REQUEST_CODE = 100;

    private EditText etLoginPhone, etLoginPassword;
    private CountryCodePicker ccpLogin;
    private TriggerManager triggerManager;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private View customLoading;
    private TextView tvLoadingText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        triggerManager = new TriggerManager(this);
        mAuth = FirebaseAuth.getInstance();
        
        try {
            db = FirebaseFirestore.getInstance();
        } catch (Exception e) {
            Log.e(TAG, "Firestore initialization failed", e);
        }

        // Auto-redirect if already logged in
        if (mAuth.getCurrentUser() != null && triggerManager.isLoggedIn()) {
            updateFcmToken(mAuth.getCurrentUser().getUid());
            redirectUserByRole();
            return;
        }

        setContentView(R.layout.activity_login);

        customLoading = findViewById(R.id.customLoading);
        tvLoadingText = findViewById(R.id.tvLoadingText);

        ccpLogin = findViewById(R.id.ccpLogin);
        etLoginPhone = findViewById(R.id.etLoginPhone);
        etLoginPassword = findViewById(R.id.etLoginPassword);
        Button btnLogin = findViewById(R.id.btnLogin);
        TextView tvGoToSignup = findViewById(R.id.tvGoToSignup);

        ccpLogin.registerCarrierNumberEditText(etLoginPhone);

        btnLogin.setOnClickListener(v -> {
            String phoneInput = etLoginPhone.getText().toString().trim();
            String password = etLoginPassword.getText().toString().trim();

            if (TextUtils.isEmpty(phoneInput)) {
                etLoginPhone.setError(getString(R.string.msg_enter_phone_number));
                return;
            }
            if (TextUtils.isEmpty(password)) {
                etLoginPassword.setError(getString(R.string.hint_password));
                return;
            }

            if (!ccpLogin.isValidFullNumber()) {
                showErrorDialog(getString(R.string.msg_invalid_format), getString(R.string.msg_phone_format_error));
                return;
            }

            loginWithPhone(ccpLogin.getFullNumberWithPlus(), password);
        });

        tvGoToSignup.setOnClickListener(v -> startActivity(new Intent(this, SignupActivity.class)));
    }

    private void showLoading(String message) {
        if (customLoading != null) {
            customLoading.setVisibility(View.VISIBLE);
            if (tvLoadingText != null) tvLoadingText.setText(message.toUpperCase());
            LoadingUtils.startCustomAnimation(customLoading);
        }
    }

    private void hideLoading() {
        if (customLoading != null) {
            customLoading.setVisibility(View.GONE);
            LoadingUtils.stopCustomAnimation(customLoading);
        }
    }

    private void loginWithPhone(String phone, String password) {
        showLoading(getString(R.string.msg_verifying_credentials));
        String cleanPhone = phone.replaceAll("\\s+", "");
        String virtualEmail = cleanPhone.replace("+", "") + "@velyx.com";
        Log.d(TAG, "Attempting login for: " + virtualEmail);

        if (mAuth == null) {
            hideLoading();
            showErrorDialog(getString(R.string.msg_login_error), getString(R.string.msg_auth_service_error));
            return;
        }

        mAuth.signInWithEmailAndPassword(virtualEmail, password)
            .addOnCompleteListener(this, task -> {
                if (task.isSuccessful()) {
                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user != null) {
                        fetchUserDataAndRedirect(user.getUid());
                    }
                } else {
                    hideLoading();
                    Exception e = task.getException();
                    Log.e(TAG, "Login failed", e);
                    String errorMsg = e != null ? e.getMessage() : "Authentication failed";
                    
                    if (errorMsg != null && (errorMsg.contains("no user record") || errorMsg.contains("INVALID_LOGIN_CREDENTIALS"))) {
                        showErrorDialog(getString(R.string.msg_account_not_found_title), getString(R.string.msg_account_not_found_desc));
                    } else if (errorMsg != null && (errorMsg.contains("wrong-password") || errorMsg.contains("invalid-credential"))) {
                        showErrorDialog(getString(R.string.msg_login_error), getString(R.string.msg_incorrect_password));
                    } else {
                        showErrorDialog(getString(R.string.msg_login_error), errorMsg);
                    }
                }
            });
    }

    private void showErrorDialog(String title, String message) {
        if (isFinishing() || isDestroyed()) return;
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.btn_ok, null)
                .show();
    }

    private void fetchUserDataAndRedirect(String userId) {
        if (db == null) {
            hideLoading();
            showErrorDialog(getString(R.string.msg_login_error), getString(R.string.msg_db_service_error));
            return;
        }

        db.collection("users").document(userId).get()
            .addOnSuccessListener(documentSnapshot -> {
                hideLoading();
                if (documentSnapshot.exists()) {
                    User user = documentSnapshot.toObject(User.class);
                    if (user != null) {
                        // Save all critical data
                        triggerManager.saveUserId(userId);
                        triggerManager.saveUserName(user.getName());
                        triggerManager.saveUserEmail(user.getEmail());
                        triggerManager.saveUserPhone(user.getPhone());
                        triggerManager.saveUserRole(user.getRole());
                        triggerManager.saveProfileImageUri(user.getProfileImageUri());
                        triggerManager.setLoggedIn(true);

                        updateFcmToken(userId);
                        
                        Toast.makeText(LoginActivity.this, getString(R.string.msg_welcome_back_toast, user.getName()), Toast.LENGTH_SHORT).show();
                        checkAndRequestPermissions();
                    }
                } else {
                    Log.e(TAG, "User data not found in Firestore for UID: " + userId);
                    showErrorDialog(getString(R.string.msg_data_error), getString(R.string.msg_profile_data_not_found));
                }
            })
            .addOnFailureListener(e -> {
                hideLoading();
                Log.e(TAG, "Firestore error: " + e.getMessage());
                showErrorDialog(getString(R.string.msg_server_error), getString(R.string.msg_failed_retrieve_profile, e.getMessage()));
            });
    }

    private void updateFcmToken(String userId) {
        FirebaseMessaging.getInstance().getToken()
            .addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult() != null) {
                    String token = task.getResult();
                    db.collection("users").document(userId)
                        .update("fcmToken", token)
                        .addOnFailureListener(e -> Log.e(TAG, "Failed to update FCM token", e));
                }
            });
    }

    private void redirectUserByRole() {
        String role = triggerManager.getUserRole();
        Intent intent;
        if ("Admin".equalsIgnoreCase(role)) {
            intent = new Intent(this, AdminActivity.class);
        } else {
            intent = new Intent(this, MainActivity.class);
        }
        startActivity(intent);
        finish();
    }

    private void checkAndRequestPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.RECORD_AUDIO);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.SEND_SMS);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.READ_CONTACTS);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        if (permissionsNeeded.isEmpty()) {
            redirectUserByRole();
        } else {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            redirectUserByRole();
        }
    }
}

