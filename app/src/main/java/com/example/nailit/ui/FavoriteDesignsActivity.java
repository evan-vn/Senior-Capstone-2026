package com.example.nailit.ui;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nailit.R;
import com.example.nailit.data.model.NailDesign;
import com.example.nailit.data.network.TokenStore;
import com.example.nailit.data.repo.DesignFavoritesRepository;
import com.example.nailit.data.repo.DesignsRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FavoriteDesignsActivity extends AppCompatActivity {

    private static final String TAG = "FavDesigns";

    private ProgressBar progress;
    private TextView emptyText;
    private RecyclerView recycler;

    private DesignsByPolishAdapter adapter;
    private DesignFavoritesRepository favoritesRepo;
    private DesignsRepository designsRepo;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_favorite_designs);

        findViewById(R.id.favDesignBack).setOnClickListener(v -> finish());
        progress = findViewById(R.id.favDesignProgress);
        emptyText = findViewById(R.id.favDesignEmpty);
        recycler = findViewById(R.id.favDesignRecycler);

        TokenStore tokenStore = new TokenStore(this);
        favoritesRepo = new DesignFavoritesRepository(tokenStore);
        designsRepo = new DesignsRepository(tokenStore);

        recycler.setLayoutManager(new GridLayoutManager(this, 2));
        adapter = new DesignsByPolishAdapter();
        recycler.setAdapter(adapter);

        adapter.setOnFavoriteClickListener((design, newState, position) -> {
            if (newState) {
                favoritesRepo.addFavorite(design.getId(), new DesignFavoritesRepository.FavoriteCallback() {
                    @Override
                    public void onSuccess() {
                        runOnUiThread(() ->
                                adapter.updateFavoriteState(design.getId(), true, position));
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> Toast.makeText(
                                FavoriteDesignsActivity.this,
                                message != null ? message : "Could not favorite design",
                                Toast.LENGTH_SHORT).show());
                    }
                });
            } else {
                favoritesRepo.removeFavorite(design.getId(), new DesignFavoritesRepository.FavoriteCallback() {
                    @Override
                    public void onSuccess() {
                        runOnUiThread(() ->
                                adapter.updateFavoriteState(design.getId(), false, position));
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> Toast.makeText(
                                FavoriteDesignsActivity.this,
                                message != null ? message : "Could not remove favorite",
                                Toast.LENGTH_SHORT).show());
                    }
                });
            }
        });

        loadFavorites();
    }

    private void loadFavorites() {
        showLoading();
        favoritesRepo.getMyFavoriteDesigns(new DesignFavoritesRepository.FavoritesListCallback() {
            @Override
            public void onSuccess(Set<Long> designIds) {
                Log.d(TAG, "Favorite design IDs: " + designIds.size());
                if (designIds.isEmpty()) {
                    runOnUiThread(() -> showEmpty());
                    return;
                }
                adapter.setFavoriteIds(designIds);
                fetchDesigns(designIds);
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "Failed to load favorite design IDs: " + message);
                runOnUiThread(() -> showEmpty());
            }
        });
    }

    private void fetchDesigns(Set<Long> ids) {
        designsRepo.getDesignsByIds(ids, new DesignsRepository.DesignsCallback() {
            @Override
            public void onSuccess(List<NailDesign> designs) {
                Log.d(TAG, "Fetched " + designs.size() + " favorite designs");
                runOnUiThread(() -> showResults(designs));
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "Failed to fetch designs: " + message);
                runOnUiThread(() -> {
                    showEmpty();
                    Toast.makeText(FavoriteDesignsActivity.this, message, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void showLoading() {
        progress.setVisibility(View.VISIBLE);
        emptyText.setVisibility(View.GONE);
        recycler.setVisibility(View.GONE);
    }

    private void showResults(List<NailDesign> designs) {
        progress.setVisibility(View.GONE);
        if (designs == null || designs.isEmpty()) {
            showEmpty();
            return;
        }
        emptyText.setVisibility(View.GONE);
        recycler.setVisibility(View.VISIBLE);
        adapter.setItems(designs);
    }

    private void showEmpty() {
        progress.setVisibility(View.GONE);
        recycler.setVisibility(View.GONE);
        emptyText.setVisibility(View.VISIBLE);
    }
}
