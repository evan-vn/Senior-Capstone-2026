package com.example.nailit.data.api;

import java.util.List;
import java.util.Map;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface DesignFavoritesApi {

    @GET("user_favorite_designs")
    Call<List<com.example.nailit.data.model.DesignFavoriteRow>> getMyFavorites(
            @Query("select") String select);

    @POST("user_favorite_designs")
    Call<ResponseBody> addFavorite(@Body Map<String, Object> body);

    @DELETE("user_favorite_designs")
    Call<ResponseBody> removeFavorite(@Query("design_id") String designIdFilter);
}

