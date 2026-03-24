package com.example.nailit.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.nailit.R;
import com.example.nailit.data.model.Polish;

import java.util.ArrayList;
import java.util.List;

public class ChatPolishAdapter extends RecyclerView.Adapter<ChatPolishAdapter.ViewHolder> {

    private List<Polish> items = new ArrayList<>();

    public void setItems(List<Polish> polishes) {
        items = polishes != null ? polishes : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chat_polish, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Polish polish = items.get(position);
        holder.name.setText(polish.getShadeName() != null ? polish.getShadeName() : "—");
        holder.brand.setText(polish.getBrand() != null ? polish.getBrand() : "");

        byte[] thumb = polish.getThumbnailBytes();
        Object source = thumb != null ? (Object) thumb : polish.getSwatchUrl();
        Glide.with(holder.itemView.getContext())
                .load(source)
                .placeholder(R.drawable.placeholder_swatch)
                .error(R.drawable.placeholder_swatch)
                .centerCrop()
                .into(holder.image);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView image;
        final TextView name;
        final TextView brand;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.chatPolishImage);
            name = itemView.findViewById(R.id.chatPolishName);
            brand = itemView.findViewById(R.id.chatPolishBrand);
        }
    }
}

