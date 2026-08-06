package com.alert.beacon.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alert.beacon.R;
import com.alert.beacon.data.ChatMessage;
import com.alert.beacon.data.Group;
import com.alert.beacon.data.TriggerManager;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class ForwardActivity extends BaseActivity implements GroupAdapter.OnGroupClickListener {

    private RecyclerView rvForwardGroups;
    private GroupAdapter adapter;
    private TriggerManager triggerManager;
    private ChatMessage messageToForward;
    
    private final List<Group> groupList = new ArrayList<>();
    private final List<Group> filteredList = new ArrayList<>();
    private DatabaseReference groupsRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forward);

        messageToForward = (ChatMessage) getIntent().getSerializableExtra("MESSAGE_TO_FORWARD");
        if (messageToForward == null) {
            finish();
            return;
        }

        triggerManager = new TriggerManager(this);
        groupsRef = FirebaseDatabase.getInstance("https://beaconsos-a2dba-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference("groups");

        setupToolbar();
        setupRecyclerView();
        setupSearch();
        listenForGroups();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbarForward);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupRecyclerView() {
        rvForwardGroups = findViewById(R.id.rvForwardGroups);
        adapter = new GroupAdapter(filteredList, this);
        rvForwardGroups.setLayoutManager(new LinearLayoutManager(this));
        rvForwardGroups.setAdapter(adapter);
    }

    private void setupSearch() {
        EditText etSearch = findViewById(R.id.etSearchForward);
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filter(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void listenForGroups() {
        String myPhone = triggerManager.getUserPhone();
        groupsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                groupList.clear();
                for (DataSnapshot s : snapshot.getChildren()) {
                    Group g = parseGroup(s);
                    if (g != null && isUserMember(g, myPhone)) {
                        groupList.add(g);
                    }
                }
                filter("");
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private Group parseGroup(DataSnapshot snapshot) {
        try {
            Group group = new Group();
            group.setId(snapshot.getKey());
            group.setName(snapshot.child("name").getValue(String.class));
            group.setGroupImageUri(snapshot.child("groupImageUri").getValue(String.class));
            List<String> members = new ArrayList<>();
            for (DataSnapshot m : snapshot.child("memberPhones").getChildren()) {
                String p = m.getValue(String.class); if (p != null) members.add(p);
            }
            group.setMemberPhones(members);
            return group;
        } catch (Exception e) { return null; }
    }

    private boolean isUserMember(Group g, String phone) {
        if (g.getMemberPhones() == null) return false;
        for (String p : g.getMemberPhones()) {
            if (TriggerManager.isSamePhone(p, phone)) return true;
        }
        return false;
    }

    private void filter(String query) {
        filteredList.clear();
        String q = query.toLowerCase().trim();
        for (Group g : groupList) {
            if (q.isEmpty() || (g.getName() != null && g.getName().toLowerCase().contains(q))) {
                filteredList.add(g);
            }
        }
        adapter.updateList(filteredList);
    }

    @Override
    public void onGroupClick(Group group) {
        forwardToGroup(group);
    }

    private void forwardToGroup(Group group) {
        DatabaseReference msgRef = groupsRef.child(group.getId()).child("messages").push();
        ChatMessage newMsg = new ChatMessage(
                triggerManager.getUserPhone(),
                triggerManager.getUserName(),
                messageToForward.getMessage(),
                System.currentTimeMillis()
        );
        newMsg.setType(messageToForward.getType());
        newMsg.setFileUri(messageToForward.getFileUri());
        newMsg.setStatus(ChatMessage.STATUS_SENT);
        newMsg.setForwarded(true);
        
        msgRef.setValue(newMsg).addOnSuccessListener(aVoid -> {
            Toast.makeText(this, "Forwarded to " + group.getName(), Toast.LENGTH_SHORT).show();
            finish();
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Failed to forward", Toast.LENGTH_SHORT).show();
        });
    }

    @Override public void onGroupLongClick(Group group, android.view.View view) {}@Override public void onGroupIconClick(Group group) {}
    @Override public void onSelectionChanged(int count) {}
}
