package com.example.nailit.data.auth;

import android.util.Log;

import androidx.annotation.NonNull;

import com.example.nailit.data.network.TokenStore;

import java.util.HashMap;
import java.util.Map;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

//TODO: Neon Auth passwordless flow. Better Auth supports magic-link and email-otp
//plugins. The exact endpoints depend on which plugin is enabled in the Neon console.
//Likely endpoints:
//  POST /sign-in/magic-link  {"email":"...", "callbackURL":"..."}
//  POST /sign-in/email-otp   {"email":"..."}
//  POST /email-otp/verify-email  {"email":"...", "otp":"..."}
//Adjust once Neon Auth's passwordless configuration is confirmed.
public class PasswordlessAuthProvider implements AuthProvider {

    private static final String TAG = "PasswordlessAuth";

    private final AuthApi authApi;
    private final TokenStore tokenStore;
    private String pendingEmail;

    public PasswordlessAuthProvider(AuthApi authApi, TokenStore tokenStore) {
        this.authApi = authApi;
        this.tokenStore = tokenStore;
    }

    @Override
    public void signInStart(String email, String passwordOrNull, AuthCallback callback) {
        pendingEmail = email;

        Map<String, String> body = new HashMap<>();
        body.put("email", email);

        //TODO: switch between magic-link and email-otp based on Neon console config
        Log.d(TAG, "Requesting passwordless sign-in for " + email);

        authApi.requestMagicLink(body).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call,
                                   @NonNull Response<ResponseBody> response) {
                if (!response.isSuccessful()) {
                    String msg = "Magic link request failed: HTTP " + response.code();
                    try {
                        if (response.errorBody() != null) {
                            msg += " — " + response.errorBody().string();
                        }
                    } catch (Exception ignored) {}
                    //TODO: if magic-link is not enabled, fallback to email-otp
                    callback.onError(msg);
                    return;
                }

                Log.d(TAG, "Sign-in link/code sent to " + email);
                callback.onSuccess();
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                callback.onError("Passwordless request error: " + t.getMessage());
            }
        });
    }

    @Override
    public void signInComplete(String codeOrPayload, AuthCallback callback) {
        if (pendingEmail == null) {
            callback.onError("No pending sign-in. Call signInStart first.");
            return;
        }

        Map<String, String> body = new HashMap<>();
        body.put("email", pendingEmail);
        body.put("otp", codeOrPayload);

        //TODO: if using magic-link, the verification happens via redirect callback,
        //not a manual code entry. Adjust based on Neon Auth flow.
        Log.d(TAG, "Verifying OTP/code for " + pendingEmail);

        authApi.verifyEmailOtp(body).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call,
                                   @NonNull Response<ResponseBody> response) {
                if (!response.isSuccessful()) {
                    String msg = "Verify failed: HTTP " + response.code();
                    try {
                        if (response.errorBody() != null) {
                            msg += " — " + response.errorBody().string();
                        }
                    } catch (Exception ignored) {}
                    callback.onError(msg);
                    return;
                }

                Log.d(TAG, "OTP verified, fetching JWT…");
                fetchJwt(callback);
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                callback.onError("Verify network error: " + t.getMessage());
            }
        });
    }

    private void fetchJwt(AuthCallback callback) {
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
                pendingEmail = null;
                callback.onSuccess();
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                callback.onError("Get-session network error: " + t.getMessage());
            }
        });
    }
}
