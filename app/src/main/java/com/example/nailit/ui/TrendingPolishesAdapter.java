package com.example.nailit.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.example.nailit.R;
import com.example.nailit.data.model.Polish;

import java.util.ArrayList;
import java.util.List;

public class TrendingPolishesAdapter
        extends RecyclerView.Adapter<TrendingPolishesAdapter.ViewHolder> {

    private List<Polish> items;

    public TrendingPolishesAdapter(List<Polish> items) {
        this.items = items != null ? items : new ArrayList<>();
    }

    public void setItems(List<Polish> items) {
        this.items = items != null ? items : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.row_polish, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        Polish p = items.get(position);

        h.tvName.setText(p.getShadeName() != null ? p.getShadeName() : "—");
        h.tvBrand.setText(p.getBrand() != null ? p.getBrand() : "—");

        Glide.with(h.itemView.getContext())
                .load(p.getSwatchUrl())
                .placeholder(R.drawable.placeholder_swatch)
                .error(R.drawable.placeholder_swatch)
                .transform(new CircleCrop())
                .into(h.swatchImage);

        if (p.getFavoriteCount() > 0) {
            h.tvFavoriteCount.setText("♥ " + p.getFavoriteCount());
            h.tvFavoriteCount.setVisibility(View.VISIBLE);
        } else {
            h.tvFavoriteCount.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return items != null ? items.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView swatchImage;
        final TextView tvName;
        final TextView tvBrand;
        final TextView tvFavoriteCount;

        ViewHolder(View itemView) {
            super(itemView);
            swatchImage = itemView.findViewById(R.id.swatch_image);
            tvName = itemView.findViewById(R.id.tv_polish_name);
            tvBrand = itemView.findViewById(R.id.tv_polish_brand);
            tvFavoriteCount = itemView.findViewById(R.id.tv_favorite_count);
        }
    }
}
