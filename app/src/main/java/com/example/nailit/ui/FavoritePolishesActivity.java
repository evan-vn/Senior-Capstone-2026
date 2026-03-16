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
import com.example.nailit.data.model.Polish;
import com.example.nailit.data.network.TokenStore;
import com.example.nailit.data.repo.FavoritesRepository;
import com.example.nailit.data.repo.PolishesRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class FavoritePolishesActivity extends AppCompatActivity {

    private static final String TAG = "FavPolishes";

    private ProgressBar progress;
    private TextView emptyText;
    private RecyclerView recycler;

    private PolishGridAdapter adapter;
    private FavoritesRepository favoritesRepo;
    private PolishesRepository polishesRepo;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_favorite_polishes);

        findViewById(R.id.favPolishBack).setOnClickListener(v -> finish());
        progress = findViewById(R.id.favPolishProgress);
        emptyText = findViewById(R.id.favPolishEmpty);
        recycler = findViewById(R.id.favPolishRecycler);

        TokenStore tokenStore = new TokenStore(this);
        favoritesRepo = new FavoritesRepository(tokenStore);
        polishesRepo = new PolishesRepository(tokenStore);

        recycler.setLayoutManager(new GridLayoutManager(this, 2));
        adapter = new PolishGridAdapter(favoritesRepo);
        recycler.setAdapter(adapter);

        loadFavorites();
    }

    private void loadFavorites() {
        showLoading();
        favoritesRepo.getMyFavoritePolishes(new FavoritesRepository.FavoritesListCallback() {
            @Override
            public void onSuccess(Set<String> polishUids) {
                Log.d(TAG, "Favorite polish UIDs: " + polishUids.size());
                if (polishUids.isEmpty()) {
                    runOnUiThread(() -> showEmpty());
                    return;
                }
                adapter.setFavoriteUids(polishUids);
                fetchPolishes(polishUids);
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "Failed to load favorite UIDs: " + message);
                runOnUiThread(() -> showEmpty());
            }
        });
    }

    private void fetchPolishes(Set<String> uids) {
        polishesRepo.getPolishesByUids(uids, new PolishesRepository.PolishesCallback() {
            @Override
            public void onSuccess(List<Polish> polishes) {
                Log.d(TAG, "Fetched " + polishes.size() + " favorite polishes");
                runOnUiThread(() -> showResults(polishes));
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "Failed to fetch polishes: " + message);
                runOnUiThread(() -> {
                    showEmpty();
                    Toast.makeText(FavoritePolishesActivity.this, message, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void showLoading() {
        progress.setVisibility(View.VISIBLE);
        emptyText.setVisibility(View.GONE);
        recycler.setVisibility(View.GONE);
    }

    private void showResults(List<Polish> polishes) {
        progress.setVisibility(View.GONE);
        if (polishes == null || polishes.isEmpty()) {
            showEmpty();
            return;
        }
        emptyText.setVisibility(View.GONE);
        recycler.setVisibility(View.VISIBLE);
        adapter.setItems(polishes);
    }

    private void showEmpty() {
        progress.setVisibility(View.GONE);
        recycler.setVisibility(View.GONE);
        emptyText.setVisibility(View.VISIBLE);
    }
}
