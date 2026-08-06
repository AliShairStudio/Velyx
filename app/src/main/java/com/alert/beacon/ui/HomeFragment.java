package com.alert.beacon.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.alert.beacon.R;
import com.alert.beacon.data.ChatMessage;
import com.alert.beacon.data.Contact;
import com.alert.beacon.data.SosHistoryEntry;
import com.alert.beacon.data.TriggerManager;
import com.alert.beacon.location.LocationHelper;
import com.alert.beacon.utils.LoadingUtils;
import com.alert.beacon.utils.SmsHelper;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class HomeFragment extends Fragment {

    private TriggerManager triggerManager;
    private LocationHelper locationHelper;
    private SmsHelper smsHelper;

    private MaterialCardView cardStatus;
    private LinearLayout layoutStatusDetails;
    private TextView textViewStatus, tvStatusMic, tvStatusLoc, tvStatusSms;
    private View statusDot;
    private View customLoading;
    private TextView tvLoadingText;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        triggerManager = new TriggerManager(requireContext());
        locationHelper = new LocationHelper(requireContext());
        smsHelper = new SmsHelper(requireContext());

        cardStatus = view.findViewById(R.id.cardStatus);
        layoutStatusDetails = view.findViewById(R.id.layoutStatusDetails);
        textViewStatus = view.findViewById(R.id.textViewStatus);
        tvStatusMic = view.findViewById(R.id.tvStatusMic);
        tvStatusLoc = view.findViewById(R.id.tvStatusLoc);
        tvStatusSms = view.findViewById(R.id.tvStatusSms);
        statusDot = view.findViewById(R.id.statusDot);
        customLoading = view.findViewById(R.id.customLoading);
        tvLoadingText = view.findViewById(R.id.tvLoadingText);

        Button btnEmergency = view.findViewById(R.id.btnEmergency);
        View btnCallEmergency = view.findViewById(R.id.btnCallEmergency);
        View btnAlertMessage = view.findViewById(R.id.btnAlertMessage);
        View btnTrustedContacts = view.findViewById(R.id.btnTrustedContacts);

        btnEmergency.setOnClickListener(v -> triggerEmergencySms());

        btnCallEmergency.setOnClickListener(v -> startActivity(new Intent(getActivity(), EmergencyNumbersActivity.class)));

        btnAlertMessage.setOnClickListener(v -> startActivity(new Intent(getActivity(), AlertMessageActivity.class)));

        btnTrustedContacts.setOnClickListener(v -> startActivity(new Intent(getActivity(), TrustedContactsActivity.class)));

        setupStatusCard();

        return view;
    }

    private void setupStatusCard() {
        updateStatusUI();

        cardStatus.setOnClickListener(v -> {
            boolean isVisible = layoutStatusDetails.getVisibility() == View.VISIBLE;
            
            AutoTransition transition = new AutoTransition();
            transition.setDuration(400);
            transition.setInterpolator(new AccelerateDecelerateInterpolator());
            
            TransitionManager.beginDelayedTransition((ViewGroup) cardStatus.getParent(), transition);
            
            if (isVisible) {
                layoutStatusDetails.setVisibility(View.GONE);
            } else {
                updateStatusUI(); 
                layoutStatusDetails.setVisibility(View.VISIBLE);
            }
        });
    }

    private void updateStatusUI() {
        if (!isAdded()) return;

        boolean micEnabled = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED 
                            && triggerManager.isMicPermissionEnabled();
        boolean locEnabled = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED 
                            && triggerManager.isLocationPermissionEnabled();
        boolean smsEnabled = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED 
                            && triggerManager.isSmsPermissionEnabled();

        int green = ContextCompat.getColor(requireContext(), R.color.success_green);
        int yellow = ContextCompat.getColor(requireContext(), R.color.warning_yellow);
        int red = ContextCompat.getColor(requireContext(), R.color.error_red);

        if (micEnabled && locEnabled) {
            textViewStatus.setText(R.string.status_protected);
            textViewStatus.setTextColor(green);
            statusDot.setBackgroundResource(R.drawable.status_dot_green);
        } else if (smsEnabled) {
            textViewStatus.setText(R.string.status_protected);
            textViewStatus.setTextColor(yellow);
            statusDot.setBackgroundResource(R.drawable.status_dot_yellow);
        } else {
            textViewStatus.setText(R.string.status_unprotected);
            textViewStatus.setTextColor(red);
            statusDot.setBackgroundResource(R.drawable.status_dot_red);
        }

        String activeStr = getString(R.string.status_active);
        String offStr = getString(R.string.status_off);

        tvStatusMic.setText(getString(R.string.status_mic, micEnabled ? activeStr : offStr));
        tvStatusMic.setTextColor(micEnabled ? green : red);

        tvStatusLoc.setText(getString(R.string.status_location, locEnabled ? activeStr : offStr));
        tvStatusLoc.setTextColor(locEnabled ? green : red);

        tvStatusSms.setText(getString(R.string.status_sms, smsEnabled ? activeStr : offStr));
        tvStatusSms.setTextColor(smsEnabled ? green : red);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateStatusUI();
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

    private void triggerEmergencySms() {
        List<Contact> allContacts = triggerManager.getContacts();
        Set<String> selectedIds = triggerManager.getSelectedContactIds();
        Set<String> selectedGroupIds = triggerManager.getSelectedGroupIds();

        if (allContacts.isEmpty() && selectedGroupIds.isEmpty()) {
            Toast.makeText(getContext(), "No recipients selected. Please go to 'Alert Message' to select contacts or groups.", Toast.LENGTH_LONG).show();
            return;
        }

        showLoading("Fetching Location & Sending SOS");

        locationHelper.getLastLocation(locationUrl -> {
            if (!isAdded()) return;

            if (!triggerManager.isSmsPermissionEnabled()) {
                hideLoading();
                Toast.makeText(getContext(), "SMS Feature is disabled in app settings!", Toast.LENGTH_LONG).show();
                return;
            }

            String customMessage = triggerManager.getEmergencyMessage();
            String locationPart = (locationUrl != null) ? ("\n\nMy location: " + locationUrl) : " [Location Unavailable]";
            String fullMessage = customMessage + locationPart;

            Set<String> recipientPhones = new HashSet<>();
            List<String> sentToNames = new ArrayList<>();
            
            int contactCount = 0;
            for (Contact contact : allContacts) {
                if (selectedIds.contains(contact.getId())) {
                    recipientPhones.add(contact.getPhoneNumber());
                    sentToNames.add(contact.getName());
                    contactCount++;
                }
            }
            
            int groupCount = 0;
            List<com.alert.beacon.data.Group> allGroups = triggerManager.getGroups();
            String myPhone = triggerManager.getUserPhone();
            for (com.alert.beacon.data.Group group : allGroups) {
                if (selectedGroupIds.contains(group.getId())) {
                    groupCount++;
                    sentToNames.add(group.getName() + " (Group)");
                    List<String> members = group.getMemberPhones();
                    if (members != null) {
                        for (String phone : members) {
                            if (!com.alert.beacon.data.TriggerManager.isSamePhone(phone, myPhone)) {
                                recipientPhones.add(phone);
                            }
                        }
                    }
                }
            }

            for (String phone : recipientPhones) {
                smsHelper.sendSms(phone, fullMessage);
            }

            // Also send to Firebase Group Chats
            try {
                FirebaseDatabase database = FirebaseDatabase.getInstance("https://beaconsos-a2dba-default-rtdb.asia-southeast1.firebasedatabase.app/");
                String myName = triggerManager.getUserName();
                // myPhone is already defined above
                for (String groupId : selectedGroupIds) {
                    DatabaseReference groupMsgRef = database.getReference("groups").child(groupId).child("messages");
                    String msgKey = groupMsgRef.push().getKey();
                    if (msgKey != null) {
                        ChatMessage groupMsg = new ChatMessage(myPhone, myName, fullMessage, System.currentTimeMillis());
                        groupMsgRef.child(msgKey).setValue(groupMsg);
                    }
                }
            } catch (Exception e) {
                android.util.Log.e("HomeFragment", "Failed to send to Firebase groups", e);
            }

            if (!recipientPhones.isEmpty()) {
                SosHistoryEntry entry = new SosHistoryEntry(
                    UUID.randomUUID().toString(),
                    System.currentTimeMillis(),
                    fullMessage,
                    sentToNames,
                    "Manual SOS"
                );
                triggerManager.addSosHistoryEntry(entry);
            }

            hideLoading();
            if (!recipientPhones.isEmpty()) {
                StringBuilder msgBuilder = new StringBuilder("SOS sent to ");
                msgBuilder.append(contactCount).append(" contacts");
                if (groupCount > 0) {
                    msgBuilder.append(" and ").append(groupCount).append(" groups");
                }
                msgBuilder.append(" (").append(recipientPhones.size()).append(" unique recipients)");
                
                Toast.makeText(getContext(), msgBuilder.toString(), Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(getContext(), "Failed to send SOS. No valid recipients found.", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
