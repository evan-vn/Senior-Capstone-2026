package com.example.nailit.data.network;

import androidx.annotation.NonNull;

import com.example.nailit.BuildConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

//Retrofit client WITHOUT AuthInterceptor.
//Used for OpenID discovery and auth requests (sign-in, get-session).
//Includes CookieJar so Better Auth session cookies persist across requests.
public final class PlainClient {

    private static volatile Retrofit instance;

    private static final CookieJar COOKIE_JAR = new CookieJar() {
        private final Map<String, List<Cookie>> store = new HashMap<>();

        @Override
        public void saveFromResponse(@NonNull HttpUrl url, @NonNull List<Cookie> cookies) {
            store.put(url.host(), new ArrayList<>(cookies));
        }

        @NonNull
        @Override
        public List<Cookie> loadForRequest(@NonNull HttpUrl url) {
            List<Cookie> cookies = store.get(url.host());
            return cookies != null ? cookies : Collections.emptyList();
        }
    };

    private PlainClient() {}

    public static Retrofit getInstance(String baseUrl) {
        if (instance == null) {
            synchronized (PlainClient.class) {
                if (instance == null) {
                    OkHttpClient.Builder httpBuilder = new OkHttpClient.Builder()
                            .cookieJar(COOKIE_JAR);

                    if (BuildConfig.DEBUG) {
                        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
                        logging.setLevel(HttpLoggingInterceptor.Level.HEADERS);
                        httpBuilder.addInterceptor(logging);
                    }

                    instance = new Retrofit.Builder()
                            .baseUrl(baseUrl)
                            .client(httpBuilder.build())
                            .addConverterFactory(GsonConverterFactory.create())
                            .build();
                }
            }
        }
        return instance;
    }
}
