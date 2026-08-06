package com.alert.beacon.ui;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alert.beacon.R;
import com.alert.beacon.data.ChatMessage;
import com.alert.beacon.data.Group;
import com.alert.beacon.data.TriggerManager;
import com.alert.beacon.services.BeaconService;
import com.bumptech.glide.Glide;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.vanniktech.emoji.EmojiEditText;
import com.vanniktech.emoji.EmojiPopup;
import com.vanniktech.emoji.EmojiTheming;
import com.vanniktech.emoji.EmojiView;
import com.vanniktech.emoji.recent.RecentEmojiManager;
import com.vanniktech.emoji.search.SearchEmojiManager;
import com.vanniktech.emoji.variant.VariantEmojiManager;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.List;

public class GroupChatActivity extends BaseActivity implements MessageAdapter.OnMessageClickListener {
    private static final String TAG = "GroupChatActivity";

    private RecyclerView rvMessages;
    private EmojiEditText etMessage;
    private ImageButton btnSend;

    private String chatId;
    private DatabaseReference chatRef;
    private Group currentGroup;
    private TriggerManager triggerManager;
    private MessageAdapter adapter;
    private final List<ChatMessage> messageList = new ArrayList<>();
    private final List<ChatMessage> allMessages = new ArrayList<>();
    private final java.util.Set<String> notifiedMessageIds = new java.util.HashSet<>();
    private boolean isInitialLoad = true;

    private View searchCardChat;
    private android.widget.EditText etSearchChat;
    private TextView tvSearchCount;
    private ImageButton btnSearchUp, btnSearchDown;
    private int currentSearchIndex = -1;

