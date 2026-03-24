package com.example.nailit.data.model;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

public class AiSuggestionPayload {

    @SerializedName("assistant_message")
    private String assistantMessage;

    @SerializedName("options")
    private List<AiSuggestionOption> options;

    @SerializedName("color_keywords")
    private List<String> legacyColorKeywords;

    @SerializedName("season_tags")
    private List<String> legacySeasonTags;

    @SerializedName("occasion_tags")
    private List<String> legacyOccasionTags;

    public String getAssistantMessage() {
        return assistantMessage != null ? assistantMessage.trim() : "";
    }

    public List<AiSuggestionOption> getOptions() {
        if (options != null && !options.isEmpty()) {
            return options;
        }
        List<AiSuggestionOption> fallback = new ArrayList<>();
        if ((legacyColorKeywords != null && !legacyColorKeywords.isEmpty())
                || (legacySeasonTags != null && !legacySeasonTags.isEmpty())
                || (legacyOccasionTags != null && !legacyOccasionTags.isEmpty())) {
            fallback.add(new AiSuggestionOption(
                    "Suggested style",
                    legacyColorKeywords != null ? legacyColorKeywords : new ArrayList<>(),
                    legacySeasonTags != null ? legacySeasonTags : new ArrayList<>(),
                    legacyOccasionTags != null ? legacyOccasionTags : new ArrayList<>()
            ));
        }
        return fallback;
    }
}

