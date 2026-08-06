package com.alert.beacon.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import androidx.appcompat.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alert.beacon.R;
import com.alert.beacon.data.ChatMessage;
import com.alert.beacon.data.Group;
import com.alert.beacon.data.TriggerManager;
import com.alert.beacon.utils.LoadingUtils;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.hbb20.CountryCodePicker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ChatFragment extends Fragment implements GroupAdapter.OnGroupClickListener {

    private static final String TAG = "ChatFragment";
    
    private RecyclerView rvChatGroups;
    private View customLoading;
    private TriggerManager triggerManager;
    private GroupAdapter adapter;
    
    private DatabaseReference groupsRef;
    
    private final Map<String, Group> groupMap = new HashMap<>();
    private final List<Group> groupChatsList = new ArrayList<>();
    private final List<Group> filteredList = new ArrayList<>();
    
    private ChildEventListener groupsListener;
    private boolean showingFavoritesOnly = false;
    private final android.os.Handler updateHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable updateRunnable = this::refreshListAndUIInternal;

    private MaterialCardView searchCard;
    private EditText etSearchGroups;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chat, container, false);

        triggerManager = new TriggerManager(requireContext());
        
        FirebaseDatabase db = FirebaseDatabase.getInstance("https://beaconsos-a2dba-default-rtdb.asia-southeast1.firebasedatabase.app/");
        groupsRef = db.getReference("groups");

        rvChatGroups = view.findViewById(R.id.rvChatGroups);
        FloatingActionButton fabNewGroup = view.findViewById(R.id.fabNewGroup);
        customLoading = view.findViewById(R.id.customLoading);
        searchCard = view.findViewById(R.id.searchCard);
        etSearchGroups = view.findViewById(R.id.etSearchGroups);
        ImageButton btnChatMenu = view.findViewById(R.id.btnChatMenu);
        ImageButton btnCloseSearch = view.findViewById(R.id.btnCloseSearch);
        
        setupRecyclerView();
        loadCachedGroups();
        listenForGroups();

        if (etSearchGroups != null) {
            etSearchGroups.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    filter(s.toString());
                }
                @Override public void afterTextChanged(Editable s) {}
            });
        }

        if (btnCloseSearch != null) btnCloseSearch.setOnClickListener(v -> hideSearchBar());
        
        if (fabNewGroup != null) {
            fabNewGroup.setOnClickListener(v -> showCreateGroupDialog());
        }
        
        if (btnChatMenu != null) btnChatMenu.setOnClickListener(this::showPopupMenu);

        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (adapter != null && adapter.getSelectedCount() > 0) {
                    adapter.clearSelection();
                } else if (searchCard != null && searchCard.getVisibility() == View.VISIBLE) {
                    hideSearchBar();
                } else if (showingFavoritesOnly) {
                    showingFavoritesOnly = false;
                    updateUI();
                } else {
                    setEnabled(false);
                    requireActivity().getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateUI();
    }

    private void setupRecyclerView() {
        adapter = new GroupAdapter(filteredList, this);
        rvChatGroups.setLayoutManager(new LinearLayoutManager(getContext()));
        rvChatGroups.setAdapter(adapter);
    }

    private void loadCachedGroups() {
        List<Group> cached = triggerManager.getGroups();
        if (!cached.isEmpty()) {
            groupChatsList.clear();
            groupChatsList.addAll(cached);
            updateUI();
        }
    }

    private void listenForGroups() {
        showLoading(true);
        String userPhone = triggerManager.getUserPhone();

        groupsListener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                handleGroupUpdate(snapshot, userPhone);
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                handleGroupUpdate(snapshot, userPhone);
            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot snapshot) {
                groupMap.remove(snapshot.getKey());
                refreshListAndUI();
            }

            @Override
            public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                showLoading(false);
                Log.e(TAG, "Groups sync error: " + error.getMessage());
            }
        };

        groupsRef.addChildEventListener(groupsListener);
    }

    private synchronized void handleGroupUpdate(DataSnapshot snapshot, String userPhone) {
        Group g = parseGroup(snapshot);
        if (g != null && isUserMember(g, userPhone)) {
            calculateUnreadCount(snapshot, g, userPhone);
            groupMap.put(g.getId(), g);
        } else {
            groupMap.remove(snapshot.getKey());
        }
        
        refreshListAndUI();
    }

    private void refreshListAndUI() {
        updateHandler.removeCallbacks(updateRunnable);
        updateHandler.postDelayed(updateRunnable, 100);
    }

    private synchronized void refreshListAndUIInternal() {
        if (!isAdded()) return;
        groupChatsList.clear();
        groupChatsList.addAll(groupMap.values());
        triggerManager.saveGroups(new ArrayList<>(groupChatsList));
        updateUI();
    }

    private void calculateUnreadCount(DataSnapshot snapshot, Group group, String userPhone) {
        int count = 0;
        DataSnapshot lastMsgSnap = null;
        DataSnapshot messagesSnap = snapshot.child("messages");
        
        if (messagesSnap.exists()) {
            String normalizedUserPhone = TriggerManager.normalizePhone(userPhone);
            // Optimization: Get only the count of unread and the last child directly if possible
            // In Firebase RTDB, we have to iterate, but we can minimize work inside the loop
            for (DataSnapshot msgSnap : messagesSnap.getChildren()) {
                lastMsgSnap = msgSnap;
                // Check if readBy contains our phone without full object parsing
                String senderPhone = msgSnap.child("senderPhone").getValue(String.class);
                if (senderPhone != null && !TriggerManager.isSamePhone(senderPhone, userPhone)) {
                    if (!msgSnap.child("readBy").hasChild(normalizedUserPhone)) {
                        count++;
                    }
                }
            }
        }
        
        group.setUnreadCount(count);
        if (lastMsgSnap != null) {
            ChatMessage lastMsg = lastMsgSnap.getValue(ChatMessage.class);
            if (lastMsg != null) {
                String preview = "";
                String type = lastMsg.getType();
                if (Objects.equals(type, ChatMessage.TYPE_TEXT)) {
                    preview = lastMsg.getMessage();
                } else if (Objects.equals(type, ChatMessage.TYPE_IMAGE)) {
                    preview = "📷 Photo";
                } else if (Objects.equals(type, ChatMessage.TYPE_AUDIO)) {
                    preview = "🎤 Audio";
                }
                
                String sender = lastMsg.getSenderName();
                if (TriggerManager.isSamePhone(lastMsg.getSenderPhone(), userPhone)) {
                    sender = "You";
                }
                
                group.setLastMessage(sender + ": " + preview);
                group.setLastMessageTime(lastMsg.getTimestamp());
            }
        } else {
            group.setLastMessage("No messages yet");
            group.setLastMessageTime(0);
        }
    }

    private void updateUI() {
        showLoading(false);
        java.util.Collections.sort(groupChatsList, (g1, g2) -> {
            if (g1.isPinned() && !g2.isPinned()) return -1;
            if (!g1.isPinned() && g2.isPinned()) return 1;
            
            boolean g1HasNotification = g1.getUnreadCount() > 0 || g1.isMarkedUnread();
            boolean g2HasNotification = g2.getUnreadCount() > 0 || g2.isMarkedUnread();
            if (g1HasNotification && !g2HasNotification) return -1;
            if (!g1HasNotification && g2HasNotification) return 1;
            
            return Long.compare(g2.getLastMessageTime(), g1.getLastMessageTime());
        });

        filter(etSearchGroups != null ? etSearchGroups.getText().toString() : "");
    }

    private Group parseGroup(DataSnapshot snapshot) {
        try {
            Group group = new Group();
            group.setId(snapshot.getKey());
            group.setName(snapshot.child("name").getValue(String.class));
            group.setAdminPhone(snapshot.child("adminPhone").getValue(String.class));
            group.setGroupImageUri(snapshot.child("groupImageUri").getValue(String.class));
            group.setGroupChat(true);
            
            group.setPinned(Objects.equals(Boolean.TRUE, snapshot.child("isPinned").getValue(Boolean.class)));
            group.setFavorite(Objects.equals(Boolean.TRUE, snapshot.child("isFavorite").getValue(Boolean.class)));
            group.setMarkedUnread(Objects.equals(Boolean.TRUE, snapshot.child("isMarkedUnread").getValue(Boolean.class)));

            List<String> members = new ArrayList<>();
            DataSnapshot membersSnap = snapshot.child("memberPhones");
            if (membersSnap.exists()) {
                for (DataSnapshot member : membersSnap.getChildren()) {
                    String phone = member.getValue(String.class);
                    if (phone != null) members.add(phone);
                }
            }
            group.setMemberPhones(members);
            return group;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isUserMember(Group group, String userPhone) {
        if (group.getMemberPhones() == null || userPhone == null) return false;
        for (String phone : group.getMemberPhones()) {
            if (TriggerManager.isSamePhone(phone, userPhone)) return true;
        }
        return false;
    }

    private void filter(String text) {
        filteredList.clear();
        String query = text.toLowerCase().trim();
        for (Group group : groupChatsList) {
            boolean matchesQuery = query.isEmpty() || (group.getName() != null && group.getName().toLowerCase().contains(query));
            boolean matchesFavorite = !showingFavoritesOnly || group.isFavorite();
            
            if (matchesQuery && matchesFavorite) {
                filteredList.add(group);
            }
        }
        if (adapter != null) {
            adapter.updateList(new ArrayList<>(filteredList));
        }
    }

    private void showLoading(boolean show) {
        if (customLoading != null) {
            customLoading.setVisibility(show ? View.VISIBLE : View.GONE);
            if (show) LoadingUtils.startCustomAnimation(customLoading);
            else LoadingUtils.stopCustomAnimation(customLoading);
        }
    }

    @Override
    public void onGroupClick(Group group) {
        if (adapter != null && adapter.getSelectedCount() > 0) {
            adapter.toggleSelection(group.getId());
        } else {
            if (group.getUnreadCount() > 0 || group.isMarkedUnread()) {
                if (group.getUnreadCount() > 0) {
                    markAllMessagesAsRead(group.getId());
                }
                group.setUnreadCount(0);
                group.setMarkedUnread(false);
                updateGroupInFirebase(group);
            }

            Intent intent = new Intent(getActivity(), GroupChatActivity.class);
            intent.putExtra("GROUP_ID", group.getId());
            intent.putExtra("IS_GROUP_CHAT", true);
            startActivity(intent);
        }
    }

    @Override
    public void onGroupIconClick(Group group) {
        showGroupDpOptions(group);
    }

    private void showGroupDpOptions(Group group) {
        String myPhone = triggerManager.getUserPhone();
        boolean isAdmin = TriggerManager.isSamePhone(myPhone, group.getAdminPhone());

        if (isAdmin) {
            String[] options = {"View Image", "Change Image"};
            new AlertDialog.Builder(requireContext())
                    .setTitle(group.getName())
                    .setItems(options, (dialog, which) -> {
                        if (which == 0) {
                            openImagePreview(group.getGroupImageUri());
                        } else {
                            Intent intent = new Intent(getActivity(), GroupSettingsActivity.class);
                            intent.putExtra("GROUP_ID", group.getId());
                            startActivity(intent);
                        }
                    }).show();
        } else {
            openImagePreview(group.getGroupImageUri());
        }
    }

    private void openImagePreview(String imageUri) {
        if (imageUri == null || imageUri.isEmpty()) {
            Toast.makeText(getContext(), "No group image set", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(getActivity(), ImagePreviewActivity.class);
        intent.putExtra("IMAGE_URL", imageUri);
        startActivity(intent);
    }

    private void markAllMessagesAsRead(String groupId) {
        String myPhone = triggerManager.getUserPhone();
        String myNormalizedPhone = TriggerManager.normalizePhone(myPhone);
        DatabaseReference msgsRef = groupsRef.child(groupId).child("messages");
        msgsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot d : snapshot.getChildren()) {
                    ChatMessage msg = d.getValue(ChatMessage.class);
                    if (msg != null && !TriggerManager.isSamePhone(msg.getSenderPhone(), myPhone)) {
                        if (msg.getReadBy() == null || !msg.getReadBy().containsKey(myNormalizedPhone)) {
                            d.getRef().child("readBy").child(myNormalizedPhone).setValue(System.currentTimeMillis());
                        }
                    }
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    @Override
    public void onGroupLongClick(Group group, View view) {
        if (adapter != null) {
            adapter.toggleSelection(group.getId());
        }
    }

    @Override
    public void onSelectionChanged(int count) {
        Log.d(TAG, "Selection count: " + count);
    }

    private void showSearchBar() {
        if (searchCard != null) {
            searchCard.setVisibility(View.VISIBLE);
            etSearchGroups.requestFocus();
            InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(etSearchGroups, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void hideSearchBar() {
        if (searchCard != null) {
            searchCard.setVisibility(View.GONE);
            etSearchGroups.setText("");
            InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(etSearchGroups.getWindowToken(), 0);
        }
    }

    private void showPopupMenu(View view) {
        if (getContext() == null || adapter == null) return;
        ContextThemeWrapper wrapper = new ContextThemeWrapper(getContext(), R.style.GlassyPopupTheme);
        PopupMenu popup = new PopupMenu(wrapper, view);

        if (adapter.getSelectedCount() > 0) {
            popup.getMenuInflater().inflate(R.menu.menu_group_selection, popup.getMenu());
            List<Group> selectedGroups = adapter.getSelectedGroups();
            boolean allRead = true;
            boolean allPinned = true;
            boolean allFavorites = true;
            for (Group g : selectedGroups) {
                if (g.getUnreadCount() > 0 || g.isMarkedUnread()) allRead = false;
                if (!g.isPinned()) allPinned = false;
                if (!g.isFavorite()) allFavorites = false;
            }

            android.view.MenuItem readUnreadItem = popup.getMenu().findItem(R.id.action_mark_read_unread);
            if (readUnreadItem != null) {
                readUnreadItem.setTitle(allRead ? "Mark as unread" : "Mark as read");
            }

            android.view.MenuItem pinItem = popup.getMenu().findItem(R.id.action_pin_unpin);
            if (pinItem != null) {
                pinItem.setTitle(allPinned ? "Unpin" : "Pin");
            }

            android.view.MenuItem favItem = popup.getMenu().findItem(R.id.action_favorite);
            if (favItem != null) {
                favItem.setTitle(allFavorites ? "Remove from favourites" : "Add to favourites");
            }
            
            android.view.MenuItem deleteItem = popup.getMenu().findItem(R.id.action_delete_groups);
            if (deleteItem != null) {
                SpannableString s = new SpannableString("Delete");
                s.setSpan(new ForegroundColorSpan(Color.parseColor("#FF5252")), 0, s.length(), 0);
                deleteItem.setTitle(s);
            }

            popup.setOnMenuItemClickListener(item -> {
                handleSelectionAction(item.getItemId(), selectedGroups);
                return true;
            });
        } else {
            popup.getMenuInflater().inflate(R.menu.menu_chat_fragment, popup.getMenu());
            popup.getMenu().removeItem(R.id.action_delete_all);

            android.view.MenuItem favFilterItem = popup.getMenu().findItem(R.id.action_favorites);
            if (favFilterItem != null) {
                favFilterItem.setTitle(showingFavoritesOnly ? "Show All" : "Favourites");
            }

            popup.setOnMenuItemClickListener(item -> {
                int itemId = item.getItemId();
                if (itemId == R.id.action_search) { showSearchBar(); return true; }
                if (itemId == R.id.action_create_group) { showCreateGroupDialog(); return true; }
                if (itemId == R.id.action_favorites) {
                    showingFavoritesOnly = !showingFavoritesOnly;
                    updateUI();
                    return true;
                }
                return false;
            });
        }
        popup.show();
    }

    private void handleSelectionAction(int itemId, List<Group> groups) {
        if (itemId == R.id.action_mark_read_unread) {
            boolean allRead = true;
            for (Group g : groups) {
                if (g.getUnreadCount() > 0 || g.isMarkedUnread()) {
                    allRead = false;
                    break;
                }
            }
            for (Group g : groups) {
                if (allRead) {
                    g.setMarkedUnread(true);
                } else {
                    if (g.getUnreadCount() > 0) {
                        markAllMessagesAsRead(g.getId());
                    }
                    g.setUnreadCount(0);
                    g.setMarkedUnread(false);
                }
                updateGroupInFirebase(g);
            }
        } else if (itemId == R.id.action_favorite) {
            boolean allFavs = true;
            for (Group g : groups) {
                if (!g.isFavorite()) {
                    allFavs = false;
                    break;
                }
            }
            for (Group g : groups) {
                g.setFavorite(!allFavs);
                updateGroupInFirebase(g);
            }
        } else if (itemId == R.id.action_pin_unpin) {
            boolean allPinned = true;
            for (Group g : groups) {
                if (!g.isPinned()) {
                    allPinned = false;
                    break;
                }
            }
            for (Group g : groups) {
                g.setPinned(!allPinned);
                updateGroupInFirebase(g);
            }
        } else if (itemId == R.id.action_clear_chat) {
            showGlassyConfirmDialog(getString(R.string.clear_chat_title), getString(R.string.clear_chat_msg), getString(R.string.clear), () -> {
                for (Group g : groups) {
                    clearGroupChat(g);
                }
                adapter.clearSelection();
                updateUI();
            });
            return;
        } else if (itemId == R.id.action_exit_groups) {
            showGlassyConfirmDialog(getString(R.string.exit_group_title), getString(R.string.exit_group_msg), getString(R.string.exit), () -> {
                for (Group g : groups) {
                    exitGroup(g);
                }
                adapter.clearSelection();
                updateUI();
            });
            return;
        } else if (itemId == R.id.action_delete_groups) {
            showGlassyConfirmDialog(getString(R.string.delete_chat_title), getString(R.string.delete_chat_msg), getString(R.string.delete), () -> {
                for (Group g : groups) {
                    deleteGroup(g);
                }
                adapter.clearSelection();
                updateUI();
            });
            return;
        }
        adapter.clearSelection();
        updateUI();
    }

    private void showGlassyConfirmDialog(String title, String message, String positive, Runnable onConfirm) {
        if (getActivity() instanceof BaseActivity) {
            ((BaseActivity) getActivity()).showGlassyConfirmDialog(title, message, positive, onConfirm);
        }
    }

    private void updateGroupInFirebase(Group group) {
        groupsRef.child(group.getId()).child("isMarkedUnread").setValue(group.isMarkedUnread());
        groupsRef.child(group.getId()).child("isPinned").setValue(group.isPinned());
        groupsRef.child(group.getId()).child("isFavorite").setValue(group.isFavorite());
        groupsRef.child(group.getId()).child("unreadCount").setValue(group.getUnreadCount());
    }

    private void clearGroupChat(Group group) {
        groupsRef.child(group.getId()).child("messages").removeValue();
    }

    private void exitGroup(Group group) {
        String userPhone = triggerManager.getUserPhone();
        group.getMemberPhones().remove(userPhone);
        if (group.getMemberPhones().isEmpty()) {
            deleteGroup(group);
        } else {
            if (userPhone.equals(group.getAdminPhone())) {
                group.setAdminPhone(group.getMemberPhones().get(0));
            }
            groupsRef.child(group.getId()).setValue(group);
        }
    }

    private void deleteGroup(Group group) {
        if (triggerManager.getUserPhone().equals(group.getAdminPhone())) {
            groupsRef.child(group.getId()).removeValue();
        } else {
            exitGroup(group);
        }
    }

    private void showCreateGroupDialog() {
        if (getActivity() == null) return;
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_create_group, null);

        com.google.android.material.dialog.MaterialAlertDialogBuilder builder = 
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(new ContextThemeWrapper(getActivity(), R.style.GlassyDialog));
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();

        dialogView.setOnTouchListener((v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                View focus = dialog.getCurrentFocus();
                if (focus != null && focus instanceof EditText) {
                    focus.clearFocus();
                    InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
                }
            }
            return false;
        });

        EditText etGroupNameInput = dialogView.findViewById(R.id.etGroupNameInput);
        CountryCodePicker ccpDialog = dialogView.findViewById(R.id.ccpDialog);
        EditText etMemberPhoneInput = dialogView.findViewById(R.id.etMemberPhoneInput);
        View btnAddMember = dialogView.findViewById(R.id.btnAddMember);
        TextView tvMemberStatus = dialogView.findViewById(R.id.tvMemberStatus);
        TextView tvAddedMembers = dialogView.findViewById(R.id.tvAddedMembers);
        Button btnCreate = dialogView.findViewById(R.id.btnCreateGroupAction);
        Button btnCancel = dialogView.findViewById(R.id.btnCancelGroupAction);

        List<String> selectedMemberPhones = new ArrayList<>();
        List<String> selectedMemberNames = new ArrayList<>();

        if (ccpDialog != null) {
            ccpDialog.setDefaultCountryUsingNameCode("PK");
            ccpDialog.resetToDefaultCountry();
            ccpDialog.registerCarrierNumberEditText(etMemberPhoneInput);
        }

        if (btnAddMember != null) {
            btnAddMember.setOnClickListener(v -> {
                if (ccpDialog == null) return;
                String phone = ccpDialog.getFullNumberWithPlus().replaceAll("\\s+", "");
                if (phone.length() < 10) {
                    tvMemberStatus.setText("Invalid phone number");
                    tvMemberStatus.setTextColor(Color.RED);
                    tvMemberStatus.setVisibility(View.VISIBLE);
                    return;
                }

                for (String p : selectedMemberPhones) {
                    if (TriggerManager.isSamePhone(p, phone)) {
                        tvMemberStatus.setText("User already added");
                        tvMemberStatus.setTextColor(Color.BLUE);
                        tvMemberStatus.setVisibility(View.VISIBLE);
                        return;
                    }
                }

                if (TriggerManager.isSamePhone(triggerManager.getUserPhone(), phone)) {
                    tvMemberStatus.setText("You are already in the group");
                    tvMemberStatus.setTextColor(Color.BLUE);
                    tvMemberStatus.setVisibility(View.VISIBLE);
                    return;
                }

                tvMemberStatus.setText("Checking user...");
                tvMemberStatus.setTextColor(Color.GRAY);
                tvMemberStatus.setVisibility(View.VISIBLE);

                String cleanPhoneNoPlus = phone.replace("+", "");
                String searchEmail = cleanPhoneNoPlus + "@velyx.com";

                // LENIENT SEARCH: Fetch all users and compare using isSamePhone (last 10 digits match)
                FirebaseFirestore.getInstance().collection("users")
                        .get()
                        .addOnSuccessListener(queryDocumentSnapshots -> {
                            boolean found = false;
                            for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                                String dbPhone = doc.getString("phone");
                                String dbEmail = doc.getString("email");
                                
                                if (TriggerManager.isSamePhone(dbPhone, phone) || (dbEmail != null && dbEmail.equalsIgnoreCase(searchEmail))) {
                                    handleUserFound(doc, selectedMemberPhones, selectedMemberNames, tvMemberStatus, tvAddedMembers, etMemberPhoneInput);
                                    found = true;
                                    break;
                                }
                            }
                            
                            if (!found) {
                                tvMemberStatus.setText("User doesn't exist on Velyx");
                                tvMemberStatus.setTextColor(Color.RED);
                            }
                        })
                        .addOnFailureListener(e -> {
                            tvMemberStatus.setText("Error checking user");
                            tvMemberStatus.setTextColor(Color.RED);
                        });
            });
        }

        if (btnCreate != null) {
            btnCreate.setOnClickListener(v -> {
                String groupName = etGroupNameInput.getText().toString().trim();
                if (groupName.isEmpty()) {
                    etGroupNameInput.setError(getString(R.string.error_group_name_required));
                    return;
                }
                createGroup(groupName, selectedMemberPhones);
                dialog.dismiss();
            });
        }

        if (btnCancel != null) btnCancel.setOnClickListener(v -> dialog.dismiss());
        
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        }

        dialog.show();
    }

    private void handleUserFound(QueryDocumentSnapshot doc, List<String> selectedPhones, List<String> selectedNames, TextView tvStatus, TextView tvAdded, EditText etInput) {
        String name = doc.getString("name");
        String dbPhone = doc.getString("phone");
        
        if (dbPhone == null) return;

        for (String p : selectedPhones) {
            if (TriggerManager.isSamePhone(p, dbPhone)) {
                tvStatus.setText("User already added");
                tvStatus.setTextColor(Color.BLUE);
                return;
            }
        }

        selectedPhones.add(dbPhone);
        selectedNames.add(name);

        tvStatus.setText("Added: " + name);
        tvStatus.setTextColor(Color.GREEN);

        StringBuilder membersText = new StringBuilder("Members: ");
        for (int i = 0; i < selectedNames.size(); i++) {
            membersText.append(selectedNames.get(i));
            if (i < selectedNames.size() - 1) membersText.append(", ");
        }
        tvAdded.setText(membersText.toString());
        etInput.setText("");
    }

    private void createGroup(String groupName, List<String> memberPhones) {
        String adminPhone = triggerManager.getUserPhone();
        showLoading(true);

        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (isAdded() && customLoading != null && customLoading.getVisibility() == View.VISIBLE) {
                showLoading(false);
                Toast.makeText(getContext(), "Connection timeout. Please check your internet.", Toast.LENGTH_LONG).show();
            }
        }, 10000);

        DatabaseReference newGroupRef = groupsRef.push();
        Group group = new Group(groupName, adminPhone);
        group.setId(newGroupRef.getKey());

        if (memberPhones != null) {
            for (String phone : memberPhones) {
                group.addMember(phone);
            }
        }

        newGroupRef.setValue(group)
            .addOnSuccessListener(aVoid -> {
                String msgKey = newGroupRef.child("messages").push().getKey();
                if (msgKey != null) {
                    ChatMessage systemMsg = new ChatMessage();
                    systemMsg.setId(msgKey);
                    systemMsg.setType(ChatMessage.TYPE_SYSTEM);
                    systemMsg.setMessage("You created group \"" + groupName + "\"");
                    systemMsg.setTimestamp(System.currentTimeMillis());
                    newGroupRef.child("messages").child(msgKey).setValue(systemMsg);
                }

                showLoading(false);
                if (getActivity() != null) {
                    Intent intent = new Intent(getActivity(), GroupChatActivity.class);
                    intent.putExtra("GROUP_ID", group.getId());
                    intent.putExtra("IS_GROUP_CHAT", true);
                    startActivity(intent);
                }
            })
            .addOnFailureListener(e -> {
                showLoading(false);
                Log.e(TAG, "Failed to create group", e);
                Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        if (groupsRef != null && groupsListener != null) groupsRef.removeEventListener(groupsListener);
        updateHandler.removeCallbacks(updateRunnable);
    }
}
