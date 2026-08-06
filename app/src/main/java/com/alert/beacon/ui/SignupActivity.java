package com.alert.beacon.ui;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.alert.beacon.R;
import com.alert.beacon.data.TriggerManager;
import com.alert.beacon.data.User;
import com.alert.beacon.utils.LoadingUtils;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.hbb20.CountryCodePicker;

import java.util.regex.Pattern;

public class SignupActivity extends BaseActivity {

    private static final String TAG = "SignupActivity";

    private EditText etFullName, etPassword, etConfirmPassword, etPhone;
    private TextView ruleNumber, ruleLower, ruleUpper, ruleSpecial, ruleLength, tvMatchError;
    private AutoCompleteTextView roleDropdown;
    private CountryCodePicker ccp;
    private TriggerManager triggerManager;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private View customLoading;
    private TextView tvLoadingText;

    private boolean isPasswordValid = false;
    private boolean isMatchValid = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        triggerManager = new TriggerManager(this);
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        customLoading = findViewById(R.id.customLoading);
        tvLoadingText = findViewById(R.id.tvLoadingText);

        roleDropdown = findViewById(R.id.roleDropdown);
        ccp = findViewById(R.id.ccp);
        etPhone = findViewById(R.id.etPhone);
        etFullName = findViewById(R.id.etFullName);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        
        ruleNumber = findViewById(R.id.ruleNumber);
        ruleLower = findViewById(R.id.ruleLower);
        ruleUpper = findViewById(R.id.ruleUpper);
        ruleSpecial = findViewById(R.id.ruleSpecial);
        ruleLength = findViewById(R.id.ruleLength);
        tvMatchError = findViewById(R.id.tvMatchError);
        
        Button btnRegister = findViewById(R.id.btnRegister);
        TextView tvGoToLogin = findViewById(R.id.tvGoToLogin);

        // Updated roles list: Removed "Guardian"
        String[] roles = {"User", "Admin"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, roles);
        roleDropdown.setAdapter(adapter);
        roleDropdown.setText(roles[0], false); 

        ccp.registerCarrierNumberEditText(etPhone);

        setupPasswordValidation();
        btnRegister.setOnClickListener(v -> handleSignup());

