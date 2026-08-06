package com.alert.beacon.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alert.beacon.R;
import com.alert.beacon.data.Contact;
import com.alert.beacon.data.Group;
import com.alert.beacon.data.SosHistoryEntry;
import com.alert.beacon.data.TriggerManager;
import com.alert.beacon.location.LocationHelper;
import com.alert.beacon.utils.LoadingUtils;
import com.alert.beacon.utils.SmsHelper;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.shape.CornerFamily;
import com.google.android.material.shape.ShapeAppearanceModel;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class AlertMessageActivity extends BaseActivity {

    private EditText etAlertMessage, etVoiceTrigger;
    private RecyclerView rvSelectContacts, rvSelectGroups, rvSosHistory;
    private TriggerManager triggerManager;
    private LocationHelper locationHelper;
    private SmsHelper smsHelper;
    private Set<String> currentSelectedIds = new HashSet<>();
    private Set<String> currentSelectedGroupIds = new HashSet<>();
    private Button btnSaveConfig;
    private MaterialButton btnTabConfig, btnTabHistory;
    private MaterialButton btnRecipientsContacts, btnRecipientsGroups;
    private View viewConfig, viewHistory;
    private TextView tvEmptyHistory, tvHistoryLabel;
    private View customLoading;
    private TextView tvLoadingText;

    // Selection logic
    private View layoutSelection;
    private CheckBox cbSelectAll;
    private TextView tvSelectionCount;
    private boolean isSelectionMode = false;
    private final Set<Integer> selectedPositions = new HashSet<>();
    private HistoryAdapter historyAdapter;
    private List<SosHistoryEntry> sosHistoryList;

    private final OnBackPressedCallback backCallback = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            if (isSelectionMode) {
                exitSelectionMode();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alert_message);

        getOnBackPressedDispatcher().addCallback(this, backCallback);

        triggerManager = new TriggerManager(this);
        locationHelper = new LocationHelper(this);
        smsHelper = new SmsHelper(this);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> {
            if (isSelectionMode) exitSelectionMode();
            else finish();
        });

        // Navigation Tabs
        btnTabConfig = findViewById(R.id.btnTabConfig);
        btnTabHistory = findViewById(R.id.btnTabHistory);
        viewConfig = findViewById(R.id.viewConfig);
        viewHistory = findViewById(R.id.viewHistory);
        rvSosHistory = findViewById(R.id.rvSosHistory);
        tvEmptyHistory = findViewById(R.id.tvEmptyHistory);
        tvHistoryLabel = findViewById(R.id.tvHistoryLabel);

        layoutSelection = findViewById(R.id.layoutSelectionHistory);
        cbSelectAll = findViewById(R.id.cbSelectAllHistory);
        tvSelectionCount = findViewById(R.id.tvSelectionCountHistory);

        btnTabConfig.setOnClickListener(v -> showTab(true));
        btnTabHistory.setOnClickListener(v -> showTab(false));

        btnRecipientsContacts = findViewById(R.id.btnRecipientsContacts);
        btnRecipientsGroups = findViewById(R.id.btnRecipientsGroups);
        
        btnRecipientsContacts.setOnClickListener(v -> toggleRecipientsList(true));
        btnRecipientsGroups.setOnClickListener(v -> toggleRecipientsList(false));

        etAlertMessage = findViewById(R.id.etAlertMessage);
        etVoiceTrigger = findViewById(R.id.etVoiceTrigger);
        rvSelectContacts = findViewById(R.id.rvSelectContacts);
        rvSelectGroups = findViewById(R.id.rvSelectGroups);
        btnSaveConfig = findViewById(R.id.btnSaveAlertConfig);
        Button btnSendAlert = findViewById(R.id.btnSendAlert);
        
        customLoading = findViewById(R.id.customLoading);
        tvLoadingText = findViewById(R.id.tvLoadingText);

        loadConfiguration();
        setupHistoryList();
        setupSelectionToolbar();

        btnSaveConfig.setOnClickListener(v -> saveConfiguration());
        btnSendAlert.setOnClickListener(v -> sendAlert());

        setupContactsList();
        setupGroupsList();
        setupChangeListeners();
        showTab(true);
    }

    private void setupSelectionToolbar() {
        cbSelectAll.setOnClickListener(v -> {
            if (cbSelectAll.isChecked()) {
                for (int i = 0; i < sosHistoryList.size(); i++) {
                    selectedPositions.add(i);
                }
            } else {
                selectedPositions.clear();
            }
            updateSelectionUI();
            historyAdapter.notifyDataSetChanged();
        });

        findViewById(R.id.btnDeleteHistory).setOnClickListener(v -> {
            if (selectedPositions.isEmpty()) {
                Toast.makeText(this, "No items selected", Toast.LENGTH_SHORT).show();
                return;
            }

            showGlassyConfirmDialog(
                    getString(R.string.delete_confirm_title),
                    "Are you sure you want to delete the selected alert logs?",
                    getString(R.string.delete),
                    this::deleteSelectedItems
            );
        });
    }

    private void enterSelectionMode(int position) {
        isSelectionMode = true;
        backCallback.setEnabled(true);
        selectedPositions.clear();
        selectedPositions.add(position);
        
        tvHistoryLabel.setVisibility(View.GONE);
        layoutSelection.setVisibility(View.VISIBLE);
        
        updateSelectionUI();
        historyAdapter.notifyDataSetChanged();
    }

    private void exitSelectionMode() {
        isSelectionMode = false;
        backCallback.setEnabled(false);
        selectedPositions.clear();
        
        tvHistoryLabel.setVisibility(View.VISIBLE);
        layoutSelection.setVisibility(View.GONE);
        cbSelectAll.setChecked(false);
        
        historyAdapter.notifyDataSetChanged();
    }

    private void updateSelectionUI() {
        tvSelectionCount.setText(String.format(Locale.getDefault(), "%d selected", selectedPositions.size()));
        cbSelectAll.setChecked(selectedPositions.size() == sosHistoryList.size() && !sosHistoryList.isEmpty());
    }

    private void deleteSelectedItems() {
        if (selectedPositions.isEmpty()) {
            Toast.makeText(this, "No items selected", Toast.LENGTH_SHORT).show();
            return;
        }

        List<SosHistoryEntry> newList = new ArrayList<>();
        for (int i = 0; i < sosHistoryList.size(); i++) {
            if (!selectedPositions.contains(i)) {
                newList.add(sosHistoryList.get(i));
            }
        }

        triggerManager.saveSosHistory(newList);
        sosHistoryList = newList;
        
        exitSelectionMode();
        if (sosHistoryList.isEmpty()) {
            tvEmptyHistory.setVisibility(View.VISIBLE);
            rvSosHistory.setVisibility(View.GONE);
        } else {
            historyAdapter.updateList(sosHistoryList);
        }
        
        Toast.makeText(this, "Deleted successfully", Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            View v = getCurrentFocus();
            if (v instanceof EditText) {
                Rect outRect = new Rect();
                v.getGlobalVisibleRect(outRect);
                if (!outRect.contains((int)event.getRawX(), (int)event.getRawY())) {
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

    private void toggleRecipientsList(boolean showContacts) {
        if (showContacts) {
            rvSelectContacts.setVisibility(View.VISIBLE);
            rvSelectGroups.setVisibility(View.GONE);
            btnRecipientsContacts.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.primary_blue)));
            btnRecipientsContacts.setTextColor(ContextCompat.getColor(this, R.color.white));
            btnRecipientsGroups.setBackgroundTintList(ColorStateList.valueOf(android.graphics.Color.TRANSPARENT));
            btnRecipientsGroups.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        } else {
            rvSelectContacts.setVisibility(View.GONE);
            rvSelectGroups.setVisibility(View.VISIBLE);
            btnRecipientsGroups.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.primary_blue)));
            btnRecipientsGroups.setTextColor(ContextCompat.getColor(this, R.color.white));
            btnRecipientsContacts.setBackgroundTintList(ColorStateList.valueOf(android.graphics.Color.TRANSPARENT));
            btnRecipientsContacts.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        }
    }

    private void showTab(boolean isConfig) {
        if (isSelectionMode) exitSelectionMode();
        
        if (isConfig) {
            viewConfig.setVisibility(View.VISIBLE);
            viewHistory.setVisibility(View.GONE);
            btnTabConfig.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.tab_active_bg)));
            btnTabConfig.setTextColor(ContextCompat.getColor(this, R.color.primary_blue));
            btnTabHistory.setBackgroundTintList(ColorStateList.valueOf(android.graphics.Color.TRANSPARENT));
            btnTabHistory.setTextColor(ContextCompat.getColor(this, R.color.tab_inactive_text));
            findViewById(R.id.layout_bottom_btns).setVisibility(View.VISIBLE);
            if (getSupportActionBar() != null) getSupportActionBar().setTitle("SOS Configuration");
        } else {
            viewConfig.setVisibility(View.GONE);
            viewHistory.setVisibility(View.VISIBLE);
            btnTabHistory.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.tab_active_bg)));
            btnTabHistory.setTextColor(ContextCompat.getColor(this, R.color.primary_blue));
            btnTabConfig.setBackgroundTintList(ColorStateList.valueOf(android.graphics.Color.TRANSPARENT));
            btnTabConfig.setTextColor(ContextCompat.getColor(this, R.color.tab_inactive_text));
            findViewById(R.id.layout_bottom_btns).setVisibility(View.GONE);
            if (getSupportActionBar() != null) getSupportActionBar().setTitle("SOS History");
            loadHistory();
        }
    }

    private void showLoading(String message) {
        if (customLoading != null) {
            customLoading.setVisibility(View.VISIBLE);
            if (tvLoadingText != null) tvLoadingText.setText(message.toUpperCase());
            LoadingUtils.startCustomAnimation(customLoading);
        }
    }

    private void hideLoading() {
        if (customLoading != null) {
            customLoading.setVisibility(View.GONE);
            LoadingUtils.stopCustomAnimation(customLoading);
        }
    }

    private void loadConfiguration() {
        String msg = triggerManager.getEmergencyMessage();
        if (msg != null && !msg.isEmpty()) {
            etAlertMessage.setText(msg);
        }
        
        etVoiceTrigger.setText(triggerManager.getVoiceTriggerPhrase());
        currentSelectedIds = new HashSet<>(triggerManager.getSelectedContactIds());
        currentSelectedGroupIds = new HashSet<>(triggerManager.getSelectedGroupIds());
        btnSaveConfig.setVisibility(View.GONE);
    }

    private void setupContactsList() {
        List<Contact> allContacts = triggerManager.getContacts();
        sortContacts(allContacts);
        rvSelectContacts.setLayoutManager(new LinearLayoutManager(this));
        rvSelectContacts.setAdapter(new ContactCheckboxAdapter(allContacts));
    }

    private void sortContacts(List<Contact> contacts) {
        contacts.sort((c1, c2) -> {
            boolean s1 = currentSelectedIds.contains(c1.getId());
            boolean s2 = currentSelectedIds.contains(c2.getId());
            if (s1 && !s2) return -1;
            if (!s1 && s2) return 1;
            return c1.getName().compareToIgnoreCase(c2.getName());
        });
    }

    private void setupGroupsList() {
        List<Group> allGroups = triggerManager.getGroups();
        sortGroups(allGroups);
        rvSelectGroups.setLayoutManager(new LinearLayoutManager(this));
        rvSelectGroups.setAdapter(new GroupCheckboxAdapter(allGroups));
    }

    private void sortGroups(List<Group> groups) {
        groups.sort((g1, g2) -> {
            boolean s1 = currentSelectedGroupIds.contains(g1.getId());
            boolean s2 = currentSelectedGroupIds.contains(g2.getId());
            if (s1 && !s2) return -1;
            if (!s1 && s2) return 1;
            return g1.getName().compareToIgnoreCase(g2.getName());
        });
    }

    private void setupHistoryList() {
        rvSosHistory.setLayoutManager(new LinearLayoutManager(this));
        loadHistory();
    }

    private void loadHistory() {
        sosHistoryList = triggerManager.getSosHistory();
        if (sosHistoryList.isEmpty()) {
            tvEmptyHistory.setVisibility(View.VISIBLE);
            rvSosHistory.setVisibility(View.GONE);
        } else {
            tvEmptyHistory.setVisibility(View.GONE);
            rvSosHistory.setVisibility(View.VISIBLE);
            historyAdapter = new HistoryAdapter(sosHistoryList);
            rvSosHistory.setAdapter(historyAdapter);
        }
    }

    private void setupChangeListeners() {
        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) { checkIfChanged(); }
        };
        etAlertMessage.addTextChangedListener(watcher);
        etVoiceTrigger.addTextChangedListener(watcher);
    }

    private void checkIfChanged() {
        String currentMsg = etAlertMessage.getText().toString();
        String currentVoice = etVoiceTrigger.getText().toString();
        
        boolean msgChanged = !currentMsg.equals(triggerManager.getEmergencyMessage());
        boolean voiceChanged = !currentVoice.equals(triggerManager.getVoiceTriggerPhrase());
        
        Set<String> savedIds = new HashSet<>(triggerManager.getSelectedContactIds());
        boolean contactsChanged = !currentSelectedIds.equals(savedIds);
        
        Set<String> savedGroupIds = new HashSet<>(triggerManager.getSelectedGroupIds());
        boolean groupsChanged = !currentSelectedGroupIds.equals(savedGroupIds);
        
        if (msgChanged || voiceChanged || contactsChanged || groupsChanged) {
            btnSaveConfig.setVisibility(View.VISIBLE);
        } else {
            btnSaveConfig.setVisibility(View.GONE);
        }
    }

    private void saveConfiguration() {
        triggerManager.saveEmergencyMessage(etAlertMessage.getText().toString());
        triggerManager.saveVoiceTriggerPhrase(etVoiceTrigger.getText().toString());
        triggerManager.saveSelectedContactIds(new HashSet<>(currentSelectedIds));
        triggerManager.saveSelectedGroupIds(new HashSet<>(currentSelectedGroupIds));
        
        Toast.makeText(this, R.string.config_saved, Toast.LENGTH_SHORT).show();
        btnSaveConfig.setVisibility(View.GONE);
    }

    private void sendAlert() {
        if (currentSelectedIds.isEmpty() && currentSelectedGroupIds.isEmpty()) {
            Toast.makeText(this, R.string.select_contact_error, Toast.LENGTH_SHORT).show();
            return;
        }

        showLoading(getString(R.string.fetching_location));
        locationHelper.getLastLocation(locationUrl -> {
            String customMessage = etAlertMessage.getText().toString();
            String locationPart = (locationUrl != null) ? ("\n\nMy location: " + locationUrl) : " [Location Unavailable]";
            String fullMessage = customMessage + locationPart;

            Set<String> recipientPhones = new HashSet<>();
            List<String> sentToNames = new ArrayList<>();
            
            int selectedContactCount = 0;
            List<Contact> allContacts = triggerManager.getContacts();
            for (Contact contact : allContacts) {
                if (currentSelectedIds.contains(contact.getId())) {
                    String phone = contact.getPhoneNumber();
                    if (phone != null && !phone.isEmpty()) {
                        recipientPhones.add(phone);
                        sentToNames.add(contact.getName());
                        selectedContactCount++;
                    }
                }
            }
            
            int selectedGroupCount = 0;
            List<String> selectedGroupNames = new ArrayList<>();
            List<Group> allGroups = triggerManager.getGroups();
            String myPhone = triggerManager.getUserPhone();
            
            for (Group group : allGroups) {
                if (currentSelectedGroupIds.contains(group.getId())) {
                    selectedGroupCount++;
                    selectedGroupNames.add(group.getName());
                    sentToNames.add(group.getName() + " (Group)");
                    
                    List<String> memberPhones = group.getMemberPhones();
                    if (memberPhones != null && !memberPhones.isEmpty()) {
                        for (String phone : memberPhones) {
                            if (phone != null && !phone.isEmpty() && !TriggerManager.isSamePhone(phone, myPhone)) {
                                recipientPhones.add(phone);
                            }
                        }
                    }
                }
            }

            int sentSmsCount = 0;
            for (String phone : recipientPhones) {
                smsHelper.sendSms(phone, fullMessage);
                sentSmsCount++;
            }

            if (sentSmsCount > 0) {
                SosHistoryEntry entry = new SosHistoryEntry(
                    UUID.randomUUID().toString(),
                    System.currentTimeMillis(),
                    fullMessage,
                    sentToNames,
                    "Manual Send"
                );
                triggerManager.addSosHistoryEntry(entry);
            }

            hideLoading();
            if (sentSmsCount > 0) {
                StringBuilder msgBuilder = new StringBuilder("SOS sent to ");
                msgBuilder.append(selectedContactCount).append(" contacts");
                if (selectedGroupCount > 0) {
                    msgBuilder.append(" and ").append(selectedGroupCount).append(" groups");
                    if (selectedGroupCount <= 3) {
                        msgBuilder.append(" (").append(String.join(", ", selectedGroupNames)).append(")");
                    }
                }
                msgBuilder.append(" (").append(recipientPhones.size()).append(" unique recipients)");
                
                Toast.makeText(this, msgBuilder.toString(), Toast.LENGTH_LONG).show();
                showTab(false);
            } else {
                Toast.makeText(this, "No recipients found! Please select contacts or groups.", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void applyPixelCorners(View view, int position, int totalCount) {
        if (!(view instanceof MaterialCardView)) return;
        MaterialCardView card = (MaterialCardView) view;
        float bigRadius = 32f * getResources().getDisplayMetrics().density;
        float smallRadius = 4f * getResources().getDisplayMetrics().density;

        ShapeAppearanceModel.Builder builder = card.getShapeAppearanceModel().toBuilder();

        if (totalCount == 1) {
            builder.setAllCorners(CornerFamily.ROUNDED, bigRadius);
        } else if (position == 0) {
            builder.setTopLeftCorner(CornerFamily.ROUNDED, bigRadius)
                   .setTopRightCorner(CornerFamily.ROUNDED, bigRadius)
                   .setBottomLeftCorner(CornerFamily.ROUNDED, smallRadius)
                   .setBottomRightCorner(CornerFamily.ROUNDED, smallRadius);
        } else if (position == totalCount - 1) {
            builder.setTopLeftCorner(CornerFamily.ROUNDED, smallRadius)
                   .setTopRightCorner(CornerFamily.ROUNDED, smallRadius)
                   .setBottomLeftCorner(CornerFamily.ROUNDED, bigRadius)
                   .setBottomRightCorner(CornerFamily.ROUNDED, bigRadius);
        } else {
            builder.setAllCorners(CornerFamily.ROUNDED, smallRadius);
        }
        card.setShapeAppearanceModel(builder.build());
    }

    private class ContactCheckboxAdapter extends RecyclerView.Adapter<ContactCheckboxAdapter.ContactViewHolder> {
        private final List<Contact> contacts;
        public ContactCheckboxAdapter(List<Contact> contacts) { this.contacts = contacts; }

        @NonNull @Override
        public ContactViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_contact_checkbox, parent, false);
            return new ContactViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ContactViewHolder holder, int position) {
            Contact contact = contacts.get(position);
            holder.tvName.setText(contact.getName());
            holder.tvPhone.setText(contact.getPhoneNumber());
            holder.cbContact.setOnCheckedChangeListener(null);
            holder.cbContact.setChecked(currentSelectedIds.contains(contact.getId()));
            holder.cbContact.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) currentSelectedIds.add(contact.getId());
                else currentSelectedIds.remove(contact.getId());
                checkIfChanged();
            });

            applyPixelCorners(holder.itemView, position, getItemCount());
        }

        @Override
        public int getItemCount() { return contacts.size(); }

        class ContactViewHolder extends RecyclerView.ViewHolder {
            CheckBox cbContact;
            TextView tvName, tvPhone;
            ContactViewHolder(View itemView) {
                super(itemView);
                cbContact = itemView.findViewById(R.id.cbContact);
                tvName = itemView.findViewById(R.id.tvContactName);
                tvPhone = itemView.findViewById(R.id.tvContactPhone);
            }
        }
    }

    private class GroupCheckboxAdapter extends RecyclerView.Adapter<GroupCheckboxAdapter.GroupViewHolder> {
        private final List<Group> groups;
        public GroupCheckboxAdapter(List<Group> groups) { this.groups = groups; }

        @NonNull @Override
        public GroupViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_contact_checkbox, parent, false);
            return new GroupViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull GroupViewHolder holder, int position) {
            Group group = groups.get(position);
            holder.tvName.setText(group.getName());
            int memberCount = group.getMemberPhones() != null ? group.getMemberPhones().size() : 0;
            holder.tvSub.setText(getString(R.string.member_count, memberCount));
            holder.cbGroup.setOnCheckedChangeListener(null);
            holder.cbGroup.setChecked(currentSelectedGroupIds.contains(group.getId()));
            holder.cbGroup.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) currentSelectedGroupIds.add(group.getId());
                else currentSelectedGroupIds.remove(group.getId());
                checkIfChanged();
            });

            applyPixelCorners(holder.itemView, position, getItemCount());
        }

        @Override
        public int getItemCount() { return groups.size(); }

        class GroupViewHolder extends RecyclerView.ViewHolder {
            CheckBox cbGroup;
            TextView tvName, tvSub;
            GroupViewHolder(View itemView) {
                super(itemView);
                cbGroup = itemView.findViewById(R.id.cbContact);
                tvName = itemView.findViewById(R.id.tvContactName);
                tvSub = itemView.findViewById(R.id.tvContactPhone);
            }
        }
    }

    private class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {
        private List<SosHistoryEntry> history;
        HistoryAdapter(List<SosHistoryEntry> history) { this.history = history; }

        void updateList(List<SosHistoryEntry> newList) {
            this.history = newList;
            notifyDataSetChanged();
        }

        @NonNull @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_sos_history, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            SosHistoryEntry entry = history.get(position);
            holder.tvType.setText(entry.getTriggerType());
            holder.tvTime.setText(new SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()).format(new Date(entry.getTimestamp())));
            holder.tvMessage.setText(entry.getMessage());
            holder.tvMessage.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
            holder.tvContacts.setText(holder.itemView.getContext().getString(R.string.sent_to_prefix, String.join(", ", entry.getContactNames())));

            // Selection Logic
            holder.checkBox.setVisibility(isSelectionMode ? View.VISIBLE : View.GONE);
            holder.checkBox.setChecked(selectedPositions.contains(position));

            holder.itemView.setOnLongClickListener(v -> {
                if (!isSelectionMode) {
                    enterSelectionMode(position);
                    return true;
                }
                return false;
            });

            holder.itemView.setOnClickListener(v -> {
                if (isSelectionMode) {
                    toggleSelection(position);
                }
            });

            holder.checkBox.setOnClickListener(v -> toggleSelection(position));
        }

        private void toggleSelection(int position) {
            if (selectedPositions.contains(position)) {
                selectedPositions.remove(position);
            } else {
                selectedPositions.add(position);
            }
            
            if (selectedPositions.isEmpty()) {
                exitSelectionMode();
            } else {
                updateSelectionUI();
                notifyItemChanged(position);
            }
        }

        @Override
        public int getItemCount() { return history.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvType, tvTime, tvMessage, tvContacts;
            CheckBox checkBox;
            ViewHolder(View v) {
                super(v);
                tvType = v.findViewById(R.id.tvHistoryType);
                tvTime = v.findViewById(R.id.tvHistoryTime);
                tvMessage = v.findViewById(R.id.tvHistoryMessage);
                tvContacts = v.findViewById(R.id.tvHistoryContacts);
                checkBox = v.findViewById(R.id.cbHistorySelect);
            }
        }
    }
}
