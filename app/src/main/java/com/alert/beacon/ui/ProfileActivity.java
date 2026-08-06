package com.alert.beacon.ui;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.alert.beacon.R;
import com.alert.beacon.data.TriggerManager;
import com.bumptech.glide.Glide;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;

public class ProfileActivity extends BaseActivity {

    private ImageView ivProfilePic;
    private TextView tvName, tvPhone;
    private TriggerManager triggerManager;
    private FirebaseAuth mAuth;
    private BottomNavigationView bottomNavigationView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        triggerManager = new TriggerManager(this);
        mAuth = FirebaseAuth.getInstance();

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        ivProfilePic = findViewById(R.id.ivProfilePic);
        tvName = findViewById(R.id.tvProfileName);
        tvPhone = findViewById(R.id.tvProfilePhone);
        Button btnLogout = findViewById(R.id.btnLogout);
        
        LinearLayout optionEditProfile = findViewById(R.id.optionEditProfile);
        LinearLayout optionPermissions = findViewById(R.id.optionPermissions);
        LinearLayout optionChangePassword = findViewById(R.id.optionChangePassword);
        LinearLayout optionSettings = findViewById(R.id.optionSettings);
        LinearLayout optionFeedback = findViewById(R.id.optionFeedback);
        LinearLayout optionAbout = findViewById(R.id.optionAbout);

        bottomNavigationView = findViewById(R.id.bottom_navigation);

        optionEditProfile.setOnClickListener(v -> startActivity(new Intent(this, EditProfileActivity.class)));
        
        optionPermissions.setOnClickListener(v -> {
            Intent intent = new Intent(this, PermissionsActivity.class);
            startActivity(intent);
        });

        optionChangePassword.setOnClickListener(v -> startActivity(new Intent(this, ChangePasswordActivity.class)));
        optionSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        
        optionFeedback.setOnClickListener(v -> {
            Intent emailIntent = new Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", "support@velyx.com", null));
            emailIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name) + " Feedback");
            try {
                startActivity(Intent.createChooser(emailIntent, "Send feedback..."));
            } catch (Exception e) {
                Toast.makeText(this, "No email client found", Toast.LENGTH_SHORT).show();
            }
        });
        
        optionAbout.setOnClickListener(v -> Toast.makeText(this, getString(R.string.app_name) + " " + getString(R.string.app_version) + "\n" + getString(R.string.tagline), Toast.LENGTH_LONG).show());

        btnLogout.setOnClickListener(v -> {
            mAuth.signOut();
            triggerManager.clearData();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        ivProfilePic.setOnClickListener(v -> showProfileImageOptions());

        bottomNavigationView.setSelectedItemId(R.id.nav_profile);
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            animateNavIconPulse(itemId);
            
            if (itemId == R.id.nav_home) {
                Intent intent = new Intent(this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish();
                return true;
            } else if (itemId == R.id.nav_map || itemId == R.id.nav_message) {
                loadFragmentIntoMain(itemId);
                return true;
            }
            return itemId == R.id.nav_profile;
        });
    }

    private void animateNavIconPulse(int itemId) {
        View itemView = bottomNavigationView.findViewById(itemId);
        if (itemView != null) {
            // Slower, "slighter" pulse animation
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
    protected void onResume() {
        super.onResume();
        loadUserData();
    }

    private void loadUserData() {
        if (tvName != null) tvName.setText(triggerManager.getUserName());
        if (tvPhone != null) tvPhone.setText(triggerManager.getUserPhone());

        String imageSource = triggerManager.getProfileImageUri();
        if (ivProfilePic != null) {
            if (imageSource == null || imageSource.isEmpty()) {
                ivProfilePic.setImageResource(R.drawable.profile_pic);
            } else if (imageSource.startsWith("data:image") || imageSource.length() > 500) {
                try {
                    String base64Data = imageSource.contains(",") ? imageSource.substring(imageSource.indexOf(",") + 1) : imageSource;
                    byte[] decodedString = Base64.decode(base64Data, Base64.DEFAULT);
                    Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                    Glide.with(this).load(decodedByte).circleCrop().into(ivProfilePic);
                } catch (Exception e) {
                    ivProfilePic.setImageResource(R.drawable.profile_pic);
                }
            } else {
                Glide.with(this)
                        .load(Uri.parse(imageSource))
                        .placeholder(R.drawable.profile_pic)
                        .error(R.drawable.profile_pic)
                        .circleCrop()
                        .into(ivProfilePic);
            }
        }
    }

    private void loadFragmentIntoMain(int itemId) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("TARGET_NAV_ID", itemId);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void showProfileImageOptions() {
        String[] options = {"View Profile", "Change Profile"};
        com.google.android.material.dialog.MaterialAlertDialogBuilder builder =
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(new android.view.ContextThemeWrapper(this, R.style.GlassyDialog));
        builder.setTitle("Profile Photo");
        builder.setItems(options, (dialog, which) -> {
            if (which == 0) {
                openImagePreview();
            } else if (which == 1) {
                startActivity(new Intent(this, EditProfileActivity.class));
            }
        });
        builder.show();
    }

    private void openImagePreview() {
        String imageUri = triggerManager.getProfileImageUri();
        if (imageUri != null && !imageUri.isEmpty()) {
            Intent intent = new Intent(this, ImagePreviewActivity.class);
            intent.putExtra("IMAGE_URL", imageUri);
            startActivity(intent);
        } else {
            Toast.makeText(this, "No profile photo to show", Toast.LENGTH_SHORT).show();
        }
    }
}

