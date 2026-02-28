package com.example.nailit.ui;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

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

        int dotColor = parseHex(p.getHex());
        h.colorDot.setBackgroundTintList(ColorStateList.valueOf(dotColor));

        if (p.getFavoriteCount() > 0) {
            h.tvFavoriteCount.setText("♥ " + p.getFavoriteCount());
            h.tvFavoriteCount.setVisibility(View.VISIBLE);
        } else {
            h.tvFavoriteCount.setVisibility(View.GONE);
        }

        String collection = p.getCollection();
        h.tvCollection.setText(collection != null ? collection : "");
        h.tvCollection.setVisibility(collection != null ? View.VISIBLE : View.GONE);
    }

    @Override
    public int getItemCount() {
        return items != null ? items.size() : 0;
    }

    private static int parseHex(String hex) {
        if (hex == null || hex.isEmpty()) return 0xFFCCCCCC;
        try {
            if (!hex.startsWith("#")) hex = "#" + hex;
            return Color.parseColor(hex);
        } catch (IllegalArgumentException e) {
            return 0xFFCCCCCC;
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final View colorDot;
        final TextView tvName;
        final TextView tvBrand;
        final TextView tvFavoriteCount;
        final TextView tvCollection;

        ViewHolder(View itemView) {
            super(itemView);
            colorDot = itemView.findViewById(R.id.color_dot);
            tvName = itemView.findViewById(R.id.tv_polish_name);
            tvBrand = itemView.findViewById(R.id.tv_polish_brand);
            tvFavoriteCount = itemView.findViewById(R.id.tv_favorite_count);
            tvCollection = itemView.findViewById(R.id.tv_polish_finish);
        }
    }
}