    private ImageView ivToolbarGroupImage;
    private TextView tvToolbarGroupName;
    private EmojiPopup emojiPopup;
    private android.widget.PopupWindow reactionPopup;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_chat);

        chatId = getIntent().getStringExtra("GROUP_ID");
        if (chatId == null) {
            finish();
            return;
        }

        triggerManager = new TriggerManager(this);
        chatRef = FirebaseDatabase.getInstance("https://beaconsos-a2dba-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference("groups").child(chatId);

        setupToolbar();
        initViews();
        loadCachedMessages();
        setupInputListeners();
        setupRecyclerView();
        listenForMessages();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (emojiPopup != null && emojiPopup.isShowing()) {
                    emojiPopup.dismiss();
                } else if (searchCardChat != null && searchCardChat.getVisibility() == View.VISIBLE) {
                    hideSearchBar();
                } else if (adapter != null && adapter.getSelectedCount() > 0) {
                    adapter.clearSelection();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        BeaconService.activeChatId = chatId;
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (chatId != null && chatId.equals(BeaconService.activeChatId)) {
            BeaconService.activeChatId = null;
        }
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbarChat);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        ivToolbarGroupImage = findViewById(R.id.ivToolbarGroupImage);
        if (ivToolbarGroupImage != null) {
            ivToolbarGroupImage.setOnClickListener(v -> {
                if (currentGroup != null) {
                    showGroupDpOptions(currentGroup);
                }
            });
        }
        tvToolbarGroupName = findViewById(R.id.tvToolbarGroupName);
        LinearLayout layoutToolbarClickable = findViewById(R.id.layoutToolbarClickable);
        if (layoutToolbarClickable != null) {
            layoutToolbarClickable.setOnClickListener(v -> openSettings());
        }
    }

    private void openSettings() {
        Intent intent = new Intent(this, GroupSettingsActivity.class);
        intent.putExtra("GROUP_ID", chatId);
        startActivity(intent);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void initViews() {
        rvMessages = findViewById(R.id.rvMessages);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);

        ImageButton btnEmoji = findViewById(R.id.btnEmoji);
        
        searchCardChat = findViewById(R.id.searchCardChat);
        etSearchChat = findViewById(R.id.etSearchChat);
        tvSearchCount = findViewById(R.id.tvSearchCount);
        btnSearchUp = findViewById(R.id.btnSearchUp);
        btnSearchDown = findViewById(R.id.btnSearchDown);
        ImageButton btnCloseSearch = findViewById(R.id.btnCloseSearchChat);

        if (etSearchChat != null) {
            etSearchChat.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    filterMessages(s.toString());
                }
                @Override public void afterTextChanged(Editable s) {}
            });
        }
        if (btnCloseSearch != null) btnCloseSearch.setOnClickListener(v -> hideSearchBar());
        if (btnSearchUp != null) btnSearchUp.setOnClickListener(v -> navigateSearch(-1));
        if (btnSearchDown != null) btnSearchDown.setOnClickListener(v -> navigateSearch(1));

        if (rvMessages != null) {
            rvMessages.setOnTouchListener(new View.OnTouchListener() {
                @android.annotation.SuppressLint("ClickableViewAccessibility")
                @Override
                public boolean onTouch(View v, android.view.MotionEvent event) {
                    if (searchCardChat != null && searchCardChat.getVisibility() == View.VISIBLE) {
                        hideSearchBar();
                    }
                    return false;
                }
            });
        }

        View rootView = findViewById(R.id.rootView);
        emojiPopup = new EmojiPopup(rootView, etMessage);

        btnEmoji.setOnClickListener(v -> emojiPopup.toggle());

        etMessage.setOnClickListener(v -> {
            if (emojiPopup.isShowing()) {
                emojiPopup.toggle();
            }
        });

        rvMessages.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (bottom < oldBottom && !messageList.isEmpty()) {
                rvMessages.postDelayed(() -> rvMessages.smoothScrollToPosition(messageList.size() - 1), 100);
            }
        });
    }

    private void clearChat() {
        showGlassyConfirmDialog(getString(R.string.clear_chat_title), getString(R.string.clear_chat_msg), getString(R.string.clear), () -> {
            if (chatRef != null) {
                chatRef.child("messages").removeValue().addOnSuccessListener(aVoid -> 
                    Toast.makeText(this, R.string.msg_chat_cleared, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void deleteChat() {
        showGlassyConfirmDialog(getString(R.string.delete_chat_title), getString(R.string.delete_chat_msg), getString(R.string.delete), () -> {
            if (chatRef != null) {
                chatRef.removeValue().addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, R.string.msg_chat_deleted, Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        });
    }

    private void exitChat() {
        showGlassyConfirmDialog(getString(R.string.exit_group_title), getString(R.string.exit_group_msg), getString(R.string.exit), () -> {
            if (currentGroup != null && chatRef != null) {
                String myPhone = triggerManager.getUserPhone();
                List<String> members = currentGroup.getMemberPhones();
                if (members != null) {
                    members.remove(myPhone);
                    if (members.isEmpty()) {
                        chatRef.removeValue().addOnSuccessListener(aVoid -> finish());
                    } else {
                        if (myPhone.equals(currentGroup.getAdminPhone())) {
                            chatRef.child("adminPhone").setValue(members.get(0));
                        }
                        chatRef.child("memberPhones").setValue(members).addOnSuccessListener(aVoid -> {
                            Toast.makeText(this, R.string.msg_exited_group, Toast.LENGTH_SHORT).show();
                            finish();
                        });
                    }
                }
            }
        });
    }

    private void showSearchBar() {
        if (searchCardChat != null) {
            searchCardChat.setVisibility(View.VISIBLE);
            etSearchChat.requestFocus();
            etSearchChat.postDelayed(() -> {
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(etSearchChat, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            }, 100);
        }
    }

    private void hideSearchBar() {
        if (searchCardChat != null) {
            searchCardChat.setVisibility(View.GONE);
            etSearchChat.setText("");
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(etSearchChat.getWindowToken(), 0);
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private void filterMessages(String query) {
        messageList.clear();
        String q = query.toLowerCase().trim();
        
        if (adapter != null) {
            adapter.setSearchQuery(q);
        }

        for (ChatMessage m : allMessages) {
            if (q.isEmpty() || (m.getMessage() != null && m.getMessage().toLowerCase().contains(q))) {
                messageList.add(m);
            }
        }
        
        currentSearchIndex = messageList.isEmpty() ? -1 : messageList.size() - 1;

        if (tvSearchCount != null) {
            if (q.isEmpty()) {
                tvSearchCount.setVisibility(View.GONE);
                if (btnSearchUp != null) btnSearchUp.setVisibility(View.GONE);
                if (btnSearchDown != null) btnSearchDown.setVisibility(View.GONE);
            } else {
                tvSearchCount.setVisibility(View.VISIBLE);
                if (btnSearchUp != null) btnSearchUp.setVisibility(messageList.size() > 1 ? View.VISIBLE : View.GONE);
                if (btnSearchDown != null) btnSearchDown.setVisibility(messageList.size() > 1 ? View.VISIBLE : View.GONE);
                updateSearchCountText();
            }
        }

        if (adapter != null) {
            adapter.notifyDataSetChanged();
            if (!messageList.isEmpty() && currentSearchIndex != -1) {
                rvMessages.scrollToPosition(currentSearchIndex);
            }
        }
    }

    private void updateSearchCountText() {
        if (tvSearchCount != null) {
            if (messageList.isEmpty()) {
                tvSearchCount.setText(R.string.search_results_zero);
            } else {
                tvSearchCount.setText(getString(R.string.search_count_format, currentSearchIndex + 1, messageList.size()));
            }
        }
    }

    private void navigateSearch(int direction) {
        if (messageList.isEmpty()) return;
        
        currentSearchIndex += direction;
        if (currentSearchIndex < 0) currentSearchIndex = messageList.size() - 1;
        if (currentSearchIndex >= messageList.size()) currentSearchIndex = 0;
        
        rvMessages.smoothScrollToPosition(currentSearchIndex);
        updateSearchCountText();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (adapter != null && adapter.getSelectedCount() > 0) {
            getMenuInflater().inflate(R.menu.menu_message_selection, menu);
            
            MenuItem forwardItem = menu.findItem(R.id.action_forward);
            if (forwardItem != null) {
                forwardItem.setVisible(adapter.getSelectedCount() == 1);
            }

            List<ChatMessage> selected = adapter.getSelectedMessages();
            boolean allStarred = true;
            boolean allPinned = true;
            for (ChatMessage m : selected) {
                if (!m.isStarred()) allStarred = false;
                if (!m.isPinned()) allPinned = false;
            }

            MenuItem starItem = menu.findItem(R.id.action_star);
            if (starItem != null) {
                starItem.setTitle(allStarred ? R.string.unstar : R.string.star);
            }

            MenuItem pinItem = menu.findItem(R.id.action_pin_msg);
            if (pinItem != null) {
                pinItem.setTitle(allPinned ? R.string.unpin : R.string.pin);
            }
            return true;
        }
        getMenuInflater().inflate(R.menu.menu_chat_activity, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_search_chat) { showSearchBar(); return true; }
        if (id == R.id.action_edit_chat) { openSettings(); return true; }
        if (id == R.id.action_clear_chat) { clearChat(); return true; }
        if (id == R.id.action_exit_chat) { exitChat(); return true; }
        if (id == R.id.action_delete_chat) { deleteChat(); return true; }
        
        if (id == R.id.action_copy) { copySelectedMessages(); return true; }
        if (id == R.id.action_star) { starSelectedMessages(); return true; }
        if (id == R.id.action_pin_msg) { pinSelectedMessages(); return true; }
        if (id == R.id.action_forward) { forwardSelectedMessage(); return true; }
        if (id == R.id.action_delete_msg) { deleteSelectedMessages(); return true; }
        
        return super.onOptionsItemSelected(item);
    }

    private void copySelectedMessages() {
        List<ChatMessage> selected = adapter.getSelectedMessages();
        StringBuilder sb = new StringBuilder();
        for (ChatMessage m : selected) {
            if (ChatMessage.TYPE_TEXT.equals(m.getType())) {
                sb.append(m.getMessage()).append("\n");
            }
        }
        if (sb.length() > 0) {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Copied Messages", sb.toString().trim());
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, R.string.msg_copied, Toast.LENGTH_SHORT).show();
        }
        adapter.clearSelection();
    }

    private void starSelectedMessages() {
        List<ChatMessage> selected = adapter.getSelectedMessages();
        boolean allStarred = true;
        for (ChatMessage m : selected) {
            if (!m.isStarred()) {
                allStarred = false;
                break;
            }
        }
        
        for (ChatMessage m : selected) {
            chatRef.child("messages").child(m.getId()).child("starred").setValue(!allStarred);
        }
        adapter.clearSelection();
        Toast.makeText(this, allStarred ? R.string.msg_messages_unstarred : R.string.msg_messages_starred, Toast.LENGTH_SHORT).show();
    }

    private void pinSelectedMessages() {
        List<ChatMessage> selected = adapter.getSelectedMessages();
        boolean allPinned = true;
        for (ChatMessage m : selected) {
            if (!m.isPinned()) {
                allPinned = false;
                break;
            }
        }

        for (ChatMessage m : selected) {
            chatRef.child("messages").child(m.getId()).child("pinned").setValue(!allPinned);
        }
        adapter.clearSelection();
        Toast.makeText(this, allPinned ? R.string.msg_messages_unpinned : R.string.msg_messages_pinned, Toast.LENGTH_SHORT).show();
    }

    private void deleteSelectedMessages() {
        showGlassyConfirmDialog("Delete Messages", "Are you sure you want to delete the selected message(s)?", "Delete", () -> {
            List<ChatMessage> selected = adapter.getSelectedMessages();
            for (ChatMessage m : selected) {
                chatRef.child("messages").child(m.getId()).removeValue();
            }
            adapter.clearSelection();
            Toast.makeText(this, R.string.msg_messages_deleted, Toast.LENGTH_SHORT).show();
        });
    }

    private void forwardSelectedMessage() {
        List<ChatMessage> selected = adapter.getSelectedMessages();
        if (selected.size() == 1) {
            Intent intent = new Intent(this, ForwardActivity.class);
            intent.putExtra("MESSAGE_TO_FORWARD", selected.get(0));
            startActivity(intent);
            adapter.clearSelection();
        }
    }

    private void setupInputListeners() {
        // Simple click listener for send button.
        // It's always visible now since mic is removed.
        btnSend.setOnClickListener(v -> sendMessage());
        
        etMessage.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void loadCachedMessages() {
        List<ChatMessage> cached = triggerManager.getGroupMessages(chatId);
        if (!cached.isEmpty()) {
            messageList.clear();
            messageList.addAll(cached);
        }
    }

    private void setupRecyclerView() {
        adapter = new MessageAdapter(messageList, triggerManager.getUserPhone(), this);
        LinearLayoutManager lm = new LinearLayoutManager(this);
        lm.setStackFromEnd(true);
        rvMessages.setLayoutManager(lm);
        rvMessages.setAdapter(adapter);
    }

    @Override
    public void onMessageClick(ChatMessage message, View view) {
        // Single click no longer selects the message.
        // It remains for future use (e.g. opening images)
    }

    @Override
    public void onSelectionChanged(int count) {
        if (count == 0 && reactionPopup != null && reactionPopup.isShowing()) {
            reactionPopup.dismiss();
        }
        invalidateOptionsMenu();
        LinearLayout layoutToolbarClickable = findViewById(R.id.layoutToolbarClickable);
        if (layoutToolbarClickable != null) {
            layoutToolbarClickable.setVisibility(count > 0 ? View.GONE : View.VISIBLE);
        }
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(count > 0);
            if (count > 0) {
                getSupportActionBar().setTitle(String.valueOf(count));
            } else {
                getSupportActionBar().setTitle(null);
            }
        }
    }

    private void showReactionPopup(ChatMessage message, View anchor) {
        if (reactionPopup != null && reactionPopup.isShowing()) {
            reactionPopup.dismiss();
        }

        View popupView = getLayoutInflater().inflate(R.layout.layout_reaction_bar, (android.view.ViewGroup) anchor.getParent(), false);
        reactionPopup = new android.widget.PopupWindow(popupView, 
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, false);
        
        reactionPopup.setElevation(10);
        reactionPopup.setOutsideTouchable(true);
        reactionPopup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        
        View.OnClickListener reactionListener = v -> {
            String emoji = "";
            if (v.getId() == R.id.reaction_like) emoji = "👍";
            else if (v.getId() == R.id.reaction_heart) emoji = "❤️";
            else if (v.getId() == R.id.reaction_laugh) emoji = "😂";
            else if (v.getId() == R.id.reaction_wow) emoji = "😮";
            else if (v.getId() == R.id.reaction_sad) emoji = "😢";
            else if (v.getId() == R.id.reaction_pray) emoji = "🙏";
            
            if (!emoji.isEmpty()) {
                addReactionToMessage(message, emoji);
                reactionPopup.dismiss();
                adapter.clearSelection();
            }
        };

        popupView.findViewById(R.id.reaction_like).setOnClickListener(reactionListener);
        popupView.findViewById(R.id.reaction_heart).setOnClickListener(reactionListener);
        popupView.findViewById(R.id.reaction_laugh).setOnClickListener(reactionListener);
        popupView.findViewById(R.id.reaction_wow).setOnClickListener(reactionListener);
        popupView.findViewById(R.id.reaction_sad).setOnClickListener(reactionListener);
        popupView.findViewById(R.id.reaction_pray).setOnClickListener(reactionListener);

        popupView.findViewById(R.id.btn_add_reaction).setOnClickListener(v -> {
            reactionPopup.dismiss();
            showFullEmojiPickerForReaction(message);
        });

        int[] location = new int[2];
        anchor.getLocationOnScreen(location);
        reactionPopup.showAtLocation(anchor, android.view.Gravity.NO_GRAVITY,
                location[0] + (anchor.getWidth() / 2) - 200, location[1] - 150);
    }

    private void showFullEmojiPickerForReaction(ChatMessage message) {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
        EmojiView emojiView = new EmojiView(this);
        emojiView.setUp(
                findViewById(R.id.rootView),
                emoji -> {
                    addReactionToMessage(message, emoji.getUnicode());
                    adapter.clearSelection();
                    bottomSheetDialog.dismiss();
                },
                null,
                null,
                EmojiTheming.Companion.from(this),
                new RecentEmojiManager(this),
                new SearchEmojiManager(),
                new VariantEmojiManager(this),
                null
        );
        bottomSheetDialog.setContentView(emojiView);
        bottomSheetDialog.setOnDismissListener(dialog -> emojiView.tearDown());
        bottomSheetDialog.show();
    }

    private void addReactionToMessage(ChatMessage message, String emoji) {
        String myPhone = triggerManager.getUserPhone();
        DatabaseReference reactionRef = chatRef.child("messages").child(message.getId()).child("reactions").child(myPhone);
        
        if (message.getReactions() != null && emoji.equals(message.getReactions().get(myPhone))) {
            reactionRef.removeValue();
        } else {
            reactionRef.setValue(emoji);
        }
    }

    @Override
    public void onReactionClick(ChatMessage message, String emoji) {
        addReactionToMessage(message, emoji);
    }

    @Override
    public void onMessageLongClick(ChatMessage message, View view) {
        adapter.toggleSelection(message.getId());
        if (adapter.getSelectedCount() == 1) {
            showReactionPopup(message, view);
        }
    }

    private void listenForMessages() {
        if (chatRef == null) return;
        chatRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (isFinishing() || isDestroyed() || !snapshot.exists()) return;

                currentGroup = new Group();
                currentGroup.setId(snapshot.getKey());
                currentGroup.setName(snapshot.child("name").getValue(String.class));
                currentGroup.setAdminPhone(snapshot.child("adminPhone").getValue(String.class));
                currentGroup.setGroupImageUri(snapshot.child("groupImageUri").getValue(String.class));
                currentGroup.setGroupChat(true);
                
                List<String> members = new ArrayList<>();
                for (DataSnapshot m : snapshot.child("memberPhones").getChildren()) {
                    String p = m.getValue(String.class); if (p != null) members.add(p);
                }
                currentGroup.setMemberPhones(members);

                // Clear manual unread mark if it exists
                if (Boolean.TRUE.equals(snapshot.child("isMarkedUnread").getValue(Boolean.class))) {
                    chatRef.child("isMarkedUnread").setValue(false);
                }

                updateToolbarUI();

                allMessages.clear();
                String myPhone = triggerManager.getUserPhone();
                String myNormalizedPhone = TriggerManager.normalizePhone(myPhone);
                DataSnapshot msgs = snapshot.child("messages");
                if (msgs.exists()) {
                    boolean hasNewIncoming = false;
                    int membersCount = currentGroup.getMemberPhones() != null ? currentGroup.getMemberPhones().size() : 0;

                    for (DataSnapshot d : msgs.getChildren()) {
                        ChatMessage msg = d.getValue(ChatMessage.class);
                        if (msg != null) {
                            if (msg.getId() == null) msg.setId(d.getKey());
                            
                            // 1. Mark as READ & DELIVERED if it's incoming
                            if (!TriggerManager.isSamePhone(msg.getSenderPhone(), myPhone)) {
                                if (msg.getReadBy() == null || !msg.getReadBy().containsKey(myNormalizedPhone)) {
                                    d.getRef().child("readBy").child(myNormalizedPhone).setValue(System.currentTimeMillis());
                                }
                                if (msg.getDeliveredTo() == null || !msg.getDeliveredTo().containsKey(myNormalizedPhone)) {
                                    d.getRef().child("deliveredTo").child(myNormalizedPhone).setValue(System.currentTimeMillis());
                                }
                            }

                            // 2. Aggregate status for the sender to see
                            if (TriggerManager.isSamePhone(msg.getSenderPhone(), myPhone) && membersCount > 1) {
                                int deliveredCount = msg.getDeliveredTo() != null ? msg.getDeliveredTo().size() : 0;
                                int readCount = msg.getReadBy() != null ? msg.getReadBy().size() : 0;

                                if (readCount >= membersCount - 1) {
                                    if (msg.getStatus() != ChatMessage.STATUS_SEEN) {
                                        d.getRef().child("status").setValue(ChatMessage.STATUS_SEEN);
                                        msg.setStatus(ChatMessage.STATUS_SEEN);
                                    }
                                } else if (deliveredCount >= membersCount - 1) {
                                    if (msg.getStatus() != ChatMessage.STATUS_DELIVERED) {
                                        d.getRef().child("status").setValue(ChatMessage.STATUS_DELIVERED);
                                        msg.setStatus(ChatMessage.STATUS_DELIVERED);
                                    }
                                }
                            }

                            allMessages.add(msg);
                            
                            // Notify only for truly new messages
                            if (!TriggerManager.isSamePhone(msg.getSenderPhone(), myPhone) && msg.getStatus() < ChatMessage.STATUS_SEEN) {
                                if (!isInitialLoad && !notifiedMessageIds.contains(msg.getId())) {
                                    hasNewIncoming = true;
                                }
                            }
                            if (msg.getId() != null) notifiedMessageIds.add(msg.getId());
                        }
                    }
                    if (hasNewIncoming) {
                        playNotificationSound();
                    }
                }
                isInitialLoad = false;
                filterMessages(etSearchChat != null ? etSearchChat.getText().toString() : "");
                triggerManager.saveGroupMessages(chatId, allMessages);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) { 
                Log.e(TAG, "Sync error: " + error.getMessage());
            }
        });
    }

    private void showGroupDpOptions(Group group) {
        String myPhone = triggerManager.getUserPhone();
        boolean isAdmin = TriggerManager.isSamePhone(myPhone, group.getAdminPhone());

        if (isAdmin) {
            String[] options = {"View Image", "Change Image"};
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(group.getName())
                    .setItems(options, (dialog, which) -> {
                        if (which == 0) {
                            openImagePreview(group.getGroupImageUri());
                        } else {
                            Intent intent = new Intent(this, GroupSettingsActivity.class);
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
            Toast.makeText(this, "No group image set", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(this, ImagePreviewActivity.class);
        intent.putExtra("IMAGE_URL", imageUri);
        startActivity(intent);
    }

    private void updateToolbarUI() {
        if (currentGroup == null) return;
        tvToolbarGroupName.setText(currentGroup.getName());
        if (ivToolbarGroupImage != null) {
            String imageSource = currentGroup.getGroupImageUri();
            if (imageSource == null || imageSource.isEmpty()) {
                ivToolbarGroupImage.setImageResource(R.drawable.profile_pic);
            } else if (imageSource.startsWith("data:image") || imageSource.length() > 500) {
                try {
                    String base64Data = imageSource.contains(",") ? imageSource.substring(imageSource.indexOf(",") + 1) : imageSource;
                    byte[] decodedString = Base64.decode(base64Data, Base64.DEFAULT);
                    Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                    Glide.with(this).load(decodedByte).circleCrop().into(ivToolbarGroupImage);
                } catch (Exception e) {
                    ivToolbarGroupImage.setImageResource(R.drawable.profile_pic);
                }
            } else {
                Glide.with(this)
                        .load(Uri.parse(imageSource))
                        .placeholder(R.drawable.profile_pic)
                        .error(R.drawable.profile_pic)
                        .circleCrop()
                        .into(ivToolbarGroupImage);
            }
        }
    }

    private void sendMessage() {
        Editable editable = etMessage.getText();
        String text = (editable != null) ? editable.toString().trim() : "";
        if (text.isEmpty()) return;
        doSendMessage(text);
        etMessage.setText("");
        etMessage.requestFocus();
        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(etMessage, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
    }

    private void doSendMessage(String text) {
        if (chatRef == null) return;
        String msgKey = chatRef.child("messages").push().getKey();
        if (msgKey == null) return;
        ChatMessage msg = new ChatMessage(triggerManager.getUserPhone(), triggerManager.getUserName(), text, System.currentTimeMillis());
        chatRef.child("messages").child(msgKey).setValue(msg);
    }

    private void playNotificationSound() {
        try {
            String soundUriString = triggerManager.getNotificationSoundUri();
            Uri soundUri;
            
            if (soundUriString != null) {
                soundUri = Uri.parse(soundUriString);
            } else {
                String soundName = triggerManager.getNotificationSound();
                if ("Default".equals(soundName)) {
                    soundUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION);
                } else {
                    android.media.RingtoneManager manager = new android.media.RingtoneManager(this);
                    manager.setType(android.media.RingtoneManager.TYPE_NOTIFICATION);
                    android.database.Cursor cursor = manager.getCursor();
                    soundUri = null;
                    if (cursor != null) {
                        while (cursor.moveToNext()) {
                            if (soundName.equals(cursor.getString(android.media.RingtoneManager.TITLE_COLUMN_INDEX))) {
                                soundUri = Uri.parse(cursor.getString(android.media.RingtoneManager.URI_COLUMN_INDEX) + "/" + cursor.getString(android.media.RingtoneManager.ID_COLUMN_INDEX));
                                break;
                            }
                        }
                        cursor.close();
                    }
                    if (soundUri == null) {
                        soundUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION);
                    }
                }
            }
            android.media.Ringtone r = android.media.RingtoneManager.getRingtone(this, soundUri);
            if (r != null) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    android.media.AudioAttributes aa = new android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build();
                    r.setAudioAttributes(aa);
                }
                r.play();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error playing notification sound", e);
        }
    }

}
