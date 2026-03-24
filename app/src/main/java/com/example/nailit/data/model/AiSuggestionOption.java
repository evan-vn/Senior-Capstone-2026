package com.example.nailit.data.model;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

public class AiSuggestionOption {

    @SerializedName("label")
    private String label;

    @SerializedName("base_color")
    private String baseColor;

    @SerializedName("finish")
    private String finish;

    @SerializedName("depth")
    private String depth;

    @SerializedName("style_keywords")
    private List<String> styleKeywords;

    @SerializedName("color_keywords")
    private List<String> colorKeywords;

    @SerializedName("season_tags")
    private List<String> seasonTags;

    @SerializedName("occasion_tags")
    private List<String> occasionTags;

    @SerializedName("target_hsv_hint")
    private TargetHsvHint targetHsvHint;

    public AiSuggestionOption() {
    }

    public AiSuggestionOption(String label,
                              List<String> colorKeywords,
                              List<String> seasonTags,
                              List<String> occasionTags) {
        this.label = label;
        this.colorKeywords = colorKeywords;
        this.seasonTags = seasonTags;
        this.occasionTags = occasionTags;
    }

    public String getLabel() {
        return label != null ? label.trim() : "";
    }

    public String getBaseColor() {
        return baseColor != null ? baseColor.trim() : "";
    }

    public String getFinish() {
        return finish != null ? finish.trim() : "";
    }

    public List<String> getStyleKeywords() {
        return styleKeywords != null ? styleKeywords : new ArrayList<>();
    }

    public List<String> getColorKeywords() {
        return colorKeywords != null ? colorKeywords : new ArrayList<>();
    }

    public List<String> getSeasonTags() {
        return seasonTags != null ? seasonTags : new ArrayList<>();
    }

    public List<String> getOccasionTags() {
        return occasionTags != null ? occasionTags : new ArrayList<>();
    }

    public String getDepth() {
        return depth != null ? depth.trim() : "";
    }

    public TargetHsvHint getTargetHsvHint() {
        return targetHsvHint != null ? targetHsvHint : new TargetHsvHint();
    }

    public static class TargetHsvHint {
        @SerializedName("hue_family")
        private String hueFamily;
        @SerializedName("saturation")
        private String saturation;
        @SerializedName("value")
        private String value;

        public String getHueFamily() {
            return hueFamily != null ? hueFamily.trim() : "";
        }

        public String getSaturation() {
            return saturation != null ? saturation.trim() : "";
        }

        public String getValue() {
            return value != null ? value.trim() : "";
        }
    }
}

