package com.example.nailit.data.network;

import retrofit2.Response;

public final class RetrofitUtil {

    private RetrofitUtil() {}

    public static String extractError(String label, Response<?> response) {
        String msg = label + " request failed: HTTP " + response.code();
        try {
            if (response.errorBody() != null) {
                msg += " — " + response.errorBody().string();
            }
        } catch (Exception ignored) {}
        return msg;
    }
}
