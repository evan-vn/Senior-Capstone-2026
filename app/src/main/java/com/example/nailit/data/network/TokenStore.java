package com.example.nailit.data.network;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

public final class TokenStore {

    private static final String TAG = "TokenStore";
    private static final String PREF_FILE = "neon_secure_prefs";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_USER_ID = "user_id";

    private final SharedPreferences prefs;

    public TokenStore(Context context) {
        try {
            String masterKey = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
            prefs = EncryptedSharedPreferences.create(
                    PREF_FILE,
                    masterKey,
                    context.getApplicationContext(),
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException("Failed to create EncryptedSharedPreferences", e);
        }
    }

    public void saveAccessToken(String token) {
        //Clear stale user_id when a new JWT is saved (different user may be logging in)
        prefs.edit()
                .putString(KEY_ACCESS_TOKEN, token)
                .remove(KEY_USER_ID)
                .apply();
        Log.d(TAG, "New JWT saved, cached user_id cleared");
    }

    public String getAccessToken() {
        return prefs.getString(KEY_ACCESS_TOKEN, null);
    }

    public void setUserId(String userId) {
        if (userId == null) {
            prefs.edit().remove(KEY_USER_ID).apply();
        } else {
            prefs.edit().putString(KEY_USER_ID, userId).apply();
        }
    }

    public String getUserId() {
        return prefs.getString(KEY_USER_ID, null);
    }

    public String getSubFromJwt() {
        String jwt = getAccessToken();
        if (jwt == null || jwt.isEmpty()) return null;
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length < 2) return null;
            byte[] decoded = Base64.decode(parts[1], Base64.URL_SAFE | Base64.NO_WRAP);
            JSONObject payload = new JSONObject(new String(decoded, StandardCharsets.UTF_8));
            String sub = payload.optString("sub", null);
            Log.d(TAG, "JWT sub (auth_user_id) = " + sub);
            return sub;
        } catch (Exception e) {
            Log.e(TAG, "Failed to decode JWT sub", e);
            return null;
        }
    }

    public void clear() {
        prefs.edit().clear().apply();
    }
}
