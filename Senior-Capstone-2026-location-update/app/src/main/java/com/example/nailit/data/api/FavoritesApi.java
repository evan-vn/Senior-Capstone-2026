package com.example.nailit.data.api;

import com.example.nailit.data.model.FavoriteRow;

import java.util.List;
import java.util.Map;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface FavoritesApi {

    @GET("user_favorite_polishes")
    Call<List<FavoriteRow>> getMyFavorites(@Query("select") String select);

    @POST("user_favorite_polishes")
    Call<ResponseBody> addFavorite(@Body Map<String, String> body);

    @DELETE("user_favorite_polishes")
    Call<ResponseBody> removeFavorite(@Query("polish_uid") String polishUidEq);
}
