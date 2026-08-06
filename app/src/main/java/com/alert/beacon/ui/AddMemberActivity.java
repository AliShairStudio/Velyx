package com.alert.beacon.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alert.beacon.R;
import com.alert.beacon.data.ChatMessage;
import com.alert.beacon.data.TriggerManager;
import com.alert.beacon.data.User;
import com.alert.beacon.utils.LoadingUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AddMemberActivity extends BaseActivity {

    private TextInputEditText etPhoneInput, etNameInput;
    private TextInputLayout tilNameInput;
    private RecyclerView rvContactsList;
    private MaterialButton btnAddMemberDone;
    private TextView tvHeader;
    private String groupId;
    private List<User> allUsers = new ArrayList<>();
    private List<User> filteredUsers = new ArrayList<>();
    private Set<String> selectedPhones = new HashSet<>();
    private ContactAdapter adapter;
    private FirebaseFirestore db;
    private DatabaseReference groupRef;
    private Set<String> existingMembers = new HashSet<>();
    private View customLoading;
    private TextView tvLoadingText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_member);

        groupId = getIntent().getStringExtra("GROUP_ID");
        if (groupId == null) {
            finish();
            return;
        }

        db = FirebaseFirestore.getInstance();
        groupRef = FirebaseDatabase.getInstance("https://beaconsos-a2dba-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference("groups").child(groupId);

        Toolbar toolbar = findViewById(R.id.toolbarAddMember);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        etPhoneInput = findViewById(R.id.etPhoneInput);
        etNameInput = findViewById(R.id.etNameInput);
        tilNameInput = findViewById(R.id.tilNameInput);
        rvContactsList = findViewById(R.id.rvContactsList);
        btnAddMemberDone = findViewById(R.id.btnAddMemberDone);
        tvHeader = findViewById(R.id.tvRecentContactsHeader);
        customLoading = findViewById(R.id.customLoading);
        tvLoadingText = findViewById(R.id.tvLoadingText);

        adapter = new ContactAdapter(filteredUsers);
        rvContactsList.setLayoutManager(new LinearLayoutManager(this));
        rvContactsList.setAdapter(adapter);

        loadAllPotentialUsers();

        etPhoneInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterUsers(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnAddMemberDone.setOnClickListener(v -> addMembers());
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

    private void loadAllPotentialUsers() {
        showLoading("Loading Potential Members");
        groupRef.child("memberPhones").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                existingMembers.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    existingMembers.add(ds.getValue(String.class));
                }

                db.collection("users").get().addOnCompleteListener(task -> {
                    hideLoading();
                    if (task.isSuccessful() && task.getResult() != null) {
                        allUsers.clear();
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            User user = document.toObject(User.class);
                            if (user.getPhone() != null && !existingMembers.contains(user.getPhone())) {
                                allUsers.add(user);
                            }
                        }
                        // Initially show all potential users
                        filteredUsers.clear();
                        filteredUsers.addAll(allUsers);
                        adapter.notifyDataSetChanged();
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                hideLoading();
            }
        });
    }

    private void filterUsers(String query) {
        filteredUsers.clear();
        boolean foundExactly = false;

        if (query.isEmpty()) {
            filteredUsers.addAll(allUsers);
            tilNameInput.setVisibility(View.VISIBLE);
            tvHeader.setText("Suggested Contacts");
        } else {
            for (User user : allUsers) {
                // Use robust phone comparison and name check
                boolean phoneMatch = user.getPhone() != null && (user.getPhone().contains(query) || TriggerManager.isSamePhone(user.getPhone(), query));
                boolean nameMatch = user.getName() != null && user.getName().toLowerCase().contains(query.toLowerCase());
                
                if (phoneMatch || nameMatch) {
                    filteredUsers.add(user);
                    if (TriggerManager.isSamePhone(user.getPhone(), query)) {
                        foundExactly = true;
                    }
                }
            }
            tvHeader.setText("Matching Contacts");
            
            // If we found the exact phone number in our database, hide the name input
            if (foundExactly || filteredUsers.size() > 0) {
                tilNameInput.setVisibility(View.GONE);
            } else {
                tilNameInput.setVisibility(View.VISIBLE);
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void addMembers() {
        String manualPhone = etPhoneInput.getText() != null ? etPhoneInput.getText().toString().trim() : "";
        String manualName = etNameInput.getText() != null ? etNameInput.getText().toString().trim() : "";

        if (!manualPhone.isEmpty() && tilNameInput.getVisibility() == View.VISIBLE && manualName.isEmpty()) {
            Toast.makeText(this, "Please enter a name for the new contact", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!manualPhone.isEmpty()) {
            selectedPhones.add(manualPhone);
        }

        if (selectedPhones.isEmpty()) {
            Toast.makeText(this, "Please select or type a phone number", Toast.LENGTH_SHORT).show();
            return;
        }

        showLoading("Adding Group Members");

        groupRef.child("memberPhones").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<String> currentMembers = new ArrayList<>();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    currentMembers.add(ds.getValue(String.class));
                }

                for (String phone : selectedPhones) {
                    if (!currentMembers.contains(phone)) {
                        currentMembers.add(phone);
                    }
                }

                groupRef.child("memberPhones").setValue(currentMembers).addOnSuccessListener(aVoid -> {
                    // Add system message about added members
                    String msgKey = groupRef.child("messages").push().getKey();
                    if (msgKey != null) {
                        ChatMessage systemMsg = new ChatMessage();
                        systemMsg.setId(msgKey);
                        systemMsg.setType(ChatMessage.TYPE_SYSTEM);
                        systemMsg.setTimestamp(System.currentTimeMillis());
                        
                        StringBuilder sb = new StringBuilder("Members added: ");
                        int i = 0;
                        for (String phone : selectedPhones) {
                            sb.append(phone);
                            if (i < selectedPhones.size() - 1) sb.append(", ");
                            i++;
                        }
                        systemMsg.setMessage(sb.toString());
                        groupRef.child("messages").child(msgKey).setValue(systemMsg);
                    }

                    hideLoading();
                    Toast.makeText(AddMemberActivity.this, "Members added successfully", Toast.LENGTH_SHORT).show();
                    finish();
                }).addOnFailureListener(e -> {
                    hideLoading();
                    Toast.makeText(AddMemberActivity.this, "Failed to add members", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                hideLoading();
            }
        });
    }

    private class ContactAdapter extends RecyclerView.Adapter<ContactAdapter.ViewHolder> {
        private List<User> users;

        ContactAdapter(List<User> users) {
            this.users = users;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_contact_checkbox, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            User user = users.get(position);
            holder.tvName.setText(user.getName());
            holder.tvPhone.setText(user.getPhone());
            
            holder.checkBox.setOnCheckedChangeListener(null);
            holder.checkBox.setChecked(selectedPhones.contains(user.getPhone()));
            
            holder.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    selectedPhones.add(user.getPhone());
                    // When checking a suggested contact, we might want to clear/hide manual input
                    tilNameInput.setVisibility(View.GONE);
                } else {
                    selectedPhones.remove(user.getPhone());
                    if (selectedPhones.isEmpty() && etPhoneInput.getText().toString().isEmpty()) {
                         tilNameInput.setVisibility(View.VISIBLE);
                    }
                }
            });

            holder.itemView.setOnClickListener(v -> holder.checkBox.toggle());
        }

        @Override
        public int getItemCount() {
            return users.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvPhone;
            CheckBox checkBox;

            ViewHolder(View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tvContactName);
                tvPhone = itemView.findViewById(R.id.tvContactPhone);
                checkBox = itemView.findViewById(R.id.cbContact);
            }
        }
    }
}
