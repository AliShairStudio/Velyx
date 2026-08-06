package com.alert.beacon.data;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TriggerManager {
    private static final String PREF_NAME = "velyxPrefs";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_TRIGGER_WORD = "trigger_word";
    private static final String KEY_EMERGENCY_CONTACTS = "emergency_contacts_list";
    private static final String KEY_CHAT_CONTACTS = "chat_contacts_map";
    private static final String KEY_SELECTED_CONTACT_IDS = "selected_contact_ids";
    private static final String KEY_SELECTED_GROUP_IDS = "selected_group_ids";
    private static final String KEY_USER_ROLE = "user_role";
    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_USER_EMAIL = "user_email";
    private static final String KEY_USER_PASSWORD = "user_password";
    private static final String KEY_USER_PHONE = "user_phone";
    private static final String KEY_IS_LOGGED_IN = "is_logged_in";
    private static final String KEY_EMERGENCY_MESSAGE = "emergency_message";
    private static final String KEY_PROFILE_IMAGE_URI = "profile_image_uri";
    private static final String KEY_LANGUAGE = "app_language";
    private static final String KEY_THEME_MODE = "theme_mode"; // 0: System, 1: Light, 2: Dark
    private static final String KEY_GROUPS = "chat_groups";
    private static final String KEY_BLOCKED_PHONES = "blocked_phones";
    private static final String KEY_SOS_HISTORY = "sos_history";
    private static final String KEY_SAFETY_HISTORY = "safety_history";
    private static final String KEY_GROUP_MESSAGES_PREFIX = "messages_";
    private static final String KEY_NOTIFICATION_SOUND = "notification_sound";
    private static final String KEY_NOTIFICATION_SOUND_URI = "notification_sound_uri";
    private static final String KEY_SAFETY_SOUND = "safety_sound";
    private static final String KEY_SAFETY_SOUND_URI = "safety_sound_uri";
    private static final String KEY_ALARM_SOUND = "alarm_sound";
    private static final String KEY_ALARM_SOUND_URI = "alarm_sound_uri";
    private static final String KEY_SCHEDULED_START_TIME = "scheduled_start_time";
    private static final String KEY_SCHEDULED_DURATION = "scheduled_duration";
    
    // Feature Keys
    private static final String KEY_SHAKE_ENABLED = "shake_trigger_enabled";
    private static final String KEY_POWER_TRIGGER_ENABLED = "power_trigger_enabled";

    // Permission Keys
    private static final String KEY_PERM_MIC = "perm_mic_enabled";
    private static final String KEY_PERM_LOC = "perm_loc_enabled";
    private static final String KEY_PERM_SMS = "perm_sms_enabled";
    private static final String KEY_PERM_NOTIF = "perm_notif_enabled";
    private static final String KEY_PERM_CAM = "perm_cam_enabled";

    // Audio Keys
    private static final String KEY_AUDIO_MESSAGE_PATH = "audio_message_path";
    private static final String KEY_VOICE_TRIGGER_PHRASE = "voice_trigger_phrase";

    private static final String DEFAULT_TRIGGER = "help";
    private static final String DEFAULT_MESSAGE = "I am in trouble my current location is ";

    private final SharedPreferences sharedPreferences;
    private final Gson gson;

    public TriggerManager(Context context) {
        // Use MODE_MULTI_PROCESS to ensure changes are visible across processes
        this.sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS);
        this.gson = new Gson();
    }

    public static String normalizePhone(String phone) {
        if (phone == null) return "";
        return phone.replaceAll("[^0-9]", "");
    }

    public static boolean isSamePhone(String p1, String p2) {
        if (p1 == null || p2 == null) return false;
        String n1 = normalizePhone(p1);
        String n2 = normalizePhone(p2);
        if (n1.isEmpty() || n2.isEmpty()) return false;
        
        // Handle cases where one might have a country code and the other doesn't (last 10 digits)
        if (n1.length() >= 10 && n2.length() >= 10) {
            return n1.substring(n1.length() - 10).equals(n2.substring(n2.length() - 10));
        }
        return n1.equals(n2);
    }

    public void saveUserId(String id) {
        sharedPreferences.edit().putString(KEY_USER_ID, id).apply();
    }

    public String getUserId() {
        return sharedPreferences.getString(KEY_USER_ID, null);
    }

    public void saveTriggerWord(String word) {
        sharedPreferences.edit().putString(KEY_TRIGGER_WORD, word.toLowerCase().trim()).apply();
    }

    public String getTriggerWord() {
        return sharedPreferences.getString(KEY_TRIGGER_WORD, DEFAULT_TRIGGER);
    }

    public void saveContacts(List<Contact> contacts) {
        String json = gson.toJson(contacts);
        sharedPreferences.edit().putString(KEY_EMERGENCY_CONTACTS, json).apply();
    }

    public List<Contact> getContacts() {
        String json = sharedPreferences.getString(KEY_EMERGENCY_CONTACTS, null);
        if (json == null) {
            return new ArrayList<>();
        }
        Type type = new TypeToken<ArrayList<Contact>>() {}.getType();
        return gson.fromJson(json, type);
    }

    public void saveChatContact(String phone, String name) {
        Map<String, String> chatContacts = getChatContacts();
        chatContacts.put(normalizePhone(phone), name);
        String json = gson.toJson(chatContacts);
        sharedPreferences.edit().putString(KEY_CHAT_CONTACTS, json).apply();
    }

    public Map<String, String> getChatContacts() {
        String json = sharedPreferences.getString(KEY_CHAT_CONTACTS, null);
        if (json == null) {
            return new HashMap<>();
        }
        Type type = new TypeToken<Map<String, String>>() {}.getType();
        return gson.fromJson(json, type);
    }

    public String getContactName(String phone) {
        if (phone == null) return null;
        String normalized = normalizePhone(phone);
        
        // Check chat contacts first
        Map<String, String> chatContacts = getChatContacts();
        // Check exact match first
        if (chatContacts.containsKey(normalized)) {
            return chatContacts.get(normalized);
        }
        // Fallback to fuzzy match (last 10 digits)
        if (normalized.length() >= 10) {
            String last10 = normalized.substring(normalized.length() - 10);
            for (Map.Entry<String, String> entry : chatContacts.entrySet()) {
                if (entry.getKey().endsWith(last10)) return entry.getValue();
            }
        }
        
        // Then check emergency contacts
        List<Contact> contacts = getContacts();
        for (Contact c : contacts) {
            if (isSamePhone(phone, c.getPhoneNumber())) {
                return c.getName();
            }
        }
        return null;
    }

    public boolean isContactSaved(String phone) {
        return getContactName(phone) != null;
    }

    public void blockPhone(String phone) {
        Set<String> blocked = getBlockedPhones();
        blocked.add(normalizePhone(phone));
        sharedPreferences.edit().putStringSet(KEY_BLOCKED_PHONES, blocked).apply();
    }

    public void unblockPhone(String phone) {
        Set<String> blocked = getBlockedPhones();
        blocked.remove(normalizePhone(phone));
        sharedPreferences.edit().putStringSet(KEY_BLOCKED_PHONES, blocked).apply();
    }

    public boolean isPhoneBlocked(String phone) {
        String normalized = normalizePhone(phone);
        Set<String> blocked = getBlockedPhones();
        if (blocked.contains(normalized)) return true;
        
        if (normalized.length() >= 10) {
            String last10 = normalized.substring(normalized.length() - 10);
            for (String b : blocked) {
                if (b.endsWith(last10)) return true;
            }
        }
        return false;
    }

    public Set<String> getBlockedPhones() {
        return new HashSet<>(sharedPreferences.getStringSet(KEY_BLOCKED_PHONES, new HashSet<>()));
    }

    public void saveSelectedContactIds(Set<String> ids) {
        sharedPreferences.edit().putStringSet(KEY_SELECTED_CONTACT_IDS, ids).apply();
    }

    public Set<String> getSelectedContactIds() {
        return sharedPreferences.getStringSet(KEY_SELECTED_CONTACT_IDS, new HashSet<>());
    }

    public void saveSelectedGroupIds(Set<String> ids) {
        sharedPreferences.edit().putStringSet(KEY_SELECTED_GROUP_IDS, ids).apply();
    }

    public Set<String> getSelectedGroupIds() {
        return sharedPreferences.getStringSet(KEY_SELECTED_GROUP_IDS, new HashSet<>());
    }

    public void saveEmergencyMessage(String message) {
        sharedPreferences.edit().putString(KEY_EMERGENCY_MESSAGE, message).apply();
    }

    public String getEmergencyMessage() {
        return sharedPreferences.getString(KEY_EMERGENCY_MESSAGE, DEFAULT_MESSAGE);
    }

    public void saveUserRole(String role) {
        sharedPreferences.edit().putString(KEY_USER_ROLE, role).apply();
    }

    public String getUserRole() {
        return sharedPreferences.getString(KEY_USER_ROLE, "");
    }

    public void saveUserName(String name) {
        sharedPreferences.edit().putString(KEY_USER_NAME, name).apply();
    }

    public String getUserName() {
        return sharedPreferences.getString(KEY_USER_NAME, "");
    }

    public void saveUserEmail(String email) {
        sharedPreferences.edit().putString(KEY_USER_EMAIL, email).apply();
    }

    public String getUserEmail() {
        return sharedPreferences.getString(KEY_USER_EMAIL, "");
    }

    public void saveUserPassword(String password) {
        sharedPreferences.edit().putString(KEY_USER_PASSWORD, password).apply();
    }

    public String getUserPassword() {
        return sharedPreferences.getString(KEY_USER_PASSWORD, "");
    }

    public void saveUserPhone(String phone) {
        sharedPreferences.edit().putString(KEY_USER_PHONE, phone).apply();
    }

    public String getUserPhone() {
        return sharedPreferences.getString(KEY_USER_PHONE, "");
    }

    public void setLoggedIn(boolean isLoggedIn) {
        sharedPreferences.edit().putBoolean(KEY_IS_LOGGED_IN, isLoggedIn).apply();
    }

    public boolean isLoggedIn() {
        return sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false);
    }

    public void saveProfileImageUri(String uri) {
        sharedPreferences.edit().putString(KEY_PROFILE_IMAGE_URI, uri).apply();
    }

    public String getProfileImageUri() {
        return sharedPreferences.getString(KEY_PROFILE_IMAGE_URI, null);
    }

    public void saveLanguage(String language) {
        sharedPreferences.edit().putString(KEY_LANGUAGE, language).apply();
    }

    public String getLanguage() {
        return sharedPreferences.getString(KEY_LANGUAGE, "English");
    }

    public void saveNotificationSound(String name, String uri) {
        sharedPreferences.edit()
                .putString(KEY_NOTIFICATION_SOUND, name)
                .putString(KEY_NOTIFICATION_SOUND_URI, uri)
                .apply();
    }

    public String getNotificationSound() {
        return sharedPreferences.getString(KEY_NOTIFICATION_SOUND, "Default");
    }

    public String getNotificationSoundUri() {
        return sharedPreferences.getString(KEY_NOTIFICATION_SOUND_URI, null);
    }

    public void saveSafetySound(String name, String uri) {
        sharedPreferences.edit()
                .putString(KEY_SAFETY_SOUND, name)
                .putString(KEY_SAFETY_SOUND_URI, uri)
                .apply();
    }

    public String getSafetySoundName() {
        return sharedPreferences.getString(KEY_SAFETY_SOUND, "Default");
    }

    public String getSafetySoundUri() {
        return sharedPreferences.getString(KEY_SAFETY_SOUND_URI, null);
    }

    public void saveAlarmSound(String name, String uri) {
        sharedPreferences.edit()
                .putString(KEY_ALARM_SOUND, name)
                .putString(KEY_ALARM_SOUND_URI, uri)
                .apply();
    }

    public String getAlarmSoundName() {
        return sharedPreferences.getString(KEY_ALARM_SOUND, "Default");
    }

    public String getAlarmSoundUri() {
        return sharedPreferences.getString(KEY_ALARM_SOUND_URI, null);
    }

    public void saveScheduledSafety(long startTime, long duration) {
        sharedPreferences.edit()
                .putLong(KEY_SCHEDULED_START_TIME, startTime)
                .putLong(KEY_SCHEDULED_DURATION, duration)
                .apply();
    }

    public long getScheduledStartTime() {
        return sharedPreferences.getLong(KEY_SCHEDULED_START_TIME, 0);
    }

    public long getScheduledDuration() {
        return sharedPreferences.getLong(KEY_SCHEDULED_DURATION, 0);
    }

    public void clearScheduledSafety() {
        sharedPreferences.edit()
                .remove(KEY_SCHEDULED_START_TIME)
                .remove(KEY_SCHEDULED_DURATION)
                .apply();
    }

    public void saveThemeMode(int mode) {
        sharedPreferences.edit().putInt(KEY_THEME_MODE, mode).apply();
    }

    public int getThemeMode() {
        return sharedPreferences.getInt(KEY_THEME_MODE, 0); // 0 is System/Follow System
    }

    public void saveGroups(List<Group> groups) {
        String json = gson.toJson(groups);
        sharedPreferences.edit().putString(KEY_GROUPS, json).apply();
    }

    public List<Group> getGroups() {
        String json = sharedPreferences.getString(KEY_GROUPS, null);
        if (json == null) {
            return new ArrayList<>();
        }
        Type type = new TypeToken<ArrayList<Group>>() {}.getType();
        return gson.fromJson(json, type);
    }

    public void addGroup(Group group) {
        List<Group> groups = getGroups();
        groups.add(group);
        saveGroups(groups);
    }

    public void deleteGroup(String groupId) {
        List<Group> groups = getGroups();
        groups.removeIf(g -> g.getId().equals(groupId));
        saveGroups(groups);
    }

    public void updateGroup(Group updatedGroup) {
        List<Group> groups = getGroups();
        for (int i = 0; i < groups.size(); i++) {
            if (groups.get(i).getId().equals(updatedGroup.getId())) {
                groups.set(i, updatedGroup);
                break;
            }
        }
        saveGroups(groups);
    }

    public void saveGroupMessages(String groupId, List<ChatMessage> messages) {
        if (groupId == null || messages == null) return;
        // Keep only last 100 messages for local storage to save space
        List<ChatMessage> toSave = messages;
        if (messages.size() > 100) {
            toSave = messages.subList(messages.size() - 100, messages.size());
        }
        String json = gson.toJson(toSave);
        sharedPreferences.edit().putString(KEY_GROUP_MESSAGES_PREFIX + groupId, json).apply();
    }

    public List<ChatMessage> getGroupMessages(String groupId) {
        if (groupId == null) return new ArrayList<>();
        String json = sharedPreferences.getString(KEY_GROUP_MESSAGES_PREFIX + groupId, null);
        if (json == null) return new ArrayList<>();
        Type type = new TypeToken<ArrayList<ChatMessage>>() {}.getType();
        return gson.fromJson(json, type);
    }

    // Permission Management Methods
    public void setMicPermissionEnabled(boolean enabled) {
        sharedPreferences.edit().putBoolean(KEY_PERM_MIC, enabled).apply();
    }

    public boolean isMicPermissionEnabled() {
        return sharedPreferences.getBoolean(KEY_PERM_MIC, true);
    }

    public void setLocationPermissionEnabled(boolean enabled) {
        sharedPreferences.edit().putBoolean(KEY_PERM_LOC, enabled).apply();
    }

    public boolean isLocationPermissionEnabled() {
        return sharedPreferences.getBoolean(KEY_PERM_LOC, true);
    }

    public void setSmsPermissionEnabled(boolean enabled) {
        sharedPreferences.edit().putBoolean(KEY_PERM_SMS, enabled).apply();
    }

    public boolean isSmsPermissionEnabled() {
        return sharedPreferences.getBoolean(KEY_PERM_SMS, true);
    }

    public void setNotificationPermissionEnabled(boolean enabled) {
        sharedPreferences.edit().putBoolean(KEY_PERM_NOTIF, enabled).apply();
    }

    public boolean isNotificationPermissionEnabled() {
        return sharedPreferences.getBoolean(KEY_PERM_NOTIF, true);
    }

    public void setCameraPermissionEnabled(boolean enabled) {
        sharedPreferences.edit().putBoolean(KEY_PERM_CAM, enabled).apply();
    }

    public boolean isCameraPermissionEnabled() {
        return sharedPreferences.getBoolean(KEY_PERM_CAM, true);
    }

    // Feature toggles
    public void setShakeTriggerEnabled(boolean enabled) {
        sharedPreferences.edit().putBoolean(KEY_SHAKE_ENABLED, enabled).apply();
    }

    public boolean isShakeTriggerEnabled() {
        return sharedPreferences.getBoolean(KEY_SHAKE_ENABLED, true);
    }

    public void setPowerTriggerEnabled(boolean enabled) {
        sharedPreferences.edit().putBoolean(KEY_POWER_TRIGGER_ENABLED, enabled).apply();
    }

    public boolean isPowerTriggerEnabled() {
        return sharedPreferences.getBoolean(KEY_POWER_TRIGGER_ENABLED, true);
    }

    // Audio Methods
    public void saveAudioMessagePath(String path) {
        sharedPreferences.edit().putString(KEY_AUDIO_MESSAGE_PATH, path).apply();
    }

    public String getAudioMessagePath() {
        return sharedPreferences.getString(KEY_AUDIO_MESSAGE_PATH, null);
    }

    public void saveVoiceTriggerPhrase(String phrase) {
        sharedPreferences.edit().putString(KEY_VOICE_TRIGGER_PHRASE, phrase.toLowerCase().trim()).apply();
    }

    public String getVoiceTriggerPhrase() {
        return sharedPreferences.getString(KEY_VOICE_TRIGGER_PHRASE, DEFAULT_TRIGGER);
    }

    // History Methods
    public void addSosHistoryEntry(SosHistoryEntry entry) {
        if (entry == null) return;
        List<SosHistoryEntry> history = getSosHistory();
        history.add(0, entry); // Add to top
        if (history.size() > 50) history.remove(history.size() - 1); // Keep last 50
        String json = gson.toJson(history);
        // Use commit() to ensure it's saved immediately during background service operation
        sharedPreferences.edit().putString(KEY_SOS_HISTORY, json).commit();
        android.util.Log.d("TriggerManager", "Saved SOS history entry: " + entry.getTriggerType());
    }

    public List<SosHistoryEntry> getSosHistory() {
        String json = sharedPreferences.getString(KEY_SOS_HISTORY, null);
        if (json == null) return new ArrayList<>();
        try {
            Type type = new TypeToken<ArrayList<SosHistoryEntry>>() {}.getType();
            List<SosHistoryEntry> history = gson.fromJson(json, type);
            return history != null ? history : new ArrayList<>();
        } catch (Exception e) {
            android.util.Log.e("TriggerManager", "Error parsing SOS history: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public void saveSosHistory(List<SosHistoryEntry> history) {
        if (history == null) return;
        String json = gson.toJson(history);
        sharedPreferences.edit().putString(KEY_SOS_HISTORY, json).apply();
    }

    // Safety History Methods
    public void addSafetyHistoryEntry(SafetyHistoryEntry entry) {
        if (entry == null) return;
        List<SafetyHistoryEntry> history = getSafetyHistory();
        history.add(0, entry); // Add to top
        if (history.size() > 50) history.remove(history.size() - 1);
        String json = gson.toJson(history);
        sharedPreferences.edit().putString(KEY_SAFETY_HISTORY, json).apply();
    }

    public List<SafetyHistoryEntry> getSafetyHistory() {
        String json = sharedPreferences.getString(KEY_SAFETY_HISTORY, null);
        if (json == null) return new ArrayList<>();
        try {
            Type type = new TypeToken<ArrayList<SafetyHistoryEntry>>() {}.getType();
            List<SafetyHistoryEntry> history = gson.fromJson(json, type);
            return history != null ? history : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public void saveSafetyHistory(List<SafetyHistoryEntry> history) {
        if (history == null) return;
        String json = gson.toJson(history);
        sharedPreferences.edit().putString(KEY_SAFETY_HISTORY, json).apply();
    }

    public void clearData() {
        sharedPreferences.edit().clear().apply();
    }
}

