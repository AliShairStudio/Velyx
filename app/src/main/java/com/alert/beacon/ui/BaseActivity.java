package com.alert.beacon.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.alert.beacon.R;
import com.alert.beacon.data.TriggerManager;

import java.util.Locale;

public class BaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(updateBaseContextLocale(newBase));
    }

    private Context updateBaseContextLocale(Context context) {
        TriggerManager triggerManager = new TriggerManager(context);
        String language = triggerManager.getLanguage();
        String langCode = getLanguageCode(language);
        
        Locale locale = new Locale(langCode);
        Locale.setDefault(locale);

        Resources resources = context.getResources();
        Configuration configuration = resources.getConfiguration();
        configuration.setLocale(locale);
        configuration.setLayoutDirection(new Locale("en"));
        
        return context.createConfigurationContext(configuration);
    }

    private String getLanguageCode(String language) {
        switch (language) {
            case "Urdu": return "ur";
            case "Spanish": return "es";
            case "Russian": return "ru";
            case "Chinese": return "zh";
            case "Japanese": return "ja";
            case "Arabic": return "ar";
            default: return "en";
        }
    }

    protected void showGlassyConfirmDialog(String title, String message, String positiveText, Runnable onConfirm) {
        showGlassyConfirmDialog(title, message, positiveText, getString(R.string.btn_cancel), onConfirm);
    }

    protected void showGlassyConfirmDialog(String title, String message, String positiveText, String negativeText, Runnable onConfirm) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.layout_glassy_dialog, null);
        AlertDialog dialog = new AlertDialog.Builder(this, R.style.GlassyDialog)
                .setView(dialogView)
                .create();

        // Ensure the dialog window background is transparent
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        TextView tvTitle = dialogView.findViewById(R.id.dialogTitle);
        TextView tvMessage = dialogView.findViewById(R.id.dialogMessage);
        Button btnNegative = dialogView.findViewById(R.id.btnNegative);
        Button btnPositive = dialogView.findViewById(R.id.btnPositive);

        if (tvTitle != null) tvTitle.setText(title);
        if (tvMessage != null) tvMessage.setText(message);
        if (btnPositive != null) btnPositive.setText(positiveText);
        if (btnNegative != null) btnNegative.setText(negativeText);

        if (btnNegative != null) {
            btnNegative.setOnClickListener(v -> dialog.dismiss());
        }

        if (btnPositive != null) {
            btnPositive.setOnClickListener(v -> {
                if (onConfirm != null) onConfirm.run();
                dialog.dismiss();
            });
        }

        dialog.show();

        // Set dialog width
        if (dialog.getWindow() != null) {
            WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
            layoutParams.copyFrom(dialog.getWindow().getAttributes());
            layoutParams.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.90);
            layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
            dialog.getWindow().setAttributes(layoutParams);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            View v = getCurrentFocus();
            if (v instanceof EditText) {
                Rect outRect = new Rect();
                v.getGlobalVisibleRect(outRect);
                if (!outRect.contains((int) event.getRawX(), (int) event.getRawY())) {
                    v.clearFocus();
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                    }
                }
            }
        }
        return super.dispatchTouchEvent(event);
    }
}
