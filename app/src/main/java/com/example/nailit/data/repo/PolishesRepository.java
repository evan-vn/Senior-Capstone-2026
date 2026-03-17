package com.example.nailit.data.repo;

import android.util.Log;

import androidx.annotation.NonNull;

import com.example.nailit.data.api.PolishesApi;
import com.example.nailit.data.model.Polish;
import com.example.nailit.data.network.ApiClient;
import com.example.nailit.data.network.RetrofitUtil;
import com.example.nailit.data.network.TokenStore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PolishesRepository {

    private static final String TAG = "PolishesRepository";
    //Base table select (no favorite_count column, no image_data)
    private static final String SELECT_LIST_BASE =
            "uid,brand,collection,shade_name,shade_code,description,hex,swatch_images,thumbnail_data";
    //Trending view select (view has favorite_count; omit thumbnail_data — view may predate that column)
    private static final String SELECT_LIST_TRENDING =
            "uid,brand,collection,shade_name,shade_code,description,hex,favorite_count,swatch_images";

    private final PolishesApi polishesApi;

    public interface PolishesCallback {
        void onSuccess(List<Polish> polishes);
        void onError(String message);
    }

    public PolishesRepository(TokenStore tokenStore) {
        this.polishesApi = ApiClient.getInstance(tokenStore).create(PolishesApi.class);
    }

    public void getTrendingPolishes(PolishesCallback callback) {
        polishesApi.getTrendingPolishes(SELECT_LIST_TRENDING, "favorite_count.desc", "30")
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

    public void getPolishesByUids(Collection<String> uids, PolishesCallback callback) {
        if (uids == null || uids.isEmpty()) {
            callback.onSuccess(new ArrayList<>());
            return;
        }
        StringBuilder sb = new StringBuilder("in.(");
        boolean first = true;
        for (String uid : uids) {
            if (!first) sb.append(",");
            sb.append(uid);
            first = false;
        }
        sb.append(")");

        polishesApi.getPolishesByUids(SELECT_LIST_BASE, sb.toString())
                .enqueue(new Callback<List<Polish>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<Polish>> call,
                                           @NonNull Response<List<Polish>> response) {
                        if (!response.isSuccessful() || response.body() == null) {
                            callback.onError(RetrofitUtil.extractError("Polishes by UIDs", response));
                            return;
                        }
                        Log.d(TAG, "Fetched " + response.body().size()
                                + " polishes for " + uids.size() + " favorite UIDs");
                        callback.onSuccess(response.body());
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<Polish>> call, @NonNull Throwable t) {
                        callback.onError("Polishes network error: " + t.getMessage());
                    }
                });
    }

    //PostgREST JSONB containment: season_labels=cs.["spring"]
    public void getPolishesBySeason(String seasonTag, PolishesCallback callback) {
        String filter = "cs.[\"" + seasonTag + "\"]";

        polishesApi.getPolishesBySeason(SELECT_LIST_BASE, filter, "30")
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
