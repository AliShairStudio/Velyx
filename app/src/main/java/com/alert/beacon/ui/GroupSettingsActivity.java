package com.alert.beacon.ui;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alert.beacon.R;
import com.alert.beacon.data.Group;
import com.alert.beacon.data.TriggerManager;
import com.alert.beacon.utils.LoadingUtils;
import com.bumptech.glide.Glide;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.yalantis.ucrop.UCrop;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class GroupSettingsActivity extends BaseActivity {

    private static final String TAG = "GroupSettingsActivity";
    private ImageView ivGroupImageLarge, ivGroupImageSmall;
    private EditText etGroupName;
    private TextInputEditText etGroupDescription;
    private RecyclerView rvMembersSettings;
    private Group currentGroup;
    private String selectedImageUri;
    private DatabaseReference groupRef;
    private String userPhone;
    private View btnAddMember, btnDeleteGroupForEveryone, layoutToolbarCustom, layoutLargeHeader;
    private ValueEventListener groupListener;
    private View customLoading, layoutSaveButton;
    private TextView tvLoadingText, tvGroupNameToolbar, tvGroupNameHeaderDisplay;
    private boolean isInitialLoad = true;

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
                        selectedImageUri = resultUri.toString();
                        updateUIBasedOnPermissions();
                        checkForChanges();
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_settings);

        TriggerManager triggerManager = new TriggerManager(this);
        userPhone = triggerManager.getUserPhone();
        
        String groupId = getIntent().getStringExtra("GROUP_ID");
        if (groupId == null) {
            finish();
            return;
        }
        groupRef = FirebaseDatabase.getInstance("https://beaconsos-a2dba-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference("groups").child(groupId);

        Toolbar toolbar = findViewById(R.id.toolbarGroupSettings);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        toolbar.setTitle("");
        toolbar.setNavigationOnClickListener(v -> finish());

        ivGroupImageLarge = findViewById(R.id.ivGroupImageLarge);
        ivGroupImageSmall = findViewById(R.id.ivGroupImageSmall);
        tvGroupNameToolbar = findViewById(R.id.tvGroupNameToolbar);
        tvGroupNameHeaderDisplay = findViewById(R.id.tvGroupNameHeaderDisplay);
        layoutToolbarCustom = findViewById(R.id.layoutToolbarCustom);
        layoutLargeHeader = findViewById(R.id.layoutLargeHeader);
        etGroupName = findViewById(R.id.etGroupName);
        etGroupDescription = findViewById(R.id.etGroupDescription);
        rvMembersSettings = findViewById(R.id.rvMembersSettings);
        layoutSaveButton = findViewById(R.id.layout_settings_buttons);
        View btnSave = findViewById(R.id.btnSaveGroupSettings);
        btnAddMember = findViewById(R.id.layoutAddMemberHeader);
        View btnExitGroup = findViewById(R.id.layoutExitGroup);
        btnDeleteGroupForEveryone = findViewById(R.id.layoutDeleteGroup);
        
        customLoading = findViewById(R.id.customLoading);
        tvLoadingText = findViewById(R.id.tvLoadingText);

        setupScrollAnimation();
        setupChangeListeners();
        listenForGroupData();

        ivGroupImageLarge.setOnClickListener(v -> {
            boolean isAdmin = Objects.equals(userPhone, currentGroup != null ? currentGroup.getAdminPhone() : null);
            if (isAdmin) {
                showImageOptionsDialog();
            } else {
                openImagePreview();
            }
        });

        btnAddMember.setOnClickListener(v -> showGlassyConfirmDialog(
                "Add Member",
                "Do you want to add a new member to this group?",
                "Add",
                () -> {
                    Intent intent = new Intent(this, AddMemberActivity.class);
                    intent.putExtra("GROUP_ID", groupId);
                    startActivity(intent);
                }
        ));

        btnSave.setOnClickListener(v -> saveChanges());
        
        btnExitGroup.setOnClickListener(v -> showExitConfirmation());

        btnDeleteGroupForEveryone.setOnClickListener(v -> showDeleteConfirmation());
    }

    private void showImageOptionsDialog() {
        String[] options = {"View Profile Photo", "Change Photo"};
        com.google.android.material.dialog.MaterialAlertDialogBuilder builder = 
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(new android.view.ContextThemeWrapper(this, R.style.GlassyDialog));
        builder.setTitle("Group Photo");
        builder.setItems(options, (dialog, which) -> {
            if (which == 0) {
                openImagePreview();
            } else if (which == 1) {
                openImagePicker();
            }
        });
        builder.show();
    }

    private void openImagePreview() {
        if (currentGroup != null && currentGroup.getGroupImageUri() != null && !currentGroup.getGroupImageUri().isEmpty()) {
            Intent intent = new Intent(this, ImagePreviewActivity.class);
            intent.putExtra("IMAGE_URL", currentGroup.getGroupImageUri());
            startActivity(intent);
        } else {
            Toast.makeText(this, "No photo to show", Toast.LENGTH_SHORT).show();
        }
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        imagePickerLauncher.launch(Intent.createChooser(intent, "Select Picture"));
    }

    private void startCrop(Uri sourceUri) {
        String fileName = "crop_group_" + System.currentTimeMillis() + ".jpg";
        Uri destinationUri = Uri.fromFile(new File(getCacheDir(), fileName));

        UCrop.Options options = new UCrop.Options();
        options.setCircleDimmedLayer(true);
        options.setShowCropGrid(false);
        options.setToolbarColor(androidx.core.content.ContextCompat.getColor(this, R.color.primary_blue));
        options.setStatusBarColor(androidx.core.content.ContextCompat.getColor(this, R.color.primary_dark_blue));
        options.setActiveControlsWidgetColor(androidx.core.content.ContextCompat.getColor(this, R.color.primary_blue));

        cropLauncher.launch(UCrop.of(sourceUri, destinationUri)
                .withAspectRatio(1, 1) // Square ratio
                .withOptions(options)
                .getIntent(this));
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

    private void setupScrollAnimation() {
        com.google.android.material.appbar.AppBarLayout appBarLayout = findViewById(R.id.appBarLayout);
        
        appBarLayout.addOnOffsetChangedListener((appBar, verticalOffset) -> {
            float totalRange = appBar.getTotalScrollRange();
            float percentage = (float) Math.abs(verticalOffset) / totalRange;
            
            // 1. Fade out large header content
            if (layoutLargeHeader != null) {
                layoutLargeHeader.setAlpha(1f - (percentage * 1.2f)); // Fade slightly slower
                float scale = 1f - (percentage * 0.2f);
                layoutLargeHeader.setScaleX(scale);
                layoutLargeHeader.setScaleY(scale);
            }
            
            // 2. Animate Toolbar elements
            if (layoutToolbarCustom != null) {
                if (percentage > 0.4f) {
                    float progress = (percentage - 0.4f) / 0.6f;
                    
                    // Group Pic and Name slide in horizontally from the left
                    layoutToolbarCustom.setAlpha(progress);
                    layoutToolbarCustom.setTranslationX(-50f * (1f - progress)); 
                } else {
                    layoutToolbarCustom.setAlpha(0f);
                    layoutToolbarCustom.setTranslationX(-50f);
                }
            }
        });
    }

    private void listenForGroupData() {
        showLoading("Loading Group Profile");
        groupListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                hideLoading();
                if (isFinishing() || isDestroyed()) return;
                
                currentGroup = new Group();
                currentGroup.setId(snapshot.getKey());
                currentGroup.setName(snapshot.child("name").getValue(String.class));
                currentGroup.setDescription(snapshot.child("description").getValue(String.class));
                currentGroup.setAdminPhone(snapshot.child("adminPhone").getValue(String.class));
                currentGroup.setGroupImageUri(snapshot.child("groupImageUri").getValue(String.class));

                // Manual memberPhones parsing
                List<String> members = new ArrayList<>();
                DataSnapshot membersSnapshot = snapshot.child("memberPhones");
                if (membersSnapshot.exists()) {
                    for (DataSnapshot member : membersSnapshot.getChildren()) {
                        String phone = member.getValue(String.class);
                        if (phone != null) members.add(phone);
                    }
                }
                currentGroup.setMemberPhones(members);
                
                if (currentGroup != null) {
                    if (tvGroupNameToolbar != null) {
                        tvGroupNameToolbar.setText(currentGroup.getName());
                    }
                    if (tvGroupNameHeaderDisplay != null) {
                        tvGroupNameHeaderDisplay.setText(currentGroup.getName());
                    }
                    isInitialLoad = true;
                    updateUIBasedOnPermissions();
                    isInitialLoad = false;
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                hideLoading();
                if (!isFinishing() && !isDestroyed()) {
                    Toast.makeText(GroupSettingsActivity.this, "Failed to load data", Toast.LENGTH_SHORT).show();
                }
            }
        };
        groupRef.addValueEventListener(groupListener);
    }

    private void updateUIBasedOnPermissions() {
        if (currentGroup == null || isFinishing() || isDestroyed()) return;

        boolean isAdmin = Objects.equals(userPhone, currentGroup.getAdminPhone());

        // Use setText if content changed to avoid losing cursor position while typing (if applicable)
        String currentName = etGroupName.getText() != null ? etGroupName.getText().toString() : "";
        if (!Objects.equals(currentName, currentGroup.getName())) {
            etGroupName.setText(currentGroup.getName());
        }

        if (tvGroupNameHeaderDisplay != null) {
            tvGroupNameHeaderDisplay.setText(currentGroup.getName());
        }
        
        String currentDesc = etGroupDescription.getText() != null ? etGroupDescription.getText().toString() : "";
        if (currentGroup.getDescription() != null && !Objects.equals(currentDesc, currentGroup.getDescription())) {
            etGroupDescription.setText(currentGroup.getDescription());
        }
        
        // Use selectedImageUri if user just picked a photo, otherwise use DB URI
        String imageUri = (selectedImageUri != null) ? selectedImageUri : currentGroup.getGroupImageUri();
        
        loadGroupImage(imageUri, ivGroupImageLarge, ivGroupImageSmall);

        // Admin Only Permissions
        etGroupName.setEnabled(isAdmin);
        etGroupDescription.setEnabled(isAdmin);
        
        // Save button handled by change listener now
        if (layoutSaveButton != null) layoutSaveButton.setVisibility(View.GONE);
        
        btnAddMember.setVisibility(isAdmin ? View.VISIBLE : View.GONE);
        btnDeleteGroupForEveryone.setVisibility(isAdmin ? View.VISIBLE : View.GONE);
        
        setupMembersList(isAdmin);
    }

    private void loadGroupImage(String imageSource, ImageView largeView, ImageView smallView) {
        if (imageSource == null || imageSource.isEmpty()) {
            if (largeView != null) largeView.setImageResource(R.drawable.profile_pic);
            if (smallView != null) smallView.setImageResource(R.drawable.profile_pic);
            return;
        }

        if (imageSource.startsWith("data:image") || imageSource.length() > 500) {
            try {
                String base64Data = imageSource.contains(",") ? imageSource.substring(imageSource.indexOf(",") + 1) : imageSource;
                byte[] decodedString = Base64.decode(base64Data, Base64.DEFAULT);
                Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                if (largeView != null) Glide.with(this).load(decodedByte).into(largeView);
                if (smallView != null) Glide.with(this).load(decodedByte).circleCrop().into(smallView);
            } catch (Exception e) {
                if (largeView != null) largeView.setImageResource(R.drawable.profile_pic);
                if (smallView != null) smallView.setImageResource(R.drawable.profile_pic);
            }
        } else {
            if (largeView != null) Glide.with(this).load(imageSource).placeholder(R.drawable.profile_pic).into(largeView);
            if (smallView != null) Glide.with(this).load(imageSource).placeholder(R.drawable.profile_pic).circleCrop().into(smallView);
        }
    }

    private void setupChangeListeners() {
        android.text.TextWatcher watcher = new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                if (!isInitialLoad) checkForChanges();
            }
        };

        etGroupName.addTextChangedListener(watcher);
        etGroupDescription.addTextChangedListener(watcher);
    }

    private void checkForChanges() {
        if (currentGroup == null) return;

        String name = etGroupName.getText() != null ? etGroupName.getText().toString().trim() : "";
        String desc = etGroupDescription.getText() != null ? etGroupDescription.getText().toString().trim() : "";

        boolean nameChanged = !Objects.equals(name, currentGroup.getName());
        boolean descChanged = !Objects.equals(desc, currentGroup.getDescription());
        boolean imageChanged = selectedImageUri != null;

        boolean hasChanges = (nameChanged || descChanged || imageChanged) && Objects.equals(userPhone, currentGroup.getAdminPhone());

        if (layoutSaveButton != null) {
            layoutSaveButton.setVisibility(hasChanges ? View.VISIBLE : View.GONE);
        }
    }

    private void setupMembersList(boolean isAdmin) {
        if (currentGroup == null || isFinishing() || isDestroyed()) return;

        MemberAdapter memberAdapter = new MemberAdapter(currentGroup.getMemberPhones(), isAdmin, userPhone, currentGroup.getAdminPhone(), this::showRemoveMemberConfirmation);
        rvMembersSettings.setLayoutManager(new LinearLayoutManager(this));
        rvMembersSettings.setAdapter(memberAdapter);
    }

    private void showRemoveMemberConfirmation(String memberPhone, int position) {
        showGlassyConfirmDialog("Remove Member", 
            "Are you sure you want to remove " + memberPhone + " from the group?", 
            "Remove", () -> {
                if (currentGroup != null && currentGroup.getMemberPhones() != null) {
                    showLoading("Updating Members");
                    currentGroup.getMemberPhones().remove(position);
                    groupRef.child("memberPhones").setValue(currentGroup.getMemberPhones()).addOnSuccessListener(aVoid -> {
                        hideLoading();
                        Toast.makeText(GroupSettingsActivity.this, "Member removed", Toast.LENGTH_SHORT).show();
                    }).addOnFailureListener(e -> {
                        hideLoading();
                        Toast.makeText(GroupSettingsActivity.this, "Failed to remove member", Toast.LENGTH_SHORT).show();
                    });
                }
            });
    }

    private void saveChanges() {
        if (currentGroup == null) return;

        String name = etGroupName.getText() != null ? etGroupName.getText().toString().trim() : "";
        if (name.isEmpty()) {
            Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }

        String desc = etGroupDescription.getText() != null ? etGroupDescription.getText().toString().trim() : "";

        if (selectedImageUri != null && !selectedImageUri.startsWith("http") && !selectedImageUri.startsWith("data:image")) {
            // New local image selected
            uploadImageAndSave(name, desc);
        } else {
            // No new image or already encoded/uploaded, just update database
            updateDatabase(name, desc, currentGroup.getGroupImageUri());
        }
    }

    private void uploadImageAndSave(String name, String desc) {
        showLoading("Processing Image");
        
        new Thread(() -> {
            String base64Image = encodeImageToBase64(Uri.parse(selectedImageUri));
            
            runOnUiThread(() -> {
                if (base64Image == null) {
                    hideLoading();
                    Toast.makeText(this, "Failed to process image", Toast.LENGTH_SHORT).show();
                    return;
                }
                updateDatabase(name, desc, base64Image);
            });
        }).start();
    }

    private String encodeImageToBase64(Uri imageUri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(imageUri);
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            if (bitmap == null) return null;

            // Resize for group image (Small size for DB string optimization)
            int maxSize = 150; // Optimized for 52dp list icons
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            float ratio = (float) width / (float) height;
            
            int finalWidth, finalHeight;
            if (ratio > 1) {
                finalWidth = maxSize;
                finalHeight = (int) (maxSize / ratio);
            } else {
                finalHeight = maxSize;
                finalWidth = (int) (maxSize * ratio);
            }
            
            Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, finalWidth, finalHeight, true);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 60, baos); // 60% quality is plenty for 52dp
            byte[] b = baos.toByteArray();
            return "data:image/jpeg;base64," + Base64.encodeToString(b, Base64.NO_WRAP);
        } catch (Exception e) {
            Log.e(TAG, "Base64 conversion failed", e);
            return null;
        }
    }

    private void updateDatabase(String name, String desc, String imageUrl) {
        showLoading("Updating Group Settings");
        
        Map<String, Object> updates = new HashMap<>();
        updates.put("name", name);
        updates.put("description", desc);
        updates.put("groupImageUri", imageUrl);
        
        groupRef.updateChildren(updates).addOnSuccessListener(aVoid -> {
            hideLoading();
            if (!isFinishing() && !isDestroyed()) {
                Toast.makeText(this, "Group updated", Toast.LENGTH_SHORT).show();
                finish();
            }
        }).addOnFailureListener(e -> {
            hideLoading();
            Toast.makeText(this, "Failed to update database", Toast.LENGTH_SHORT).show();
        });
    }

    private void showExitConfirmation() {
        showGlassyConfirmDialog("Exit Group", 
            "Are you sure you want to leave this group?", 
            "Exit", this::exitGroup);
    }

    private void showDeleteConfirmation() {
        showGlassyConfirmDialog("Delete Group", 
            "Are you sure you want to delete this group for everyone? This action cannot be undone.", 
            "Delete", this::deleteGroupForEveryone);
    }

    private void deleteGroupForEveryone() {
        if (groupRef == null) return;
        showLoading("Deleting Group");
        groupRef.removeValue().addOnSuccessListener(aVoid -> {
            hideLoading();
            if (!isFinishing() && !isDestroyed()) {
                Toast.makeText(this, "Group deleted for everyone", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                intent.putExtra("TARGET_NAV_ID", R.id.nav_message); // Ensure we return to chat tab
                startActivity(intent);
                finish();
            }
        }).addOnFailureListener(e -> hideLoading());
    }

    private void exitGroup() {
        if (currentGroup == null) return;
        
        showLoading("Exiting Group");
        
        currentGroup.getMemberPhones().remove(userPhone);
        
        // If the admin is leaving, assign a new admin if members still exist
        if (userPhone != null && userPhone.equals(currentGroup.getAdminPhone())) {
            if (!currentGroup.getMemberPhones().isEmpty()) {
                currentGroup.setAdminPhone(currentGroup.getMemberPhones().get(0));
            } else {
                // If no members left, delete group
                groupRef.removeValue().addOnSuccessListener(aVoid -> {
                    hideLoading();
                    if (!isFinishing() && !isDestroyed()) {
                        Intent intent = new Intent(this, MainActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        intent.putExtra("TARGET_NAV_ID", R.id.nav_message);
                        startActivity(intent);
                        finish();
                    }
                }).addOnFailureListener(e -> hideLoading());
                return;
            }
        }
        
        groupRef.setValue(currentGroup).addOnSuccessListener(aVoid -> {
            hideLoading();
            if (!isFinishing() && !isDestroyed()) {
                Toast.makeText(this, "You left the group", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                intent.putExtra("TARGET_NAV_ID", R.id.nav_message); // Ensure we return to chat tab
                startActivity(intent);
                finish();
            }
        }).addOnFailureListener(e -> hideLoading());
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            View v = getCurrentFocus();
            if (v instanceof EditText) {
                Rect outRect = new Rect();
                v.getGlobalVisibleRect(outRect);
                if (!outRect.contains((int)event.getRawX(), (int)event.getRawY())) {
                    v.clearFocus();
                    InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                    }
                }
            }
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (groupRef != null && groupListener != null) {
            groupRef.removeEventListener(groupListener);
        }
    }
}
