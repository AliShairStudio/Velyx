package com.alert.beacon.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alert.beacon.R;
import com.alert.beacon.data.TriggerManager;
import com.alert.beacon.data.User;
import com.alert.beacon.utils.LoadingUtils;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class AdminActivity extends BaseActivity {

    private RecyclerView rvUsers;
    private Button btnLogoutAdmin;
    private TriggerManager triggerManager;
    private DatabaseReference mDatabase;
    private FirebaseAuth mAuth;
    private UserAdapter adapter;
    private List<User> userList;
    private View customLoading;
    private TextView tvLoadingText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        triggerManager = new TriggerManager(this);
        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance("https://beaconsos-a2dba-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference();

        rvUsers = findViewById(R.id.rvUsers);
        btnLogoutAdmin = findViewById(R.id.btnLogoutAdmin);
        customLoading = findViewById(R.id.customLoading);
        tvLoadingText = findViewById(R.id.tvLoadingText);

        rvUsers.setLayoutManager(new LinearLayoutManager(this));
        userList = new ArrayList<>();
        adapter = new UserAdapter(userList);
        rvUsers.setAdapter(adapter);

        fetchUsersFromFirebase();

        btnLogoutAdmin.setOnClickListener(v -> {
            mAuth.signOut();
            triggerManager.clearData();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
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

    private void fetchUsersFromFirebase() {
        showLoading(getString(R.string.msg_loading_users));
        mDatabase.child("users").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                hideLoading();
                userList.clear();
                for (DataSnapshot postSnapshot : snapshot.getChildren()) {
                    User user = postSnapshot.getValue(User.class);
                    if (user != null) {
                        userList.add(user);
                    }
                }
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                hideLoading();
                Toast.makeText(AdminActivity.this, R.string.msg_failed_load_users, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private class UserAdapter extends RecyclerView.Adapter<UserAdapter.UserViewHolder> {
        private List<User> users;

        public UserAdapter(List<User> users) {
            this.users = users;
        }

        @NonNull
        @Override
        public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_user, parent, false);
            return new UserViewHolder(view);
        }

        @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        User user = users.get(position);
        holder.tvName.setText(user.getName());
        holder.tvEmail.setText(user.getEmail());
        holder.tvRole.setText(getString(R.string.label_role_prefix, user.getRole()));

        holder.btnDelete.setOnClickListener(v -> {
            showGlassyConfirmDialog(
                    getString(R.string.msg_delete_user_title),
                    getString(R.string.msg_delete_user_confirm, user.getName()),
                    getString(R.string.delete),
                    () -> {
                        if (user.getId() != null) {
                            showLoading(getString(R.string.msg_deleting_user));
                            mDatabase.child("users").child(user.getId()).removeValue()
                                    .addOnSuccessListener(aVoid -> {
                                        hideLoading();
                                        Toast.makeText(AdminActivity.this, R.string.msg_user_deleted, Toast.LENGTH_SHORT).show();
                                    })
                                    .addOnFailureListener(e -> {
                                        hideLoading();
                                        Toast.makeText(AdminActivity.this, R.string.msg_failed_delete_user, Toast.LENGTH_SHORT).show();
                                    });
                        } else {
                            Toast.makeText(AdminActivity.this, R.string.msg_cannot_delete_user_no_id, Toast.LENGTH_SHORT).show();
                        }
                    }
            );
        });
    }

        @Override
        public int getItemCount() {
            return users.size();
        }

        class UserViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvEmail, tvRole;
            ImageButton btnDelete;

            public UserViewHolder(@NonNull View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tvUserName);
                tvEmail = itemView.findViewById(R.id.tvUserEmail);
                tvRole = itemView.findViewById(R.id.tvUserRole);
                btnDelete = itemView.findViewById(R.id.btnDeleteUser);
            }
        }
    }
}
