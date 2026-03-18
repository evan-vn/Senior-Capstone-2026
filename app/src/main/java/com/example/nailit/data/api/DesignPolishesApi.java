package com.example.nailit.data.api;

import com.example.nailit.data.model.DesignPolishRow;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface DesignPolishesApi {

    @GET("design_polishes")
    Call<List<DesignPolishRow>> getDesignIdsForPolish(
            @Query("select") String select,
            @Query("polish_uid") String polishUidEq
    );
}