        tvGoToLogin.setOnClickListener(v -> {
            startActivity(new Intent(SignupActivity.this, LoginActivity.class));
            finish();
        });
    }

    private void setupPasswordValidation() {
        etPassword.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                validatePassword(s.toString());
                checkPasswordMatch();
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        etConfirmPassword.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                checkPasswordMatch();
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void validatePassword(String password) {
        boolean hasNum = Pattern.compile("[0-9]").matcher(password).find();
        boolean hasLower = Pattern.compile("[a-z]").matcher(password).find();
        boolean hasUpper = Pattern.compile("[A-Z]").matcher(password).find();
        boolean hasSpecial = Pattern.compile("[!@#$%^&*(),.?\":{}|<>]").matcher(password).find();
        boolean hasLength = password.length() >= 8;

        updateRuleUI(ruleNumber, hasNum, R.string.rule_number_check, R.string.rule_number_error);
        updateRuleUI(ruleLower, hasLower, R.string.rule_lower_check, R.string.rule_lower_error);
        updateRuleUI(ruleUpper, hasUpper, R.string.rule_upper_check, R.string.rule_upper_error);
        updateRuleUI(ruleSpecial, hasSpecial, R.string.rule_special_check, R.string.rule_special_error);
        updateRuleUI(ruleLength, hasLength, R.string.rule_length_check, R.string.rule_length_error);

        isPasswordValid = hasNum && hasLower && hasUpper && hasSpecial && hasLength;
    }

    private void updateRuleUI(TextView textView, boolean isValid, int validResId, int invalidResId) {
        if (isValid) {
            textView.setText(validResId);
            textView.setTextColor(ContextCompat.getColor(this, R.color.success_green));
        } else {
            textView.setText(invalidResId);
            textView.setTextColor(ContextCompat.getColor(this, R.color.error_red));
        }
    }

    private void checkPasswordMatch() {
        String p1 = etPassword.getText().toString();
        String p2 = etConfirmPassword.getText().toString();
        
        if (p2.isEmpty()) {
            tvMatchError.setVisibility(View.GONE);
            isMatchValid = false;
        } else if (p1.equals(p2)) {
            tvMatchError.setVisibility(View.GONE);
            isMatchValid = true;
        } else {
            tvMatchError.setVisibility(View.VISIBLE);
            isMatchValid = false;
        }
    }

    private void handleSignup() {
        if (!isNetworkAvailable()) {
            showErrorDialog(getString(R.string.msg_network_error), getString(R.string.msg_internet_required));
            return;
        }

        String name = etFullName.getText().toString().trim();
        String phoneInput = etPhone.getText().toString().trim();

        if (TextUtils.isEmpty(name)) {
            showErrorDialog(getString(R.string.msg_input_required), getString(R.string.msg_enter_full_name));
            return;
        }

        if (TextUtils.isEmpty(phoneInput)) {
            showErrorDialog(getString(R.string.msg_input_required), getString(R.string.msg_enter_phone_number));
            return;
        }

        if (!ccp.isValidFullNumber()) {
            showErrorDialog(getString(R.string.msg_invalid_format), getString(R.string.msg_phone_format_error));
            return;
        }

        if (!isPasswordValid) {
            showErrorDialog(getString(R.string.msg_password_weak), getString(R.string.msg_password_requirements));
            return;
        }

        if (!isMatchValid) {
            showErrorDialog(getString(R.string.msg_password_mismatch_title), getString(R.string.msg_password_mismatch_desc));
            return;
        }

        registerUser();
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
    }

    private void showErrorDialog(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.btn_dismiss, null)
                .show();
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

    private void registerUser() {
        String name = etFullName.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String phone = ccp.getFullNumberWithPlus().replaceAll("\\s+", "");
        String role = roleDropdown.getText().toString();

        showLoading(getString(R.string.msg_connecting_security));

        String virtualEmail = phone.replace("+", "") + "@velyx.com";
        Log.d(TAG, "Starting Firebase Registration for: " + virtualEmail);

        // Fail-safe timeout increased to 60 seconds
        final android.os.Handler handler = new android.os.Handler();
        final Runnable timeoutRunnable = () -> {
            if (customLoading != null && customLoading.getVisibility() == View.VISIBLE) {
                hideLoading();
                showErrorDialog(getString(R.string.msg_security_timeout_title), getString(R.string.msg_security_timeout_desc));
            }
        };
        handler.postDelayed(timeoutRunnable, 60000);

        mAuth.createUserWithEmailAndPassword(virtualEmail, password)
            .addOnSuccessListener(authResult -> {
                handler.removeCallbacks(timeoutRunnable);
                if (authResult.getUser() != null) {
                    String userId = authResult.getUser().getUid();
                    User user = new User(userId, name, virtualEmail, phone, role, password);

                    db.collection("users").document(userId).set(user)
                        .addOnSuccessListener(aVoid -> {
                            hideLoading();
                            mAuth.signOut();
                            triggerManager.clearData();
                            Toast.makeText(SignupActivity.this, R.string.msg_account_verified, Toast.LENGTH_LONG).show();
                            startActivity(new Intent(SignupActivity.this, LoginActivity.class));
                            finish();
                        })
                        .addOnFailureListener(e -> {
                            hideLoading();
                            showErrorDialog(getString(R.string.msg_database_error), getString(R.string.msg_firestore_failed, e.getMessage()));
                        });
                } else {
                    hideLoading();
                    showErrorDialog(getString(R.string.msg_registration_failed), "Failed to get user ID.");
                }
            })
            .addOnFailureListener(e -> {
                handler.removeCallbacks(timeoutRunnable);
                hideLoading();
                Log.e(TAG, "Firebase Auth Failure", e);
                showErrorDialog(getString(R.string.msg_registration_failed), e.getMessage());
            });
    }
}

