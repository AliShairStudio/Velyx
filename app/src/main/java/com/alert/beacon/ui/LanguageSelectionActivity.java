package com.alert.beacon.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.core.os.LocaleListCompat;

import com.alert.beacon.R;
import com.alert.beacon.data.TriggerManager;

public class LanguageSelectionActivity extends BaseActivity {

    private TriggerManager triggerManager;
    private LinearLayout languageList;
    private String[] languages = {"English", "Urdu", "Spanish", "Russian", "Chinese", "Japanese", "Arabic"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_language_selection);

        triggerManager = new TriggerManager(this);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        languageList = findViewById(R.id.languageList);
        setupLanguageList();
    }

    private void setupLanguageList() {
        languageList.removeAllViews();
        String currentLang = triggerManager.getLanguage();

        for (String lang : languages) {
            View itemView = LayoutInflater.from(this).inflate(R.layout.item_selection_box, languageList, false);
            
            TextView tvName = itemView.findViewById(R.id.tvItemName);
            ImageView ivCheck = itemView.findViewById(R.id.ivCheck);
            
            tvName.setText(lang);
            if (lang.equals(currentLang)) {
                ivCheck.setVisibility(View.VISIBLE);
            } else {
                ivCheck.setVisibility(View.GONE);
            }

            itemView.setOnClickListener(v -> {
                if (!lang.equals(triggerManager.getLanguage())) {
                    triggerManager.saveLanguage(lang);
                    updateAppLocale(lang);
                }
                finish();
            });

            languageList.addView(itemView);
            
            // Add a small spacer
            View spacer = new View(this);
            spacer.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 
                    (int) (12 * getResources().getDisplayMetrics().density)));
            languageList.addView(spacer);
        }
    }

    private void updateAppLocale(String language) {
        String langCode = "en";
        switch (language) {
            case "Urdu": langCode = "ur"; break;
            case "Spanish": langCode = "es"; break;
            case "Russian": langCode = "ru"; break;
            case "Chinese": langCode = "zh"; break;
            case "Japanese": langCode = "ja"; break;
            case "Arabic": langCode = "ar"; break;
        }

        LocaleListCompat appLocale = LocaleListCompat.forLanguageTags(langCode);
        AppCompatDelegate.setApplicationLocales(appLocale);
    }
}
