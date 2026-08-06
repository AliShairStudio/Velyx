package com.alert.beacon.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alert.beacon.R;
import com.alert.beacon.data.EmergencyService;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.shape.ShapeAppearanceModel;

import java.util.ArrayList;
import java.util.List;

public class EmergencyNumbersActivity extends BaseActivity {

    private static final int PERMISSION_REQUEST_CALL = 101;
    private String pendingNumber;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_emergency_numbers);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Emergency Services");
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        RecyclerView rvNumbers = findViewById(R.id.rvEmergencyNumbers);
        rvNumbers.setLayoutManager(new LinearLayoutManager(this));

        List<EmergencyService> services = new ArrayList<>();
        services.add(new EmergencyService("Police", "15", 0));
        services.add(new EmergencyService("Rescue", "1122", 0));
        services.add(new EmergencyService("Fire Brigade", "16", 0));
        services.add(new EmergencyService("Punjab Women Helpline", "1043", 0));
        services.add(new EmergencyService("Ambulance", "115", 0));
        services.add(new EmergencyService("Child Protection", "1121", 0));

        rvNumbers.setAdapter(new EmergencyNumbersAdapter(services));
    }

    private void showCallConfirmationDialog(String name, String number) {
        new AlertDialog.Builder(this)
                .setTitle("Confirm Emergency Call")
                .setMessage("Are you sure you want to call " + name + " (" + number + ")?")
                .setPositiveButton("Call Now", (dialog, which) -> {
                    makeCall(number);
                })
                .setNegativeButton("Cancel", null)
                .setIcon(android.R.drawable.ic_menu_call)
                .show();
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
                Toast.makeText(this, "Permission denied to make calls. Opening dialer instead.", Toast.LENGTH_SHORT).show();
                if (pendingNumber != null) {
                    Intent intent = new Intent(Intent.ACTION_DIAL);
                    intent.setData(Uri.parse("tel:" + pendingNumber));
                    startActivity(intent);
                }
            }
            pendingNumber = null;
        }
    }

    private class EmergencyNumbersAdapter extends RecyclerView.Adapter<EmergencyNumbersAdapter.ViewHolder> {
        private final List<EmergencyService> services;

        public EmergencyNumbersAdapter(List<EmergencyService> services) {
            this.services = services;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_emergency_number, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            EmergencyService service = services.get(position);
            holder.tvName.setText(service.getName());
            holder.tvNumber.setText(service.getNumber());
            
            View.OnClickListener clickListener = v -> showCallConfirmationDialog(service.getName(), service.getNumber());
            
            holder.btnCall.setOnClickListener(clickListener);
            holder.itemView.setOnClickListener(clickListener);

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
        }

        @Override
        public int getItemCount() {
            return services.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvNumber;
            View btnCall;
            MaterialCardView cardView;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tvServiceName);
                tvNumber = itemView.findViewById(R.id.tvNumber);
                btnCall = itemView.findViewById(R.id.btnCall);
                cardView = (MaterialCardView) itemView;
            }
        }
    }
}
