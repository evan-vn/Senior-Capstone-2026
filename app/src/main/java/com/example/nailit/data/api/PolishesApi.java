package com.example.nailit.data.api;

import com.example.nailit.data.model.Polish;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface PolishesApi {

    @GET("polishes")
    Call<List<Polish>> getPolishes(@Query("select") String select);

    @GET("polishes")
    Call<List<Polish>> getPolishesBySeason(
            @Query("select") String select,
            @Query("season_labels") String seasonFilter,
            @Query("limit") String limit
    );

    @GET("trending_polishes_last_7_days")
    Call<List<Polish>> getTrendingPolishes(
            @Query("select") String select,
            @Query("order") String order,
            @Query("limit") String limit
    );
}
