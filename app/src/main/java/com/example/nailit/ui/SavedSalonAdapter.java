package com.example.nailit.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nailit.R;
import com.example.nailit.data.model.SavedSalonResponse;

import java.util.List;

public class SavedSalonAdapter extends RecyclerView.Adapter<SavedSalonAdapter.SalonViewHolder> {

    public interface OnHeartClickListener {
        void onHeartClick(SavedSalonResponse salon, int position);
    }

    private final List<SavedSalonResponse> salons;
    private OnHeartClickListener onHeartClickListener;

    public SavedSalonAdapter(List<SavedSalonResponse> salons) {
        this.salons = salons;
    }

    public void setOnHeartClickListener(OnHeartClickListener listener) {
        this.onHeartClickListener = listener;
    }

    @NonNull
    @Override
    public SalonViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_saved_salon, parent, false);
        return new SalonViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SalonViewHolder holder, int position) {
        SavedSalonResponse salon = salons.get(position);

        holder.tvSalonName.setText(salon.getSalonName());
        holder.tvSalonAddress.setText(
                salon.getAddress() != null ? salon.getAddress() : "No address"
        );
        holder.tvSalonRating.setText(
                salon.getRating() != null ? "Rating: " + salon.getRating() : "Rating: N/A"
        );

        holder.btnHeartSavedSalon.setImageResource(R.drawable.ic_heart_filled);

        holder.btnHeartSavedSalon.setOnClickListener(v -> {
            if (onHeartClickListener != null) {
                onHeartClickListener.onHeartClick(salon, holder.getAdapterPosition());
            }
        });
    }

    @Override
    public int getItemCount() {
        return salons == null ? 0 : salons.size();
    }

    static class SalonViewHolder extends RecyclerView.ViewHolder {
        TextView tvSalonName, tvSalonAddress, tvSalonRating;
        ImageButton btnHeartSavedSalon;

        public SalonViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSalonName = itemView.findViewById(R.id.tvSalonName);
            tvSalonAddress = itemView.findViewById(R.id.tvSalonAddress);
            tvSalonRating = itemView.findViewById(R.id.tvSalonRating);
            btnHeartSavedSalon = itemView.findViewById(R.id.btnHeartSavedSalon);
        }
    }
}