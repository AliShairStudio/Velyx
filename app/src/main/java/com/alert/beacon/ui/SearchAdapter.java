package com.alert.beacon.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.alert.beacon.R;
import com.alert.beacon.data.SearchResult;

import java.util.List;

public class SearchAdapter extends RecyclerView.Adapter<SearchAdapter.ViewHolder> {

    private List<SearchResult> results;

    public SearchAdapter(List<SearchResult> results) {
        this.results = results;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_search_result, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SearchResult result = results.get(position);
        holder.tvTitle.setText(result.getTitle());
        holder.tvSubtitle.setText(result.getSubtitle());
        holder.ivIcon.setImageResource(result.getIconResId());

        holder.itemView.setOnClickListener(v -> {
            if (result.getIntent() != null) {
                v.getContext().startActivity(result.getIntent());
            } else if (result.getAction() != null) {
                result.getAction().run();
            }
        });
    }

    @Override
    public int getItemCount() {
        return results.size();
    }

    public void updateData(List<SearchResult> newResults) {
        this.results = newResults;
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        TextView tvTitle, tvSubtitle;

        ViewHolder(View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.ivSearchIcon);
            tvTitle = itemView.findViewById(R.id.tvSearchTitle);
            tvSubtitle = itemView.findViewById(R.id.tvSearchSubtitle);
        }
    }
}
