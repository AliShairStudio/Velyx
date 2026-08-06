package com.alert.beacon.data;

import android.content.Intent;

public class SearchResult {
    private String title;
    private String subtitle;
    private int iconResId;
    private Intent intent; // Action to perform when clicked
    private Runnable action; // Alternative action

    public SearchResult(String title, String subtitle, int iconResId, Intent intent) {
        this.title = title;
        this.subtitle = subtitle;
        this.iconResId = iconResId;
        this.intent = intent;
    }

    public SearchResult(String title, String subtitle, int iconResId, Runnable action) {
        this.title = title;
        this.subtitle = subtitle;
        this.iconResId = iconResId;
        this.action = action;
    }

    public String getTitle() { return title; }
    public String getSubtitle() { return subtitle; }
    public int getIconResId() { return iconResId; }
    public Intent getIntent() { return intent; }
    public Runnable getAction() { return action; }
}
