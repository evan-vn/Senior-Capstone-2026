package com.example.nailit.data.api;

import com.example.nailit.data.model.NailDesign;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface NailDesignsApi {

    @GET("nail_designs")
    Call<List<NailDesign>> getDesignsByIds(
            @Query("select") String select,
            @Query("id") String idInFilter
    );
}

