package com.example.nailit.data.api;

import com.example.nailit.data.model.SavedSalonRequest;
import com.example.nailit.data.model.SavedSalonResponse;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface SavedSalonApi {

    @Headers({
            "Prefer: return=representation,resolution=ignore-duplicates"
    })
    @POST("saved_salons")
    Call<List<SavedSalonResponse>> saveSalon(@Body SavedSalonRequest request);

    @GET("saved_salons?select=*")
    Call<List<SavedSalonResponse>> getSavedSalons(
            @Query("user_id") String userIdFilter,
            @Query("order") String order
    );

    @DELETE("saved_salons")
    Call<Void> removeSavedSalon(
            @Query("user_id") String userIdFilter,
            @Query("place_id") String placeIdFilter
    );
}