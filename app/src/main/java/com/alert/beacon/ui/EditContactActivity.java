package com.alert.beacon.ui;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.Toolbar;

import com.alert.beacon.R;
import com.alert.beacon.data.Contact;
import com.alert.beacon.data.TriggerManager;
import com.alert.beacon.utils.LoadingUtils;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class EditContactActivity extends BaseActivity {

    private static final String TAG = "EditContactActivity";
    private EditText etName, etPhone, etRelation;
    private TriggerManager triggerManager;
    private String contactId;
    private FirebaseFirestore db;
    private String userId;
    private View customLoading;
    private TextView tvLoadingText;
    private boolean isEditMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_contact);

        triggerManager = new TriggerManager(this);
        db = FirebaseFirestore.getInstance();
        userId = FirebaseAuth.getInstance().getUid();

        etName = findViewById(R.id.etEditContactName);
        etPhone = findViewById(R.id.etEditPhoneNumber);
        etRelation = findViewById(R.id.etEditRelation);
        Button btnSave = findViewById(R.id.btnUpdateContact);
        Button btnCancel = findViewById(R.id.btnCancelEdit);
        
        customLoading = findViewById(R.id.customLoading);
        tvLoadingText = findViewById(R.id.tvLoadingText);

        // Get data from intent
        contactId = getIntent().getStringExtra("contact_id");
        isEditMode = (contactId != null);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setTitle(isEditMode ? "Edit Contact" : "Add Contact");
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        if (btnSave != null) {
            btnSave.setText(isEditMode ? "UPDATE CONTACT" : "SAVE CONTACT");
        }

        String initialName = getIntent().getStringExtra("contact_name");
        String initialPhone = getIntent().getStringExtra("contact_phone");
        String initialRelation = getIntent().getStringExtra("contact_relation");

        if (etName != null) etName.setText(initialName);
        if (etPhone != null) etPhone.setText(initialPhone);
        if (etRelation != null) etRelation.setText(initialRelation);

        btnSave.setOnClickListener(v -> {
            if (isEditMode) {
                showUpdateConfirmation();
            } else {
                saveNewContact();
            }
        });
        btnCancel.setOnClickListener(v -> finish());
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

    private void showUpdateConfirmation() {
        String name = etName.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();

        if (name.isEmpty() || phone.isEmpty()) {
            Toast.makeText(this, "Please fill all required fields", Toast.LENGTH_SHORT).show();
            return;
        }

        showGlassyConfirmDialog(
                "Confirm Update",
                "Are you sure you want to update this contact?",
                "Update",
                this::updateContact
        );
    }

    private void saveNewContact() {
        String name = etName.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        String relation = etRelation.getText().toString().trim();

        if (name.isEmpty() || phone.isEmpty()) {
            Toast.makeText(this, "Please fill all required fields", Toast.LENGTH_SHORT).show();
            return;
        }

        if (userId == null) return;

        showLoading("Saving Contact");

        com.google.firebase.firestore.DocumentReference newContactRef = db.collection("users").document(userId).collection("contacts").document();
        String newId = newContactRef.getId();
        
        Contact contact = new Contact(newId, name, phone, relation);
        
        newContactRef.set(contact)
            .addOnSuccessListener(aVoid -> {
                hideLoading();
                Toast.makeText(this, "Contact added successfully!", Toast.LENGTH_SHORT).show();
                finish();
            })
            .addOnFailureListener(e -> {
                hideLoading();
                Log.e(TAG, "Save failed", e);
                Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            });
    }

    private void updateContact() {
        String name = etName.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        String relation = etRelation.getText().toString().trim();

        if (userId == null || contactId == null) return;

        showLoading("Updating Contact Info");

        Map<String, Object> contactUpdates = new HashMap<>();
        contactUpdates.put("name", name);
        contactUpdates.put("phoneNumber", phone);
        contactUpdates.put("relation", relation);

        db.collection("users").document(userId).collection("contacts").document(contactId).update(contactUpdates)
            .addOnSuccessListener(aVoid -> {
                hideLoading();
                Toast.makeText(EditContactActivity.this, "Contact updated successfully!", Toast.LENGTH_SHORT).show();
                finish();
            })
            .addOnFailureListener(e -> {
                hideLoading();
                Log.e(TAG, "Update failed", e);
                Toast.makeText(EditContactActivity.this, "Update failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            });
    }
}
