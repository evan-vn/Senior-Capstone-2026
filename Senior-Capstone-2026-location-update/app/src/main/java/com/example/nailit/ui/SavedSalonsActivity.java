package com.example.nailit.ui;

import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nailit.R;
import com.example.nailit.data.api.SavedSalonApi;
import com.example.nailit.data.model.SavedSalonResponse;
import com.example.nailit.data.network.ApiClient;
import com.example.nailit.data.network.TokenStore;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SavedSalonsActivity extends AppCompatActivity {

    public static final String EXTRA_USER_ID = "user_id";

    private RecyclerView recyclerSavedSalons;
    private SavedSalonAdapter adapter;
    private final List<SavedSalonResponse> salonList = new ArrayList<>();
    private ImageView btnBack;
    private String currentUserId;
    private SavedSalonApi savedSalonApi;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_saved_salons);

        btnBack = findViewById(R.id.btnBack);
        recyclerSavedSalons = findViewById(R.id.recyclerSavedSalons);

        btnBack.setOnClickListener(v -> finish());

        recyclerSavedSalons.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SavedSalonAdapter(salonList);
        recyclerSavedSalons.setAdapter(adapter);

        TokenStore tokenStore = new TokenStore(this);
        savedSalonApi = ApiClient.getInstance(tokenStore).create(SavedSalonApi.class);

        currentUserId = getIntent().getStringExtra(EXTRA_USER_ID);
        if (currentUserId == null || currentUserId.isEmpty()) {
            currentUserId = tokenStore.getUserId();
        }

        adapter.setOnHeartClickListener((salon, position) ->
                removeSavedSalon(salon, position));

        loadSavedSalons(currentUserId);
    }

    private void loadSavedSalons(String userId) {
        Log.d("SavedSalonsActivity", "userId = " + userId);

        if (userId == null || userId.isEmpty()) {
            Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show();
            return;
        }

        savedSalonApi.getSavedSalons("eq." + userId, "created_at.desc")
                .enqueue(new Callback<List<SavedSalonResponse>>() {
                    @Override
                    public void onResponse(Call<List<SavedSalonResponse>> call, Response<List<SavedSalonResponse>> response) {
                        Log.d("SavedSalonsActivity", "response code = " + response.code());

                        if (response.isSuccessful() && response.body() != null) {
                            salonList.clear();
                            salonList.addAll(response.body());
                            adapter.notifyDataSetChanged();

                            if (salonList.isEmpty()) {
                                Toast.makeText(SavedSalonsActivity.this, "No saved salons found", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(SavedSalonsActivity.this, "Failed to load saved salons", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<List<SavedSalonResponse>> call, Throwable t) {
                        Log.e("SavedSalonsActivity", "API error", t);
                        Toast.makeText(SavedSalonsActivity.this, "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void removeSavedSalon(SavedSalonResponse salon, int position) {
        if (currentUserId == null || currentUserId.trim().isEmpty()) {
            Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show();
            return;
        }

        savedSalonApi.removeSavedSalon("eq." + currentUserId, "eq." + salon.getPlaceId())
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> response) {
                        if (response.isSuccessful()) {
                            if (position >= 0 && position < salonList.size()) {
                                salonList.remove(position);
                                adapter.notifyItemRemoved(position);
                                Toast.makeText(SavedSalonsActivity.this, "Salon removed", Toast.LENGTH_SHORT).show();
                            }

                            if (salonList.isEmpty()) {
                                Toast.makeText(SavedSalonsActivity.this, "No saved salons found", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(SavedSalonsActivity.this, "Failed to remove salon", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        Toast.makeText(SavedSalonsActivity.this, "Error removing salon", Toast.LENGTH_SHORT).show();
                    }
                });
    }
}