package com.example.nailit.data.api;

import com.example.nailit.data.model.UserIdRow;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface UsersApi {

    @GET("users")
    Call<List<UserIdRow>> getCurrentUser(
            @Query("select") String select,
            @Query("auth_user_id") String authUserIdFilter);
}
