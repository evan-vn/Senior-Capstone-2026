package com.example.nailit.ui;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
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

public class PolishesActivity extends AppCompatActivity {

    private static final String TAG = "PolishesActivity";

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView tvEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_polishes);

        recyclerView = findViewById(R.id.rv_polishes);
        progressBar = findViewById(R.id.progress_bar);
        tvEmpty = findViewById(R.id.tv_empty);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        loadPolishes();
    }

    private void loadPolishes() {
        progressBar.setVisibility(View.VISIBLE);

        TokenStore tokenStore = new TokenStore(this);
        PolishesRepository repo = new PolishesRepository(tokenStore);

        repo.getPolishes(new PolishesRepository.PolishesCallback() {
            @Override
            public void onSuccess(List<Polish> polishes) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);

                    if (polishes.isEmpty()) {
                        tvEmpty.setVisibility(View.VISIBLE);
                        return;
                    }

                    Log.d(TAG, "Displaying " + polishes.size() + " polishes");
                    recyclerView.setAdapter(new PolishesAdapter(polishes));
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    tvEmpty.setVisibility(View.VISIBLE);
                    tvEmpty.setText(message);
                    Toast.makeText(PolishesActivity.this, message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }
}
