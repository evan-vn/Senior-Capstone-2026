package com.example.nailit.data.repo;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.nailit.data.api.OpenAiApi;
import com.example.nailit.data.model.AiChatResult;
import com.example.nailit.data.model.AiMatchedOption;
import com.example.nailit.data.model.AiSuggestionPayload;
import com.example.nailit.data.model.ChatRequest;
import com.example.nailit.data.model.ChatResponse;
import com.example.nailit.data.network.OpenAiClient;
import com.example.nailit.data.network.RetrofitUtil;
import com.example.nailit.data.network.TokenStore;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AiRepository {

    private static final String TAG = "AiRepository";
    private static final String MODEL = "gpt-4o-mini";
    private static final String SYSTEM_PROMPT =
            "You are a nail style assistant for a beauty app called NailIt.\n" +
            "Your job is to suggest realistic nail polish shades based on the user's outfit, season, occasion, and style.\n" +
            "Return valid JSON only. Do not include markdown. Do not include code fences.\n" +
            "JSON schema:\n" +
            "{\n" +
            "  \"assistant_message\": \"short stylish response\",\n" +
            "  \"options\": [\n" +
            "    {\n" +
            "      \"label\": \"deep burgundy\",\n" +
            "      \"base_color\": \"burgundy\",\n" +
            "      \"finish\": \"cream\",\n" +
            "      \"depth\": \"deep\",\n" +
            "      \"style_keywords\": [\"rich\", \"elegant\"],\n" +
            "      \"color_keywords\": [\"burgundy\", \"wine\", \"deep red\", \"oxblood\"],\n" +
            "      \"season_tags\": [\"fall\"],\n" +
            "      \"occasion_tags\": [\"everyday\", \"formal\"],\n" +
            "      \"target_hsv_hint\": {\n" +
            "        \"hue_family\": \"red-purple\",\n" +
            "        \"saturation\": \"medium-high\",\n" +
            "        \"value\": \"low-medium\"\n" +
            "      }\n" +
            "    }\n" +
            "  ]\n" +
            "}\n" +
            "Rules:\n" +
            "- Return 2 to 4 options maximum\n" +
            "- Keep labels short and natural, avoid vague or poetic labels\n" +
            "- base_color must be one of: black, burgundy, red, gold, silver, nude, pink, purple\n" +
            "- finish must be one of: glossy, metallic, shimmer, glitter, cream, chrome, matte, sheer\n" +
            "- depth must be one of: deep, medium, light\n" +
            "- Separate color from finish (finish is not a color)\n" +
            "- target_hsv_hint uses coarse buckets only\n" +
            "- Keep arrays short and relevant\n" +
            "- JSON only";

    private final OpenAiApi openAiApi;
    private final PolishesRepository polishesRepository;
    private final Gson gson = new Gson();

    public interface AiCallback {
        void onSuccess(AiChatResult response);
        void onError(String error);
    }

    public AiRepository(Context context) {
        this.openAiApi = OpenAiClient.getInstance(context).create(OpenAiApi.class);
        this.polishesRepository = new PolishesRepository(new TokenStore(context));
    }

    public void askStyleSuggestions(String userPrompt, AiCallback callback) {
        List<ChatRequest.Message> messages = new ArrayList<>();
        messages.add(new ChatRequest.Message("system", SYSTEM_PROMPT));
        messages.add(new ChatRequest.Message("user", userPrompt));

        ChatRequest request = new ChatRequest(MODEL, messages);
        openAiApi.chatCompletions(request).enqueue(new Callback<ChatResponse>() {
            @Override
            public void onResponse(@NonNull Call<ChatResponse> call, @NonNull Response<ChatResponse> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    callback.onError(RetrofitUtil.extractError("OpenAI", response));
                    return;
                }

                ChatResponse body = response.body();
                if (body.getChoices() == null
                        || body.getChoices().isEmpty()
                        || body.getChoices().get(0).getMessage() == null
                        || body.getChoices().get(0).getMessage().getContent() == null
                        || body.getChoices().get(0).getMessage().getContent().trim().isEmpty()) {
                    callback.onError("OpenAI returned an empty response");
                    return;
                }

                String rawJson = body.getChoices().get(0).getMessage().getContent().trim();
                Log.d(TAG, "Raw assistant JSON: " + rawJson);
                AiSuggestionPayload payload = parsePayload(rawJson);
                if (payload == null) {
                    callback.onError("Could not parse AI suggestion JSON");
                    return;
                }
                Log.d(TAG, "Parsed options count: " + payload.getOptions().size());
                for (int i = 0; i < payload.getOptions().size(); i++) {
                    Log.d(TAG, "Option[" + i + "] label=" + payload.getOptions().get(i).getLabel()
                            + " base_color=" + payload.getOptions().get(i).getBaseColor()
                            + " finish=" + payload.getOptions().get(i).getFinish()
                            + " depth=" + payload.getOptions().get(i).getDepth()
                            + " hsv_hint.hue_family=" + payload.getOptions().get(i).getTargetHsvHint().getHueFamily()
                            + " hsv_hint.saturation=" + payload.getOptions().get(i).getTargetHsvHint().getSaturation()
                            + " hsv_hint.value=" + payload.getOptions().get(i).getTargetHsvHint().getValue()
                            + " color_keywords=" + payload.getOptions().get(i).getColorKeywords()
                            + " style_keywords=" + payload.getOptions().get(i).getStyleKeywords());
                }

                polishesRepository.getPolishesByAiSuggestions(payload, new PolishesRepository.AiPolishesCallback() {
                    @Override
                    public void onSuccess(List<AiMatchedOption> options) {
                        String message = payload.getAssistantMessage();
                        if (message.isEmpty()) {
                            message = "Here are some nail styles you might like.";
                        }
                        callback.onSuccess(new AiChatResult(message, options));
                    }

                    @Override
                    public void onError(String message) {
                        Log.w(TAG, "Polish matching failed, returning text-only response: " + message);
                        String fallbackMessage = payload.getAssistantMessage();
                        if (fallbackMessage.isEmpty()) {
                            fallbackMessage = "Here are some nail styles you might like.";
                        }
                        callback.onSuccess(new AiChatResult(
                                fallbackMessage,
                                buildEmptyMatchedOptions(payload)
                        ));
                    }
                });
            }

            @Override
            public void onFailure(@NonNull Call<ChatResponse> call, @NonNull Throwable t) {
                callback.onError("OpenAI request failed: " + t.getMessage());
            }
        });
    }

    //Temporary debug helper to isolate Neon candidate fetch from AI scoring.
    public void debugFetchAiCandidates() {
        polishesRepository.debugControlFetch(new PolishesRepository.PolishesCallback() {
            @Override
            public void onSuccess(List<com.example.nailit.data.model.Polish> polishes) {
                Log.d(TAG, "debugControlFetch success count=" + (polishes != null ? polishes.size() : 0));
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "debugControlFetch error=" + message);
            }
        });

        polishesRepository.getAllPolishesForAiMatching(new PolishesRepository.PolishesCallback() {
            @Override
            public void onSuccess(List<com.example.nailit.data.model.Polish> polishes) {
                Log.d(TAG, "getAllPolishesForAiMatching success count=" + (polishes != null ? polishes.size() : 0));
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "getAllPolishesForAiMatching error=" + message);
            }
        });
    }

    private AiSuggestionPayload parsePayload(String rawJson) {
        try {
            String cleaned = rawJson;
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.replace("```json", "").replace("```", "").trim();
            }
            return gson.fromJson(cleaned, AiSuggestionPayload.class);
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse AI JSON", e);
            return null;
        }
    }

    private List<AiMatchedOption> buildEmptyMatchedOptions(AiSuggestionPayload payload) {
        List<AiMatchedOption> empty = new ArrayList<>();
        if (payload == null) return empty;
        for (int i = 0; i < payload.getOptions().size(); i++) {
            String label = payload.getOptions().get(i).getLabel();
            empty.add(new AiMatchedOption(
                    label.isEmpty() ? "Suggested style" : label,
                    payload.getOptions().get(i).getColorKeywords(),
                    payload.getOptions().get(i).getSeasonTags(),
                    payload.getOptions().get(i).getOccasionTags(),
                    new ArrayList<>()
            ));
        }
        return empty;
    }
}

