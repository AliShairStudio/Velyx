package com.alert.beacon.data;

import java.util.List;

public class SosHistoryEntry {
    private String id;
    private long timestamp;
    private String message;
    private List<String> contactNames;
    private String triggerType; // "Shake" or "Voice"

    public SosHistoryEntry() {}

    public SosHistoryEntry(String id, long timestamp, String message, List<String> contactNames, String triggerType) {
        this.id = id;
        this.timestamp = timestamp;
        this.message = message;
        this.contactNames = contactNames;
        this.triggerType = triggerType;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public List<String> getContactNames() { return contactNames; }
    public void setContactNames(List<String> contactNames) { this.contactNames = contactNames; }

    public String getTriggerType() { return triggerType; }
    public void setTriggerType(String triggerType) { this.triggerType = triggerType; }
}
