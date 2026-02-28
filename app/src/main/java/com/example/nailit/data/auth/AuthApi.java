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

    //TODO: Better Auth magic-link plugin (enable in Neon console first)
    @POST("sign-in/magic-link")
    Call<ResponseBody> requestMagicLink(@Body Map<String, String> body);

    //TODO: Better Auth email-otp plugin (enable in Neon console first)
    @POST("sign-in/email-otp")
    Call<ResponseBody> requestEmailOtp(@Body Map<String, String> body);

    //TODO: Better Auth email-otp verification
    @POST("email-otp/verify-email")
    Call<ResponseBody> verifyEmailOtp(@Body Map<String, String> body);
}
