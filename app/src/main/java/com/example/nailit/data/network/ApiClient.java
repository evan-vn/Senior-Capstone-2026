package com.example.nailit.data.network;

import com.example.nailit.BuildConfig;
import com.example.nailit.data.config.NeonConfig;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class ApiClient {

    private static volatile Retrofit instance;

    private ApiClient() {}

    public static Retrofit getInstance(TokenStore tokenStore) {
        if (instance == null) {
            synchronized (ApiClient.class) {
                if (instance == null) {
                    OkHttpClient.Builder httpBuilder = new OkHttpClient.Builder()
                            .addInterceptor(new HeadersInterceptor())
                            .addInterceptor(new AuthInterceptor(tokenStore));

                    if (BuildConfig.DEBUG) {
                        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
                        logging.setLevel(HttpLoggingInterceptor.Level.BODY);
                        httpBuilder.addInterceptor(logging);
                    }

                    instance = new Retrofit.Builder()
                            .baseUrl(NeonConfig.REST_BASE_URL)
                            .client(httpBuilder.build())
                            .addConverterFactory(GsonConverterFactory.create())
                            .build();
                }
            }
        }
        return instance;
    }
}
