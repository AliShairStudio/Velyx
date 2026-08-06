package com.alert.beacon.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.alert.beacon.R;
import com.bumptech.glide.Glide;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.List;

public class MemberAdapter extends RecyclerView.Adapter<MemberAdapter.MemberViewHolder> {

    private List<String> members;
    private boolean isUserAdmin;
    private String currentUserPhone;
    private String groupAdminPhone;
    private OnMemberRemoveListener listener;

    public interface OnMemberRemoveListener {
        void onRemove(String member, int position);
    }

    public MemberAdapter(List<String> members, boolean isUserAdmin, String currentUserPhone, String groupAdminPhone, OnMemberRemoveListener listener) {
        this.members = members;
        this.isUserAdmin = isUserAdmin;
        this.currentUserPhone = currentUserPhone;
        this.groupAdminPhone = groupAdminPhone;
        this.listener = listener;
    }

    @NonNull
    @Override
    public MemberViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_member, parent, false);
        return new MemberViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MemberViewHolder holder, int position) {
        String memberPhone = members.get(position);
        
        // Apply shape based on position to match profile style
        ShapeAppearanceModel shape;
        if (getItemCount() == 1) {
            shape = ShapeAppearanceModel.builder(holder.itemView.getContext(), R.style.RoundedAll, R.style.RoundedAll).build();
        } else if (position == 0) {
            shape = ShapeAppearanceModel.builder(holder.itemView.getContext(), R.style.RoundedTop, R.style.RoundedTop).build();
        } else if (position == getItemCount() - 1) {
            shape = ShapeAppearanceModel.builder(holder.itemView.getContext(), R.style.RoundedBottom, R.style.RoundedBottom).build();
        } else {
            shape = ShapeAppearanceModel.builder(holder.itemView.getContext(), R.style.RectangularSmall, R.style.RectangularSmall).build();
        }
        
        if (holder.itemView instanceof MaterialCardView) {
            ((MaterialCardView) holder.itemView).setShapeAppearanceModel(shape);
        }

        // Fetch user details (name and profile pic) from Realtime Database
        FirebaseDatabase.getInstance("https://beaconsos-a2dba-default-rtdb.asia-southeast1.firebasedatabase.app/")
                .getReference("users").child(memberPhone)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String name = snapshot.child("name").getValue(String.class);
                        String imageSource = snapshot.child("profileImageUri").getValue(String.class);

                        StringBuilder displayText = new StringBuilder(name != null ? name : memberPhone);
                        if (memberPhone.equals(groupAdminPhone)) displayText.append(" (Admin)");
                        if (memberPhone.equals(currentUserPhone)) displayText.append(" (You)");
                        holder.tvMemberInfo.setText(displayText.toString());

                        loadMemberImage(imageSource, holder.ivMemberIcon);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });

        // Only Admin can remove members, and Admin cannot remove themselves from here (they use Exit)
        if (isUserAdmin && !memberPhone.equals(currentUserPhone)) {
            holder.itemView.setOnLongClickListener(v -> {
                listener.onRemove(memberPhone, position);
                return true;
            });
        } else {
            holder.itemView.setOnLongClickListener(null);
        }
    }

    private void loadMemberImage(String imageSource, ImageView imageView) {
        if (imageSource == null || imageSource.isEmpty()) {
            imageView.setImageResource(R.drawable.profile_pic);
            return;
        }

        if (imageSource.startsWith("data:image") || imageSource.length() > 500) {
            try {
                String base64Data = imageSource.contains(",") ? imageSource.substring(imageSource.indexOf(",") + 1) : imageSource;
                byte[] decodedString = Base64.decode(base64Data, Base64.DEFAULT);
                Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                Glide.with(imageView.getContext()).load(decodedByte).circleCrop().into(imageView);
            } catch (Exception e) {
                imageView.setImageResource(R.drawable.profile_pic);
            }
        } else {
            Glide.with(imageView.getContext())
                    .load(imageSource)
                    .placeholder(R.drawable.profile_pic)
                    .circleCrop()
                    .into(imageView);
        }
    }

    @Override
    public int getItemCount() {
        return members.size();
    }

    static class MemberViewHolder extends RecyclerView.ViewHolder {
        TextView tvMemberInfo;
        ImageView ivMemberIcon;

        public MemberViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMemberInfo = itemView.findViewById(R.id.tvMemberInfo);
            ivMemberIcon = itemView.findViewById(R.id.ivMemberIcon);
        }
    }
}
