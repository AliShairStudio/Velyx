package com.alert.beacon.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alert.beacon.R;
import com.alert.beacon.data.EmergencyService;
import com.alert.beacon.data.TriggerManager;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.List;

public class EmergencyNumbersFragment extends Fragment {

    private String pendingNumber;
    private TriggerManager triggerManager;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    if (pendingNumber != null) makeCall(pendingNumber);
                } else {
                    Toast.makeText(getContext(), R.string.permission_denied_dialer, Toast.LENGTH_SHORT).show();
                    if (pendingNumber != null) {
                        Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + pendingNumber));
                        startActivity(intent);
                    }
                }
                pendingNumber = null;
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_emergency_numbers, container, false);

        triggerManager = new TriggerManager(requireContext());

        RecyclerView rvNumbers = view.findViewById(R.id.rvEmergencyNumbers);
        rvNumbers.setLayoutManager(new LinearLayoutManager(getContext()));

        List<EmergencyService> services = new ArrayList<>();
        services.add(new EmergencyService("Police", "15", 0));
        services.add(new EmergencyService("Rescue", "1122", 0));
        services.add(new EmergencyService("Fire Brigade", "16", 0));
        services.add(new EmergencyService("Punjab Women Helpline", "1043", 0));
        services.add(new EmergencyService("Ambulance", "115", 0));
        services.add(new EmergencyService("Child Protection", "1121", 0));

        rvNumbers.setAdapter(new EmergencyNumbersAdapter(services));

        return view;
    }

    private void showInAppCallConfirmation(String name, String number) {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(requireContext());
        View sheetView = getLayoutInflater().inflate(R.layout.layout_call_confirmation, null);
        bottomSheetDialog.setContentView(sheetView);

        // Make the background of the bottom sheet container transparent so our glassy drawable shows correctly
        View parent = (View) sheetView.getParent();
        if (parent != null) {
            parent.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        TextView tvTitle = sheetView.findViewById(R.id.tvConfirmTitle);
        TextView tvSubTitle = sheetView.findViewById(R.id.tvConfirmSubTitle);
        Button btnConfirm = sheetView.findViewById(R.id.btnConfirmCall);
        Button btnCancel = sheetView.findViewById(R.id.btnCancelCall);

        tvTitle.setText(getString(R.string.call_confirmation_title, name));
        String userPhone = triggerManager.getUserPhone();
        tvSubTitle.setText(getString(R.string.call_confirmation_subtitle, number, userPhone));

        btnConfirm.setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            makeCall(number);
        });

        btnCancel.setOnClickListener(v -> bottomSheetDialog.dismiss());

        bottomSheetDialog.show();
    }

    private void makeCall(String number) {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            pendingNumber = number;
            requestPermissionLauncher.launch(Manifest.permission.CALL_PHONE);
        } else {
            Intent intent = new Intent(Intent.ACTION_CALL);
            intent.setData(Uri.parse("tel:" + number));
            startActivity(intent);
        }
    }

    private class EmergencyNumbersAdapter extends RecyclerView.Adapter<EmergencyNumbersAdapter.ViewHolder> {
        private final List<EmergencyService> services;

        public EmergencyNumbersAdapter(List<EmergencyService> services) { this.services = services; }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_emergency_number, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            EmergencyService s = services.get(position);
            holder.tvName.setText(s.getName());
            holder.tvNumber.setText(s.getNumber());
            
            View.OnClickListener cl = v -> showInAppCallConfirmation(s.getName(), s.getNumber());
            holder.btnCall.setOnClickListener(cl);
            holder.itemView.setOnClickListener(cl);
        }

        @Override
        public int getItemCount() { return services.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvNumber;
            View btnCall;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tvServiceName);
                tvNumber = itemView.findViewById(R.id.tvNumber);
                btnCall = itemView.findViewById(R.id.btnCall);
            }
        }
    }
}
