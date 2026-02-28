package com.example.nailit.data.auth;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Url;

public interface OpenIdApi {

    @GET
    Call<OpenIdConfiguration> getConfiguration(@Url String url);
}
