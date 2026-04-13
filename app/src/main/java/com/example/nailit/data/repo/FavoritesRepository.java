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
import java.util.function.Consumer;

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
        String sub = tokenStore.getSubFromJwt();
        if (userId != null && !userId.isEmpty()) {
            Log.d(TAG, "addFavorite using cached app_user_id=" + userId + " jwt_sub=" + sub);
            doAddFavorite(userId, polishUid, callback);
            return;
        }
        resolveCurrentUser(resolvedId -> doAddFavorite(resolvedId, polishUid, callback), callback);
    }

    private void resolveCurrentUser(Consumer<String> onResolved, FavoriteCallback errorCallback) {
        resolveCurrentUserId(onResolved, errorCallback::onError);
    }

    private void resolveCurrentUserId(Consumer<String> onResolved, Consumer<String> onError) {
        String sub = tokenStore.getSubFromJwt();
        if (sub == null || sub.isEmpty()) {
            onError.accept("No auth session. Please log in again.");
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
                    onError.accept("User not found. Please log in again.");
                    return;
                }
                String resolvedId = response.body().get(0).getId();
                Log.d(TAG, "Resolved public.users.id=" + resolvedId
                        + " rows=" + response.body().size() + " jwt_sub=" + sub);
                if (resolvedId == null || resolvedId.isEmpty()) {
                    onError.accept("User not found. Please log in again.");
                    return;
                }
                tokenStore.setUserId(resolvedId);
                onResolved.accept(resolvedId);
            }

            @Override
            public void onFailure(@NonNull Call<List<UserIdRow>> call, @NonNull Throwable t) {
                onError.accept("Could not load user: " + t.getMessage());
            }
        });
    }

    public void getMyFavoritePolishes(FavoritesListCallback callback) {
        String sub = tokenStore.getSubFromJwt();
        Log.d(TAG, "getMyFavoritePolishes jwt_sub=" + sub);

        Consumer<String> fetch = userId -> {
            String userEq = "eq." + userId;
            Log.d(TAG, "GET user_favorite_polishes user_id=" + userEq + " select=polish_uid");
            favoritesApi.getMyFavorites("polish_uid", userEq).enqueue(new Callback<List<FavoriteRow>>() {
                @Override
                public void onResponse(@NonNull Call<List<FavoriteRow>> call,
                                       @NonNull Response<List<FavoriteRow>> response) {
                    if (!response.isSuccessful() || response.body() == null) {
                        callback.onError(RetrofitUtil.extractError("Favorites", response));
                        return;
                    }
                    List<FavoriteRow> body = response.body();
                    Log.d(TAG, "Favorites fetch returned count=" + body.size()
                            + " app_user_id=" + userId);
                    Set<String> uids = new HashSet<>();
                    for (FavoriteRow row : body) {
                        if (row != null && row.getPolishUid() != null) {
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
        };

        String cached = tokenStore.getUserId();
        if (cached != null && !cached.isEmpty()) {
            Log.d(TAG, "getMyFavoritePolishes using cached app_user_id=" + cached);
            fetch.accept(cached);
        } else {
            resolveCurrentUserId(fetch, callback::onError);
        }
    }

    private void doAddFavorite(String userId, String polishUid, FavoriteCallback callback) {
        Map<String, String> body = new HashMap<>();
        body.put("user_id", userId);
        body.put("polish_uid", polishUid);
        Log.d(TAG, "POST user_favorite_polishes body=" + body + " jwt_sub=" + tokenStore.getSubFromJwt());
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
        Consumer<String> go = userId -> {
            String userEq = "eq." + userId;
            String polishEq = "eq." + polishUid;
            Log.d(TAG, "DELETE user_favorite_polishes user_id=" + userEq + " polish_uid=" + polishEq
                    + " jwt_sub=" + tokenStore.getSubFromJwt());
            favoritesApi.removeFavorite(userEq, polishEq).enqueue(new Callback<ResponseBody>() {
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
        };

        String cached = tokenStore.getUserId();
        if (cached != null && !cached.isEmpty()) {
            go.accept(cached);
        } else {
            resolveCurrentUserId(go, callback::onError);
        }
    }
}
