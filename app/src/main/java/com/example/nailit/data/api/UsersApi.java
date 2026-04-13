package com.example.nailit.data.api;

import com.example.nailit.data.model.UserIdRow;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface UsersApi {

    @GET("users")
    Call<List<UserIdRow>> getCurrentUser(
            @Query("select") String select,
            @Query("id") String idFilter);

    @GET("users")
    Call<List<UserIdRow>> getUserIdByEmail(
            @Query("select") String select,
            @Query("email") String emailFilter);

    //Upserts by a real unique key (id primary key by default).
    @Headers({
            "Prefer: return=representation,resolution=merge-duplicates"
    })
    @POST("users")
    Call<List<UserIdRow>> insertUser(
            @Query("on_conflict") String onConflictColumns,
            @Body Map<String, String> body);
}
