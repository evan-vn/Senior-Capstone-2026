package com.example.nailit.data.api;

import com.example.nailit.data.model.ChatRequest;
import com.example.nailit.data.model.ChatResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface OpenAiApi {

    @POST("v1/chat/completions")
    Call<ChatResponse> chatCompletions(@Body ChatRequest request);
}

