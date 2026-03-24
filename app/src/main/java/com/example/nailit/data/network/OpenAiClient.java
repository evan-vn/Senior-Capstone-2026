package com.example.nailit.data.network;

import android.content.Context;

import com.example.nailit.BuildConfig;
import com.example.nailit.R;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class OpenAiClient {

    private static volatile Retrofit instance;

    private OpenAiClient() {
    }

    public static Retrofit getInstance(Context context) {
        if (instance == null) {
            synchronized (OpenAiClient.class) {
                if (instance == null) {
                    String apiKey = context.getApplicationContext().getString(R.string.openai_api_key);

                    Interceptor authInterceptor = chain -> chain.proceed(
                            chain.request().newBuilder()
                                    .header("Authorization", "Bearer " + apiKey)
                                    .header("Content-Type", "application/json")
                                    .header("Accept", "application/json")
                                    .build()
                    );

                    OkHttpClient.Builder httpBuilder = new OkHttpClient.Builder()
                            .addInterceptor(authInterceptor);

                    if (BuildConfig.DEBUG) {
                        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
                        logging.setLevel(HttpLoggingInterceptor.Level.BASIC);
                        httpBuilder.addInterceptor(logging);
                    }

                    instance = new Retrofit.Builder()
                            .baseUrl("https://api.openai.com/")
                            .client(httpBuilder.build())
                            .addConverterFactory(GsonConverterFactory.create())
                            .build();
                }
            }
        }
        return instance;
    }
}

