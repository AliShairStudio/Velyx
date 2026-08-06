package com.alert.beacon.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.alert.beacon.R;
import com.alert.beacon.data.Group;
import com.alert.beacon.data.TriggerManager;
import com.bumptech.glide.Glide;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class GroupAdapter extends RecyclerView.Adapter<GroupAdapter.GroupViewHolder> {

    private static final String TAG = "GroupAdapter";
    private List<Group> groups;
    private final OnGroupClickListener listener;
    private TriggerManager triggerManager;
    private final Set<String> selectedGroupIds = new HashSet<>();
    private static final java.text.SimpleDateFormat TIME_FORMAT = new java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault());

    public interface OnGroupClickListener {
        void onGroupClick(Group group);
        void onGroupIconClick(Group group);
        void onGroupLongClick(Group group, View view);
        void onSelectionChanged(int count);
    }

    public GroupAdapter(List<Group> groups, OnGroupClickListener listener) {
        this.groups = new ArrayList<>(groups);
        this.listener = listener;
    }

    public void updateList(List<Group> newList) {
        final List<Group> oldList = new ArrayList<>(this.groups);
        new Thread(() -> {
            DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new GroupDiffCallback(oldList, newList));
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                this.groups.clear();
                this.groups.addAll(newList);
                diffResult.dispatchUpdatesTo(this);
            });
        }).start();
    }

    public void toggleSelection(String groupId) {
        if (selectedGroupIds.contains(groupId)) {
            selectedGroupIds.remove(groupId);
        } else {
            selectedGroupIds.add(groupId);
        }
        
        // Find position and notify only that item for better performance
        for (int i = 0; i < groups.size(); i++) {
            if (groups.get(i).getId().equals(groupId)) {
                notifyItemChanged(i, "SELECTION_CHANGE");
                break;
            }
        }

        if (listener != null) {
            listener.onSelectionChanged(selectedGroupIds.size());
        }
    }

    public void clearSelection() {
        Set<String> previousSelection = new HashSet<>(selectedGroupIds);
        selectedGroupIds.clear();
        
        // Notify only affected items
        for (int i = 0; i < groups.size(); i++) {
            if (previousSelection.contains(groups.get(i).getId())) {
                notifyItemChanged(i, "SELECTION_CHANGE");
            }
        }

        if (listener != null) {
            listener.onSelectionChanged(0);
        }
    }

    public List<String> getSelectedGroupIds() {
        return new ArrayList<>(selectedGroupIds);
    }
    
    public List<Group> getSelectedGroups() {
        List<Group> selected = new ArrayList<>();
        for (Group g : groups) {
            if (selectedGroupIds.contains(g.getId())) {
                selected.add(g);
            }
        }
        return selected;
    }

    public int getSelectedCount() {
        return selectedGroupIds.size();
    }

    public boolean isSelected(String groupId) {
        return selectedGroupIds.contains(groupId);
    }

    @NonNull
    @Override
    public GroupViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (triggerManager == null) {
            triggerManager = new TriggerManager(parent.getContext());
        }
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_group, parent, false);
        final GroupViewHolder holder = new GroupViewHolder(view);
        
        holder.itemView.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && listener != null) {
                listener.onGroupClick(groups.get(pos));
            }
        });

        holder.ivGroupIcon.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && listener != null) {
                listener.onGroupIconClick(groups.get(pos));
            }
        });
        
        holder.itemView.setOnLongClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && listener != null) {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                listener.onGroupLongClick(groups.get(pos), v);
                return true;
            }
            return false;
        });
        
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull GroupViewHolder holder, int position) {
        onBindViewHolder(holder, position, new ArrayList<>());
    }

    @Override
    public void onBindViewHolder(@NonNull GroupViewHolder holder, int position, @NonNull List<Object> payloads) {
        final Group group = groups.get(position);

        if (payloads.contains("SELECTION_CHANGE")) {
            updateSelectionState(holder, group);
            return;
        }

        String displayName = group.getName();
        holder.tvGroupName.setText(displayName);
        
        if (group.getLastMessage() != null) {
            holder.tvLastMessage.setText(group.getLastMessage());
        } else {
            holder.tvLastMessage.setText("No messages yet");
        }

        if (group.getLastMessageTime() > 0) {
            holder.tvLastMessageTime.setVisibility(View.VISIBLE);
            holder.tvLastMessageTime.setText(formatTime(group.getLastMessageTime()));
        } else {
            holder.tvLastMessageTime.setVisibility(View.GONE);
        }

        if (group.getUnreadCount() > 0) {
            holder.tvUnreadCount.setVisibility(View.VISIBLE);
            holder.tvUnreadCount.setText(String.valueOf(group.getUnreadCount()));
        } else {
            if (group.isMarkedUnread()) {
                holder.tvUnreadCount.setVisibility(View.VISIBLE);
                holder.tvUnreadCount.setText("");
            } else {
                holder.tvUnreadCount.setVisibility(View.GONE);
            }
        }

        if (holder.ivPinIcon != null) {
            holder.ivPinIcon.setVisibility(group.isPinned() ? View.VISIBLE : View.GONE);
        }

        if (holder.ivFavoriteIcon != null) {
            holder.ivFavoriteIcon.setVisibility(group.isFavorite() ? View.VISIBLE : View.GONE);
        }

        updateSelectionState(holder, group);
        loadGroupImage(group.getGroupImageUri(), holder.ivGroupIcon);
    }

    private void updateSelectionState(GroupViewHolder holder, Group group) {
        boolean isSelected = selectedGroupIds.contains(group.getId());
        if (holder.cardView != null) {
            holder.cardView.setChecked(isSelected);
            if (isSelected) {
                holder.cardView.setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(holder.itemView.getContext(), R.color.sky_blue));
            } else {
                holder.cardView.setCardBackgroundColor(android.graphics.Color.TRANSPARENT);
            }
        }
    }

    private void loadGroupImage(String imageSource, ImageView imageView) {
        if (imageSource == null || imageSource.isEmpty()) {
            imageView.setImageResource(R.drawable.profile_pic);
            return;
        }

        // Optimization: Use Glide directly with the string if possible, or decode in background
        if (imageSource.startsWith("data:image")) {
            // Glide can handle data URIs in some versions, but to be safe and fast:
            // We pass the string directly to Glide and let it handle its own caching/decoding
            Glide.with(imageView.getContext())
                    .load(imageSource)
                    .placeholder(R.drawable.profile_pic)
                    .error(R.drawable.profile_pic)
                    .circleCrop()
                    .into(imageView);
        } else {
            Glide.with(imageView.getContext())
                    .load(imageSource)
                    .placeholder(R.drawable.profile_pic)
                    .error(R.drawable.profile_pic)
                    .circleCrop()
                    .into(imageView);
        }
    }

    @Override
    public int getItemCount() {
        return groups != null ? groups.size() : 0;
    }

    static class GroupViewHolder extends RecyclerView.ViewHolder {
        TextView tvGroupName, tvLastMessage, tvLastMessageTime, tvUnreadCount;
        ImageView ivGroupIcon, ivPinIcon, ivFavoriteIcon;
        MaterialCardView cardView;

        public GroupViewHolder(@NonNull View itemView) {
            super(itemView);
            if (itemView instanceof MaterialCardView) {
                cardView = (MaterialCardView) itemView;
            }
            tvGroupName = itemView.findViewById(R.id.tvGroupName);
            tvLastMessage = itemView.findViewById(R.id.tvLastMessage);
            tvLastMessageTime = itemView.findViewById(R.id.tvLastMessageTime);
            tvUnreadCount = itemView.findViewById(R.id.tvUnreadCount);
            ivGroupIcon = itemView.findViewById(R.id.ivGroupIcon);
            ivPinIcon = itemView.findViewById(R.id.ivPinIcon);
            ivFavoriteIcon = itemView.findViewById(R.id.ivFavoriteIcon);
            
            // Disable clicks on children except icon
            tvGroupName.setClickable(false);
            if (tvLastMessage != null) tvLastMessage.setClickable(false);
        }
    }

    private String formatTime(long timestamp) {
        synchronized (TIME_FORMAT) {
            return TIME_FORMAT.format(new java.util.Date(timestamp));
        }
    }

    private static class GroupDiffCallback extends DiffUtil.Callback {
        private final List<Group> oldList;
        private final List<Group> newList;

        public GroupDiffCallback(List<Group> oldList, List<Group> newList) {
            this.oldList = oldList;
            this.newList = newList;
        }

        @Override
        public int getOldListSize() {
            return oldList.size();
        }

        @Override
        public int getNewListSize() {
            return newList.size();
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            return Objects.equals(oldList.get(oldItemPosition).getId(), newList.get(newItemPosition).getId());
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            Group oldGroup = oldList.get(oldItemPosition);
            Group newGroup = newList.get(newItemPosition);
            
            // Optimization: Fast string comparison for heavy image data
            boolean imageSame = Objects.equals(oldGroup.getGroupImageUri(), newGroup.getGroupImageUri());
            
            return imageSame &&
                    Objects.equals(oldGroup.getName(), newGroup.getName()) &&
                    Objects.equals(oldGroup.getLastMessage(), newGroup.getLastMessage()) &&
                    oldGroup.getLastMessageTime() == newGroup.getLastMessageTime() &&
                    oldGroup.isGroupChat() == newGroup.isGroupChat() &&
                    oldGroup.getUnreadCount() == newGroup.getUnreadCount() &&
                    oldGroup.isPinned() == newGroup.isPinned() &&
                    oldGroup.isFavorite() == newGroup.isFavorite() &&
                    oldGroup.isMarkedUnread() == newGroup.isMarkedUnread();
        }
    }
}
