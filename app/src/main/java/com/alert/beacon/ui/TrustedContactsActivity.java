package com.alert.beacon.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alert.beacon.R;
import com.alert.beacon.data.Contact;
import com.alert.beacon.data.TriggerManager;
import com.alert.beacon.utils.LoadingUtils;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class TrustedContactsActivity extends BaseActivity {

    private static final String TAG = "TrustedContactsActivity";
    private static final int PERMISSION_REQUEST_CALL = 101;
    private EditText etName, etPhone, etRelation;
    private RecyclerView rvContacts;
    private TriggerManager triggerManager;
    private View cardAddContact;
    private FloatingActionButton btnToggleAdd;
    private String pendingNumber;
    private FirebaseFirestore db;
    private String userId;
    private ListenerRegistration contactsListener;
    private View customLoading;
    private TextView tvLoadingText;

    private final ActivityResultLauncher<Intent> contactPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri contactUri = result.getData().getData();
                    String[] projection = {
                            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                            ContactsContract.CommonDataKinds.Phone.NUMBER
                    };

                    try (Cursor cursor = getContentResolver().query(contactUri, projection, null, null, null)) {
                        if (cursor != null && cursor.moveToFirst()) {
                            int nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                            int numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                            
                            String name = cursor.getString(nameIndex);
                            String number = cursor.getString(numberIndex);
                            
                            etName.setText(name);
                            etPhone.setText(number);
                        }
                    } catch (Exception e) {
                        Toast.makeText(this, "Failed to pick contact", Toast.LENGTH_SHORT).show();
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trusted_contacts);

        triggerManager = new TriggerManager(this);
        db = FirebaseFirestore.getInstance();
        userId = FirebaseAuth.getInstance().getUid();

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        etName = findViewById(R.id.etNewContactName);
        etPhone = findViewById(R.id.etNewPhoneNumber);
        etRelation = findViewById(R.id.etNewRelation);
        rvContacts = findViewById(R.id.rvContacts);
        cardAddContact = findViewById(R.id.cardAddContact);
        btnToggleAdd = findViewById(R.id.btnToggleAddContact);
        customLoading = findViewById(R.id.customLoading);
        tvLoadingText = findViewById(R.id.tvLoadingText);

        btnToggleAdd.setOnClickListener(v -> {
            if (cardAddContact.getVisibility() == View.GONE) {
                cardAddContact.setVisibility(View.VISIBLE);
                btnToggleAdd.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
            } else {
                cardAddContact.setVisibility(View.GONE);
                btnToggleAdd.setImageResource(android.R.drawable.ic_input_add);
            }
        });

        findViewById(R.id.btnAddContact).setOnClickListener(v -> addContact());
        findViewById(R.id.btnPickContact).setOnClickListener(v -> pickContact());

        rvContacts.setLayoutManager(new LinearLayoutManager(this));
        
        observeContacts();
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

    private void observeContacts() {
        if (userId == null) return;
        
        showLoading("Syncing Trusted Contacts");
        
        contactsListener = db.collection("users").document(userId).collection("contacts")
            .addSnapshotListener((value, error) -> {
                hideLoading();
                if (error != null) {
                    Log.e(TAG, "Listen failed.", error);
                    return;
                }

                List<Contact> contacts = new ArrayList<>();
                if (value != null) {
                    for (QueryDocumentSnapshot doc : value) {
                        contacts.add(doc.toObject(Contact.class));
                    }
                }
                triggerManager.saveContacts(contacts);
                updateList(contacts);
            });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (contactsListener != null) {
            contactsListener.remove();
        }
    }

    private void pickContact() {
        Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
        contactPickerLauncher.launch(intent);
    }

    private void addContact() {
        String name = etName.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        String relation = etRelation.getText().toString().trim();

        if (name.isEmpty() || phone.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_fill_all_fields), Toast.LENGTH_SHORT).show();
            return;
        }

        showLoading("Adding Contact");

        com.google.firebase.firestore.DocumentReference newContactRef = db.collection("users").document(userId).collection("contacts").document();
        String contactId = newContactRef.getId();
        
        Contact contact = new Contact(contactId, name, phone, relation);
        
        newContactRef.set(contact)
            .addOnSuccessListener(aVoid -> {
                hideLoading();
                etName.setText("");
                etPhone.setText("");
                etRelation.setText("");
                cardAddContact.setVisibility(View.GONE);
                btnToggleAdd.setImageResource(android.R.drawable.ic_input_add);
                Toast.makeText(TrustedContactsActivity.this, getString(R.string.msg_contact_added), Toast.LENGTH_SHORT).show();
            })
            .addOnFailureListener(e -> {
                hideLoading();
                Log.e(TAG, "Error adding contact", e);
                Toast.makeText(this, "Failed to add contact: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
    }

    private void updateList(List<Contact> contacts) {
        ContactsAdapter adapter = new ContactsAdapter(contacts);
        rvContacts.setAdapter(adapter);
    }

    private void showCallConfirmationDialog(String name, String number) {
        showGlassyConfirmDialog(
                "Confirm Call",
                "Are you sure you want to call " + name + " (" + number + ")?",
                "CALL NOW",
                () -> makeCall(number)
        );
    }

    private void makeCall(String number) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            pendingNumber = number;
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CALL_PHONE}, PERMISSION_REQUEST_CALL);
        } else {
            Intent intent = new Intent(Intent.ACTION_CALL);
            intent.setData(Uri.parse("tel:" + number));
            startActivity(intent);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CALL) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (pendingNumber != null) {
                    makeCall(pendingNumber);
                }
            } else {
                Toast.makeText(this, "Permission denied. Opening dialer.", Toast.LENGTH_SHORT).show();
                if (pendingNumber != null) {
                    Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + pendingNumber));
                    startActivity(intent);
                }
            }
            pendingNumber = null;
        }
    }

    private class ContactsAdapter extends RecyclerView.Adapter<ContactsAdapter.ContactViewHolder> {
        private final List<Contact> contacts;

        public ContactsAdapter(List<Contact> contacts) {
            this.contacts = contacts;
        }

        @NonNull
        @Override
        public ContactViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_contact, parent, false);
            return new ContactViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ContactViewHolder holder, int position) {
            Contact contact = contacts.get(position);
            holder.tvName.setText(contact.getName());
            holder.tvPhone.setText(contact.getPhoneNumber());
            holder.tvRelation.setText(contact.getRelation());

            // Position-based rounding (Pixel OS Style)
            int styleRes;
            if (getItemCount() == 1) {
                styleRes = R.style.RoundedAll;
            } else if (position == 0) {
                styleRes = R.style.RoundedTop;
            } else if (position == getItemCount() - 1) {
                styleRes = R.style.RoundedBottom;
            } else {
                styleRes = R.style.RectangularSmall;
            }

            holder.cardView.setShapeAppearanceModel(
                    ShapeAppearanceModel.builder(holder.itemView.getContext(), styleRes, 0).build()
            );
            
            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(TrustedContactsActivity.this, EditContactActivity.class);
                intent.putExtra("contact_id", contact.getId());
                intent.putExtra("contact_name", contact.getName());
                intent.putExtra("contact_phone", contact.getPhoneNumber());
                intent.putExtra("contact_relation", contact.getRelation());
                startActivity(intent);
            });

            holder.btnCall.setOnClickListener(v -> {
                showCallConfirmationDialog(contact.getName(), contact.getPhoneNumber());
            });

            holder.btnDelete.setOnClickListener(v -> {
                showGlassyConfirmDialog(
                        getString(R.string.delete_confirm_title),
                        getString(R.string.delete_confirm_msg),
                        getString(R.string.yes),
                        () -> {
                            showLoading("Removing Contact");
                            db.collection("users").document(userId).collection("contacts").document(contact.getId()).delete()
                                .addOnSuccessListener(aVoid -> {
                                    hideLoading();
                                    Toast.makeText(TrustedContactsActivity.this, getString(R.string.msg_contact_removed), Toast.LENGTH_SHORT).show();
                                })
                                .addOnFailureListener(e -> {
                                    hideLoading();
                                    Toast.makeText(TrustedContactsActivity.this, "Delete failed", Toast.LENGTH_SHORT).show();
                                });
                        }
                );
            });
        }

        @Override
        public int getItemCount() {
            return contacts.size();
        }

        class ContactViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvPhone, tvRelation;
            ImageButton btnDelete, btnCall;
            MaterialCardView cardView;

            public ContactViewHolder(@NonNull View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tvContactName);
                tvPhone = itemView.findViewById(R.id.tvContactPhone);
                tvRelation = itemView.findViewById(R.id.tvContactRelation);
                btnDelete = itemView.findViewById(R.id.btnDelete);
                btnCall = itemView.findViewById(R.id.btnCallContact);
                cardView = (MaterialCardView) itemView;
            }
        }
    }
}
