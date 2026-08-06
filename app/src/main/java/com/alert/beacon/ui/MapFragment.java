package com.alert.beacon.ui;

import android.annotation.SuppressLint;
import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.location.Location;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alert.beacon.R;
import com.alert.beacon.data.Group;
import com.alert.beacon.data.TriggerManager;
import com.alert.beacon.data.User;
import com.alert.beacon.utils.LoadingUtils;
import com.alert.beacon.utils.MapUtils;
import com.bumptech.glide.Glide;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.osmdroid.config.Configuration;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class MapFragment extends Fragment {

    private MapView map;
    private FusedLocationProviderClient fusedLocationClient;
    private TriggerManager triggerManager;
    private View customLoading;
    private TextView tvLoadingText;
    private Marker userMarker;
    private final Map<String, Marker> otherUserMarkers = new HashMap<>();
    private DatabaseReference usersRef, groupsRef;
    private ValueEventListener usersListener, groupsListener;
    
    private AutoCompleteTextView autoCompleteGroup;
    private ShapeableImageView ivSelectedGroupIcon;
    private GroupDropdownAdapter groupNamesAdapter;
    private final List<Group> myGroups = new ArrayList<>();
    private String selectedGroupId = null;
    private String userPhone;
    private org.osmdroid.views.overlay.FolderOverlay saferPolygonsOverlay;

    private RecyclerView rvMapMembers;
    private MapMemberAdapter membersAdapter;
    private final List<User> membersList = new ArrayList<>();
    private boolean isMembersExpanded = false;

    private interface OnMemberClickListener {
        void onMemberClick(User user);
    }

    private final androidx.activity.result.ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    initMapLocation();
                } else {
                    Toast.makeText(getContext(), "Location permission is required for the map", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // OSMDroid configuration
        Configuration.getInstance().load(requireContext(), 
                requireContext().getSharedPreferences("osm_pref", Context.MODE_PRIVATE));
        Configuration.getInstance().setUserAgentValue(requireContext().getPackageName());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_map, container, false);

        triggerManager = new TriggerManager(requireContext());
        userPhone = triggerManager.getUserPhone();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext());

        customLoading = view.findViewById(R.id.customLoading);
        tvLoadingText = view.findViewById(R.id.tvLoadingText);

        map = view.findViewById(R.id.map);
        map.setMultiTouchControls(true);
        map.setTilesScaledToDpi(true); // Makes labels readable on high-res screens
        map.getZoomController().setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER);
        map.getController().setZoom(15.0);

        GeoPoint startPoint = new GeoPoint(33.6844, 73.0479);
        map.getController().setCenter(startPoint);

        view.findViewById(R.id.fabMyLocation).setOnClickListener(v -> {
            if (userMarker != null) {
                map.getController().setZoom(18.5); // Deep zoom for precise location
                map.getController().animateTo(userMarker.getPosition());
            } else {
                // If marker doesn't exist yet, fetch location and zoom when received
                showLoading("Locating Device...");
                requestFreshLocationAndZoom();
            }
        });

        autoCompleteGroup = view.findViewById(R.id.autoCompleteGroup);
        View cardGroup = view.findViewById(R.id.cardGroupDropdown);
        autoCompleteGroup.setDropDownAnchor(R.id.cardGroupDropdown);
        cardGroup.post(() -> autoCompleteGroup.setDropDownWidth(cardGroup.getWidth()));
        ivSelectedGroupIcon = view.findViewById(R.id.ivSelectedGroupIcon);
        groupNamesAdapter = new GroupDropdownAdapter(requireContext(), myGroups);
        autoCompleteGroup.setAdapter(groupNamesAdapter);

        // Ensure the dropdown opens when the view is clicked
        autoCompleteGroup.setOnClickListener(v -> autoCompleteGroup.showDropDown());

        autoCompleteGroup.setOnItemClickListener((parent, v, position, id) -> {
            Group selectedGroup = myGroups.get(position);
            selectedGroupId = selectedGroup.getId();
            
            updateSelectedGroupIcon(selectedGroup);
            
            // Clear other user markers when group changes
            for (Marker m : otherUserMarkers.values()) {
                map.getOverlays().remove(m);
            }
            otherUserMarkers.clear();
            
            // Refresh map display
            if (usersRef != null && usersListener != null) {
                usersRef.addListenerForSingleValueEvent(usersListener);
            }
        });

        rvMapMembers = view.findViewById(R.id.rvMapMembers);
        rvMapMembers.setLayoutManager(new LinearLayoutManager(requireContext()));
        membersAdapter = new MapMemberAdapter(requireContext(), membersList, user -> {
            if (user.getLatitude() != 0 && user.getLongitude() != 0) {
                GeoPoint point = new GeoPoint(user.getLatitude(), user.getLongitude());
                map.getController().setZoom(18.5);
                map.getController().animateTo(point);
                // Optionally collapse list after clicking? User didn't ask but often expected. 
                // Let's keep it open for now as requested.
            } else {
                Toast.makeText(getContext(), "Location not available for " + user.getName(), Toast.LENGTH_SHORT).show();
            }
        });
        rvMapMembers.setAdapter(membersAdapter);

        ImageView ivMembersToggle = view.findViewById(R.id.ivMembersToggle);
        view.findViewById(R.id.cardMembersToggle).setOnClickListener(v -> {
            isMembersExpanded = !isMembersExpanded;
            rvMapMembers.setVisibility(isMembersExpanded ? View.VISIBLE : View.GONE);
            ivMembersToggle.setColorFilter(ContextCompat.getColor(requireContext(), 
                    isMembersExpanded ? R.color.success_green : R.color.text_secondary));
            if (isMembersExpanded) {
                if (usersRef != null && usersListener != null) {
                    usersRef.addListenerForSingleValueEvent(usersListener);
                }
            }
        });

        View cardStatusInput = view.findViewById(R.id.cardStatusInputContainer);
        android.widget.EditText etMapStatus = view.findViewById(R.id.etMapStatus);
        ImageView ivDeleteStatus = view.findViewById(R.id.ivDeleteStatus);
        ImageView ivUpdateStatus = view.findViewById(R.id.ivUpdateStatus);

        view.findViewById(R.id.cardStatusToggle).setOnClickListener(v -> {
            if (cardStatusInput.getVisibility() == View.VISIBLE) {
                cardStatusInput.setVisibility(View.GONE);
                InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(etMapStatus.getWindowToken(), 0);
            } else {
                cardStatusInput.setVisibility(View.VISIBLE);
                boolean hasStatus = ivDeleteStatus.getVisibility() == View.VISIBLE;
                if (!hasStatus) {
                    etMapStatus.requestFocus();
                    InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.showSoftInput(etMapStatus, InputMethodManager.SHOW_IMPLICIT);
                }
            }
        });

        ivUpdateStatus.setOnClickListener(v -> {
            String status = etMapStatus.getText().toString().trim();
            updateMyStatusInFirebase(status);
            cardStatusInput.setVisibility(View.GONE);
            InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(etMapStatus.getWindowToken(), 0);
        });

        ivDeleteStatus.setOnClickListener(v -> new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext(), R.style.GlassyDialog)
                .setTitle("Clear Status")
                .setMessage("Do you want to delete your current status?")
                .setPositiveButton("Clear", (dialog, which) -> updateMyStatusInFirebase(""))
                .setNegativeButton("Cancel", null)
                .show());

        etMapStatus.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                boolean hasStatus = ivDeleteStatus.getVisibility() == View.VISIBLE;
                if (!hasStatus) {
                    ivUpdateStatus.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                }
            }
        });

        etMapStatus.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                String status = etMapStatus.getText().toString().trim();
                updateMyStatusInFirebase(status);
                cardStatusInput.setVisibility(View.GONE);
                InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(etMapStatus.getWindowToken(), 0);
                return true;
            }
            return false;
        });

        checkPermissionsAndInit();
        listenForMyGroups();
        initSaferAreas();

        return view;
    }

    private void initSaferAreas() {
        saferPolygonsOverlay = new org.osmdroid.views.overlay.FolderOverlay();
        map.getOverlays().add(saferPolygonsOverlay);
        
        // --- Pakistan Cities (Major & Irregular) ---
        addIrregularSaferArea(getIslamabadBoundary());
        addIrregularSaferArea(getKarachiBoundary());
        addIrregularSaferArea(getLahoreBoundary());

        // --- Pakistan - Other Cities (Organic Sprawl) ---
        addOrganicSaferArea(new GeoPoint(34.0151, 71.5249), 0.13); // Peshawar
        addOrganicSaferArea(new GeoPoint(30.1575, 71.5249), 0.11); // Multan
        addOrganicSaferArea(new GeoPoint(31.4504, 73.1350), 0.13); // Faisalabad
        addOrganicSaferArea(new GeoPoint(30.1798, 66.9750), 0.10); // Quetta
        addOrganicSaferArea(new GeoPoint(32.0836, 72.6711), 0.10); // Sargodha
        addOrganicSaferArea(new GeoPoint(29.3544, 71.6911), 0.09); // Bahawalpur
        addOrganicSaferArea(new GeoPoint(27.7244, 68.8228), 0.07); // Sukkur
        addOrganicSaferArea(new GeoPoint(32.4945, 74.5229), 0.08); // Sialkot
        addOrganicSaferArea(new GeoPoint(32.1877, 74.1945), 0.09); // Gujranwala
        addOrganicSaferArea(new GeoPoint(34.1986, 72.0404), 0.06); // Mardan
        addOrganicSaferArea(new GeoPoint(33.5651, 73.0169), 0.11); // Rawalpindi
        addOrganicSaferArea(new GeoPoint(34.1463, 73.2116), 0.06); // Abbottabad
        addOrganicSaferArea(new GeoPoint(25.3960, 68.3578), 0.09); // Hyderabad
        addOrganicSaferArea(new GeoPoint(31.8981, 73.2731), 0.06); // Pindi Bhattian
        addOrganicSaferArea(new GeoPoint(30.6682, 73.1114), 0.08); // Sahiwal
        addOrganicSaferArea(new GeoPoint(30.8100, 73.4500), 0.07); // Okara
        addOrganicSaferArea(new GeoPoint(31.7131, 73.9783), 0.07); // Sheikhupura
        addOrganicSaferArea(new GeoPoint(31.1167, 74.4500), 0.06); // Kasur
        addOrganicSaferArea(new GeoPoint(33.7800, 72.7200), 0.06); // Wah Cantt
        addOrganicSaferArea(new GeoPoint(31.7167, 72.9833), 0.06); // Chiniot
        addOrganicSaferArea(new GeoPoint(33.7458, 72.8028), 0.06); // Taxila
        addOrganicSaferArea(new GeoPoint(34.3700, 73.4708), 0.07); // Muzaffarabad
        addOrganicSaferArea(new GeoPoint(33.1484, 73.7519), 0.06); // Mirpur
        addOrganicSaferArea(new GeoPoint(32.9328, 73.7269), 0.06); // Jhelum
        addOrganicSaferArea(new GeoPoint(33.5869, 71.4414), 0.07); // Kohat
        addOrganicSaferArea(new GeoPoint(26.2483, 68.4096), 0.07); // Nawabshah
        addOrganicSaferArea(new GeoPoint(28.4195, 70.3025), 0.08); // Rahim Yar Khan
        addOrganicSaferArea(new GeoPoint(30.0489, 70.6355), 0.07); // Dera Ghazi Khan
        addOrganicSaferArea(new GeoPoint(25.5269, 69.0125), 0.06); // Mirpur Khas
        addOrganicSaferArea(new GeoPoint(34.7717, 72.3602), 0.07); // Mingora
        addOrganicSaferArea(new GeoPoint(31.9758, 74.2245), 0.06); // Kamoke
        addOrganicSaferArea(new GeoPoint(30.1500, 72.6833), 0.07); // Burewala
        addOrganicSaferArea(new GeoPoint(31.2681, 72.3181), 0.08); // Jhang

        // --- Punjab - Additional Cities ---
        addOrganicSaferArea(new GeoPoint(32.5742, 74.0754), 0.07); // Gujrat
        addOrganicSaferArea(new GeoPoint(32.5872, 73.4862), 0.06); // Mandi Bahauddin
        addOrganicSaferArea(new GeoPoint(32.0722, 73.6877), 0.06); // Hafizabad
        addOrganicSaferArea(new GeoPoint(33.7667, 72.3667), 0.06); // Attock
        addOrganicSaferArea(new GeoPoint(32.9328, 72.8628), 0.06); // Chakwal
        addOrganicSaferArea(new GeoPoint(32.2967, 72.3525), 0.05); // Khushab
        addOrganicSaferArea(new GeoPoint(32.5853, 71.5436), 0.06); // Mianwali
        addOrganicSaferArea(new GeoPoint(31.6269, 71.0650), 0.06); // Bhakkar
        addOrganicSaferArea(new GeoPoint(30.9614, 70.9392), 0.06); // Layyah
        addOrganicSaferArea(new GeoPoint(30.0703, 71.1933), 0.07); // Muzaffargarh
        addOrganicSaferArea(new GeoPoint(30.3017, 71.9321), 0.06); // Khanewal
        addOrganicSaferArea(new GeoPoint(30.0419, 72.3551), 0.06); // Vehari
        addOrganicSaferArea(new GeoPoint(30.3431, 73.3897), 0.05); // Pakpattan
        addOrganicSaferArea(new GeoPoint(29.9983, 73.2536), 0.06); // Bahawalnagar
        addOrganicSaferArea(new GeoPoint(29.5383, 71.6333), 0.05); // Lodhran
        addOrganicSaferArea(new GeoPoint(29.1035, 70.3250), 0.05); // Rajanpur
        addOrganicSaferArea(new GeoPoint(32.1000, 74.8833), 0.06); // Narowal
        addOrganicSaferArea(new GeoPoint(31.1353, 72.4811), 0.06); // Toba Tek Singh

        // --- Sindh - More Cities ---
        addOrganicSaferArea(new GeoPoint(27.5295, 68.2167), 0.08); // Larkana
        addOrganicSaferArea(new GeoPoint(27.9573, 68.6380), 0.06); // Shikarpur
        addOrganicSaferArea(new GeoPoint(28.2810, 68.4383), 0.06); // Jacobabad
        addOrganicSaferArea(new GeoPoint(27.5295, 68.7592), 0.07); // Khairpur
        addOrganicSaferArea(new GeoPoint(26.7303, 67.7769), 0.06); // Dadu
        addOrganicSaferArea(new GeoPoint(24.6558, 68.8383), 0.06); // Badin
        addOrganicSaferArea(new GeoPoint(24.7475, 67.9235), 0.05); // Thatta
        addOrganicSaferArea(new GeoPoint(28.0060, 69.3150), 0.06); // Ghotki
        addOrganicSaferArea(new GeoPoint(26.6631, 68.0000), 0.05); // Moro
        addOrganicSaferArea(new GeoPoint(26.4258, 67.8631), 0.05); // Sehwan
        addOrganicSaferArea(new GeoPoint(25.3616, 69.7362), 0.06); // Umerkot
        addOrganicSaferArea(new GeoPoint(26.0461, 68.9481), 0.05); // Sanghar
        addOrganicSaferArea(new GeoPoint(28.2439, 69.1825), 0.05); // Kandhkot
        addOrganicSaferArea(new GeoPoint(25.7682, 68.6620), 0.06); // Tando Adam
        addOrganicSaferArea(new GeoPoint(25.4605, 68.7153), 0.06); // Tando Allahyar

        // --- KPK - More Cities ---
        addOrganicSaferArea(new GeoPoint(34.1202, 72.4698), 0.07); // Swabi
        addOrganicSaferArea(new GeoPoint(34.0158, 71.9812), 0.08); // Nowshera
        addOrganicSaferArea(new GeoPoint(34.1481, 71.7406), 0.07); // Charsadda
        addOrganicSaferArea(new GeoPoint(34.3333, 73.2000), 0.07); // Mansehra
        addOrganicSaferArea(new GeoPoint(32.9889, 70.6056), 0.06); // Bannu
        addOrganicSaferArea(new GeoPoint(31.8314, 70.9019), 0.08); // Dera Ismail Khan
        addOrganicSaferArea(new GeoPoint(33.5320, 71.0595), 0.05); // Hangu
        addOrganicSaferArea(new GeoPoint(33.1111, 71.0914), 0.05); // Karak
        addOrganicSaferArea(new GeoPoint(34.5656, 71.9286), 0.06); // Malakand
        addOrganicSaferArea(new GeoPoint(34.8353, 71.7850), 0.06); // Timergara (Dir Lower)
        addOrganicSaferArea(new GeoPoint(35.2074, 71.8768), 0.06); // Upper Dir
        addOrganicSaferArea(new GeoPoint(35.8510, 71.7864), 0.07); // Chitral
        addOrganicSaferArea(new GeoPoint(32.2217, 70.3797), 0.05); // Tank
        addOrganicSaferArea(new GeoPoint(32.6072, 70.9114), 0.06); // Lakki Marwat
        addOrganicSaferArea(new GeoPoint(34.6772, 73.0233), 0.05); // Batagram

        // --- Balochistan - More Cities ---
        addOrganicSaferArea(new GeoPoint(25.1216, 62.3254), 0.09); // Gwadar
        addOrganicSaferArea(new GeoPoint(26.0012, 63.0485), 0.08); // Turbat
        addOrganicSaferArea(new GeoPoint(27.8109, 66.6163), 0.07); // Khuzdar
        addOrganicSaferArea(new GeoPoint(30.9177, 66.4525), 0.06); // Chaman
        addOrganicSaferArea(new GeoPoint(29.5448, 67.8764), 0.06); // Sibi
        addOrganicSaferArea(new GeoPoint(31.3411, 69.4481), 0.06); // Zhob
        addOrganicSaferArea(new GeoPoint(25.0119, 66.8856), 0.07); // Hub
        addOrganicSaferArea(new GeoPoint(30.3705, 68.5979), 0.06); // Loralai
        addOrganicSaferArea(new GeoPoint(25.2631, 63.4692), 0.05); // Pasni
        addOrganicSaferArea(new GeoPoint(25.2088, 64.6349), 0.05); // Ormara

        // --- AJK & GB ---
        addOrganicSaferArea(new GeoPoint(35.9208, 74.3083), 0.08); // Gilgit
        addOrganicSaferArea(new GeoPoint(35.2975, 75.6333), 0.07); // Skardu
        addOrganicSaferArea(new GeoPoint(33.5158, 73.9019), 0.06); // Kotli
        addOrganicSaferArea(new GeoPoint(33.8500, 73.7500), 0.06); // Rawalakot
        addOrganicSaferArea(new GeoPoint(32.9754, 74.0725), 0.05); // Bhimber
        addOrganicSaferArea(new GeoPoint(36.3167, 74.6500), 0.05); // Hunza

        // --- Punjab - Smaller Additional ---
        addOrganicSaferArea(new GeoPoint(33.9070, 73.3943), 0.06); // Murree
        addOrganicSaferArea(new GeoPoint(30.7258, 72.6447), 0.06); // Kamalia
        addOrganicSaferArea(new GeoPoint(29.7958, 72.8577), 0.07); // Chishtian
        addOrganicSaferArea(new GeoPoint(29.6967, 72.5484), 0.06); // Hasilpur
        addOrganicSaferArea(new GeoPoint(31.0214, 73.8528), 0.06); // Pattoki

        // --- Punjab - Extensive Additional ---
        addOrganicSaferArea(new GeoPoint(30.4397, 72.3553), 0.06); // Mian Channu
        addOrganicSaferArea(new GeoPoint(29.1431, 71.2575), 0.07); // Ahmedpur East
        addOrganicSaferArea(new GeoPoint(29.6111, 73.1361), 0.06); // Haroonabad
        addOrganicSaferArea(new GeoPoint(29.1925, 72.8536), 0.06); // Fort Abbas
        addOrganicSaferArea(new GeoPoint(30.8267, 72.0833), 0.06); // Shorkot
        addOrganicSaferArea(new GeoPoint(32.8122, 73.8828), 0.06); // Kharian
        addOrganicSaferArea(new GeoPoint(32.7003, 73.9558), 0.06); // Lalamusa
        addOrganicSaferArea(new GeoPoint(32.4333, 73.5833), 0.06); // Phalia
        addOrganicSaferArea(new GeoPoint(32.2667, 72.9000), 0.06); // Bhalwal
        addOrganicSaferArea(new GeoPoint(32.1931, 73.0283), 0.06); // Kot Momin
        addOrganicSaferArea(new GeoPoint(32.2667, 72.4667), 0.06); // Shahpur
        addOrganicSaferArea(new GeoPoint(32.0500, 72.5333), 0.06); // Sillanwali
        addOrganicSaferArea(new GeoPoint(28.3006, 70.1303), 0.07); // Sadiqabad
        addOrganicSaferArea(new GeoPoint(28.9328, 70.9575), 0.06); // Liaquatpur
        addOrganicSaferArea(new GeoPoint(28.6472, 70.6569), 0.06); // Khanpur
        addOrganicSaferArea(new GeoPoint(29.3833, 70.9167), 0.06); // Alipur
        addOrganicSaferArea(new GeoPoint(29.5167, 70.8500), 0.06); // Jatoi
        addOrganicSaferArea(new GeoPoint(31.2225, 70.9517), 0.06); // Karor Lal Esan
        addOrganicSaferArea(new GeoPoint(30.7042, 70.6508), 0.06); // Taunsa
        addOrganicSaferArea(new GeoPoint(29.8803, 71.2933), 0.06); // Shujaabad
        addOrganicSaferArea(new GeoPoint(30.2708, 71.9161), 0.06); // Jahanian
        addOrganicSaferArea(new GeoPoint(30.4067, 71.8667), 0.06); // Kabirwala
        addOrganicSaferArea(new GeoPoint(32.2867, 71.3328), 0.06); // Piplan
        addOrganicSaferArea(new GeoPoint(32.7831, 72.6975), 0.06); // Kallar Kahar
        addOrganicSaferArea(new GeoPoint(32.9297, 72.4206), 0.06); // Talagang
        addOrganicSaferArea(new GeoPoint(32.5833, 73.0500), 0.06); // Pind Dadan Khan
        addOrganicSaferArea(new GeoPoint(32.9833, 73.6000), 0.06); // Dina
        addOrganicSaferArea(new GeoPoint(33.1228, 73.4286), 0.06); // Sohawa
        addOrganicSaferArea(new GeoPoint(33.2567, 73.3042), 0.06); // Gujar Khan
        addOrganicSaferArea(new GeoPoint(33.4114, 73.3669), 0.06); // Kallar Syedan
        addOrganicSaferArea(new GeoPoint(33.5667, 72.6417), 0.06); // Fateh Jang
        addOrganicSaferArea(new GeoPoint(33.2389, 72.2647), 0.06); // Pindi Gheb
        addOrganicSaferArea(new GeoPoint(33.4333, 72.0167), 0.06); // Jand
        addOrganicSaferArea(new GeoPoint(33.8167, 72.6833), 0.06); // Hassan Abdal
        addOrganicSaferArea(new GeoPoint(33.9167, 72.4833), 0.06); // Hazro
        addOrganicSaferArea(new GeoPoint(31.8000, 74.2500), 0.06); // Muridke
        addOrganicSaferArea(new GeoPoint(31.6444, 74.1958), 0.06); // Ferozewala
        addOrganicSaferArea(new GeoPoint(31.4667, 74.0000), 0.06); // Sharakpur
        addOrganicSaferArea(new GeoPoint(31.4417, 73.7042), 0.06); // Nankana Sahib
        addOrganicSaferArea(new GeoPoint(31.7167, 73.3833), 0.06); // Sangla Hill
        addOrganicSaferArea(new GeoPoint(31.5667, 73.4833), 0.06); // Shahkot
        addOrganicSaferArea(new GeoPoint(32.3278, 74.3411), 0.06); // Daska
        addOrganicSaferArea(new GeoPoint(32.4764, 74.3517), 0.06); // Sambrial
        addOrganicSaferArea(new GeoPoint(32.2681, 74.6672), 0.06); // Pasrur
        addOrganicSaferArea(new GeoPoint(32.4411, 74.1203), 0.06); // Wazirabad
        addOrganicSaferArea(new GeoPoint(30.6722, 73.6514), 0.06); // Deepalpur
        addOrganicSaferArea(new GeoPoint(30.8833, 73.6000), 0.06); // Renala Khurd
        addOrganicSaferArea(new GeoPoint(30.5333, 72.7000), 0.06); // Chichawatni
        addOrganicSaferArea(new GeoPoint(30.2917, 73.0667), 0.06); // Arifwala
        addOrganicSaferArea(new GeoPoint(29.8000, 72.1833), 0.06); // Mailsi

        // --- Sindh - Extensive Additional ---
        addOrganicSaferArea(new GeoPoint(28.0167, 69.5500), 0.06); // Mirpur Mathelo
        addOrganicSaferArea(new GeoPoint(28.0481, 69.7300), 0.06); // Daharki
        addOrganicSaferArea(new GeoPoint(28.1631, 69.7317), 0.06); // Ubauro
        addOrganicSaferArea(new GeoPoint(27.8500, 69.1167), 0.06); // Pano Akil
        addOrganicSaferArea(new GeoPoint(27.6833, 68.9000), 0.06); // Rohri
        addOrganicSaferArea(new GeoPoint(27.3517, 68.5208), 0.06); // Gambat
        addOrganicSaferArea(new GeoPoint(27.2886, 68.5081), 0.06); // Ranipur
        addOrganicSaferArea(new GeoPoint(27.3400, 68.7000), 0.06); // Kot Diji
        addOrganicSaferArea(new GeoPoint(26.8400, 68.1200), 0.06); // Naushahro Feroze
        addOrganicSaferArea(new GeoPoint(27.0600, 68.2100), 0.06); // Kandiaro
        addOrganicSaferArea(new GeoPoint(27.1167, 68.4833), 0.06); // Mehrabpur
        addOrganicSaferArea(new GeoPoint(25.9228, 68.6225), 0.06); // Shahdadpur
        addOrganicSaferArea(new GeoPoint(25.8114, 68.4231), 0.06); // Hala
        addOrganicSaferArea(new GeoPoint(25.5961, 68.4464), 0.06); // Matiari
        addOrganicSaferArea(new GeoPoint(25.3614, 68.3097), 0.06); // Kotri
        addOrganicSaferArea(new GeoPoint(25.4286, 68.2611), 0.06); // Jamshoro
        addOrganicSaferArea(new GeoPoint(27.1814, 67.8219), 0.06); // Mehar
        addOrganicSaferArea(new GeoPoint(27.0328, 67.7314), 0.06); // KN Shah
        addOrganicSaferArea(new GeoPoint(26.6911, 67.6133), 0.06); // Johi
        addOrganicSaferArea(new GeoPoint(27.8017, 68.2861), 0.06); // Ratodero
        addOrganicSaferArea(new GeoPoint(27.6711, 68.3753), 0.06); // Naudero
        addOrganicSaferArea(new GeoPoint(27.9103, 68.5117), 0.06); // Garhi Yasin
        addOrganicSaferArea(new GeoPoint(27.8525, 68.6975), 0.06); // Lakhi Ghulam Shah
        addOrganicSaferArea(new GeoPoint(28.0611, 67.9714), 0.06); // Garhi Khairo
        addOrganicSaferArea(new GeoPoint(28.2417, 68.7750), 0.06); // Thul
        addOrganicSaferArea(new GeoPoint(28.4333, 69.5833), 0.06); // Kashmore
        addOrganicSaferArea(new GeoPoint(28.2789, 68.9950), 0.06); // Tangwani
        addOrganicSaferArea(new GeoPoint(27.8483, 67.9061), 0.06); // Shahdadkot
        addOrganicSaferArea(new GeoPoint(27.5878, 68.0019), 0.06); // Kambar
        addOrganicSaferArea(new GeoPoint(27.3814, 67.9250), 0.06); // Nasirabad

        // --- KPK - Additional Smaller ---
        addOrganicSaferArea(new GeoPoint(34.2833, 71.9333), 0.06); // Takht-i-Bahi
        addOrganicSaferArea(new GeoPoint(34.3475, 72.0736), 0.06); // Katlang
        addOrganicSaferArea(new GeoPoint(34.0700, 72.6200), 0.06); // Topi
        addOrganicSaferArea(new GeoPoint(34.0117, 71.7917), 0.06); // Pabbi
        addOrganicSaferArea(new GeoPoint(33.9575, 72.2150), 0.06); // Jehangira
        addOrganicSaferArea(new GeoPoint(33.9939, 72.1247), 0.06); // Akora Khattak
        addOrganicSaferArea(new GeoPoint(34.0583, 72.0000), 0.06); // Risalpur
        addOrganicSaferArea(new GeoPoint(34.3014, 71.6528), 0.06); // Tangi
        addOrganicSaferArea(new GeoPoint(34.2150, 71.5542), 0.06); // Shabqadar
        addOrganicSaferArea(new GeoPoint(33.8992, 70.1008), 0.06); // Parachinar
        addOrganicSaferArea(new GeoPoint(33.8344, 70.2872), 0.06); // Sadda
        addOrganicSaferArea(new GeoPoint(33.0011, 70.0681), 0.06); // Miran Shah
        addOrganicSaferArea(new GeoPoint(32.2989, 69.5725), 0.06); // Wana
        addOrganicSaferArea(new GeoPoint(31.9303, 70.4578), 0.06); // Kulachi
        addOrganicSaferArea(new GeoPoint(32.1039, 70.9708), 0.06); // Paharpur

        // --- Balochistan - Additional Smaller ---
        addOrganicSaferArea(new GeoPoint(29.5519, 66.0214), 0.06); // Nushki
        addOrganicSaferArea(new GeoPoint(28.8925, 64.4061), 0.06); // Dalbandin
        addOrganicSaferArea(new GeoPoint(28.5833, 65.4167), 0.06); // Kharan
        addOrganicSaferArea(new GeoPoint(29.7997, 66.8453), 0.06); // Mastung
        addOrganicSaferArea(new GeoPoint(29.0225, 66.5914), 0.06); // Kalat

        // --- Punjab - Massive Expansion ---
        addOrganicSaferArea(new GeoPoint(31.8016, 74.2547), 0.06); // Muridke
        addOrganicSaferArea(new GeoPoint(31.4492, 73.7125), 0.06); // Nankana Sahib
        addOrganicSaferArea(new GeoPoint(30.9639, 73.9803), 0.05); // Chunian
        addOrganicSaferArea(new GeoPoint(31.1717, 74.0989), 0.05); // Kot Radha Kishan
        addOrganicSaferArea(new GeoPoint(31.7167, 73.5667), 0.05); // Safdarabad
        addOrganicSaferArea(new GeoPoint(32.0167, 74.0000), 0.05); // Nowshera Virkan
        addOrganicSaferArea(new GeoPoint(32.2667, 74.1500), 0.05); // Ghakhar Mandi
        addOrganicSaferArea(new GeoPoint(32.2667, 73.8167), 0.05); // Alipur Chatha
        addOrganicSaferArea(new GeoPoint(32.3333, 74.9000), 0.05); // Zafarwal
        addOrganicSaferArea(new GeoPoint(32.2625, 75.1583), 0.05); // Shakargarh
        addOrganicSaferArea(new GeoPoint(31.9833, 74.6667), 0.05); // Baddomalhi
        addOrganicSaferArea(new GeoPoint(32.6397, 74.2158), 0.05); // Jalalpur Jattan
        addOrganicSaferArea(new GeoPoint(32.5500, 73.2167), 0.05); // Malakwal
        addOrganicSaferArea(new GeoPoint(31.9750, 72.3294), 0.05); // Sahiwal (Sargodha)
        addOrganicSaferArea(new GeoPoint(32.2908, 72.2822), 0.05); // Jauharabad
        addOrganicSaferArea(new GeoPoint(32.2833, 72.2000), 0.05); // Hadali
        addOrganicSaferArea(new GeoPoint(32.2458, 72.1064), 0.05); // Mitha Tiwana
        addOrganicSaferArea(new GeoPoint(32.5600, 72.1800), 0.05); // Naushahra
        addOrganicSaferArea(new GeoPoint(32.4500, 71.4833), 0.05); // Kundian
        addOrganicSaferArea(new GeoPoint(32.8750, 71.5667), 0.05); // Daud Khel
        addOrganicSaferArea(new GeoPoint(32.9667, 71.5500), 0.05); // Kalabagh
        addOrganicSaferArea(new GeoPoint(32.6833, 71.2833), 0.05); // Isa Khel
        addOrganicSaferArea(new GeoPoint(30.4700, 70.9600), 0.06); // Kot Adu
        addOrganicSaferArea(new GeoPoint(29.6433, 70.5953), 0.05); // Jampur
        addOrganicSaferArea(new GeoPoint(29.2833, 70.4833), 0.05); // Fazilpur
        addOrganicSaferArea(new GeoPoint(28.6833, 69.9500), 0.05); // Rojhan
        addOrganicSaferArea(new GeoPoint(28.9500, 70.3667), 0.05); // Mithankot
        addOrganicSaferArea(new GeoPoint(29.1333, 71.7500), 0.05); // Yazman
        addOrganicSaferArea(new GeoPoint(29.5833, 72.2333), 0.05); // Khairpur Tamewali
        addOrganicSaferArea(new GeoPoint(30.1667, 73.5667), 0.05); // Minchinabad
        addOrganicSaferArea(new GeoPoint(32.7214, 72.9842), 0.05); // Choa Saidan Shah
        addOrganicSaferArea(new GeoPoint(32.6489, 73.0108), 0.05); // Khewra
        addOrganicSaferArea(new GeoPoint(29.8000, 71.7167), 0.05); // Dunyapur
        addOrganicSaferArea(new GeoPoint(31.3833, 71.4333), 0.05); // Mankera
        addOrganicSaferArea(new GeoPoint(32.3275, 71.8906), 0.05); // Quaidabad
        addOrganicSaferArea(new GeoPoint(32.1333, 71.8833), 0.05); // Noorpur Thal
        addOrganicSaferArea(new GeoPoint(32.1556, 71.2611), 0.05); // Kallur Kot
        addOrganicSaferArea(new GeoPoint(31.4286, 74.3461), 0.04); // Kahna
        addOrganicSaferArea(new GeoPoint(31.1167, 74.4500), 0.05); // Raiwind
        addOrganicSaferArea(new GeoPoint(32.1931, 74.1945), 0.05); // Kamoke
        addOrganicSaferArea(new GeoPoint(31.6444, 74.1958), 0.05); // Ferozewala
        addOrganicSaferArea(new GeoPoint(31.8000, 74.2500), 0.06); // Muridke
        addOrganicSaferArea(new GeoPoint(32.4411, 74.1203), 0.05); // Wazirabad
        addOrganicSaferArea(new GeoPoint(32.2681, 74.6672), 0.05); // Pasrur
        addOrganicSaferArea(new GeoPoint(32.4333, 73.5833), 0.05); // Phalia
        addOrganicSaferArea(new GeoPoint(32.2667, 72.9000), 0.05); // Bhalwal
        addOrganicSaferArea(new GeoPoint(32.2667, 72.4667), 0.05); // Shahpur
        addOrganicSaferArea(new GeoPoint(30.3431, 73.3897), 0.05); // Pakpattan
        addOrganicSaferArea(new GeoPoint(30.6722, 73.6514), 0.05); // Depalpur
        addOrganicSaferArea(new GeoPoint(30.8833, 73.6000), 0.05); // Renala Khurd
        addOrganicSaferArea(new GeoPoint(30.5333, 72.7000), 0.05); // Chichawatni
        addOrganicSaferArea(new GeoPoint(29.8000, 72.1833), 0.05); // Mailsi
        addOrganicSaferArea(new GeoPoint(29.7958, 72.8577), 0.05); // Chishtian
        addOrganicSaferArea(new GeoPoint(29.1431, 71.2575), 0.05); // Ahmedpur East
        addOrganicSaferArea(new GeoPoint(33.1228, 73.4286), 0.05); // Sohawa
        addOrganicSaferArea(new GeoPoint(33.2567, 73.3042), 0.05); // Gujar Khan
        addOrganicSaferArea(new GeoPoint(33.4114, 73.3669), 0.05); // Kallar Syedan
        addOrganicSaferArea(new GeoPoint(33.8167, 72.6833), 0.05); // Hassan Abdal
        addOrganicSaferArea(new GeoPoint(33.5667, 72.6417), 0.05); // Fateh Jang
        addOrganicSaferArea(new GeoPoint(33.2389, 72.2647), 0.05); // Pindi Gheb
        addOrganicSaferArea(new GeoPoint(33.4333, 72.0167), 0.05); // Jand
        addOrganicSaferArea(new GeoPoint(32.9297, 72.4206), 0.05); // Talagang

        // --- Sindh - Massive Expansion ---
        addOrganicSaferArea(new GeoPoint(25.8114, 68.4231), 0.05); // Hala
        addOrganicSaferArea(new GeoPoint(25.5961, 68.4464), 0.05); // Matiari
        addOrganicSaferArea(new GeoPoint(25.3614, 68.3097), 0.05); // Kotri
        addOrganicSaferArea(new GeoPoint(25.4286, 68.2611), 0.05); // Jamshoro
        addOrganicSaferArea(new GeoPoint(24.7475, 67.9235), 0.05); // Thatta
        addOrganicSaferArea(new GeoPoint(24.8719, 67.6167), 0.04); // Gharo
        addOrganicSaferArea(new GeoPoint(24.3644, 67.9272), 0.05); // Sujawal
        addOrganicSaferArea(new GeoPoint(24.6558, 68.8383), 0.05); // Badin
        addOrganicSaferArea(new GeoPoint(24.5000, 69.1667), 0.05); // Matli
        addOrganicSaferArea(new GeoPoint(24.9667, 69.0000), 0.05); // Tando Bago
        addOrganicSaferArea(new GeoPoint(25.1500, 68.5333), 0.05); // Tando Muhammad Khan
        addOrganicSaferArea(new GeoPoint(25.3616, 69.7362), 0.05); // Umerkot
        addOrganicSaferArea(new GeoPoint(25.5167, 69.7833), 0.05); // Chachro
        addOrganicSaferArea(new GeoPoint(24.8333, 70.1333), 0.05); // Mithi
        addOrganicSaferArea(new GeoPoint(24.4667, 70.7167), 0.05); // Islamkot
        addOrganicSaferArea(new GeoPoint(24.3333, 70.9167), 0.05); // Nagarparkar
        addOrganicSaferArea(new GeoPoint(25.5269, 69.0125), 0.05); // Mirpur Khas
        addOrganicSaferArea(new GeoPoint(25.7682, 68.6620), 0.05); // Tando Adam
        addOrganicSaferArea(new GeoPoint(25.9228, 68.6225), 0.05); // Shahdadpur
        addOrganicSaferArea(new GeoPoint(26.0461, 68.9481), 0.05); // Sanghar
        addOrganicSaferArea(new GeoPoint(26.2483, 68.4096), 0.06); // Nawabshah
        addOrganicSaferArea(new GeoPoint(26.6631, 68.0000), 0.05); // Moro
        addOrganicSaferArea(new GeoPoint(26.4258, 67.8631), 0.05); // Sehwan
        addOrganicSaferArea(new GeoPoint(26.7303, 67.7769), 0.05); // Dadu
        addOrganicSaferArea(new GeoPoint(27.1814, 67.8219), 0.05); // Mehar
        addOrganicSaferArea(new GeoPoint(27.0328, 67.7314), 0.05); // KN Shah
        addOrganicSaferArea(new GeoPoint(27.5295, 68.2167), 0.06); // Larkana
        addOrganicSaferArea(new GeoPoint(27.8017, 68.2861), 0.05); // Ratodero
        addOrganicSaferArea(new GeoPoint(27.9573, 68.6380), 0.05); // Shikarpur
        addOrganicSaferArea(new GeoPoint(28.2810, 68.4383), 0.05); // Jacobabad
        addOrganicSaferArea(new GeoPoint(27.5295, 68.7592), 0.06); // Khairpur
        addOrganicSaferArea(new GeoPoint(27.7244, 68.8228), 0.06); // Sukkur
        addOrganicSaferArea(new GeoPoint(28.0060, 69.3150), 0.05); // Ghotki
        addOrganicSaferArea(new GeoPoint(28.0167, 69.5500), 0.05); // Mirpur Mathelo
        addOrganicSaferArea(new GeoPoint(28.4333, 69.5833), 0.05); // Kashmore

        // --- KPK - Massive Expansion ---
        addOrganicSaferArea(new GeoPoint(34.0158, 71.9812), 0.05); // Nowshera
        addOrganicSaferArea(new GeoPoint(34.1202, 72.4698), 0.05); // Swabi
        addOrganicSaferArea(new GeoPoint(34.1481, 71.7406), 0.05); // Charsadda
        addOrganicSaferArea(new GeoPoint(34.3333, 73.2000), 0.06); // Mansehra
        addOrganicSaferArea(new GeoPoint(34.1463, 73.2116), 0.05); // Abbottabad
        addOrganicSaferArea(new GeoPoint(34.0117, 71.7917), 0.05); // Pabbi
        addOrganicSaferArea(new GeoPoint(34.1986, 72.0404), 0.05); // Mardan
        addOrganicSaferArea(new GeoPoint(34.7717, 72.3602), 0.06); // Mingora
        addOrganicSaferArea(new GeoPoint(34.8353, 71.7850), 0.05); // Timergara
        addOrganicSaferArea(new GeoPoint(35.2074, 71.8768), 0.05); // Upper Dir
        addOrganicSaferArea(new GeoPoint(35.8510, 71.7864), 0.06); // Chitral
        addOrganicSaferArea(new GeoPoint(34.3700, 73.4708), 0.06); // Muzaffarabad
        addOrganicSaferArea(new GeoPoint(33.5869, 71.4414), 0.06); // Kohat
        addOrganicSaferArea(new GeoPoint(33.5320, 71.0595), 0.05); // Hangu
        addOrganicSaferArea(new GeoPoint(33.1111, 71.0914), 0.05); // Karak
        addOrganicSaferArea(new GeoPoint(32.9889, 70.6056), 0.05); // Bannu
        addOrganicSaferArea(new GeoPoint(32.6072, 70.9114), 0.05); // Lakki Marwat
        addOrganicSaferArea(new GeoPoint(31.8314, 70.9019), 0.06); // Dera Ismail Khan
        addOrganicSaferArea(new GeoPoint(32.2217, 70.3797), 0.05); // Tank
        addOrganicSaferArea(new GeoPoint(34.6772, 73.0233), 0.05); // Batagram
        addOrganicSaferArea(new GeoPoint(34.1200, 71.1300), 0.05); // Jamrud
        addOrganicSaferArea(new GeoPoint(34.1100, 71.0400), 0.05); // Landi Kotal
        addOrganicSaferArea(new GeoPoint(33.0011, 70.0681), 0.05); // Miran Shah
        addOrganicSaferArea(new GeoPoint(32.3000, 70.0000), 0.05); // Mir Ali
        addOrganicSaferArea(new GeoPoint(32.2989, 69.5725), 0.05); // Wana

        // --- Balochistan - Massive Expansion ---
        addOrganicSaferArea(new GeoPoint(25.1216, 62.3254), 0.07); // Gwadar
        addOrganicSaferArea(new GeoPoint(26.0012, 63.0485), 0.06); // Turbat
        addOrganicSaferArea(new GeoPoint(27.8109, 66.6163), 0.06); // Khuzdar
        addOrganicSaferArea(new GeoPoint(30.9177, 66.4525), 0.05); // Chaman
        addOrganicSaferArea(new GeoPoint(29.5448, 67.8764), 0.05); // Sibi
        addOrganicSaferArea(new GeoPoint(31.3411, 69.4481), 0.05); // Zhob
        addOrganicSaferArea(new GeoPoint(25.0119, 66.8856), 0.06); // Hub
        addOrganicSaferArea(new GeoPoint(30.3705, 68.5979), 0.05); // Loralai
        addOrganicSaferArea(new GeoPoint(25.2631, 63.4692), 0.04); // Pasni
        addOrganicSaferArea(new GeoPoint(25.2088, 64.6349), 0.04); // Ormara
        addOrganicSaferArea(new GeoPoint(29.5519, 66.0214), 0.05); // Nushki
        addOrganicSaferArea(new GeoPoint(28.8925, 64.4061), 0.05); // Dalbandin
        addOrganicSaferArea(new GeoPoint(28.5833, 65.4167), 0.05); // Kharan
        addOrganicSaferArea(new GeoPoint(29.7997, 66.8453), 0.05); // Mastung

        // --- Global City Sprawl Approximations ---
        addOrganicSaferArea(new GeoPoint(40.7128, -74.0060), 0.25); // New York
        addOrganicSaferArea(new GeoPoint(51.5074, -0.1278), 0.22);  // London
        addOrganicSaferArea(new GeoPoint(35.6762, 139.6503), 0.30); // Tokyo
        addOrganicSaferArea(new GeoPoint(28.6139, 77.2090), 0.24);  // Delhi
        addOrganicSaferArea(new GeoPoint(48.8566, 2.3522), 0.15);   // Paris
        addOrganicSaferArea(new GeoPoint(1.3521, 103.8198), 0.18);  // Singapore
        addOrganicSaferArea(new GeoPoint(25.2048, 55.2708), 0.16);  // Dubai
        addOrganicSaferArea(new GeoPoint(34.0522, -118.2437), 0.35);// Los Angeles
        addOrganicSaferArea(new GeoPoint(52.5200, 13.4050), 0.20);  // Berlin
        addOrganicSaferArea(new GeoPoint(41.9028, 12.4964), 0.18);  // Rome
        addOrganicSaferArea(new GeoPoint(40.4168, -3.7038), 0.18);  // Madrid
        addOrganicSaferArea(new GeoPoint(-33.8688, 151.2093), 0.22); // Sydney
        addOrganicSaferArea(new GeoPoint(43.6532, -79.3832), 0.20);  // Toronto
        addOrganicSaferArea(new GeoPoint(37.5665, 126.9780), 0.22);  // Seoul
        addOrganicSaferArea(new GeoPoint(39.9042, 116.4074), 0.25);  // Beijing
        addOrganicSaferArea(new GeoPoint(55.7558, 37.6173), 0.22);  // Moscow
        addOrganicSaferArea(new GeoPoint(30.0444, 31.2357), 0.18);  // Cairo
        addOrganicSaferArea(new GeoPoint(41.0082, 28.9784), 0.20);  // Istanbul
        addOrganicSaferArea(new GeoPoint(22.3193, 114.1694), 0.15);  // Hong Kong
        addOrganicSaferArea(new GeoPoint(19.4326, -99.1332), 0.25);  // Mexico City
    }

    private List<GeoPoint> getIslamabadBoundary() {
        List<GeoPoint> points = new ArrayList<>();
        points.add(new GeoPoint(33.755, 73.015));
        points.add(new GeoPoint(33.745, 73.125));
        points.add(new GeoPoint(33.685, 73.195));
        points.add(new GeoPoint(33.625, 73.145));
        points.add(new GeoPoint(33.595, 73.025));
        points.add(new GeoPoint(33.645, 72.935));
        points.add(new GeoPoint(33.725, 72.965));
        return points;
    }

    private List<GeoPoint> getKarachiBoundary() {
        List<GeoPoint> points = new ArrayList<>();
        points.add(new GeoPoint(24.882, 66.895));
        points.add(new GeoPoint(24.945, 67.022));
        points.add(new GeoPoint(25.031, 67.134));
        points.add(new GeoPoint(24.964, 67.255));
        points.add(new GeoPoint(24.852, 67.299));
        points.add(new GeoPoint(24.791, 67.192));
        points.add(new GeoPoint(24.822, 67.064));
        points.add(new GeoPoint(24.835, 66.931));
        return points;
    }

    private List<GeoPoint> getLahoreBoundary() {
        List<GeoPoint> points = new ArrayList<>();
        points.add(new GeoPoint(31.590, 74.280));
        points.add(new GeoPoint(31.610, 74.380));
        points.add(new GeoPoint(31.540, 74.450));
        points.add(new GeoPoint(31.450, 74.440));
        points.add(new GeoPoint(31.390, 74.320));
        points.add(new GeoPoint(31.430, 74.210));
        points.add(new GeoPoint(31.520, 74.220));
        return points;
    }

    private void addIrregularSaferArea(List<GeoPoint> points) {
        org.osmdroid.views.overlay.Polygon polygon = new org.osmdroid.views.overlay.Polygon();
        polygon.setPoints(points);
        polygon.getFillPaint().setColor(android.graphics.Color.argb(45, 34, 139, 34)); // Forest Green Shade
        polygon.getOutlinePaint().setColor(android.graphics.Color.argb(0, 0, 0, 0));
        polygon.getOutlinePaint().setStrokeWidth(0f);
        saferPolygonsOverlay.add(polygon);
    }

    private void addOrganicSaferArea(GeoPoint center, double sizeDegree) {
        // Create an irregular organic polygon around the center to simulate city sprawl
        List<GeoPoint> points = new ArrayList<>();
        int numPoints = 12;
        java.util.Random random = new java.util.Random((long) (center.getLatitude() * center.getLongitude()));
        
        for (int i = 0; i < numPoints; i++) {
            double angle = Math.toRadians(i * (360.0 / numPoints));
            double distance = sizeDegree * (0.8 + random.nextDouble() * 0.4); // Randomize radius
            double lat = center.getLatitude() + Math.cos(angle) * distance;
            double lng = center.getLongitude() + Math.sin(angle) * distance;
            points.add(new GeoPoint(lat, lng));
        }
        addIrregularSaferArea(points);
    }

    private void updateMyStatusInFirebase(String status) {
        if (userPhone.isEmpty()) return;
        DatabaseReference myRef = FirebaseDatabase.getInstance("https://beaconsos-a2dba-default-rtdb.asia-southeast1.firebasedatabase.app/")
                .getReference("users")
                .child(userPhone);
        
        Map<String, Object> updates = new HashMap<>();
        updates.put("statusText", status);
        updates.put("statusTimestamp", System.currentTimeMillis());
        
        myRef.updateChildren(updates).addOnSuccessListener(aVoid -> Toast.makeText(getContext(), "Status updated", Toast.LENGTH_SHORT).show());
    }

    private void checkPermissionsAndInit() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            initMapLocation();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    private void initMapLocation() {
        boolean appEnabled = triggerManager.isLocationPermissionEnabled();
        if (!appEnabled) {
            Toast.makeText(getContext(), "Location map view is disabled in App Permissions", Toast.LENGTH_SHORT).show();
            return;
        }

        getDeviceLocation();
        listenToUsers();
    }

    private void listenForMyGroups() {
        groupsRef = FirebaseDatabase.getInstance("https://beaconsos-a2dba-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference("groups");
        groupsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded()) return;
                myGroups.clear();
                
                // Add "Everyone" option
                Group everyone = new Group();
                everyone.setId("everyone");
                everyone.setName("Everyone");
                myGroups.add(everyone);

                for (DataSnapshot groupSnap : snapshot.getChildren()) {
                    DataSnapshot membersSnap = groupSnap.child("memberPhones");
                    boolean memberFound = false;
                    for (DataSnapshot m : membersSnap.getChildren()) {
                        if (TriggerManager.isSamePhone(userPhone, m.getValue(String.class))) {
                            memberFound = true;
                            break;
                        }
                    }

                    if (memberFound) {
                        Group group = new Group();
                        group.setId(groupSnap.getKey());
                        group.setName(groupSnap.child("name").getValue(String.class));
                        group.setGroupImageUri(groupSnap.child("groupImageUri").getValue(String.class));
                        
                        List<String> members = new ArrayList<>();
                        for (DataSnapshot m : membersSnap.getChildren()) {
                            members.add(m.getValue(String.class));
                        }
                        group.setMemberPhones(members);
                        myGroups.add(group);
                    }
                }
                
                if (selectedGroupId == null && !myGroups.isEmpty()) {
                    selectedGroupId = "everyone";
                    autoCompleteGroup.setText(myGroups.get(0).getName(), false);
                    updateSelectedGroupIcon(myGroups.get(0));
                }

                groupNamesAdapter.notifyDataSetChanged();
            }

            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        groupsRef.addValueEventListener(groupsListener);
    }

    private void listenToUsers() {
        usersRef = FirebaseDatabase.getInstance("https://beaconsos-a2dba-default-rtdb.asia-southeast1.firebasedatabase.app/")
                .getReference("users");
        
        usersListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded()) return;
                
                List<User> newMembers = new ArrayList<>();
                Group currentGroup = getSelectedGroup();
                List<String> allowedPhones = (currentGroup != null && !"everyone".equals(currentGroup.getId())) 
                        ? currentGroup.getMemberPhones() : getAllMyGroupsMembers();

                for (DataSnapshot userSnap : snapshot.getChildren()) {
                    String phone = userSnap.getKey();
                    if (phone == null) continue;

                    // Filter by group membership
                    if (allowedPhones != null && !allowedPhones.contains(phone)) {
                        if (!Objects.equals(phone, userPhone)) removeUserMarker(phone);
                        continue;
                    }

                    User user = userSnap.getValue(User.class);
                    if (user != null) {
                        user.setPhone(phone);
                        user.setInSaferArea(isUserInSaferArea(user));
                        newMembers.add(user);
                    }

                    if (Objects.equals(phone, userPhone)) {
                        String myStatus = userSnap.child("statusText").getValue(String.class);
                        Long myTime = userSnap.child("statusTimestamp").getValue(Long.class);
                        updateMyStatusUI(myStatus, myTime);
                        continue;
                    }

                    Double lat = userSnap.child("latitude").getValue(Double.class);
                    Double lng = userSnap.child("longitude").getValue(Double.class);
                    String name = userSnap.child("name").getValue(String.class);
                    String imageBase64 = userSnap.child("profileImageUri").getValue(String.class);
                    Boolean isOnline = userSnap.child("online").getValue(Boolean.class);
                    String statusText = userSnap.child("statusText").getValue(String.class);
                    Long statusTime = userSnap.child("statusTimestamp").getValue(Long.class);

                    if (lat != null && lng != null) {
                        updateOtherUserMarker(phone, name, lat, lng, imageBase64, Objects.equals(isOnline, true), statusText, Objects.requireNonNullElse(statusTime, 0L));
                    } else {
                        removeUserMarker(phone);
                    }
                }
                
                if (membersAdapter != null) {
                    membersAdapter.updateMembers(newMembers);
                }
                
                map.invalidate();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("MapFragment", "Firebase Error: " + error.getMessage());
            }
        };
        usersRef.addValueEventListener(usersListener);
    }

    private boolean isUserInSaferArea(User user) {
        if (user == null || user.getLatitude() == 0 || user.getLongitude() == 0) return false;
        GeoPoint point = new GeoPoint(user.getLatitude(), user.getLongitude());
        if (saferPolygonsOverlay == null) return false;

        for (org.osmdroid.views.overlay.Overlay overlay : saferPolygonsOverlay.getItems()) {
            if (overlay instanceof org.osmdroid.views.overlay.Polygon) {
                org.osmdroid.views.overlay.Polygon polygon = (org.osmdroid.views.overlay.Polygon) overlay;
                List<GeoPoint> points = polygon.getActualPoints();
                if (points != null && isPointInPolygon(point, points)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isPointInPolygon(GeoPoint point, List<GeoPoint> polygon) {
        int intersectCount = 0;
        for (int j = 0; j < polygon.size(); j++) {
            GeoPoint a = polygon.get(j);
            GeoPoint b = polygon.get((j + 1) % polygon.size());
            if (rayCastIntersect(point, a, b)) {
                intersectCount++;
            }
        }
        return (intersectCount % 2) == 1;
    }

    private boolean rayCastIntersect(GeoPoint tap, GeoPoint vertA, GeoPoint vertB) {
        double aY = vertA.getLatitude();
        double bY = vertB.getLatitude();
        double aX = vertA.getLongitude();
        double bX = vertB.getLongitude();
        double pY = tap.getLatitude();
        double pX = tap.getLongitude();

        if ((aY > pY && bY > pY) || (aY < pY && bY < pY) || (aX < pX && bX < pX)) {
            return false;
        }

        double m = (aY - bY) / (aX - bX);
        double bee = aY - m * aX;
        double x = (pY - bee) / m;

        return x > pX;
    }

    private void updateMyStatusUI(String status, Long timestamp) {
        if (!isAdded() || getView() == null) return;
        
        View view = getView();
        android.widget.EditText etMapStatus = view.findViewById(R.id.etMapStatus);
        TextView tvStatusTime = view.findViewById(R.id.tvStatusTime);
        ImageView ivDeleteStatus = view.findViewById(R.id.ivDeleteStatus);
        ImageView ivUpdateStatus = view.findViewById(R.id.ivUpdateStatus);
        ImageView ivStatusToggle = view.findViewById(R.id.ivStatusToggle);
        
        boolean hasStatus = status != null && !status.isEmpty();
        if (hasStatus && timestamp != null) {
            long diff = System.currentTimeMillis() - timestamp;
            if (diff > 24 * 60 * 60 * 1000L) { // Expire after 24h
                hasStatus = false;
            }
        }

        if (hasStatus) {
            etMapStatus.setText(status);
            etMapStatus.setFocusable(false);
            etMapStatus.setFocusableInTouchMode(false);
            etMapStatus.setCursorVisible(false);
            tvStatusTime.setText(formatTimeAgo(timestamp != null ? timestamp : 0));
            tvStatusTime.setVisibility(View.VISIBLE);
            ivDeleteStatus.setVisibility(View.VISIBLE);
            ivUpdateStatus.setVisibility(View.GONE);
            ivStatusToggle.setColorFilter(ContextCompat.getColor(requireContext(), R.color.primary_blue));
        } else {
            if (etMapStatus.getText().length() > 0 && !etMapStatus.hasFocus()) {
                etMapStatus.setText("");
            }
            etMapStatus.setFocusable(true);
            etMapStatus.setFocusableInTouchMode(true);
            etMapStatus.setCursorVisible(true);
            tvStatusTime.setVisibility(View.GONE);
            ivDeleteStatus.setVisibility(View.GONE);
            ivUpdateStatus.setVisibility(etMapStatus.getText().length() > 0 ? View.VISIBLE : View.GONE);
            ivStatusToggle.setColorFilter(ContextCompat.getColor(requireContext(), R.color.text_primary));
        }
    }

    private Group getSelectedGroup() {
        for (Group g : myGroups) {
            if (Objects.equals(g.getId(), selectedGroupId)) return g;
        }
        return null;
    }

    private void updateSelectedGroupIcon(Group group) {
        if (ivSelectedGroupIcon == null || group == null) return;
        
        boolean isEveryone = "everyone".equals(group.getId());
        if (isEveryone) {
            ivSelectedGroupIcon.setVisibility(View.GONE);
        } else {
            ivSelectedGroupIcon.setVisibility(View.VISIBLE);
            String imageUri = group.getGroupImageUri();
            loadGroupIcon(imageUri, ivSelectedGroupIcon);
        }
    }

    private void loadGroupIcon(String imageUri, ImageView imageView) {
        if (imageUri == null || imageUri.isEmpty()) {
            imageView.setImageResource(R.drawable.profile_pic);
        } else if (imageUri.startsWith("data:image") || imageUri.length() > 500) {
            try {
                String base64Data = imageUri.contains(",") ? imageUri.substring(imageUri.indexOf(",") + 1) : imageUri;
                byte[] decodedString = Base64.decode(base64Data, Base64.DEFAULT);
                Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                Glide.with(this).load(decodedByte).circleCrop().into(imageView);
            } catch (Exception e) {
                imageView.setImageResource(R.drawable.profile_pic);
            }
        } else {
            Glide.with(this).load(imageUri).placeholder(R.drawable.profile_pic).circleCrop().into(imageView);
        }
    }

    private List<String> getAllMyGroupsMembers() {
        Set<String> allPhones = new HashSet<>();
        for (Group g : myGroups) {
            if (!"everyone".equals(g.getId())) {
                allPhones.addAll(g.getMemberPhones());
            }
        }
        // Include self
        allPhones.add(userPhone);
        return new ArrayList<>(allPhones);
    }

    private void updateOtherUserMarker(String phone, String name, double lat, double lng, String imageBase64, boolean isOnline, String statusText, long statusTime) {
        GeoPoint point = new GeoPoint(lat, lng);
        Marker marker = otherUserMarkers.get(phone);

        if (marker == null) {
            marker = new Marker(map);
            marker.setAnchor(Marker.ANCHOR_CENTER, 1.0f);
            
            Bitmap profileBitmap = decodeBase64(imageBase64);
            if (profileBitmap == null) {
                profileBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.profile_pic);
            }
            Bitmap markerBitmap = MapUtils.createMarkerWithPointer(requireContext(), profileBitmap);
            if (markerBitmap != null) {
                marker.setIcon(new BitmapDrawable(getResources(), markerBitmap));
            }
            
            map.getOverlays().add(marker);
            otherUserMarkers.put(phone, marker);
        }

        // Show status instead of name if available and not expired
        String displayInfo = name;
        if (statusText != null && !statusText.isEmpty()) {
            long diff = System.currentTimeMillis() - statusTime;
            if (diff < 24 * 60 * 60 * 1000L) { // 24 hours
                String timeAgo = formatTimeAgo(statusTime);
                displayInfo = statusText + " (" + timeAgo + ")";
            }
        }
        marker.setTitle(displayInfo);

        marker.setAlpha(isOnline ? 1.0f : 0.5f); // Dim offline users on map
        marker.setPosition(point);
    }

    private static String formatTimeAgo(long time) {
        if (time <= 0) return "";
        long diff = System.currentTimeMillis() - time;
        if (diff < 60000) return "Just now";
        if (diff < 3600000) return (diff / 60000) + "m ago";
        if (diff < 86400000) return (diff / 3600000) + "h ago";
        return (diff / 86400000) + "d ago";
    }

    private void removeUserMarker(String phone) {
        Marker marker = otherUserMarkers.remove(phone);
        if (marker != null) {
            map.getOverlays().remove(marker);
        }
    }

    private Bitmap decodeBase64(String input) {
        if (input == null || input.isEmpty()) return null;
        try {
            String base64Data = input.contains(",") ? input.substring(input.indexOf(",") + 1) : input;
            byte[] decodedString = Base64.decode(base64Data, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
        } catch (Exception e) {
            return null;
        }
    }

    private void getDeviceLocation() {
        try {
            showLoading("Locating Device...");
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            hideLoading();
                            updateMapMarker(location);
                            syncMyLocationToFirebase(location);
                        } else {
                            requestFreshLocation();
                        }
                    })
                    .addOnFailureListener(e -> {
                        hideLoading();
                        Log.e("MapFragment", "Last location failed", e);
                    });
        } catch (SecurityException e) {
            Log.e("MapFragment", "Exception: " + e.getMessage());
            hideLoading();
        }
    }

    private void syncMyLocationToFirebase(Location location) {
        if (location == null || userPhone.isEmpty()) return;
        DatabaseReference myLocRef = FirebaseDatabase.getInstance("https://beaconsos-a2dba-default-rtdb.asia-southeast1.firebasedatabase.app/")
                .getReference("users")
                .child(userPhone);
        
        Map<String, Object> locData = new HashMap<>();
        locData.put("latitude", location.getLatitude());
        locData.put("longitude", location.getLongitude());
        locData.put("lastUpdated", System.currentTimeMillis());
        
        myLocRef.updateChildren(locData);
    }

    private void requestFreshLocation() {
        requestLocationInternal(false);
    }

    private void requestFreshLocationAndZoom() {
        requestLocationInternal(true);
    }

    private void requestLocationInternal(boolean shouldZoom) {
        if (getContext() == null || ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            hideLoading();
            return;
        }

        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
                .setMaxUpdates(1)
                .build();

        fusedLocationClient.requestLocationUpdates(locationRequest, new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                hideLoading();
                Location location = locationResult.getLastLocation();
                if (location != null) {
                    if (shouldZoom) {
                        map.getController().setZoom(18.5);
                    }
                    updateMapMarker(location);
                    syncMyLocationToFirebase(location);
                }
            }
        }, android.os.Looper.getMainLooper());
    }

    private void updateMapMarker(Location location) {
        GeoPoint point = new GeoPoint(location.getLatitude(), location.getLongitude());
        
        if (userMarker == null) {
            userMarker = new Marker(map);
            userMarker.setAnchor(Marker.ANCHOR_CENTER, 1.0f);
            userMarker.setTitle("You");
            
            Bitmap profileBitmap = decodeBase64(triggerManager.getProfileImageUri());
            if (profileBitmap == null) {
                profileBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.profile_pic);
            }
            Bitmap markerBitmap = MapUtils.createMarkerWithPointer(requireContext(), profileBitmap);
            if (markerBitmap != null) {
                userMarker.setIcon(new BitmapDrawable(getResources(), markerBitmap));
            }

            map.getOverlays().add(userMarker);
        }
        
        userMarker.setPosition(point);
        map.getController().animateTo(point);
        map.invalidate();
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

    private class GroupDropdownAdapter extends android.widget.ArrayAdapter<Group> {
        private final List<Group> groups;

        public GroupDropdownAdapter(@NonNull Context context, @NonNull List<Group> groups) {
            super(context, R.layout.item_dropdown_group, groups);
            this.groups = groups;
        }

        @NonNull
        @Override
        public android.widget.Filter getFilter() {
            return new android.widget.Filter() {
                @Override
                protected FilterResults performFiltering(CharSequence constraint) {
                    FilterResults results = new FilterResults();
                    results.values = groups;
                    results.count = groups.size();
                    return results;
                }

                @Override
                protected void publishResults(CharSequence constraint, FilterResults results) {
                    notifyDataSetChanged();
                }
            };
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_dropdown_group, parent, false);
            }

            Group group = groups.get(position);
            TextView tvName = convertView.findViewById(R.id.tvGroupNameDropdown);
            ImageView ivChat = convertView.findViewById(R.id.ivChatIconDropdown);
            ImageView ivGroupIcon = convertView.findViewById(R.id.ivGroupIconDropdown);

            tvName.setText(group.getName());

            boolean isEveryone = "everyone".equals(group.getId());
            ivChat.setVisibility(isEveryone ? View.GONE : View.VISIBLE);

            if (isEveryone) {
                ivGroupIcon.setVisibility(View.GONE);
            } else {
                ivGroupIcon.setVisibility(View.VISIBLE);
                loadGroupIcon(group.getGroupImageUri(), ivGroupIcon);
            }

            ivChat.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), GroupChatActivity.class);
                intent.putExtra("GROUP_ID", group.getId());
                startActivity(intent);
                autoCompleteGroup.dismissDropDown();
            });

            return convertView;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (map != null) map.onResume();
        if (usersRef != null && usersListener != null) {
            usersRef.addValueEventListener(usersListener);
        }
        if (groupsRef != null && groupsListener != null) {
            groupsRef.addValueEventListener(groupsListener);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (map != null) map.onPause();
        if (usersRef != null && usersListener != null) {
            usersRef.removeEventListener(usersListener);
        }
        if (groupsRef != null && groupsListener != null) {
            groupsRef.removeEventListener(groupsListener);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (usersRef != null && usersListener != null) {
            usersRef.removeEventListener(usersListener);
        }
        if (groupsRef != null && groupsListener != null) {
            groupsRef.removeEventListener(groupsListener);
        }
    }

    private static class MapMemberAdapter extends RecyclerView.Adapter<MapMemberAdapter.ViewHolder> {
        private final List<User> members;
        private final Context context;
        private final OnMemberClickListener listener;
        private int expandedPosition = -1;

        public MapMemberAdapter(Context context, List<User> members, OnMemberClickListener listener) {
            this.context = context;
            this.members = members;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_map_member, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            User user = members.get(position);
            holder.tvName.setText(user.getName());
            
            if (user.isInSaferArea()) {
                holder.tvName.setTextColor(ContextCompat.getColor(context, R.color.success_green));
            } else {
                holder.tvName.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
            }

            // Format Last Seen
            String lastSeen;
            if (user.isOnline()) {
                lastSeen = "Online";
                holder.tvLastSeen.setTextColor(ContextCompat.getColor(context, R.color.success_green));
            } else {
                long time = user.getLastUpdated();
                lastSeen = "Last seen: " + formatTimeAgo(time);
                if (time <= 0) lastSeen = "Offline";
                holder.tvLastSeen.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
            }
            holder.tvLastSeen.setText(lastSeen);

            // Handle Status Expansion
            boolean isExpanded = position == expandedPosition;
            if (isExpanded) {
                String status = user.getStatusText();
                long timestamp = user.getStatusTimestamp();
                
                boolean isExpired = false;
                if (timestamp > 0) {
                    long diff = System.currentTimeMillis() - timestamp;
                    if (diff > 24 * 60 * 60 * 1000L) { // 24 hours
                        isExpired = true;
                    }
                }
                
                String displayStatus;
                if (status == null || status.isEmpty() || isExpired) {
                    displayStatus = "No status";
                } else {
                    displayStatus = status;
                }

                holder.tvStatus.setText(displayStatus);
                holder.tvStatus.setVisibility(View.VISIBLE);
            } else {
                holder.tvStatus.setVisibility(View.GONE);
            }
            
            holder.itemView.setOnClickListener(v -> {
                int previousExpandedPosition = expandedPosition;
                expandedPosition = isExpanded ? -1 : holder.getBindingAdapterPosition();
                
                notifyItemChanged(previousExpandedPosition);
                notifyItemChanged(expandedPosition);

                if (listener != null) listener.onMemberClick(user);
            });

            String imageSource = user.getProfileImageUri();
            if (imageSource == null || imageSource.isEmpty()) {
                holder.ivIcon.setImageResource(R.drawable.profile_pic);
            } else if (imageSource.startsWith("data:image") || imageSource.length() > 500) {
                try {
                    String base64Data = imageSource.contains(",") ? imageSource.substring(imageSource.indexOf(",") + 1) : imageSource;
                    byte[] decodedString = Base64.decode(base64Data, Base64.DEFAULT);
                    Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                    Glide.with(context).load(decodedByte).circleCrop().into(holder.ivIcon);
                } catch (Exception e) {
                    holder.ivIcon.setImageResource(R.drawable.profile_pic);
                }
            } else {
                Glide.with(context).load(imageSource).placeholder(R.drawable.profile_pic).circleCrop().into(holder.ivIcon);
            }
        }

        @Override
        public int getItemCount() {
            return members.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            ShapeableImageView ivIcon;
            TextView tvName;
            TextView tvLastSeen;
            TextView tvStatus;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                ivIcon = itemView.findViewById(R.id.ivMemberIcon);
                tvName = itemView.findViewById(R.id.tvMemberName);
                tvLastSeen = itemView.findViewById(R.id.tvLastSeen);
                tvStatus = itemView.findViewById(R.id.tvMemberStatus);
            }
        }
        @SuppressLint("NotifyDataSetChanged")
        public void updateMembers(List<User> newMembers) {
            this.members.clear();
            this.members.addAll(newMembers);
            notifyDataSetChanged();
        }
    }
}
