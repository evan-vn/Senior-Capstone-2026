package com.example.nailit.ui;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.nailit.R;
import com.example.nailit.data.model.NailDesign;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DesignsByPolishAdapter
        extends RecyclerView.Adapter<DesignsByPolishAdapter.ViewHolder> {

    public interface OnFavoriteClickListener {
        void onFavoriteClick(NailDesign design, boolean newState, int position);
    }

    private final List<NailDesign> items = new ArrayList<>();
    private final Set<Long> favoriteIds = new HashSet<>();
    private OnFavoriteClickListener favoriteClickListener;

    public void setItems(List<NailDesign> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    public void setFavoriteIds(Set<Long> ids) {
        favoriteIds.clear();
        if (ids != null) {
            favoriteIds.addAll(ids);
        }
        notifyDataSetChanged();
    }

    public void setOnFavoriteClickListener(OnFavoriteClickListener listener) {
        this.favoriteClickListener = listener;
    }

    public void updateFavoriteState(long designId, boolean isFavorited, int position) {
        if (isFavorited) {
            favoriteIds.add(designId);
        } else {
            favoriteIds.remove(designId);
        }
        if (position >= 0 && position < items.size()) {
            notifyItemChanged(position);
        } else {
            notifyDataSetChanged();
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.row_design, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        NailDesign d = items.get(position);

        Glide.with(h.itemView.getContext())
                .load(d.getImageUrl())
                .placeholder(R.drawable.placeholder_swatch)
                .error(R.drawable.placeholder_swatch)
                .centerCrop()
                .into(h.image);

        boolean isFavorited = favoriteIds.contains(d.getId());
        d.setFavorited(isFavorited);

        if (isFavorited) {
            h.heart.setImageResource(R.drawable.ic_heart_filled);
            h.heart.clearColorFilter();
        } else {
            h.heart.setImageResource(R.drawable.ic_heart_outline);
            h.heart.setColorFilter(Color.WHITE);
        }

        h.heart.setOnClickListener(v -> {
            boolean newState = !d.isFavorited();
            if (favoriteClickListener != null) {
                favoriteClickListener.onFavoriteClick(d, newState, h.getBindingAdapterPosition());
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView image;
        final ImageView heart;

        ViewHolder(View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.designImage);
            heart = itemView.findViewById(R.id.designHeart);
        }
    }
}
