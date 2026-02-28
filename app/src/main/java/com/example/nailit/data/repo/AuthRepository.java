package com.example.nailit.data.repo;

import android.util.Log;

import androidx.annotation.NonNull;

import com.example.nailit.BuildConfig;
import com.example.nailit.data.auth.AuthApi;
import com.example.nailit.data.auth.AuthProvider;
import com.example.nailit.data.auth.OpenIdApi;
import com.example.nailit.data.auth.OpenIdConfiguration;
import com.example.nailit.data.auth.PasswordGrantAuthProvider;
import com.example.nailit.data.auth.PasswordlessAuthProvider;
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
    private final OpenIdApi openIdApi;

    private OpenIdConfiguration discoveredConfig;

    public interface AuthCallback {
        void onSuccess();
        void onError(String message);
    }

    public AuthRepository(TokenStore tokenStore) {
        this.tokenStore = tokenStore;

        Retrofit plain = PlainClient.getInstance(NeonConfig.AUTH_BASE_URL);
        AuthApi authApi = plain.create(AuthApi.class);
        this.openIdApi = plain.create(OpenIdApi.class);

        //Select provider based on BuildConfig.NEON_AUTH_MODE
        if ("password".equals(BuildConfig.NEON_AUTH_MODE)) {
            Log.d(TAG, "Auth mode: password grant");
            this.provider = new PasswordGrantAuthProvider(authApi, tokenStore);
        } else {
            Log.d(TAG, "Auth mode: passwordless");
            this.provider = new PasswordlessAuthProvider(authApi, tokenStore);
        }
    }

    //Attempt OpenID discovery; fall back to constructed URLs if unavailable
    public void fetchOpenIdConfig(OpenIdCallback callback) {
        String discoveryUrl = NeonConfig.AUTH_BASE_URL + ".well-known/openid-configuration";
        Log.d(TAG, "Attempting OpenID discovery: " + discoveryUrl);

        openIdApi.getConfiguration(discoveryUrl).enqueue(new Callback<OpenIdConfiguration>() {
            @Override
            public void onResponse(@NonNull Call<OpenIdConfiguration> call,
                                   @NonNull Response<OpenIdConfiguration> response) {
                if (response.isSuccessful() && response.body() != null) {
                    discoveredConfig = response.body();
                    Log.d(TAG, "OpenID discovery OK — token_endpoint: "
                            + discoveredConfig.getTokenEndpoint());
                    callback.onResult(discoveredConfig);
                } else {
                    //Neon Auth (Better Auth) may not expose standard OIDC discovery
                    Log.w(TAG, "OpenID discovery returned " + response.code()
                            + ", using constructed endpoints");
                    callback.onResult(null);
                }
            }

            @Override
            public void onFailure(@NonNull Call<OpenIdConfiguration> call, @NonNull Throwable t) {
                Log.w(TAG, "OpenID discovery failed: " + t.getMessage()
                        + ", using constructed endpoints");
                callback.onResult(null);
            }
        });
    }

    //Step 1: start sign-in
    public void signInStart(String email, String passwordOrNull, AuthCallback callback) {
        provider.signInStart(email, passwordOrNull, new AuthProvider.AuthCallback() {
            @Override
            public void onSuccess() {
                callback.onSuccess();
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    //Step 2: verify code/link (passwordless only; no-op for password flow)
    public void signInComplete(String codeOrPayload, AuthCallback callback) {
        provider.signInComplete(codeOrPayload, new AuthProvider.AuthCallback() {
            @Override
            public void onSuccess() {
                callback.onSuccess();
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    //Convenience: check if we already have a token
    public boolean hasToken() {
        String token = tokenStore.getAccessToken();
        return token != null && !token.isEmpty();
    }

    public void logout() {
        tokenStore.clear();
    }

    public interface OpenIdCallback {
        void onResult(OpenIdConfiguration config);
    }
}
