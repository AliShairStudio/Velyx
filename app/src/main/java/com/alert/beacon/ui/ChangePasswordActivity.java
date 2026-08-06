package com.alert.beacon.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.alert.beacon.R;
import com.alert.beacon.data.TriggerManager;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class ChangePasswordActivity extends BaseActivity {

    private EditText etCurrent, etNew, etConfirm;
    private LinearLayout btnUpdate;
    private TextView btnForgot;
    private View customLoading;
    private TextView tvLoadingText;
    private TriggerManager triggerManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_password);

        triggerManager = new TriggerManager(this);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        etCurrent = findViewById(R.id.etCurrentPassword);
        etNew = findViewById(R.id.etNewPassword);
        etConfirm = findViewById(R.id.etConfirmNewPassword);
        btnUpdate = findViewById(R.id.btnUpdatePassword);
        btnForgot = findViewById(R.id.btnForgotPassword);
        
        customLoading = findViewById(R.id.customLoading);
        tvLoadingText = findViewById(R.id.tvLoadingText);

        btnUpdate.setOnClickListener(v -> {
            String current = etCurrent.getText().toString();
            String newPass = etNew.getText().toString();
            String confirm = etConfirm.getText().toString();

            if (current.isEmpty() || newPass.isEmpty() || confirm.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!current.equals(triggerManager.getUserPassword())) {
                Toast.makeText(this, "Incorrect current password", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!newPass.equals(confirm)) {
                Toast.makeText(this, "New passwords do not match", Toast.LENGTH_SHORT).show();
                return;
            }
            
            updatePasswordInFirebase(newPass);
        });

        btnForgot.setOnClickListener(v -> {
            Toast.makeText(this, "Verification email sent to: " + triggerManager.getUserEmail(), Toast.LENGTH_LONG).show();
        });
    }

    private void updatePasswordInFirebase(String newPassword) {
        String phone = triggerManager.getUserPhone();
        if (phone.isEmpty()) {
            Toast.makeText(this, "User session error", Toast.LENGTH_SHORT).show();
            return;
        }

        showLoading("Updating Password...");
        
        DatabaseReference userRef = FirebaseDatabase.getInstance("https://beaconsos-a2dba-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference("users").child(phone);
        userRef.child("password").setValue(newPassword).addOnCompleteListener(task -> {
            hideLoading();
            if (task.isSuccessful()) {
                // Update local storage for future logins
                triggerManager.saveUserPassword(newPassword);
                Toast.makeText(this, "Password updated successfully!", Toast.LENGTH_SHORT).show();
                finish();
            } else {
                Toast.makeText(this, "Failed to update password in server", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showLoading(String message) {
        if (customLoading != null) {
            customLoading.setVisibility(View.VISIBLE);
            if (tvLoadingText != null) tvLoadingText.setText(message);
        }
    }

    private void hideLoading() {
        if (customLoading != null) {
            customLoading.setVisibility(View.GONE);
        }
    }
}
