package com.example.nailit.data.repo;

import android.util.Log;

import androidx.annotation.NonNull;

import com.example.nailit.data.api.FavoritesApi;
import com.example.nailit.data.api.UsersApi;
import com.example.nailit.data.model.FavoriteRow;
import com.example.nailit.data.model.UserIdRow;
import com.example.nailit.data.network.ApiClient;
import com.example.nailit.data.network.RetrofitUtil;
import com.example.nailit.data.network.TokenStore;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class FavoritesRepository {

    private static final String TAG = "FavoritesRepo";
    private static final String SELECT_ID = "id";

    private final TokenStore tokenStore;
    private final FavoritesApi favoritesApi;
    private final UsersApi usersApi;

    public interface FavoriteCallback {
        void onSuccess();
        void onError(String message);
    }

    public interface FavoritesListCallback {
        void onSuccess(Set<String> polishUids);
        void onError(String message);
    }

    public FavoritesRepository(TokenStore tokenStore) {
        this.tokenStore = tokenStore;
        retrofit2.Retrofit retrofit = ApiClient.getInstance(tokenStore);
        this.favoritesApi = retrofit.create(FavoritesApi.class);
        this.usersApi = retrofit.create(UsersApi.class);
    }

    public void addFavorite(String polishUid, FavoriteCallback callback) {
        String userId = tokenStore.getUserId();
        if (userId != null) {
            Log.d(TAG, "Using cached user_id=" + userId);
            doAddFavorite(userId, polishUid, callback);
            return;
        }
        resolveCurrentUser(resolvedId -> doAddFavorite(resolvedId, polishUid, callback), callback);
    }

    private void resolveCurrentUser(java.util.function.Consumer<String> onResolved,
                                    FavoriteCallback errorCallback) {
        String sub = tokenStore.getSubFromJwt();
        if (sub == null || sub.isEmpty()) {
            errorCallback.onError("No auth session. Please log in again.");
            return;
        }
        String authFilter = "eq." + sub;
        Log.d(TAG, "Resolving public.users.id where auth_user_id=" + authFilter);

        usersApi.getCurrentUser(SELECT_ID, authFilter).enqueue(new Callback<List<UserIdRow>>() {
            @Override
            public void onResponse(@NonNull Call<List<UserIdRow>> call,
                                   @NonNull Response<List<UserIdRow>> response) {
                if (!response.isSuccessful() || response.body() == null || response.body().isEmpty()) {
                    Log.e(TAG, "User lookup failed: HTTP " + (response != null ? response.code() : "null"));
                    errorCallback.onError("User not found. Please log in again.");
                    return;
                }
                String resolvedId = response.body().get(0).getId();
                Log.d(TAG, "Resolved public.users.id=" + resolvedId
                        + " (from " + response.body().size() + " rows)");
                if (resolvedId == null || resolvedId.isEmpty()) {
                    errorCallback.onError("User not found. Please log in again.");
                    return;
                }
                tokenStore.setUserId(resolvedId);
                onResolved.accept(resolvedId);
            }

            @Override
            public void onFailure(@NonNull Call<List<UserIdRow>> call, @NonNull Throwable t) {
                errorCallback.onError("Could not load user: " + t.getMessage());
            }
        });
    }

    public void getMyFavoritePolishes(FavoritesListCallback callback) {
        favoritesApi.getMyFavorites("polish_uid").enqueue(new Callback<List<FavoriteRow>>() {
            @Override
            public void onResponse(@NonNull Call<List<FavoriteRow>> call,
                                   @NonNull Response<List<FavoriteRow>> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    callback.onError(RetrofitUtil.extractError("Favorites", response));
                    return;
                }
                Set<String> uids = new HashSet<>();
                for (FavoriteRow row : response.body()) {
                    if (row.getPolishUid() != null) {
                        uids.add(row.getPolishUid());
                    }
                }
                callback.onSuccess(uids);
            }

            @Override
            public void onFailure(@NonNull Call<List<FavoriteRow>> call, @NonNull Throwable t) {
                callback.onError("Could not load favorites: " + t.getMessage());
            }
        });
    }

    private void doAddFavorite(String userId, String polishUid, FavoriteCallback callback) {
        Map<String, String> body = new HashMap<>();
        body.put("user_id", userId);
        body.put("polish_uid", polishUid);
        Log.d(TAG, "POST user_favorite_polishes payload: " + body);
        favoritesApi.addFavorite(body).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call,
                                   @NonNull Response<ResponseBody> response) {
                if (response.isSuccessful() || response.code() == 409) {
                    //409 = unique violation, row already exists — treat as success
                    callback.onSuccess();
                } else {
                    callback.onError(RetrofitUtil.extractError("Add favorite", response));
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                callback.onError("Add favorite failed: " + t.getMessage());
            }
        });
    }

    public void removeFavorite(String polishUid, FavoriteCallback callback) {
        //PostgREST filter: polish_uid=eq.xxx
        String filterValue = "eq." + polishUid;
        favoritesApi.removeFavorite(filterValue).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call,
                                   @NonNull Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess();
                } else {
                    callback.onError(RetrofitUtil.extractError("Remove favorite", response));
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                callback.onError("Remove favorite failed: " + t.getMessage());
            }
        });
    }
}
