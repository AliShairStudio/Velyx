package com.alert.beacon.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.widget.ImageButton;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import com.alert.beacon.R;
import com.bumptech.glide.Glide;

public class ImagePreviewActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_preview);

        ImageView ivFullImage = findViewById(R.id.ivFullImage);
        ImageButton btnClose = findViewById(R.id.btnClose);

        String imageSource = getIntent().getStringExtra("IMAGE_URL");

        if (imageSource != null && !imageSource.isEmpty()) {
            if (imageSource.startsWith("data:image") || imageSource.length() > 500) {
                try {
                    String base64Data = imageSource.contains(",") ? imageSource.substring(imageSource.indexOf(",") + 1) : imageSource;
                    byte[] decodedString = Base64.decode(base64Data, Base64.DEFAULT);
                    Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                    Glide.with(this).load(decodedByte).into(ivFullImage);
                } catch (Exception e) {
                    ivFullImage.setImageResource(R.drawable.profile_pic);
                }
            } else {
                Glide.with(this).load(imageSource).placeholder(R.drawable.profile_pic).into(ivFullImage);
            }
        }

        btnClose.setOnClickListener(v -> finish());
    }
}
