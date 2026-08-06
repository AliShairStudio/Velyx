package com.alert.beacon.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alert.beacon.R;
import com.alert.beacon.data.Contact;
import com.alert.beacon.data.Group;
import com.alert.beacon.data.SearchResult;
import com.alert.beacon.data.TriggerManager;

import java.util.ArrayList;
import java.util.List;

public class SearchFragment extends Fragment {

    private SearchView searchView;
    private RecyclerView rvSearchResults;
    private TextView tvNoResults;
    private SearchAdapter adapter;
    private TriggerManager triggerManager;
    private List<SearchResult> allSearchableItems;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_search, container, false);

        triggerManager = new TriggerManager(requireContext());
        searchView = view.findViewById(R.id.searchView);
        rvSearchResults = view.findViewById(R.id.rvSearchResults);
        tvNoResults = view.findViewById(R.id.tvNoResults);

        setupRecyclerView();
        prepareSearchData();

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                performSearch(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                performSearch(newText);
                return true;
            }
        });

        return view;
    }

    private void setupRecyclerView() {
        adapter = new SearchAdapter(new ArrayList<>());
        rvSearchResults.setLayoutManager(new LinearLayoutManager(getContext()));
        rvSearchResults.setAdapter(adapter);
    }

    private void prepareSearchData() {
        allSearchableItems = new ArrayList<>();

        // Add Settings/Profile items
        allSearchableItems.add(new SearchResult("Profile", "View and edit your profile", android.R.drawable.ic_menu_edit, new Intent(getContext(), ProfileActivity.class)));
        allSearchableItems.add(new SearchResult("Settings", "App preferences and configuration", android.R.drawable.ic_menu_preferences, new Intent(getContext(), SettingsActivity.class)));
        allSearchableItems.add(new SearchResult("Emergency Message", "Edit your SOS message", android.R.drawable.ic_menu_send, new Intent(getContext(), AlertMessageActivity.class)));
        allSearchableItems.add(new SearchResult("Change Password", "Update your account security", android.R.drawable.ic_lock_idle_lock, new Intent(getContext(), ChangePasswordActivity.class)));
        allSearchableItems.add(new SearchResult("Trusted Contacts", "Manage your emergency contacts", android.R.drawable.ic_menu_myplaces, new Intent(getContext(), TrustedContactsActivity.class)));

        // Add Contacts
        List<Contact> contacts = triggerManager.getContacts();
        for (Contact contact : contacts) {
            Intent intent = new Intent(getContext(), EditContactActivity.class);
            intent.putExtra("CONTACT_ID", contact.getId());
            allSearchableItems.add(new SearchResult(contact.getName(), "Contact: " + contact.getPhoneNumber(), android.R.drawable.ic_menu_call, intent));
        }

        // Add Groups
        List<Group> groups = triggerManager.getGroups();
        for (Group group : groups) {
            Intent intent = new Intent(getContext(), GroupChatActivity.class);
            intent.putExtra("GROUP_ID", group.getId());
            allSearchableItems.add(new SearchResult(group.getName(), "Chat Group (" + group.getMemberPhones().size() + " members)", android.R.drawable.ic_menu_agenda, intent));
        }
    }

    private void performSearch(String query) {
        if (query.isEmpty()) {
            adapter.updateData(new ArrayList<>());
            tvNoResults.setVisibility(View.GONE);
            return;
        }

        List<SearchResult> filteredResults = new ArrayList<>();
        for (SearchResult item : allSearchableItems) {
            if (item.getTitle().toLowerCase().contains(query.toLowerCase()) ||
                item.getSubtitle().toLowerCase().contains(query.toLowerCase())) {
                filteredResults.add(item);
            }
        }

        adapter.updateData(filteredResults);
        tvNoResults.setVisibility(filteredResults.isEmpty() ? View.VISIBLE : View.GONE);
    }
}
