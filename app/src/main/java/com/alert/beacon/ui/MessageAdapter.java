package com.alert.beacon.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.StyleSpan;
import android.text.util.Linkify;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.alert.beacon.R;
import com.alert.beacon.data.ChatMessage;
import com.bumptech.glide.Glide;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_SENT_TEXT = 1;
    private static final int VIEW_TYPE_RECEIVED_TEXT = 2;
    private static final int VIEW_TYPE_SENT_IMAGE = 3;
    private static final int VIEW_TYPE_RECEIVED_IMAGE = 4;
    private static final int VIEW_TYPE_SENT_AUDIO = 5;
    private static final int VIEW_TYPE_RECEIVED_AUDIO = 6;
    private static final int VIEW_TYPE_SYSTEM = 7;

    private final List<ChatMessage> messages;
    private final String currentUserPhone;
    private final List<String> selectedMessageIds = new ArrayList<>();
    private OnMessageClickListener listener;
    private String searchQuery = "";
    
    private MediaPlayer mediaPlayer;
    private String currentlyPlayingUrl = null;
    private AudioViewHolder currentPlayingViewHolder = null;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable updateSeekBarTask = new Runnable() {
        @Override
        public void run() {
            if (mediaPlayer != null && mediaPlayer.isPlaying() && currentPlayingViewHolder != null) {
                currentPlayingViewHolder.audioSeekBar.setProgress(mediaPlayer.getCurrentPosition());
                handler.postDelayed(this, 100);
            }
        }
    };

    public interface OnMessageClickListener {
        void onMessageClick(ChatMessage message, View view);
        void onMessageLongClick(ChatMessage message, View view);
        void onSelectionChanged(int count);
        void onReactionClick(ChatMessage message, String emoji);
    }

    public MessageAdapter(List<ChatMessage> messages, String currentUserPhone) {
        this.messages = messages;
        this.currentUserPhone = currentUserPhone;
    }

    public MessageAdapter(List<ChatMessage> messages, String currentUserPhone, OnMessageClickListener listener) {
        this.messages = messages;
        this.currentUserPhone = currentUserPhone;
        this.listener = listener;
    }

    public void setOnMessageClickListener(OnMessageClickListener listener) {
        this.listener = listener;
    }

    public void setSearchQuery(String query) {
        this.searchQuery = query != null ? query.toLowerCase().trim() : "";
        notifyDataSetChanged();
    }



    @Override
    public int getItemViewType(int position) {
        ChatMessage message = messages.get(position);
        
        if (ChatMessage.TYPE_SYSTEM.equals(message.getType())) {
            return VIEW_TYPE_SYSTEM;
        }

        boolean isSent = message.getSenderPhone() != null && message.getSenderPhone().equals(currentUserPhone);
        String type = message.getType() != null ? message.getType() : ChatMessage.TYPE_TEXT;

        if (isSent) {
            switch (type) {
                case ChatMessage.TYPE_IMAGE: return VIEW_TYPE_SENT_IMAGE;
                case ChatMessage.TYPE_AUDIO: return VIEW_TYPE_SENT_AUDIO;
                default: return VIEW_TYPE_SENT_TEXT;
            }
        } else {
            switch (type) {
                case ChatMessage.TYPE_IMAGE: return VIEW_TYPE_RECEIVED_IMAGE;
                case ChatMessage.TYPE_AUDIO: return VIEW_TYPE_RECEIVED_AUDIO;
                default: return VIEW_TYPE_RECEIVED_TEXT;
            }
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case VIEW_TYPE_SYSTEM:
                return new SystemMessageViewHolder(inflater.inflate(R.layout.item_message_system, parent, false));
            case VIEW_TYPE_SENT_IMAGE:
                return new SentImageViewHolder(inflater.inflate(R.layout.item_message_sent_image, parent, false));
            case VIEW_TYPE_RECEIVED_IMAGE:
                return new ReceivedImageViewHolder(inflater.inflate(R.layout.item_message_received_image, parent, false));
            case VIEW_TYPE_SENT_AUDIO:
                return new SentAudioViewHolder(inflater.inflate(R.layout.item_message_sent_audio, parent, false));
            case VIEW_TYPE_RECEIVED_AUDIO:
                return new ReceivedAudioViewHolder(inflater.inflate(R.layout.item_message_received_audio, parent, false));
            case VIEW_TYPE_SENT_TEXT:
                return new SentMessageViewHolder(inflater.inflate(R.layout.item_message_sent, parent, false));
            default:
                return new ReceivedMessageViewHolder(inflater.inflate(R.layout.item_message_received, parent, false));
        }
    }

    public void toggleSelection(String messageId) {
        if (selectedMessageIds.contains(messageId)) {
            selectedMessageIds.remove(messageId);
        } else {
            selectedMessageIds.add(messageId);
        }
        notifyDataSetChanged();
        if (listener != null) {
            listener.onSelectionChanged(selectedMessageIds.size());
        }
    }

    public void clearSelection() {
        selectedMessageIds.clear();
        notifyDataSetChanged();
        if (listener != null) {
            listener.onSelectionChanged(0);
        }
    }

    public List<ChatMessage> getSelectedMessages() {
        List<ChatMessage> selected = new ArrayList<>();
        for (ChatMessage m : messages) {
            if (selectedMessageIds.contains(m.getId())) {
                selected.add(m);
            }
        }
        return selected;
    }

    public int getSelectedCount() {
        return selectedMessageIds.size();
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage message = messages.get(position);
        
        // Handle selection background
        if (selectedMessageIds.contains(message.getId())) {
            holder.itemView.setBackgroundColor(androidx.core.content.ContextCompat.getColor(holder.itemView.getContext(), R.color.sky_blue));
        } else {
            holder.itemView.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        }

        if (holder instanceof SystemMessageViewHolder) ((SystemMessageViewHolder) holder).bind(message);
        else if (holder instanceof SentMessageViewHolder) ((SentMessageViewHolder) holder).bind(message, this);
        else if (holder instanceof ReceivedMessageViewHolder) ((ReceivedMessageViewHolder) holder).bind(message, this);
        else if (holder instanceof SentImageViewHolder) ((SentImageViewHolder) holder).bind(message, this);
        else if (holder instanceof ReceivedImageViewHolder) ((ReceivedImageViewHolder) holder).bind(message, this);
        else if (holder instanceof AudioViewHolder) ((AudioViewHolder) holder).bind(message, this);
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    private void playAudio(String audioUrl, AudioViewHolder holder) {
        if (mediaPlayer != null) {
            if (audioUrl.equals(currentlyPlayingUrl)) {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.pause();
                    holder.btnPlayAudio.setImageResource(android.R.drawable.ic_media_play);
                    handler.removeCallbacks(updateSeekBarTask);
                } else {
                    mediaPlayer.start();
                    holder.btnPlayAudio.setImageResource(android.R.drawable.ic_media_pause);
                    handler.post(updateSeekBarTask);
                }
                return;
            } else {
                stopAudio();
            }
        }

        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(audioUrl);
            mediaPlayer.prepareAsync();
            currentlyPlayingUrl = audioUrl;
            currentPlayingViewHolder = holder;
            
            mediaPlayer.setOnPreparedListener(mp -> {
                holder.audioSeekBar.setMax(mp.getDuration());
                mp.start();
                holder.btnPlayAudio.setImageResource(android.R.drawable.ic_media_pause);
                handler.post(updateSeekBarTask);
            });

            mediaPlayer.setOnCompletionListener(mp -> stopAudio());

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void stopAudio() {
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (currentPlayingViewHolder != null) {
            currentPlayingViewHolder.btnPlayAudio.setImageResource(android.R.drawable.ic_media_play);
            currentPlayingViewHolder.audioSeekBar.setProgress(0);
        }
        currentlyPlayingUrl = null;
        currentPlayingViewHolder = null;
        handler.removeCallbacks(updateSeekBarTask);
    }

    public void releasePlayer() {
        stopAudio();
    }

    public void setHighlightAndLinks(TextView textView, String message) {
        if (message == null) {
            textView.setText("");
            return;
        }

        boolean isSelectionMode = getSelectedCount() > 0;
        SpannableString spannableString = new SpannableString(message);

        // 1. Links
        if (!isSelectionMode) {
            Linkify.addLinks(spannableString, Linkify.ALL);
        }

        // 2. Search Highlight (Bold)
        if (!searchQuery.isEmpty()) {
            String lowerMessage = message.toLowerCase();
            int start = lowerMessage.indexOf(searchQuery);
            while (start != -1) {
                int end = start + searchQuery.length();
                spannableString.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                start = lowerMessage.indexOf(searchQuery, end);
            }
        }

        textView.setText(spannableString);
        if (isSelectionMode) {
            textView.setMovementMethod(null);
            textView.setClickable(false);
            textView.setLongClickable(false);
        } else {
            textView.setMovementMethod(LinkMovementMethod.getInstance());
        }
    }

    protected static void setStatusIcon(ImageView ivStatus, int status) {
        if (ivStatus == null) return;
        
        ivStatus.setVisibility(View.VISIBLE);
        ivStatus.setAlpha(1.0f);
        
        switch (status) {
            case ChatMessage.STATUS_SENT:
                ivStatus.setImageResource(R.drawable.ic_tick_single);
                ivStatus.setColorFilter(ContextCompat.getColor(ivStatus.getContext(), R.color.status_gray));
                break;
            case ChatMessage.STATUS_DELIVERED:
                ivStatus.setImageResource(R.drawable.ic_tick_double);
                ivStatus.setColorFilter(ContextCompat.getColor(ivStatus.getContext(), R.color.status_gray));
                break;
            case ChatMessage.STATUS_SEEN:
                ivStatus.setImageResource(R.drawable.ic_tick_double);
                ivStatus.setColorFilter(ContextCompat.getColor(ivStatus.getContext(), R.color.status_blue));
                break;
            default:
                ivStatus.setVisibility(View.GONE);
        }
    }

    private final java.util.Map<RecyclerView.ViewHolder, ValueEventListener> profileListeners = new java.util.HashMap<>();
    private final java.util.Map<RecyclerView.ViewHolder, com.google.firebase.database.DatabaseReference> profileRefs = new java.util.HashMap<>();

    private void loadSenderProfile(RecyclerView.ViewHolder holder, String phone, ImageView imageView) {
        if (phone == null || phone.isEmpty() || imageView == null) return;

        // Clean up old listener if this ViewHolder is being reused
        removeProfileListener(holder);

        com.google.firebase.database.DatabaseReference ref = FirebaseDatabase.getInstance("https://beaconsos-a2dba-default-rtdb.asia-southeast1.firebasedatabase.app/")
                .getReference("users").child(phone).child("profileImageUri");
        
        ValueEventListener listener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String imageSource = snapshot.getValue(String.class);
                if (imageSource == null || imageSource.isEmpty()) {
                    imageView.setImageResource(R.drawable.profile_pic);
                } else if (imageSource.startsWith("data:image") || imageSource.length() > 500) {
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
            public void onCancelled(@NonNull DatabaseError error) {}
        };

        ref.addValueEventListener(listener);
        profileListeners.put(holder, listener);
        profileRefs.put(holder, ref);
    }

    private void removeProfileListener(RecyclerView.ViewHolder holder) {
        ValueEventListener listener = profileListeners.remove(holder);
        com.google.firebase.database.DatabaseReference ref = profileRefs.remove(holder);
        if (ref != null && listener != null) {
            ref.removeEventListener(listener);
        }
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewRecycled(holder);
        removeProfileListener(holder);
    }

    static class SystemMessageViewHolder extends RecyclerView.ViewHolder {
        TextView tvSystemMessage;
        SystemMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSystemMessage = itemView.findViewById(R.id.tvSystemMessage);
        }
        void bind(ChatMessage message) {
            tvSystemMessage.setText(message.getMessage());
        }
    }

    private void bindReactions(ChatMessage message, LinearLayout layoutReactions) {
        if (layoutReactions == null) return;
        layoutReactions.removeAllViews();
        if (message.getReactions() == null || message.getReactions().isEmpty()) {
            layoutReactions.setVisibility(View.GONE);
            return;
        }

        layoutReactions.setVisibility(View.VISIBLE);
        java.util.Map<String, Integer> counts = new java.util.HashMap<>();
        for (String emoji : message.getReactions().values()) {
            Integer current = counts.get(emoji);
            counts.put(emoji, (current == null ? 0 : current) + 1);
        }

        String myReaction = message.getReactions().get(currentUserPhone);

        for (java.util.Map.Entry<String, Integer> entry : counts.entrySet()) {
            TextView tv = new TextView(layoutReactions.getContext());
            String emoji = entry.getKey();
            String text = emoji;
            if (entry.getValue() > 1) {
                text += " " + entry.getValue();
            }
            tv.setText(text);
            tv.setTextSize(11);
            tv.setPadding(12, 4, 12, 4);
            
            boolean isMyReaction = emoji.equals(myReaction);
            tv.setTextColor(isMyReaction ? 
                    ContextCompat.getColor(tv.getContext(), R.color.primary_dark_blue) :
                    android.graphics.Color.BLACK);
            
            tv.setBackgroundResource(isMyReaction ? 
                    R.drawable.bg_reaction_pill_selected : 
                    R.drawable.bg_reaction_pill);
            
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(4, 2, 4, 2);
            tv.setLayoutParams(lp);

            tv.setOnClickListener(v -> {
                if (getSelectedCount() > 0) {
                    toggleSelection(message.getId());
                } else if (listener != null) {
                    listener.onReactionClick(message, emoji);
                }
            });

            layoutReactions.addView(tv);
        }
    }

    static class SentMessageViewHolder extends RecyclerView.ViewHolder {
        TextView tvMessage, tvTimestamp, tvForwarded;
        ImageView ivStatus, ivPinned, ivStarred;
        LinearLayout layoutReactions;

        SentMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMessage = itemView.findViewById(R.id.tvMessage);
            tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
            ivStatus = itemView.findViewById(R.id.ivStatus);
            ivPinned = itemView.findViewById(R.id.ivPinned);
            ivStarred = itemView.findViewById(R.id.ivStarred);
            tvForwarded = itemView.findViewById(R.id.tvForwarded);
            layoutReactions = itemView.findViewById(R.id.layoutReactions);
        }
        void bind(ChatMessage message, MessageAdapter adapter) {
            adapter.setHighlightAndLinks(tvMessage, message.getMessage());
            tvTimestamp.setText(formatTime(message.getTimestamp()));
            setStatusIcon(ivStatus, message.getStatus());
            adapter.bindReactions(message, layoutReactions);

            if (ivPinned != null) ivPinned.setVisibility(message.isPinned() ? View.VISIBLE : View.GONE);
            if (ivStarred != null) ivStarred.setVisibility(message.isStarred() ? View.VISIBLE : View.GONE);

            if (tvForwarded != null) {
                tvForwarded.setVisibility(message.isForwarded() ? View.VISIBLE : View.GONE);
            }

            View.OnClickListener clickListener = v -> {
                if (adapter.getSelectedCount() > 0) {
                    adapter.toggleSelection(message.getId());
                } else if (adapter.listener != null) {
                    adapter.listener.onMessageClick(message, v);
                }
            };

            View.OnLongClickListener longClickListener = v -> {
                if (adapter.listener != null) {
                    adapter.listener.onMessageLongClick(message, v);
                }
                return true;
            };

            itemView.setOnClickListener(clickListener);
            itemView.setOnLongClickListener(longClickListener);
            tvMessage.setOnClickListener(clickListener);
            tvMessage.setOnLongClickListener(longClickListener);
        }
    }

    static class ReceivedMessageViewHolder extends RecyclerView.ViewHolder {
        TextView tvSenderName, tvMessage, tvTimestamp, tvForwarded;
        ImageView ivPinned, ivStarred, ivSenderProfile;
        LinearLayout layoutReactions;

        ReceivedMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSenderName = itemView.findViewById(R.id.tvSenderName);
            tvMessage = itemView.findViewById(R.id.tvMessage);
            tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
            ivPinned = itemView.findViewById(R.id.ivPinned);
            ivStarred = itemView.findViewById(R.id.ivStarred);
            ivSenderProfile = itemView.findViewById(R.id.ivSenderProfile);
            tvForwarded = itemView.findViewById(R.id.tvForwarded);
            layoutReactions = itemView.findViewById(R.id.layoutReactions);
        }
        void bind(ChatMessage message, MessageAdapter adapter) {
            tvSenderName.setText(message.getSenderName());
            adapter.setHighlightAndLinks(tvMessage, message.getMessage());
            tvTimestamp.setText(formatTime(message.getTimestamp()));
            adapter.bindReactions(message, layoutReactions);
            adapter.loadSenderProfile(this, message.getSenderPhone(), ivSenderProfile);

            if (ivPinned != null) ivPinned.setVisibility(message.isPinned() ? View.VISIBLE : View.GONE);
            if (ivStarred != null) ivStarred.setVisibility(message.isStarred() ? View.VISIBLE : View.GONE);

            if (tvForwarded != null) {
                tvForwarded.setVisibility(message.isForwarded() ? View.VISIBLE : View.GONE);
            }

            View.OnClickListener clickListener = v -> {
                if (adapter.getSelectedCount() > 0) {
                    adapter.toggleSelection(message.getId());
                } else if (adapter.listener != null) {
                    adapter.listener.onMessageClick(message, v);
                }
            };

            View.OnLongClickListener longClickListener = v -> {
                if (adapter.listener != null) {
                    adapter.listener.onMessageLongClick(message, v);
                }
                return true;
            };

            itemView.setOnClickListener(clickListener);
            itemView.setOnLongClickListener(longClickListener);
            tvMessage.setOnClickListener(clickListener);
            tvMessage.setOnLongClickListener(longClickListener);
        }
    }

    static class SentImageViewHolder extends RecyclerView.ViewHolder {
        ImageView ivMessage;
        TextView tvTimestamp, tvForwarded;
        ImageView ivStatus, ivPinned, ivStarred;
        LinearLayout layoutReactions;
        SentImageViewHolder(@NonNull View itemView) {
            super(itemView);
            ivMessage = itemView.findViewById(R.id.ivMessageImage);
            tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
            ivStatus = itemView.findViewById(R.id.ivStatus);
            ivPinned = itemView.findViewById(R.id.ivPinned);
            ivStarred = itemView.findViewById(R.id.ivStarred);
            tvForwarded = itemView.findViewById(R.id.tvForwarded);
            layoutReactions = itemView.findViewById(R.id.layoutReactions);
        }
        void bind(ChatMessage message, MessageAdapter adapter) {
            Glide.with(itemView.getContext()).load(message.getFileUri()).into(ivMessage);
            tvTimestamp.setText(formatTime(message.getTimestamp()));
            setStatusIcon(ivStatus, message.getStatus());
            adapter.bindReactions(message, layoutReactions);

            if (ivPinned != null) ivPinned.setVisibility(message.isPinned() ? View.VISIBLE : View.GONE);
            if (ivStarred != null) ivStarred.setVisibility(message.isStarred() ? View.VISIBLE : View.GONE);

            if (tvForwarded != null) {
                tvForwarded.setVisibility(message.isForwarded() ? View.VISIBLE : View.GONE);
            }

            View.OnClickListener clickListener = v -> {
                if (adapter.getSelectedCount() > 0) {
                    adapter.toggleSelection(message.getId());
                } else if (adapter.listener != null) {
                    adapter.listener.onMessageClick(message, v);
                }
            };

            View.OnLongClickListener longClickListener = v -> {
                if (adapter.listener != null) {
                    adapter.listener.onMessageLongClick(message, v);
                }
                return true;
            };

            itemView.setOnClickListener(clickListener);
            itemView.setOnLongClickListener(longClickListener);
            ivMessage.setOnClickListener(clickListener);
            ivMessage.setOnLongClickListener(longClickListener);
        }
    }

    static class ReceivedImageViewHolder extends RecyclerView.ViewHolder {
        TextView tvSenderName, tvForwarded;
        ImageView ivMessage, ivPinned, ivStarred, ivSenderProfile;
        TextView tvTimestamp;
        LinearLayout layoutReactions;
        ReceivedImageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSenderName = itemView.findViewById(R.id.tvSenderName);
            ivMessage = itemView.findViewById(R.id.ivMessageImage);
            tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
            ivPinned = itemView.findViewById(R.id.ivPinned);
            ivStarred = itemView.findViewById(R.id.ivStarred);
            ivSenderProfile = itemView.findViewById(R.id.ivSenderProfile);
            tvForwarded = itemView.findViewById(R.id.tvForwarded);
            layoutReactions = itemView.findViewById(R.id.layoutReactions);
        }
        void bind(ChatMessage message, MessageAdapter adapter) {
            tvSenderName.setText(message.getSenderName());
            Glide.with(itemView.getContext()).load(message.getFileUri()).into(ivMessage);
            tvTimestamp.setText(formatTime(message.getTimestamp()));
            adapter.bindReactions(message, layoutReactions);
            adapter.loadSenderProfile(this, message.getSenderPhone(), ivSenderProfile);

            if (ivPinned != null) ivPinned.setVisibility(message.isPinned() ? View.VISIBLE : View.GONE);
            if (ivStarred != null) ivStarred.setVisibility(message.isStarred() ? View.VISIBLE : View.GONE);

            if (tvForwarded != null) {
                tvForwarded.setVisibility(message.isForwarded() ? View.VISIBLE : View.GONE);
            }

            View.OnClickListener clickListener = v -> {
                if (adapter.getSelectedCount() > 0) {
                    adapter.toggleSelection(message.getId());
                } else if (adapter.listener != null) {
                    adapter.listener.onMessageClick(message, v);
                }
            };

            View.OnLongClickListener longClickListener = v -> {
                if (adapter.listener != null) {
                    adapter.listener.onMessageLongClick(message, v);
                }
                return true;
            };

            itemView.setOnClickListener(clickListener);
            itemView.setOnLongClickListener(longClickListener);
            ivMessage.setOnClickListener(clickListener);
            ivMessage.setOnLongClickListener(longClickListener);
        }
    }

    abstract static class AudioViewHolder extends RecyclerView.ViewHolder {
        ImageButton btnPlayAudio;
        SeekBar audioSeekBar;
        TextView tvTimestamp, tvForwarded;
        ImageView ivStatus, ivPinned, ivStarred;
        LinearLayout layoutReactions;

        AudioViewHolder(@NonNull View itemView) {
            super(itemView);
            btnPlayAudio = itemView.findViewById(R.id.btnPlayAudio);
            audioSeekBar = itemView.findViewById(R.id.audioSeekBar);
            tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
            ivStatus = itemView.findViewById(R.id.ivStatus);
            ivPinned = itemView.findViewById(R.id.ivPinned);
            ivStarred = itemView.findViewById(R.id.ivStarred);
            tvForwarded = itemView.findViewById(R.id.tvForwarded);
            layoutReactions = itemView.findViewById(R.id.layoutReactions);
        }

        void bind(ChatMessage message, MessageAdapter adapter) {
            tvTimestamp.setText(formatTime(message.getTimestamp()));
            setStatusIcon(ivStatus, message.getStatus());
            adapter.bindReactions(message, layoutReactions);

            if (ivPinned != null) ivPinned.setVisibility(message.isPinned() ? View.VISIBLE : View.GONE);
            if (ivStarred != null) ivStarred.setVisibility(message.isStarred() ? View.VISIBLE : View.GONE);
            
            if (tvForwarded != null) {
                tvForwarded.setVisibility(message.isForwarded() ? View.VISIBLE : View.GONE);
            }
            
            if (message.getFileUri() != null && message.getFileUri().equals(adapter.currentlyPlayingUrl)) {
                adapter.currentPlayingViewHolder = this;
                btnPlayAudio.setImageResource(adapter.mediaPlayer != null && adapter.mediaPlayer.isPlaying() ? 
                        android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
                if (adapter.mediaPlayer != null) {
                    audioSeekBar.setMax(adapter.mediaPlayer.getDuration());
                    audioSeekBar.setProgress(adapter.mediaPlayer.getCurrentPosition());
                }
            } else {
                btnPlayAudio.setImageResource(android.R.drawable.ic_media_play);
                audioSeekBar.setProgress(0);
            }

            btnPlayAudio.setOnClickListener(v -> {
                if (message.getFileUri() != null) {
                    adapter.playAudio(message.getFileUri(), this);
                }
            });

            View.OnClickListener itemClickListener = v -> {
                if (adapter.getSelectedCount() > 0) {
                    adapter.toggleSelection(message.getId());
                } else if (adapter.listener != null) {
                    adapter.listener.onMessageClick(message, v);
                }
            };

            View.OnLongClickListener longClickListener = v -> {
                if (adapter.listener != null) {
                    adapter.listener.onMessageLongClick(message, v);
                }
                return true;
            };

            itemView.setOnClickListener(itemClickListener);
            itemView.setOnLongClickListener(longClickListener);

            audioSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser && message.getFileUri().equals(adapter.currentlyPlayingUrl) && adapter.mediaPlayer != null) {
                        adapter.mediaPlayer.seekTo(progress);
                    }
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }
    }

    static class SentAudioViewHolder extends AudioViewHolder {
        SentAudioViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }

    static class ReceivedAudioViewHolder extends AudioViewHolder {
        TextView tvSenderName;
        ImageView ivSenderProfile;
        ReceivedAudioViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSenderName = itemView.findViewById(R.id.tvSenderName);
            ivSenderProfile = itemView.findViewById(R.id.ivSenderProfile);
        }
        @Override
        void bind(ChatMessage message, MessageAdapter adapter) {
            super.bind(message, adapter);
            if (tvSenderName != null) {
                tvSenderName.setText(message.getSenderName());
            }
            adapter.loadSenderProfile(this, message.getSenderPhone(), ivSenderProfile);
        }
    }

    private static String formatTime(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }
}
