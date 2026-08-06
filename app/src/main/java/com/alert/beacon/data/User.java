package com.alert.beacon.data;

public class User {
    private String id;
    private String name;
    private String email;
    private String phone;
    private String role;
    private String password; // Added password field for login check
    private String profileImageUri;
    private double latitude;
    private double longitude;
    private long lastUpdated;
    private boolean online;
    private String statusText;
    private long statusTimestamp;
    private boolean isInSaferArea;
    private String fcmToken;

    // Required empty constructor for Firebase
    public User() {}

    public User(String id, String name, String email, String phone, String role, String password) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.role = role;
        this.password = password;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getProfileImageUri() { return profileImageUri; }
    public void setProfileImageUri(String profileImageUri) { this.profileImageUri = profileImageUri; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public long getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(long lastUpdated) { this.lastUpdated = lastUpdated; }

    public boolean isOnline() { return online; }
    public void setOnline(boolean online) { this.online = online; }

    public String getStatusText() { return statusText; }
    public void setStatusText(String statusText) { this.statusText = statusText; }

    public long getStatusTimestamp() { return statusTimestamp; }
    public void setStatusTimestamp(long statusTimestamp) { this.statusTimestamp = statusTimestamp; }

    public boolean isInSaferArea() { return isInSaferArea; }
    public void setInSaferArea(boolean inSaferArea) { this.isInSaferArea = inSaferArea; }

    public String getFcmToken() { return fcmToken; }
    public void setFcmToken(String fcmToken) { this.fcmToken = fcmToken; }
}
