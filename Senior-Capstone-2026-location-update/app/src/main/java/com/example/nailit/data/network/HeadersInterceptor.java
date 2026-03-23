package com.example.nailit.data.network;

import androidx.annotation.NonNull;

import com.example.nailit.BuildConfig;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public final class HeadersInterceptor implements Interceptor {

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request original = chain.request();

        Request.Builder builder = original.newBuilder()
                .header("apikey", BuildConfig.NEON_DATA_API_KEY)
                .header("Accept", "application/json");

        RequestBody body = original.body();
        if (body != null && body.contentType() != null
                && body.contentType().toString().contains("json")) {
            builder.header("Content-Type", "application/json");
        }

        return chain.proceed(builder.build());
    }
}
