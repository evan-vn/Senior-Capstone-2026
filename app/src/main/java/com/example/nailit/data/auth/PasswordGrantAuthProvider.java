package com.example.nailit.data.auth;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.nailit.data.api.UsersApi;
import com.example.nailit.data.config.NeonConfig;
import com.example.nailit.data.model.UserIdRow;
import com.example.nailit.data.network.ApiClient;
import com.example.nailit.data.network.PlainClient;
import com.example.nailit.data.network.RetrofitUtil;
import com.example.nailit.data.network.TokenStore;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;

public class PasswordGrantAuthProvider implements AuthProvider {

    private static final String TAG = "PasswordGrantAuth";

    private final AuthApi authApi;
    private final AuthApi authedAuthApi;
    private final TokenStore tokenStore;

    public PasswordGrantAuthProvider(AuthApi authApi, TokenStore tokenStore) {
        this.authApi = authApi;
        this.tokenStore = tokenStore;

        Retrofit authed = new Retrofit.Builder()
                .baseUrl(NeonConfig.AUTH_BASE_URL)
                .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
                .client(PlainClient.createNeonAuthHttpClientWithBearer(tokenStore))
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
                Log.d(TAG, "Sign-in OK, fetching JWT from session");
                fetchJwt(email, null, callback);
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                callback.onError("Sign-in network error: " + t.getMessage());
            }
        });
    }

    @Override
    public void signInComplete(String codeOrPayload, AuthCallback callback) {
        callback.onSuccess();
    }

    private void fetchJwt(String email, @Nullable String signupName, AuthCallback callback) {
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
                Log.d(TAG, "JWT saved (" + jwt.length() + " chars), email=" + email);
                ApiClient.reset();
                ensurePublicAppUserRow(email, signupName, callback);
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                callback.onError("Get-session network error: " + t.getMessage());
            }
        });
    }

    @Override
    public void signUp(String email, String password, String name, AuthCallback callback) {
        if (email == null || email.trim().isEmpty()) {
            callback.onError("Email required");
            return;
        }
        if (password == null || password.isEmpty()) {
            callback.onError("Password required");
            return;
        }

        String cleanEmail = email.trim();
        String cleanName = name != null ? name.trim() : "";

        Map<String, String> body = new HashMap<>();
        body.put("email", cleanEmail);
        body.put("password", password);
        if (!cleanName.isEmpty()) {
            body.put("name", cleanName);
        }

        Log.d(TAG, "POST sign-up/email for " + cleanEmail);
        authApi.signUpEmail(body).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call,
                                   @NonNull Response<ResponseBody> response) {
                if (!response.isSuccessful()) {
                    callback.onError(RetrofitUtil.extractError("Sign-up", response));
                    return;
                }
                Log.d(TAG, "Sign-up OK HTTP " + response.code());
                fetchJwt(cleanEmail, cleanName.isEmpty() ? null : cleanName, callback);
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                callback.onError("Sign-up network error: " + t.getMessage());
            }
        });
    }

    private void ensurePublicAppUserRow(String email, @Nullable String signupName, AuthCallback callback) {
        if (email == null || email.trim().isEmpty()) {
            callback.onError("Email required for account setup");
            return;
        }
        String sub = tokenStore.getSubFromJwt();
        Log.d(TAG, "ensurePublicAppUserRow auth_user_id=" + sub + " email=" + email.trim());
        if (sub == null || sub.isEmpty()) {
            callback.onError("No auth subject in token. Please try again.");
            return;
        }

        UsersApi usersApi = ApiClient.getInstance(tokenStore).create(UsersApi.class);
        String idFilter = "eq." + sub;

        usersApi.getCurrentUser("id,email", idFilter).enqueue(new Callback<List<UserIdRow>>() {
            @Override
            public void onResponse(@NonNull Call<List<UserIdRow>> call,
                                   @NonNull Response<List<UserIdRow>> response) {
                if (!response.isSuccessful()) {
                    logResponseError("GET public.users by id", response);
                    lookupPublicUserByEmailOrInsert(usersApi, sub, email.trim(), signupName, callback);
                    return;
                }
                List<UserIdRow> rows = response.body();
                Log.d(TAG, "lookup by id result count=" + (rows != null ? rows.size() : 0)
                        + " auth_user_id=" + sub);
                if (rows != null && !rows.isEmpty() && rows.get(0) != null && rows.get(0).getId() != null) {
                    finishWithUserRow(rows.get(0), "existing", callback);
                    return;
                }
                lookupPublicUserByEmailOrInsert(usersApi, sub, email.trim(), signupName, callback);
            }

            @Override
            public void onFailure(@NonNull Call<List<UserIdRow>> call, @NonNull Throwable t) {
                Log.e(TAG, "lookup by id failed", t);
                lookupPublicUserByEmailOrInsert(usersApi, sub, email.trim(), signupName, callback);
            }
        });
    }

    private void lookupPublicUserByEmailOrInsert(
            UsersApi usersApi,
            String authUserId,
            String email,
            @Nullable String signupName,
            AuthCallback callback) {
        usersApi.getUserIdByEmail("id,email", "eq." + email).enqueue(new Callback<List<UserIdRow>>() {
            @Override
            public void onResponse(@NonNull Call<List<UserIdRow>> call,
                                   @NonNull Response<List<UserIdRow>> response) {
                if (!response.isSuccessful()) {
                    logResponseError("GET public.users by email", response);
                    upsertPublicUserRow(usersApi, authUserId, email, signupName, callback);
                    return;
                }
                List<UserIdRow> rows = response.body();
                Log.d(TAG, "lookup by email result count=" + (rows != null ? rows.size() : 0)
                        + " email=" + email);
                if (rows != null && !rows.isEmpty() && rows.get(0) != null && rows.get(0).getId() != null) {
                    UserIdRow existing = rows.get(0);
                    if (!authUserId.equals(existing.getId())) {
                        Log.w(TAG, "legacy mismatch detected auth_user_id=" + authUserId
                                + " public_user_id=" + existing.getId()
                                + " email=" + email);
                    }
                    Log.d(TAG, "skip insert because email already exists: " + email);
                    finishWithUserRow(existing, "existing_by_email", callback);
                    return;
                }
                upsertPublicUserRow(usersApi, authUserId, email, signupName, callback);
            }

            @Override
            public void onFailure(@NonNull Call<List<UserIdRow>> call, @NonNull Throwable t) {
                Log.e(TAG, "lookup by email failed", t);
                upsertPublicUserRow(usersApi, authUserId, email, signupName, callback);
            }
        });
    }

    private void upsertPublicUserRow(
            UsersApi usersApi,
            String authUserId,
            String email,
            @Nullable String signupName,
            AuthCallback callback) {

        Map<String, String> body = new HashMap<>();
        body.put("id", authUserId);
        body.put("email", email);
        body.put("username", buildSafeUsername(signupName, email, authUserId));
        body.put("display_name", buildDisplayName(signupName, email));

        Log.d(TAG, "POST public.users payload=" + body + " on_conflict=id");
        usersApi.insertUser("id", body).enqueue(new Callback<List<UserIdRow>>() {
            @Override
            public void onResponse(@NonNull Call<List<UserIdRow>> call,
                                   @NonNull Response<List<UserIdRow>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    UserIdRow row = response.body().get(0);
                    if (row != null && row.getId() != null) {
                        finishWithUserRow(row, "upsert", callback);
                        return;
                    }
                }
                if (!response.isSuccessful()) {
                    logResponseError("POST public.users", response);
                } else {
                    Log.w(TAG, "POST public.users empty body HTTP " + response.code());
                }
                refetchUserAfterUpsert(usersApi, authUserId, email, response, callback);
            }

            @Override
            public void onFailure(@NonNull Call<List<UserIdRow>> call, @NonNull Throwable t) {
                Log.e(TAG, "POST public.users failed", t);
                refetchUserAfterUpsert(usersApi, authUserId, email, null, callback);
            }
        });
    }

    private void refetchUserAfterUpsert(
            UsersApi usersApi,
            String authUserId,
            String email,
            @Nullable Response<List<UserIdRow>> upsertResponse,
            AuthCallback callback) {
        String idFilter = "eq." + authUserId;
        usersApi.getCurrentUser("id,email", idFilter).enqueue(new Callback<List<UserIdRow>>() {
            @Override
            public void onResponse(@NonNull Call<List<UserIdRow>> call,
                                   @NonNull Response<List<UserIdRow>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    UserIdRow row = response.body().get(0);
                    if (row != null && row.getId() != null) {
                        finishWithUserRow(row, "refetch_after_upsert", callback);
                        return;
                    }
                }
                if (!response.isSuccessful()) {
                    logResponseError("GET public.users by id after upsert", response);
                }
                continueWithoutSyncedPublicUser(email, upsertResponse, callback);
            }

            @Override
            public void onFailure(@NonNull Call<List<UserIdRow>> call, @NonNull Throwable t) {
                Log.e(TAG, "GET public.users by id after upsert network error", t);
                continueWithoutSyncedPublicUser(email, upsertResponse, callback);
            }
        });
    }

    private String buildSafeUsername(@Nullable String signupName, String email, String sub) {
        String base = signupName != null && !signupName.trim().isEmpty()
                ? signupName.trim()
                : getEmailLocalPart(email);
        String normalized = base.toLowerCase(Locale.US)
                .replaceAll("[^a-z0-9_]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");
        if (normalized.isEmpty()) {
            String suffix = sub.length() >= 8 ? sub.substring(0, 8) : "user";
            normalized = "user_" + suffix;
        }
        return normalized.length() > 32 ? normalized.substring(0, 32) : normalized;
    }

    private String buildDisplayName(@Nullable String signupName, String email) {
        if (signupName != null && !signupName.trim().isEmpty()) {
            return signupName.trim();
        }
        return getEmailLocalPart(email);
    }

    private String getEmailLocalPart(String email) {
        if (email == null || email.trim().isEmpty()) return "user";
        String trimmed = email.trim();
        int atIndex = trimmed.indexOf('@');
        if (atIndex <= 0) return trimmed;
        return trimmed.substring(0, atIndex);
    }

    private void continueWithoutSyncedPublicUser(
            String email,
            @Nullable Response<List<UserIdRow>> upsertResponse,
            AuthCallback callback) {
        if (upsertResponse != null && !upsertResponse.isSuccessful()) {
            logResponseError("POST public.users (final context)", upsertResponse);
        }
        String cachedUserId = tokenStore.getUserId();
        Log.w(TAG, "public.users sync best-effort failed; continuing auth login."
                + " email=" + email + " cached_public_user_id=" + cachedUserId);
        callback.onSuccess();
    }

    private void logResponseError(String label, Response<?> response) {
        if (response == null) return;
        Log.e(TAG, RetrofitUtil.extractError(label, response));
    }

    private void finishWithUserRow(UserIdRow row, String source, AuthCallback callback) {
        String appUserId = row.getId();
        tokenStore.saveUserId(appUserId);
        Log.d(TAG, "public.users ok (" + source + ") id=" + appUserId
                + " row_email=" + row.getEmail()
                + " jwt_sub=" + tokenStore.getSubFromJwt());
        callback.onSuccess();
    }

    @Override
    public void changePassword(String currentPassword, String newPassword, AuthCallback callback) {
        String token = tokenStore.getAccessToken();
        if (token == null || token.isEmpty()) {
            callback.onError("Not signed in. Please log in again.");
            return;
        }
        if (currentPassword == null || currentPassword.isEmpty()) {
            callback.onError("Current password required");
            return;
        }
        if (newPassword == null || newPassword.isEmpty()) {
            callback.onError("New password required");
            return;
        }

        Map<String, String> body = new HashMap<>();
        body.put("currentPassword", currentPassword);
        body.put("newPassword", newPassword);

        authedAuthApi.changePassword(body).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (!response.isSuccessful()) {
                    callback.onError(RetrofitUtil.extractError("Change password", response));
                    return;
                }
                callback.onSuccess();
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                String msg = t.getMessage();
                callback.onError(msg != null ? msg : "Network error");
            }
        });
    }
}
