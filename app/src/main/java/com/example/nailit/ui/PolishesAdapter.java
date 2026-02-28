package com.example.nailit.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nailit.R;
import com.example.nailit.data.model.Polish;

import java.util.List;

public class PolishesAdapter extends RecyclerView.Adapter<PolishesAdapter.ViewHolder> {

    private final List<Polish> items;

    public PolishesAdapter(List<Polish> items) {
        this.items = items;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_polish, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Polish polish = items.get(position);
        holder.tvName.setText(polish.getShadeName() != null ? polish.getShadeName() : "—");
        holder.tvBrand.setText(polish.getBrand() != null ? polish.getBrand() : "—");
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvName;
        final TextView tvBrand;

        ViewHolder(View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_polish_name);
            tvBrand = itemView.findViewById(R.id.tv_polish_brand);
        }
    }
}
