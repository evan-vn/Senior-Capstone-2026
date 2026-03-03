package com.example.nailit.data.repo;

import android.util.Log;

import androidx.annotation.NonNull;

import com.example.nailit.data.api.PolishesApi;
import com.example.nailit.data.model.Polish;
import com.example.nailit.data.network.ApiClient;
import com.example.nailit.data.network.RetrofitUtil;
import com.example.nailit.data.network.TokenStore;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PolishesRepository {

    private static final String TAG = "PolishesRepository";

    private final PolishesApi polishesApi;

    public interface PolishesCallback {
        void onSuccess(List<Polish> polishes);
        void onError(String message);
    }

    public PolishesRepository(TokenStore tokenStore) {
        this.polishesApi = ApiClient.getInstance(tokenStore).create(PolishesApi.class);
    }

    public void getPolishes(PolishesCallback callback) {
        polishesApi.getPolishes("*").enqueue(new Callback<List<Polish>>() {
            @Override
            public void onResponse(@NonNull Call<List<Polish>> call,
                                   @NonNull Response<List<Polish>> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    callback.onError(RetrofitUtil.extractError("Polishes", response));
                    return;
                }
                Log.d(TAG, "Fetched " + response.body().size() + " polishes");
                callback.onSuccess(response.body());
            }

            @Override
            public void onFailure(@NonNull Call<List<Polish>> call, @NonNull Throwable t) {
                callback.onError("Polishes network error: " + t.getMessage());
            }
        });
    }

    public void getTrendingPolishes(PolishesCallback callback) {
        polishesApi.getTrendingPolishes("*", "favorite_count.desc", "30")
                .enqueue(new Callback<List<Polish>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<Polish>> call,
                                           @NonNull Response<List<Polish>> response) {
                        if (!response.isSuccessful() || response.body() == null) {
                            String msg = RetrofitUtil.extractError("Trending", response);
                            Log.e(TAG, msg);
                            callback.onError(msg);
                            return;
                        }
                        Log.d(TAG, "Trending polishes: " + response.body().size() + " items");
                        callback.onSuccess(response.body());
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<Polish>> call, @NonNull Throwable t) {
                        callback.onError("Trending network error: " + t.getMessage());
                    }
                });
    }

    //PostgREST JSONB containment: season_labels=cs.["spring"]
    public void getPolishesBySeason(String seasonTag, PolishesCallback callback) {
        String filter = "cs.[\"" + seasonTag + "\"]";

        polishesApi.getPolishesBySeason("*", filter, "30")
                .enqueue(new Callback<List<Polish>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<Polish>> call,
                                           @NonNull Response<List<Polish>> response) {
                        if (!response.isSuccessful() || response.body() == null) {
                            String msg = RetrofitUtil.extractError("Season/" + seasonTag, response);
                            Log.e(TAG, msg);
                            callback.onError(msg);
                            return;
                        }
                        Log.d(TAG, "Season '" + seasonTag + "': "
                                + response.body().size() + " polishes");
                        callback.onSuccess(response.body());
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<Polish>> call, @NonNull Throwable t) {
                        callback.onError("Season network error: " + t.getMessage());
                    }
                });
    }

}
