package com.example.nailit.data.repo;

import android.util.Log;

import com.example.nailit.data.auth.AuthApi;
import com.example.nailit.data.auth.AuthProvider;
import com.example.nailit.data.auth.PasswordGrantAuthProvider;
import com.example.nailit.data.config.NeonConfig;
import com.example.nailit.data.network.PlainClient;
import com.example.nailit.data.network.TokenStore;

import retrofit2.Retrofit;

public class AuthRepository {

    private static final String TAG = "AuthRepository";

    private final TokenStore tokenStore;
    private final AuthProvider provider;

    public AuthRepository(TokenStore tokenStore) {
        this.tokenStore = tokenStore;

        Retrofit plain = PlainClient.getInstance(NeonConfig.AUTH_BASE_URL);
        AuthApi authApi = plain.create(AuthApi.class);

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

    public boolean hasToken() {
        String token = tokenStore.getAccessToken();
        return token != null && !token.isEmpty();
    }

    public void logout() {
        tokenStore.clear();
    }
}
