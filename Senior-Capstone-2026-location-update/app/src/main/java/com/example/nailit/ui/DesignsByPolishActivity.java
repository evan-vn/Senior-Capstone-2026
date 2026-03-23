package com.example.nailit.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nailit.R;
import com.example.nailit.data.model.NailDesign;
import com.example.nailit.data.network.TokenStore;
import com.example.nailit.data.repo.DesignFavoritesRepository;
import com.example.nailit.data.repo.DesignsRepository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DesignsByPolishActivity extends AppCompatActivity {

    public static final String EXTRA_POLISH_UID = "extra_polish_uid";
    public static final String EXTRA_POLISH_NAME = "extra_polish_name";

    private TextView titleText;
    private ProgressBar progress;
    private TextView emptyText;
    private RecyclerView recycler;

    private DesignsByPolishAdapter adapter;
    private DesignsRepository repo;
    private DesignFavoritesRepository favoritesRepo;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_designs_by_polish);

        ImageButton backButton = findViewById(R.id.designsBack);
        titleText = findViewById(R.id.designsTitle);
        progress = findViewById(R.id.designsProgress);
        emptyText = findViewById(R.id.designsEmpty);
        recycler = findViewById(R.id.designsRecycler);

        String polishUid = getIntent().getStringExtra(EXTRA_POLISH_UID);
        String polishName = getIntent().getStringExtra(EXTRA_POLISH_NAME);

        String name = polishName != null && !polishName.isEmpty() ? polishName : "this polish";
        titleText.setText("Designs using " + name);

        recycler.setLayoutManager(new GridLayoutManager(this, 2));
        adapter = new DesignsByPolishAdapter();
        recycler.setAdapter(adapter);

        TokenStore tokenStore = new TokenStore(this);
        repo = new DesignsRepository(tokenStore);
        favoritesRepo = new DesignFavoritesRepository(tokenStore);

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
                        //This is user feedback, message kept simple
                        android.widget.Toast.makeText(
                                DesignsByPolishActivity.this,
                                message != null ? message : "Could not favorite design",
                                android.widget.Toast.LENGTH_SHORT
                        ).show();
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
                        android.widget.Toast.makeText(
                                DesignsByPolishActivity.this,
                                message != null ? message : "Could not remove favorite",
                                android.widget.Toast.LENGTH_SHORT
                        ).show();
                    }
                });
            }
        });

        backButton.setOnClickListener(v -> finish());

        load(polishUid);
    }

    private void load(String polishUid) {
        showLoading();
        if (polishUid == null || polishUid.isEmpty()) {
            showEmpty("Missing polish id");
            return;
        }

        repo.getDesignsForPolish(polishUid, new DesignsRepository.DesignsCallback() {
            @Override
            public void onSuccess(List<NailDesign> designs) {
                runOnUiThread(() -> {
                    showResults(designs);
                    loadFavorites();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> showEmpty(message != null ? message : "Failed to load designs"));
            }
        });
    }

    private void showLoading() {
        progress.setVisibility(View.VISIBLE);
        emptyText.setVisibility(View.GONE);
        recycler.setVisibility(View.GONE);
        adapter.setItems(new ArrayList<>());
    }

    private void showResults(List<NailDesign> designs) {
        progress.setVisibility(View.GONE);
        if (designs == null || designs.isEmpty()) {
            showEmpty("No designs found for this polish");
            return;
        }
        emptyText.setVisibility(View.GONE);
        recycler.setVisibility(View.VISIBLE);
        adapter.setItems(designs);
    }

    private void loadFavorites() {
        favoritesRepo.getMyFavoriteDesigns(new DesignFavoritesRepository.FavoritesListCallback() {
            @Override
            public void onSuccess(Set<Long> designIds) {
                runOnUiThread(() -> adapter.setFavoriteIds(
                        designIds != null ? designIds : new HashSet<>()));
            }

            @Override
            public void onError(String message) {
                //No UI change needed on failure; favorites just will not pre-load
            }
        });
    }

    private void showEmpty(String message) {
        progress.setVisibility(View.GONE);
        recycler.setVisibility(View.GONE);
        emptyText.setText(message);
        emptyText.setVisibility(View.VISIBLE);
    }
}

