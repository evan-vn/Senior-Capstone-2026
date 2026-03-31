package com.example.nailit.ui;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.nailit.R;
import com.example.nailit.data.model.Polish;
import com.example.nailit.data.repo.FavoritesRepository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PolishGridAdapter
        extends RecyclerView.Adapter<PolishGridAdapter.ViewHolder> {

    public interface OnPolishClickListener {
        void onPolishClick(Polish polish);
    }

    @Nullable
    private OnPolishClickListener listener;

    private List<Polish> items = new ArrayList<>();
    private final Set<String> favoriteUids = new HashSet<>();
    @Nullable
    private final FavoritesRepository favoritesRepo;

    public PolishGridAdapter() {
        this.favoritesRepo = null;
        this.listener = null;
    }

    public PolishGridAdapter(@Nullable FavoritesRepository favoritesRepo) {
        this.favoritesRepo = favoritesRepo;
        this.listener = null;
    }

    public void setOnPolishClickListener(OnPolishClickListener listener) {
        this.listener = listener;
    }

    public PolishGridAdapter(@Nullable FavoritesRepository favoritesRepo,
                             @Nullable OnPolishClickListener clickListener) {
        this.favoritesRepo = favoritesRepo;
        this.listener = clickListener;
    }

    public void setItems(List<Polish> items) {
        this.items = items != null ? items : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void setFavoriteUids(Set<String> uids) {
        favoriteUids.clear();
        if (uids != null) {
            favoriteUids.addAll(uids);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_polish_grid, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        Polish p = items.get(position);
        String uid = p.getUid();

        String brand = p.getBrand() != null ? p.getBrand().trim() : "";
        String shade = p.getShadeName() != null ? p.getShadeName().trim() : "";
        String displayName;
        if (!brand.isEmpty() && !shade.isEmpty()) {
            displayName = brand + " - " + shade;
        } else if (!shade.isEmpty()) {
            displayName = shade;
        } else {
            displayName = "—";
        }
        h.tvName.setText(displayName);

        byte[] thumb = p.getThumbnailBytes();
        String swatchUrl = p.getSwatchUrl();
        Integer fallbackColor = tryParseHex(p.getHex());

        Object source = thumb != null ? (Object) thumb : swatchUrl;

        if (source != null) {
            Glide.with(h.itemView.getContext())
                    .load(source)
                    .placeholder(R.drawable.placeholder_swatch)
                    .error(R.drawable.placeholder_swatch)
                    .centerCrop()
                    .into(h.swatchImage);
        } else if (fallbackColor != null) {
            Log.d("SEARCH_RENDER", "uid=" + uid + " fallbackColorFromHex=true hex=" + p.getHex());
            h.swatchImage.setImageDrawable(new ColorDrawable(fallbackColor));
        } else {
            h.swatchImage.setImageDrawable(null);
        }

        boolean isFavorite = favoriteUids.contains(uid);
        h.heartIcon.setImageResource(isFavorite ? R.drawable.ic_heart_filled : R.drawable.ic_heart_outline);

        h.itemView.setOnClickListener(v -> {
            Log.d("ADAPTER_CLICK", "Clicked position: " + position);
            if (listener != null) {
                listener.onPolishClick(p);
            }
        });

        h.heartIcon.setOnClickListener(v -> {
            if (favoritesRepo != null) {
                if (isFavorite) {
                    favoritesRepo.removeFavorite(uid, new FavoritesRepository.FavoriteCallback() {
                        @Override
                        public void onSuccess() {
                            v.post(() -> {
                                favoriteUids.remove(uid);
                                notifyItemChanged(position);
                            });
                        }

                        @Override
                        public void onError(String message) {
                            v.post(() -> Toast.makeText(v.getContext(), message, Toast.LENGTH_SHORT).show());
                        }
                    });
                } else {
                    favoritesRepo.addFavorite(uid, new FavoritesRepository.FavoriteCallback() {
                        @Override
                        public void onSuccess() {
                            v.post(() -> {
                                favoriteUids.add(uid);
                                notifyItemChanged(position);
                            });
                        }

                        @Override
                        public void onError(String message) {
                            v.post(() -> Toast.makeText(v.getContext(), message, Toast.LENGTH_SHORT).show());
                        }
                    });
                }
            } else {
                if (favoriteUids.contains(uid)) {
                    favoriteUids.remove(uid);
                } else {
                    favoriteUids.add(uid);
                }
                notifyItemChanged(position);
            }
        });
    }

    private Integer tryParseHex(String hex) {
        if (hex == null) return null;
        String clean = hex.trim();
        if (clean.isEmpty()) return null;
        if (!clean.startsWith("#")) {
            clean = "#" + clean;
        }
        try {
            return Color.parseColor(clean);
        } catch (Exception ignored) {
            return null;
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView swatchImage;
        final ImageView heartIcon;
        final TextView tvName;

        ViewHolder(View itemView) {
            super(itemView);
            swatchImage = itemView.findViewById(R.id.swatch_image);
            heartIcon = itemView.findViewById(R.id.heart_icon);
            tvName = itemView.findViewById(R.id.tv_polish_name);
        }
    }
}