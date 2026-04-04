package com.example.nailit.data.auth;

import android.util.Log;

import androidx.annotation.NonNull;

import com.example.nailit.data.api.UsersApi;
import com.example.nailit.data.model.UserIdRow;
import com.example.nailit.data.network.ApiClient;
import com.example.nailit.data.network.RetrofitUtil;
import com.example.nailit.data.network.TokenStore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import com.example.nailit.data.config.NeonConfig;
import okhttp3.OkHttpClient;

//Better Auth password flow: POST /sign-in/email then GET /get-session
public class PasswordGrantAuthProvider implements AuthProvider {

    private static final String TAG = "PasswordGrantAuth";

    private final AuthApi authApi;
    private final AuthApi authedAuthApi; //for change password
    private final TokenStore tokenStore;

    public PasswordGrantAuthProvider(AuthApi authApi, TokenStore tokenStore) {
        this.authApi = authApi;
        this.tokenStore = tokenStore;
        //Update password part
        Retrofit authed = new Retrofit.Builder()
                .baseUrl(NeonConfig.AUTH_BASE_URL)
                .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
                .client(new okhttp3.OkHttpClient.Builder()
                        .addInterceptor(chain -> {
                            String token = tokenStore.getAccessToken();
                            okhttp3.Request request = chain.request().newBuilder()
                                    .addHeader("Authorization", "Bearer " + token)
                                    .build();
                            return chain.proceed(request);
                        })
                        .build())
                .build();

        this.authedAuthApi = authed.create(AuthApi.class);

    }

    @Override
    public void signInStart(String email, String passwordOrNull, AuthCallback callback) {
        if (passwordOrNull == null || passwordOrNull.isEmpty()) {
            callback.onError("Password required for password grant flow");
            return;
        }

        Map<String, String> body = new HashMap<>();
        body.put("email", email);
        body.put("password", passwordOrNull);

        Log.d(TAG, "POST sign-in/email for " + email);

        authApi.signInEmail(body).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call,
                                   @NonNull Response<ResponseBody> response) {
                if (!response.isSuccessful()) {
                    callback.onError(RetrofitUtil.extractError("Sign-in", response));
                    return;
                }

                Log.d(TAG, "Sign-in OK, fetching JWT from session…");
                fetchJwt(email, callback);
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                callback.onError("Sign-in network error: " + t.getMessage());
            }
        });
    }

    //Password flow completes in one step; signInComplete is a no-op
    @Override
    public void signInComplete(String codeOrPayload, AuthCallback callback) {
        callback.onSuccess();
    }

    //GET /get-session — JWT comes from the "set-auth-jwt" response header
    private void fetchJwt(String email, AuthCallback callback) {
        authApi.getSession().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call,
                                   @NonNull Response<ResponseBody> response) {
                if (!response.isSuccessful()) {
                    callback.onError("Get-session failed: HTTP " + response.code());
                    return;
                }

                String jwt = response.headers().get("set-auth-jwt");
                if (jwt == null || jwt.isEmpty()) {
                    Log.w(TAG, "Headers: " + response.headers());
                    callback.onError("JWT not found in set-auth-jwt header");
                    return;
                }

                tokenStore.saveAccessToken(jwt);
                Log.d(TAG, "JWT saved (" + jwt.length() + " chars)");
                ApiClient.reset();
                resolveAndSaveAppUserId(email, callback);
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                callback.onError("Get-session network error: " + t.getMessage());
            }
        });
    }

    public void signUp(String email, String password, String name, AuthCallback callback) {
        if (email == null || email.trim().isEmpty()) {
            callback.onError("Email required");
            return;
        }
        if (password == null || password.isEmpty()) {
            callback.onError("Password required");
            return;
        }
        Map<String, String> body = new HashMap<>();
        body.put("email", email.trim());
        body.put("password", password);
        if (name != null && !name.trim().isEmpty()) {
            body.put("name", name.trim());
        }
        Log.d(TAG, "POST sign-up/email for " + email);
        authApi.signUpEmail(body).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call,
                                   @NonNull Response<ResponseBody> response) {
                if (!response.isSuccessful()) {
                    callback.onError(RetrofitUtil.extractError("Sign-up", response));
                    return;
                }
                Log.d(TAG, "Sign-up OK, fetching JWT from session…");
                fetchJwt(email,callback);
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                callback.onError("Sign-up network error: " + t.getMessage());
            }
        });
    }
    private void resolveAndSaveAppUserId(String email, AuthCallback callback) {
        UsersApi usersApi = ApiClient.getInstance(tokenStore).create(UsersApi.class);

        usersApi.getUserIdByEmail("eq." + email).enqueue(new Callback<List<UserIdRow>>() {
            @Override
            public void onResponse(@NonNull Call<List<UserIdRow>> call,
                                   @NonNull Response<List<UserIdRow>> response) {
                if (!response.isSuccessful()) {
                    callback.onError("Failed to resolve app user: HTTP " + response.code());
                    return;
                }

                List<UserIdRow> rows = response.body();
                if (rows == null || rows.isEmpty() || rows.get(0) == null || rows.get(0).getId() == null) {
                    callback.onError("User row not found in public.users");
                    return;
                }

                String appUserId = rows.get(0).getId();
                tokenStore.saveUserId(appUserId);

                Log.d(TAG, "Resolved public.users.id = " + appUserId);
                callback.onSuccess();
            }

            @Override
            public void onFailure(@NonNull Call<List<UserIdRow>> call, @NonNull Throwable t) {
                callback.onError("Resolve user network error: " + t.getMessage());
            }
        });
    }

    @Override
    public void changePassword(String currentPassword, String newPassword, AuthCallback callback) {
        Log.d("CP", "change password fc");
        if (currentPassword == null || currentPassword.isEmpty()) {
            callback.onError("Current password required");
            return;
        }
        if (newPassword == null || newPassword.isEmpty()) {
            callback.onError("New password required");
            return;
        }

        Map<String, String> body = new HashMap<>();
        body.put("currentpassword", currentPassword);
        body.put("newpassword", newPassword);

        authedAuthApi.changePassword(body).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                Log.d("CP", "HTTP: " + response.code());

                if (!response.isSuccessful()) {
                    try {
                        Log.e("CP", "Error: " + response.errorBody().string());
                    } catch (Exception ignored) {
                    }

                    callback.onError("Failed: " + response.code());
                    return;
                }

                callback.onSuccess();
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }


}
