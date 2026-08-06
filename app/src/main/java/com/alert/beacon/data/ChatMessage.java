package com.alert.beacon.data;

import com.google.firebase.database.IgnoreExtraProperties;
import java.io.Serializable;

@IgnoreExtraProperties
public class ChatMessage implements Serializable {
    public static final String TYPE_TEXT = "text";
    public static final String TYPE_IMAGE = "image";
    public static final String TYPE_AUDIO = "audio";
    public static final String TYPE_LOCATION = "location";
    public static final String TYPE_SYSTEM = "system";

    public static final int STATUS_SENT = 0;
    public static final int STATUS_DELIVERED = 1;
    public static final int STATUS_SEEN = 2;

    private String id;
    private String senderPhone;
    private String senderName;
    private String message;
    private long timestamp;
    private String type; // text, image, audio, location, system
    private String fileUri; // For images and audio
    private double latitude;
    private double longitude;
    private int status; // 0: sent, 1: delivered, 2: seen
    private String recipientPhone; // If set, this message is only visible to this person (or the sender)
    private boolean isPinned;
    private boolean isStarred;
    private boolean isForwarded;
    private java.util.Map<String, String> reactions; // userId -> emoji
    private java.util.Map<String, Long> readBy; // phone -> timestamp
    private java.util.Map<String, Long> deliveredTo; // phone -> timestamp

    // Required empty constructor for Firebase
    public ChatMessage() {
        this.type = TYPE_TEXT;
        this.status = STATUS_SENT;
        this.isPinned = false;
        this.isStarred = false;
        this.isForwarded = false;
        this.reactions = new java.util.HashMap<>();
        this.readBy = new java.util.HashMap<>();
        this.deliveredTo = new java.util.HashMap<>();
    }

    public ChatMessage(String senderPhone, String senderName, String message, long timestamp) {
        this(senderPhone, senderName, message, timestamp, TYPE_TEXT, null);
    }

    public ChatMessage(String senderPhone, String senderName, String message, long timestamp, String type, String fileUri) {
        this.senderPhone = senderPhone;
        this.senderName = senderName;
        this.message = message;
        this.timestamp = timestamp;
        this.type = type;
        this.fileUri = fileUri;
        this.status = STATUS_SENT;
    }

    public ChatMessage(String senderPhone, String senderName, long timestamp, double latitude, double longitude) {
        this.senderPhone = senderPhone;
        this.senderName = senderName;
        this.timestamp = timestamp;
        this.type = TYPE_LOCATION;
        this.latitude = latitude;
        this.longitude = longitude;
        this.message = "Location Shared";
        this.status = STATUS_SENT;
    }

    // Constructor to match Kotlin's data class usage in IndividualChatScreen
    public ChatMessage(String id, String senderPhone, String message, long timestamp, String type) {
        this.id = id;
        this.senderPhone = senderPhone;
        this.message = message;
        this.timestamp = timestamp;
        this.type = type;
        this.status = STATUS_SENT;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSenderPhone() { return senderPhone; }
    public void setSenderPhone(String senderPhone) { this.senderPhone = senderPhone; }

    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    // Alias for getMessage() to support 'text' property used in Kotlin
    public String getText() { return message; }
    public void setText(String text) { this.message = text; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getFileUri() { return fileUri; }
    public void setFileUri(String fileUri) { this.fileUri = fileUri; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }

    public String getRecipientPhone() { return recipientPhone; }
    public void setRecipientPhone(String recipientPhone) { this.recipientPhone = recipientPhone; }

    public boolean isPinned() { return isPinned; }
    public void setPinned(boolean pinned) { isPinned = pinned; }

    public boolean isStarred() { return isStarred; }
    public void setStarred(boolean starred) { isStarred = starred; }

    public boolean isForwarded() { return isForwarded; }
    public void setForwarded(boolean forwarded) { isForwarded = forwarded; }

    public java.util.Map<String, String> getReactions() { return reactions; }
    public void setReactions(java.util.Map<String, String> reactions) { this.reactions = reactions; }

    public java.util.Map<String, Long> getReadBy() { return readBy; }
    public void setReadBy(java.util.Map<String, Long> readBy) { this.readBy = readBy; }

    public java.util.Map<String, Long> getDeliveredTo() { return deliveredTo; }
    public void setDeliveredTo(java.util.Map<String, Long> deliveredTo) { this.deliveredTo = deliveredTo; }
}
