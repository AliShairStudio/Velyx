package com.alert.beacon.data;

public class EmergencyService {
    private String name;
    private String number;
    private int iconResId;

    public EmergencyService(String name, String number, int iconResId) {
        this.name = name;
        this.number = number;
        this.iconResId = iconResId;
    }

    public String getName() { return name; }
    public String getNumber() { return number; }
    public int getIconResId() { return iconResId; }
}
