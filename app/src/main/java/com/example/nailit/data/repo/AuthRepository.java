package com.example.nailit.data.repo;

import android.util.Log;

import androidx.annotation.NonNull;

import com.example.nailit.data.auth.AuthApi;
import com.example.nailit.data.auth.AuthProvider;
import com.example.nailit.data.auth.PasswordGrantAuthProvider;
import com.example.nailit.data.auth.SessionResponse;
import com.example.nailit.data.config.NeonConfig;
import com.example.nailit.data.network.PlainClient;
import com.example.nailit.data.network.TokenStore;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;

public class AuthRepository {

    private static final String TAG = "AuthRepository";

    private final TokenStore tokenStore;
    private final AuthProvider provider;
    private final AuthApi authApi;

    public AuthRepository(TokenStore tokenStore) {
        this.tokenStore = tokenStore;

        Retrofit plain = PlainClient.getInstance(NeonConfig.AUTH_BASE_URL);
        this.authApi = plain.create(AuthApi.class);

        Log.d(TAG, "Auth mode: password grant");
        this.provider = new PasswordGrantAuthProvider(authApi, tokenStore);
    }

    public void signInStart(String email, String passwordOrNull,
                            AuthProvider.AuthCallback callback) {
        provider.signInStart(email, passwordOrNull, callback);
    }

    public void signInComplete(String codeOrPayload, AuthProvider.AuthCallback callback) {
        provider.signInComplete(codeOrPayload, callback);
    }

    public void signUp(String email, String password, String name,
                      AuthProvider.AuthCallback callback) {
        provider.signUp(email, password, name, callback);
    }

    public boolean hasToken() {
        String token = tokenStore.getAccessToken();
        return token != null && !token.isEmpty();
    }

    public void logout() {
        tokenStore.clear();
    }

    public interface ProfileCallback {
        void onSuccess(String name, String email);
        void onError(String message);
    }

    public void fetchCurrentUser(ProfileCallback callback) {
        authApi.getSessionUser().enqueue(new Callback<SessionResponse>() {
            @Override
            public void onResponse(@NonNull Call<SessionResponse> call,
                                   @NonNull Response<SessionResponse> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    callback.onError("Could not load profile");
                    return;
                }
                SessionResponse.User user = response.body().getUser();
                if (user == null) {
                    callback.onError("No user in session");
                    return;
                }
                String name = user.getName() != null ? user.getName() : "";
                String email = user.getEmail() != null ? user.getEmail() : "";
                callback.onSuccess(name, email);
            }

            @Override
            public void onFailure(@NonNull Call<SessionResponse> call, @NonNull Throwable t) {
                callback.onError(t.getMessage() != null ? t.getMessage() : "Network error");
            }
        });
    }
}
