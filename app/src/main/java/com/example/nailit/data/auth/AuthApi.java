package com.example.nailit.data.auth;

import java.util.Map;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;

public interface AuthApi {

    //Better Auth password sign-in
    @POST("sign-in/email")
    Call<ResponseBody> signInEmail(@Body Map<String, String> body);

    //Better Auth session — JWT in "set-auth-jwt" response header
    @GET("get-session")
    Call<ResponseBody> getSession();
}
