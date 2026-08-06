package com.alert.beacon.ui;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.alert.beacon.R;
import com.alert.beacon.data.TriggerManager;
import com.alert.beacon.utils.LoadingUtils;
import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.hbb20.CountryCodePicker;
import com.yalantis.ucrop.UCrop;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class EditProfileActivity extends BaseActivity {

    private static final String TAG = "EditProfileActivity";
    private EditText etName, etPhone;
    private ImageView ivEditProfilePic;
    private CountryCodePicker ccp;
    private TriggerManager triggerManager;
    private FirebaseFirestore db;
    private DatabaseReference mDatabase;
    private String userId;
    private View customLoading;
    private TextView tvLoadingText;
    private Uri selectedImageUri;

    private final ActivityResultLauncher<Intent> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null && result.getData().getData() != null) {
                    Uri imageUri = result.getData().getData();
                    startCrop(imageUri);
                }
            }
    );

    private final ActivityResultLauncher<Intent> cropLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri resultUri = UCrop.getOutput(result.getData());
                    if (resultUri != null) {
                        selectedImageUri = resultUri;
                        loadProfileImage(selectedImageUri.toString(), ivEditProfilePic);
                    }
                } else if (result.getResultCode() == UCrop.RESULT_ERROR) {
                    Intent data = result.getData();
                    final Throwable cropError = data != null ? UCrop.getError(data) : new Throwable("Unknown crop error");
                    Log.e(TAG, "Crop error", cropError);
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        triggerManager = new TriggerManager(this);
        db = FirebaseFirestore.getInstance();
        mDatabase = FirebaseDatabase.getInstance("https://beaconsos-a2dba-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference();
        
        userId = FirebaseAuth.getInstance().getUid();
        if (userId == null) {
            Toast.makeText(this, R.string.msg_session_expired, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.edit_profile_title);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        ivEditProfilePic = findViewById(R.id.ivEditProfilePic);
        etName = findViewById(R.id.etEditName);
        etPhone = findViewById(R.id.etEditPhone);
        ccp = findViewById(R.id.ccpEditProfile);
        
        customLoading = findViewById(R.id.customLoading);
        tvLoadingText = findViewById(R.id.tvLoadingText);

        ccp.registerCarrierNumberEditText(etPhone);
        loadCurrentData();

        ivEditProfilePic.setOnClickListener(v -> showImageOptionsDialog());
        
        View btnSave = findViewById(R.id.btnSaveProfile);
        if (btnSave != null) {
            btnSave.setOnClickListener(v -> handleSaveProfile());
        }
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

    private void loadCurrentData() {
        etName.setText(triggerManager.getUserName());
        String fullPhone = triggerManager.getUserPhone();
        if (fullPhone != null && !fullPhone.isEmpty()) {
            ccp.setFullNumber(fullPhone);
        }

        loadProfileImage(triggerManager.getProfileImageUri(), ivEditProfilePic);
    }

    private void openFileChooser() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        imagePickerLauncher.launch(Intent.createChooser(intent, "Select Picture"));
    }

    private void startCrop(Uri sourceUri) {
        String fileName = "crop_" + System.currentTimeMillis() + ".jpg";
        Uri destinationUri = Uri.fromFile(new File(getCacheDir(), fileName));

        UCrop.Options options = new UCrop.Options();
        options.setCircleDimmedLayer(true);
        options.setShowCropGrid(false);
        options.setToolbarColor(ContextCompat.getColor(this, R.color.primary_blue)); 
        options.setStatusBarColor(ContextCompat.getColor(this, R.color.primary_dark_blue));
        options.setActiveControlsWidgetColor(ContextCompat.getColor(this, R.color.primary_blue));
        
        Intent cropIntent = UCrop.of(sourceUri, destinationUri)
                .withAspectRatio(1, 1)
                .withOptions(options)
                .getIntent(this);
        cropLauncher.launch(cropIntent);
    }

    private String encodeImageToBase64(Uri imageUri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(imageUri);
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            if (bitmap == null) return null;

            int maxSize = 300;
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            float ratio = (float) width / (float) height;
            if (ratio > 1) {
                width = maxSize;
                height = (int) (width / ratio);
            } else {
                height = maxSize;
                width = (int) (height * ratio);
            }
            Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos);
            byte[] b = baos.toByteArray();
            return "data:image/jpeg;base64," + Base64.encodeToString(b, Base64.DEFAULT);
        } catch (Exception e) {
            Log.e(TAG, "Base64 conversion failed", e);
            return null;
        }
    }

    private void loadProfileImage(String imageSource, ImageView imageView) {
        if (imageSource == null || imageSource.isEmpty()) {
            imageView.setImageResource(R.drawable.profile_pic);
            return;
        }

        if (imageSource.startsWith("data:image") || imageSource.length() > 500) {
            try {
                String base64Data = imageSource.contains(",") ? imageSource.substring(imageSource.indexOf(",") + 1) : imageSource;
                byte[] decodedString = Base64.decode(base64Data, Base64.DEFAULT);
                Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                Glide.with(this).load(decodedByte).circleCrop().into(imageView);
            } catch (Exception e) {
                imageView.setImageResource(R.drawable.profile_pic);
            }
        } else {
            Glide.with(this).load(imageSource).placeholder(R.drawable.profile_pic).circleCrop().into(imageView);
        }
    }

    private void handleSaveProfile() {
        final String newName = etName.getText().toString().trim();
        final String newPhone = ccp.getFullNumberWithPlus().replaceAll("\\s+", "");

        if (newName.isEmpty() || etPhone.getText().toString().trim().isEmpty()) {
            Toast.makeText(this, R.string.error_fill_all_fields, Toast.LENGTH_SHORT).show();
            return;
        }

        if (!ccp.isValidFullNumber()) {
            Toast.makeText(this, R.string.error_invalid_phone, Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedImageUri != null) {
            uploadImageAndSave(newName, newPhone);
        } else {
            updateProfileInFirestore(newName, newPhone, null);
        }
    }

    private void uploadImageAndSave(String name, String phone) {
        showLoading(getString(R.string.msg_saving_profile));

        new Thread(() -> {
            String base64Image = encodeImageToBase64(selectedImageUri);
            runOnUiThread(() -> {
                if (base64Image == null) {
                    hideLoading();
                    Toast.makeText(this, "Failed to process image", Toast.LENGTH_SHORT).show();
                    return;
                }
                updateProfileInFirestore(name, phone, base64Image);
            });
        }).start();
    }

    private void updateProfileInFirestore(String name, String phone, String base64Image) {
        if (base64Image == null) showLoading(getString(R.string.msg_saving_profile));
        
        String cleanPhoneNoPlus = phone.replace("+", "").replaceAll("\\s+", "");
        String virtualEmail = cleanPhoneNoPlus + "@velyx.com";
        
        Map<String, Object> updates = new HashMap<>();
        updates.put("name", name);
        updates.put("phone", phone);
        updates.put("email", virtualEmail);
        if (base64Image != null) {
            updates.put("profileImageUri", base64Image);
        }

        db.collection("users").document(userId).set(updates, SetOptions.merge())
            .addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    Map<String, Object> rtdbUpdates = new HashMap<>();
                    rtdbUpdates.put("name", name);
                    rtdbUpdates.put("phone", phone);
                    rtdbUpdates.put("id", userId);
                    
                    String profileImageUri = (base64Image != null) ? base64Image : triggerManager.getProfileImageUri();
                    if (profileImageUri != null && !profileImageUri.isEmpty()) {
                        rtdbUpdates.put("profileImageUri", profileImageUri);
                    }

                    mDatabase.child("users").child(phone).updateChildren(rtdbUpdates).addOnCompleteListener(rtdbTask -> {
                        hideLoading();
                        triggerManager.saveUserName(name);
                        triggerManager.saveUserPhone(phone);
                        if (base64Image != null) {
                            triggerManager.saveProfileImageUri(base64Image);
                        }
                        Toast.makeText(EditProfileActivity.this, R.string.msg_profile_updated, Toast.LENGTH_SHORT).show();
                        finish();
                    });
                } else {
                    hideLoading();
                    Log.e(TAG, "Firestore update failed", task.getException());
                    Toast.makeText(EditProfileActivity.this, R.string.msg_db_update_failed, Toast.LENGTH_SHORT).show();
                }
            });
    }

    private void showImageOptionsDialog() {
        String[] options = {"View Profile", "Change Profile"};
        com.google.android.material.dialog.MaterialAlertDialogBuilder builder =
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(new android.view.ContextThemeWrapper(this, R.style.GlassyDialog));
        builder.setTitle("Profile Photo");
        builder.setItems(options, (dialog, which) -> {
            if (which == 0) {
                openImagePreview();
            } else if (which == 1) {
                openFileChooser();
            }
        });
        builder.show();
    }

    private void openImagePreview() {
        String imageUri = (selectedImageUri != null) ? selectedImageUri.toString() : triggerManager.getProfileImageUri();
        if (imageUri != null && !imageUri.isEmpty()) {
            Intent intent = new Intent(this, ImagePreviewActivity.class);
            intent.putExtra("IMAGE_URL", imageUri);
            startActivity(intent);
        } else {
            Toast.makeText(this, "No profile photo to show", Toast.LENGTH_SHORT).show();
        }
    }
}
