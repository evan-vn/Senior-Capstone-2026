package com.example.nailit.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nailit.R;
import com.example.nailit.data.model.Polish;
import com.example.nailit.data.network.TokenStore;
import com.example.nailit.data.repo.PolishesRepository;

import java.util.List;

public class TrendingPolishesActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView tvEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trending_polishes);

        recyclerView = findViewById(R.id.rv_trending);
        progressBar = findViewById(R.id.progress_bar);
        tvEmpty = findViewById(R.id.tv_empty);

        ImageView btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        loadTrending();
    }

    private void loadTrending() {
        progressBar.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);

        TokenStore tokenStore = new TokenStore(this);
        PolishesRepository repo = new PolishesRepository(tokenStore);

        repo.getTrendingPolishes(new PolishesRepository.PolishesCallback() {
            @Override
            public void onSuccess(List<Polish> polishes) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);

                    if (polishes.isEmpty()) {
                        tvEmpty.setVisibility(View.VISIBLE);
                        return;
                    }

                    recyclerView.setAdapter(new TrendingPolishesAdapter(polishes));
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    tvEmpty.setVisibility(View.VISIBLE);
                    tvEmpty.setText(message);
                    Toast.makeText(TrendingPolishesActivity.this, message,
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }
}
