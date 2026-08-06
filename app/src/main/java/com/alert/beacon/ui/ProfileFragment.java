package com.alert.beacon.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.alert.beacon.R;
import com.alert.beacon.data.TriggerManager;
import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;

public class ProfileFragment extends Fragment {

    private ImageView ivProfilePic;
    private TextView tvName, tvPhone;
    private TriggerManager triggerManager;
    private FirebaseAuth mAuth;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        triggerManager = new TriggerManager(requireContext());
        mAuth = FirebaseAuth.getInstance();

        ivProfilePic = view.findViewById(R.id.ivProfilePic);
        tvName = view.findViewById(R.id.tvProfileName);
        tvPhone = view.findViewById(R.id.tvProfilePhone);

        View btnLogout = view.findViewById(R.id.btnLogout);

        View optionEditProfile = view.findViewById(R.id.optionEditProfile);
        View optionPermissions = view.findViewById(R.id.optionPermissions);
        View optionChangePassword = view.findViewById(R.id.optionChangePassword);
        View optionSettings = view.findViewById(R.id.optionSettings);
        View optionFeedback = view.findViewById(R.id.optionFeedback);
        View optionAbout = view.findViewById(R.id.optionAbout);
        View optionSafetyCheck = view.findViewById(R.id.optionSafetyCheck);

        optionEditProfile.setOnClickListener(v -> startActivity(new Intent(requireContext(), EditProfileActivity.class)));
        
        optionPermissions.setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), PermissionsActivity.class));
        });

        optionChangePassword.setOnClickListener(v -> startActivity(new Intent(requireContext(), ChangePasswordActivity.class)));
        optionSettings.setOnClickListener(v -> startActivity(new Intent(requireContext(), SettingsActivity.class)));
        optionSafetyCheck.setOnClickListener(v -> startActivity(new Intent(requireContext(), SafetyCheckActivity.class)));
        
        optionFeedback.setOnClickListener(v -> {
            Intent emailIntent = new Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", "support@velyx.com", null));
            emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Velyx Feedback");
            try {
                startActivity(Intent.createChooser(emailIntent, "Send feedback..."));
            } catch (Exception e) {
                Toast.makeText(requireContext(), "No email client found", Toast.LENGTH_SHORT).show();
            }
        });
        
        optionAbout.setOnClickListener(v -> Toast.makeText(requireContext(), "Velyx v1.0.0\nYour Safety, Our Priority.", Toast.LENGTH_LONG).show());

        btnLogout.setOnClickListener(v -> {
            mAuth.signOut();
            triggerManager.clearData();
            Intent intent = new Intent(requireContext(), LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            requireActivity().finish();
        });

        loadUserData();

        return view;
    }

    @Override
    public void onResume() {
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
}

