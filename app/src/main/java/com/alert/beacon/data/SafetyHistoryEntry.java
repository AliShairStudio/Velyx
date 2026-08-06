package com.alert.beacon.data;

public class SafetyHistoryEntry {
    private long timestamp;
    private long durationMs;
    private boolean wasTriggered; // true if timer finished and SOS sent, false if stopped by user

    public SafetyHistoryEntry() {}

    public SafetyHistoryEntry(long timestamp, long durationMs, boolean wasTriggered) {
        this.timestamp = timestamp;
        this.durationMs = durationMs;
        this.wasTriggered = wasTriggered;
    }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }

    public boolean isWasTriggered() { return wasTriggered; }
    public void setWasTriggered(boolean wasTriggered) { this.wasTriggered = wasTriggered; }
}
